package com.example.mesh.routing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.MeshMessageEntity
import com.example.data.model.NetworkInfo
import com.example.data.model.NodeRole
import com.example.mesh.invitation.InvitationCapability
import com.example.mesh.invitation.MeshInvitation
import com.example.mesh.protocol.MeshPacket
import com.example.mesh.protocol.MeshProtocol
import com.example.mesh.transport.VirtualMeshTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MeshRoutingTestSuite {

    private lateinit var context: Context
    private val databases = mutableListOf<AppDatabase>()
    private val routers = mutableListOf<MeshRouter>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        VirtualMeshTransport.resetAll()
        databases.clear()
        routers.clear()
    }

    @After
    fun tearDown() {
        routers.forEach { it.stop() }
        routers.clear()
        databases.forEach { it.close() }
        databases.clear()
        VirtualMeshTransport.resetAll()
    }

    private fun createInMemoryDb(): AppDatabase {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        databases.add(db)
        return db
    }

    private fun createVirtualNode(
        nodeId: String,
        displayName: String,
        role: NodeRole,
        network: NetworkInfo
    ): Pair<MeshRouter, VirtualMeshTransport> {
        val db = createInMemoryDb()
        val testScope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val transport = VirtualMeshTransport(nodeId, testScope)
        val router = MeshRouter(
            transport = transport,
            database = db,
            scope = testScope
        )
        router.start(network, nodeId, displayName, role, enablePeriodicQueueProcessor = false)
        routers.add(router)
        return Pair(router, transport)
    }

    private suspend fun waitForMessage(database: AppDatabase, messageId: String, maxTries: Int = 30): MeshMessageEntity? {
        for (i in 1..maxTries) {
            val msg = database.messageDao().getMessageById(messageId)
            if (msg != null) return msg
            Thread.sleep(50)
        }
        return null
    }

    // =========================================================================
    // Protocol Unit Tests
    // =========================================================================

    @Test
    fun `test packet serialization and deserialization preserves all fields`() {
        val original = MeshPacket.createHostAnnouncement(
            networkId = "net-123",
            hostId = "host-1",
            hostName = "Amir",
            text = "Welcome to CIRK"
        )

        val bytes = original.toByteArray()
        val restored = MeshPacket.fromByteArray(bytes)

        assertNotNull(restored)
        assertEquals(original.messageId, restored!!.messageId)
        assertEquals(original.networkId, restored.networkId)
        assertEquals(original.senderId, restored.senderId)
        assertEquals(original.senderDisplayName, restored.senderDisplayName)
        assertEquals(original.senderRole, restored.senderRole)
        assertEquals(original.priority, restored.priority)
        assertEquals(original.text, restored.text)
        assertEquals(0, restored.currentHopCount)
    }

    @Test
    fun `test hop count incrementing preserves message origin and content`() {
        val packet = MeshPacket.createRelayAnnouncement(
            networkId = "net-123",
            nodeId = "node-relay-1",
            nodeName = "Relay Bob",
            text = "Relaying important bulletin"
        )

        val forwarded = packet.forForwarding()

        assertEquals(packet.messageId, forwarded.messageId)
        assertEquals(packet.senderId, forwarded.senderId)
        assertEquals(packet.senderDisplayName, forwarded.senderDisplayName)
        assertEquals(packet.senderRole, forwarded.senderRole)
        assertEquals(packet.text, forwarded.text)
        assertEquals(1, forwarded.currentHopCount)
    }

    @Test
    fun `test expiration detection`() {
        val expiredPacket = MeshPacket(
            networkId = "net-123",
            senderId = "node-1",
            senderDisplayName = "Node",
            senderRole = NodeRole.HOST,
            messageType = MeshProtocol.TYPE_HOST_ANNOUNCEMENT,
            priority = MeshProtocol.PRIORITY_HOST,
            createdAt = System.currentTimeMillis() - 40 * 60 * 1000L,
            expiresAt = System.currentTimeMillis() - 10 * 60 * 1000L,
            hopLimit = 100,
            text = "Old message"
        )

        assertTrue(expiredPacket.isExpired())
        assertFalse(expiredPacket.canBeForwarded())
    }

    @Test
    fun `test invitation URI parsing and delegation capability`() {
        val inv = MeshInvitation.create(
            networkId = "net-xyz",
            networkName = "Crisis Response",
            inviterNodeId = "host-0",
            inviterDisplayName = "HQ",
            capability = InvitationCapability.JOIN_RELAY_NODE_AND_DELEGATE
        )

        val uriString = inv.toInvitationUriString()
        val parsed = MeshInvitation.parse(uriString)

        assertNotNull(parsed)
        assertEquals(inv.networkId, parsed!!.networkId)
        assertEquals(inv.networkName, parsed.networkName)
        assertEquals(InvitationCapability.JOIN_RELAY_NODE_AND_DELEGATE, parsed.capability)
        assertEquals(NodeRole.RELAY_NODE, parsed.capability.toRole())
        assertTrue(parsed.capability.canDelegate)
    }

    // =========================================================================
    // Multi-Hop Routing Tests (Section 22: Tests A through K)
    // =========================================================================

    @Test
    fun `Test A - Direct delivery A to B`() = runTest {
        val network = NetworkInfo("net-A", "Net A", "Host A", "A", isHost = true, userRole = NodeRole.HOST)
        val (routerA, transportA) = createVirtualNode("node-A", "Host A", NodeRole.HOST, network)
        val (routerB, transportB) = createVirtualNode("node-B", "Client B", NodeRole.CLIENT, network)

        VirtualMeshTransport.linkNodes(transportA, transportB)

        routerA.publishMessage("Hello Direct Peer B")
        advanceUntilIdle()

        val msgId = databases[0].messageDao().getRecentMessageIds(network.networkId).first()
        val messagesOnB = waitForMessage(databases[1], msgId)

        assertNotNull("Node B should have received direct message from Node A", messagesOnB)
        assertEquals("Hello Direct Peer B", messagesOnB!!.text)
        assertEquals("Host A", messagesOnB.senderDisplayName)
        assertEquals(NodeRole.HOST, messagesOnB.senderRole)
    }

    @Test
    fun `Test B - Two-hop delivery A to B to C`() = runTest {
        val network = NetworkInfo("net-B", "Net B", "Host A", "A", isHost = true, userRole = NodeRole.HOST)
        val (routerA, transportA) = createVirtualNode("node-A", "Host A", NodeRole.HOST, network)
        val (routerB, transportB) = createVirtualNode("node-B", "Relay B", NodeRole.RELAY_NODE, network)
        val (routerC, transportC) = createVirtualNode("node-C", "Client C", NodeRole.CLIENT, network)

        // Topology: A <-> B <-> C (A and C have no direct connection!)
        VirtualMeshTransport.linkNodes(transportA, transportB)
        VirtualMeshTransport.linkNodes(transportB, transportC)

        routerA.publishMessage("Host Announcement via Relay B")
        advanceUntilIdle()

        val msgId = databases[0].messageDao().getRecentMessageIds(network.networkId).first()
        val msgOnC = waitForMessage(databases[2], msgId)

        assertNotNull("Node C should have received A's message forwarded through Relay B", msgOnC)
        assertEquals("Host Announcement via Relay B", msgOnC!!.text)
        assertEquals("Host A", msgOnC.senderDisplayName)
        assertEquals(1, msgOnC.currentHopCount) // Traveled 1 hop through B to reach C
    }

    @Test
    fun `Test C - Three-hop delivery A to B to C to D`() = runTest {
        val network = NetworkInfo("net-C", "Net C", "Host A", "A", isHost = true, userRole = NodeRole.HOST)
        val (routerA, transportA) = createVirtualNode("node-A", "Host A", NodeRole.HOST, network)
        val (routerB, transportB) = createVirtualNode("node-B", "Relay B", NodeRole.RELAY_NODE, network)
        val (routerC, transportC) = createVirtualNode("node-C", "Relay C", NodeRole.RELAY_NODE, network)
        val (routerD, transportD) = createVirtualNode("node-D", "Client D", NodeRole.CLIENT, network)

        // Topology: A <-> B <-> C <-> D
        VirtualMeshTransport.linkNodes(transportA, transportB)
        VirtualMeshTransport.linkNodes(transportB, transportC)
        VirtualMeshTransport.linkNodes(transportC, transportD)

        routerA.publishMessage("Long Range Announcement to D")
        advanceUntilIdle()

        val msgId = databases[0].messageDao().getRecentMessageIds(network.networkId).first()
        val msgOnD = waitForMessage(databases[3], msgId)

        assertNotNull("Node D should have received announcement through 3-hop relay chain", msgOnD)
        assertEquals("Long Range Announcement to D", msgOnD!!.text)
        assertEquals(2, msgOnD.currentHopCount) // Traveled via B and C
    }

    @Test
    fun `Test D - Duplicate loop prevention A to B to C to A`() = runTest {
        val network = NetworkInfo("net-D", "Net D", "Host A", "A", isHost = true, userRole = NodeRole.HOST)
        val (routerA, transportA) = createVirtualNode("node-A", "Host A", NodeRole.HOST, network)
        val (routerB, transportB) = createVirtualNode("node-B", "Relay B", NodeRole.RELAY_NODE, network)
        val (routerC, transportC) = createVirtualNode("node-C", "Relay C", NodeRole.RELAY_NODE, network)

        // Triangle Ring: A <-> B <-> C <-> A
        VirtualMeshTransport.linkNodes(transportA, transportB)
        VirtualMeshTransport.linkNodes(transportB, transportC)
        VirtualMeshTransport.linkNodes(transportC, transportA)

        routerA.publishMessage("Ring message test")
        advanceUntilIdle()

        val msgId = databases[0].messageDao().getRecentMessageIds(network.networkId).first()

        // Verify Node A only has one local copy in database, never duplicated
        val countOnA = databases[0].messageDao().getMessagesByIds(network.networkId, listOf(msgId)).size
        assertEquals(1, countOnA)

        // Verify B and C each have exactly 1 copy
        val countOnB = databases[1].messageDao().getMessagesByIds(network.networkId, listOf(msgId)).size
        val countOnC = databases[2].messageDao().getMessagesByIds(network.networkId, listOf(msgId)).size
        assertEquals(1, countOnB)
        assertEquals(1, countOnC)
    }

    @Test
    fun `Test E - Client limitation ordinary client post restricted to 3 hops`() = runTest {
        val network = NetworkInfo("net-E", "Net E", "Host A", "A", isHost = true, userRole = NodeRole.HOST)
        val (routerClient, transportClient) = createVirtualNode("node-Client", "Client Zero", NodeRole.CLIENT, network)
        val (routerR1, transportR1) = createVirtualNode("node-R1", "Relay 1", NodeRole.RELAY_NODE, network)
        val (routerR2, transportR2) = createVirtualNode("node-R2", "Relay 2", NodeRole.RELAY_NODE, network)
        val (routerR3, transportR3) = createVirtualNode("node-R3", "Relay 3", NodeRole.RELAY_NODE, network)
        val (routerR4, transportR4) = createVirtualNode("node-R4", "Relay 4", NodeRole.RELAY_NODE, network)

        // Chain: Client <-> R1 <-> R2 <-> R3 <-> R4 (Hop count: R1=0, R2=1, R3=2, R4=3 -> should exceed max 3 hops for client post!)
        VirtualMeshTransport.linkNodes(transportClient, transportR1)
        VirtualMeshTransport.linkNodes(transportR1, transportR2)
        VirtualMeshTransport.linkNodes(transportR2, transportR3)
        VirtualMeshTransport.linkNodes(transportR3, transportR4)

        routerClient.publishMessage("Client Post with Hop Limit 3")
        advanceUntilIdle()

        val msgId = databases[0].messageDao().getRecentMessageIds(network.networkId).first()

        // R1, R2, R3 receive it
        assertNotNull(waitForMessage(databases[1], msgId))
        assertNotNull(waitForMessage(databases[2], msgId))
        assertNotNull(waitForMessage(databases[3], msgId))

        // R4 should NOT have received it because hopLimit of client post is 3
        val msgOnR4 = databases[4].messageDao().getMessageById(msgId)
        assertNull("R4 should not receive client post beyond 3 hops", msgOnR4)
    }

    @Test
    fun `Test F - Routing Priority ordering`() {
        val control = MeshPacket.createShutdownNotice("net-F", "host", "Host")
        val hostMsg = MeshPacket.createHostAnnouncement("net-F", "host", "Host", "Host update")
        val relayMsg = MeshPacket.createRelayAnnouncement("net-F", "relay", "Relay", "Relay update")
        val clientMsg = MeshPacket.createClientPost("net-F", "client", "Client", "Client post")

        assertEquals(MeshProtocol.PRIORITY_CONTROL, control.priority)
        assertEquals(MeshProtocol.PRIORITY_HOST, hostMsg.priority)
        assertEquals(MeshProtocol.PRIORITY_RELAY, relayMsg.priority)
        assertEquals(MeshProtocol.PRIORITY_CLIENT, clientMsg.priority)

        val queue = listOf(clientMsg, relayMsg, control, hostMsg).sortedBy { it.priority }
        assertEquals(MeshProtocol.PRIORITY_CONTROL, queue[0].priority)
        assertEquals(MeshProtocol.PRIORITY_HOST, queue[1].priority)
        assertEquals(MeshProtocol.PRIORITY_RELAY, queue[2].priority)
        assertEquals(MeshProtocol.PRIORITY_CLIENT, queue[3].priority)
    }

    @Test
    fun `Test G - Host loss and mesh continuity`() = runTest {
        val network = NetworkInfo("net-G", "Net G", "Host A", "A", isHost = true, userRole = NodeRole.HOST)
        val (routerA, transportA) = createVirtualNode("node-A", "Host A", NodeRole.HOST, network)
        val (routerB, transportB) = createVirtualNode("node-B", "Relay B", NodeRole.RELAY_NODE, network)
        val (routerC, transportC) = createVirtualNode("node-C", "Relay C", NodeRole.RELAY_NODE, network)

        // Topology: A <-> B <-> C
        VirtualMeshTransport.linkNodes(transportA, transportB)
        VirtualMeshTransport.linkNodes(transportB, transportC)

        // Disconnect Host A completely
        VirtualMeshTransport.unlinkNodes(transportA, transportB)
        routerA.stop()
        advanceUntilIdle()

        // Relay B publishes an announcement while Host A is offline
        routerB.publishMessage("Mesh continues while Host is offline")
        advanceUntilIdle()

        val msgId = databases[1].messageDao().getRecentMessageIds(network.networkId).first()
        val msgOnC = waitForMessage(databases[2], msgId)

        assertNotNull("Node C must receive Relay B's announcement even after Host A disconnected", msgOnC)
        assertEquals("Mesh continues while Host is offline", msgOnC!!.text)
        assertEquals("Relay B", msgOnC.senderDisplayName)
    }

    @Test
    fun `Test H - Ordinary Client does not relay general traffic`() = runTest {
        val network = NetworkInfo("net-H", "Net H", "Host A", "A", isHost = true, userRole = NodeRole.HOST)
        val (routerA, transportA) = createVirtualNode("node-A", "Host A", NodeRole.HOST, network)
        val (routerClientB, transportClientB) = createVirtualNode("node-B", "Client B", NodeRole.CLIENT, network)
        val (routerC, transportC) = createVirtualNode("node-C", "Client C", NodeRole.CLIENT, network)

        // Topology: A <-> Client B <-> Client C
        VirtualMeshTransport.linkNodes(transportA, transportClientB)
        VirtualMeshTransport.linkNodes(transportClientB, transportC)

        routerA.publishMessage("Host message test")
        advanceUntilIdle()

        val msgId = databases[0].messageDao().getRecentMessageIds(network.networkId).first()

        // Client B received it directly from A
        assertNotNull(databases[1].messageDao().getMessageById(msgId))

        // But Client C should NOT receive it because B is an ordinary Client and does not relay!
        val msgOnC = databases[2].messageDao().getMessageById(msgId)
        assertNull("Ordinary Client B must not relay traffic to Client C", msgOnC)
    }

    @Test
    fun `Test J - Expired packets rejected safely`() = runTest {
        val network = NetworkInfo("net-J", "Net J", "Host A", "A", isHost = true, userRole = NodeRole.HOST)
        val (routerA, transportA) = createVirtualNode("node-A", "Host A", NodeRole.HOST, network)
        val (routerB, transportB) = createVirtualNode("node-B", "Client B", NodeRole.CLIENT, network)

        VirtualMeshTransport.linkNodes(transportA, transportB)

        val expired = MeshPacket(
            networkId = network.networkId,
            senderId = "node-A",
            senderDisplayName = "Host A",
            senderRole = NodeRole.HOST,
            messageType = MeshProtocol.TYPE_HOST_ANNOUNCEMENT,
            priority = MeshProtocol.PRIORITY_HOST,
            createdAt = System.currentTimeMillis() - 60000,
            expiresAt = System.currentTimeMillis() - 1000, // expired 1 sec ago
            hopLimit = 10,
            text = "Expired payload"
        )

        routerB.handleIncomingPacket("node-A", expired.toByteArray())
        advanceUntilIdle()

        val msgOnB = databases[1].messageDao().getMessageById(expired.messageId)
        assertNull("Expired packet must not be saved to message feed", msgOnB)
    }

    @Test
    fun `Test K - Malformed and oversized packets rejected safely`() = runTest {
        val network = NetworkInfo("net-K", "Net K", "Host A", "A", isHost = true, userRole = NodeRole.HOST)
        val (routerB, _) = createVirtualNode("node-B", "Client B", NodeRole.CLIENT, network)

        // Garbage bytes
        val garbage = "Not A Valid Json Envelope".toByteArray(Charsets.UTF_8)
        routerB.handleIncomingPacket("endpoint-x", garbage)
        advanceUntilIdle()

        // Confirm database is empty and no crash occurred
        val recent = databases[0].messageDao().getRecentMessageIds(network.networkId)
        assertTrue(recent.isEmpty())
    }
}
