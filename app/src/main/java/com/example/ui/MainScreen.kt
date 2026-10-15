package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.NodeRole
import com.example.mesh.routing.NetworkMeshStatus
import com.example.ui.components.MessageCard
import com.example.ui.components.RoleBadge
import com.example.ui.components.StatusChip
import com.example.ui.dialogs.*
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.ui.theme.CirkBlue
import com.example.ui.theme.CirkHostGold
import com.example.ui.theme.CirkPink
import com.example.ui.theme.CirkWarningOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.router.transport.connectedPeers.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showNodesDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var messageInput by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissInfo()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.activeNetwork?.networkName ?: "CIRK",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (uiState.activeNetwork != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                RoleBadge(role = uiState.activeNetwork!!.userRole)
                            }
                        }

                        if (uiState.activeNetwork != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusChip(status = uiState.meshStatus)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "👥 ${uiState.connectedPeerCount} peers • 📡 ${uiState.activeRelayCount} relays",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = "Your people. Your network. Anywhere.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.activeNetwork != null) {
                        IconButton(
                            onClick = { showInviteDialog = true },
                            modifier = Modifier.testTag("invite_button")
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = "QR Invitation")
                        }
                        IconButton(
                            onClick = { showNodesDialog = true },
                            modifier = Modifier.testTag("nodes_button")
                        ) {
                            Icon(Icons.Default.Hub, contentDescription = "Mesh Nodes")
                        }
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (uiState.activeNetwork != null) {
                // Message composition bottom bar
                Surface(
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val role = uiState.activeNetwork!!.userRole
                            val roleLabel = when (role) {
                                NodeRole.HOST -> "Posting as CIRK Host [Priority 1 • Max 1024 hops]"
                                NodeRole.RELAY_NODE -> "Posting as CIRK Relay [Priority 2 • Max 1024 hops]"
                                NodeRole.CLIENT -> "Posting as CIRK Member [Priority 3 • Max 3 hops]"
                            }

                            Text(
                                text = roleLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = when (role) {
                                    NodeRole.HOST -> CirkHostGold
                                    NodeRole.RELAY_NODE -> CirkBlue
                                    NodeRole.CLIENT -> CirkPink
                                },
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = "${messageInput.length}/1000",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (messageInput.length > 950) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = messageInput,
                                onValueChange = { if (it.length <= 1000) messageInput = it },
                                placeholder = { Text("Write public announcement…") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("message_input_field"),
                                maxLines = 4,
                                shape = RoundedCornerShape(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (messageInput.isNotBlank()) {
                                        viewModel.publishMessage(messageInput)
                                        messageInput = ""
                                    }
                                },
                                enabled = messageInput.isNotBlank(),
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (messageInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    )
                                    .testTag("send_message_button")
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Send Announcement",
                                    tint = if (messageInput.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Section 12.1 Host Offline Continuity Banner
            AnimatedVisibility(visible = uiState.meshStatus == NetworkMeshStatus.HOST_OFFLINE_MESH_CONTINUING) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = CirkWarningOrange.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CirkWarningOrange.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = CirkWarningOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Host offline — CIRK continuing. Relay nodes are forwarding messages seamlessly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CirkWarningOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Section 12.3 Shutdown Countdown Banner
            AnimatedVisibility(visible = uiState.shutdownCountdown != null && uiState.shutdownCountdown!! > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Host has scheduled network closure in ${uiState.shutdownCountdown} seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (uiState.activeNetwork == null) {
                // Welcome / Standby State (Section 13.1)
                WelcomeStandbyContent(
                    profile = uiState.userProfile,
                    onCreateNetworkClick = { showCreateDialog = true },
                    onJoinNetworkClick = { showJoinDialog = true }
                )
            } else {
                // Public Bulletin Feed (Section 10)
                if (uiState.messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Campaign,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Bulletin Board is Empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Post the first announcement or wait for nearby nodes to relay updates.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = uiState.messages,
                            key = { it.messageId }
                        ) { message ->
                            MessageCard(message = message)
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showCreateDialog) {
        CreateNetworkDialog(
            initialHostName = uiState.userProfile?.displayName ?: "Organizer",
            onDismiss = { showCreateDialog = false },
            onCreate = { name, alias, desc, canDelegate ->
                showCreateDialog = false
                viewModel.createNetwork(name, alias, desc, canDelegate)
            }
        )
    }

    if (showJoinDialog) {
        JoinNetworkDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { invitation ->
                showJoinDialog = false
                viewModel.joinNetwork(invitation)
            }
        )
    }

    if (showInviteDialog && uiState.activeNetwork != null && uiState.userProfile != null) {
        QrInvitationDialog(
            network = uiState.activeNetwork!!,
            localNodeId = uiState.userProfile!!.profileId,
            localDisplayName = uiState.userProfile!!.displayName,
            canDelegate = uiState.activeNetwork!!.canDelegate,
            onDismiss = { showInviteDialog = false }
        )
    }

    if (showNodesDialog && uiState.activeNetwork != null) {
        NodeManagementDialog(
            network = uiState.activeNetwork!!,
            connectedPeers = connectedPeers,
            approvedNodes = uiState.approvedNodes,
            onApprovePeer = { nodeId, name, canDelegate ->
                viewModel.approvePeerAsRelay(nodeId, name, canDelegate)
            },
            onRevokeNode = { nodeId ->
                viewModel.revokePeerRelay(nodeId)
            },
            onDismiss = { showNodesDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            profile = uiState.userProfile,
            network = uiState.activeNetwork,
            isBatterySaver = uiState.isBatterySaverActive,
            onUpdateName = { viewModel.updateDisplayName(it) },
            onToggleBattery = { viewModel.toggleBatterySaver(it) },
            onClearHistory = { viewModel.clearMessageHistory() },
            onLeaveNetwork = {
                viewModel.leaveNetwork()
                showSettingsDialog = false
            },
            onShutdownNetwork = { seconds ->
                viewModel.shutdownNetwork(seconds)
            },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun WelcomeStandbyContent(
    profile: com.example.data.model.UserProfile?,
    onCreateNetworkClick: () -> Unit,
    onJoinNetworkClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // CIRK Brand Emblem
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_cirk_logo),
                contentDescription = "CIRK Logo",
                tint = Color.Unspecified,
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "CIRK",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )

        Text(
            text = "pronounced “sirk”",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Your people. Your network. Anywhere.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "A circle of people connected by a network of nodes, even when conventional connectivity is unavailable. Stay in touch at crowded festivals, campus groups, outdoor events, and during cellular outages.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Profile Identity Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your CIRK Profile",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = profile?.displayName ?: "Anonymous Member",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Button(
            onClick = onCreateNetworkClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("create_network_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create CIRK Network as Host", style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onJoinNetworkClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("join_network_button")
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Join CIRK via QR / Code", style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "CIRK Connect • Offline Mesh Bulletin & Relaying\nNo accounts, passwords, phone numbers, or cloud servers required.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 11.sp
        )
    }
}
