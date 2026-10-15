# Privacy Policy — MeshBoard

**Effective Date:** October 12, 2026  
**Developer:** ViveScript Solutions LLC  
**Application:** MeshBoard — Offline Bulletin Network (`com.vivescriptsolutions.meshboard`)  
**Website:** https://www.vivescriptsolutions.com/  

---

## 1. Overview and Core Commitment

ViveScript Solutions LLC developed **MeshBoard** as an offline-first, decentralized peer-to-peer bulletin board application. MeshBoard operates without internet connectivity, remote servers, cloud databases, user tracking, or online identity systems.

**We do not collect, store, sell, or transmit your personal data to remote servers or third parties.**

---

## 2. Information Handled by the Application

### 2.1 Local Profiles & Identifiers
- When you first open MeshBoard, an immutable random identifier (`profileId`) is generated locally on your device.
- You can choose any display name or alias. MeshBoard does not require your legal name, email address, phone number, physical address, or password.
- Your profile data is stored exclusively on your device’s local SQLite database.

### 2.2 Public Bulletin Messages
- MeshBoard is designed as a **public community bulletin board**.
- Any announcement or message you send is transmitted as cleartext across local radio links (Bluetooth / Wi-Fi) to other participating phones within range and relay chains.
- Because messages are public by design, you must never send private personal details, financial records, passwords, or confidential information through MeshBoard.

### 2.3 Network Membership & Invitations
- When you create or join a network, network identifiers and role authorization tokens are exchanged over local peer-to-peer links and saved in your device's local database.
- Membership information is never uploaded to any cloud server or central directory.

---

## 3. Device Permissions & Hardware Usage

MeshBoard requests only permissions necessary for offline peer-to-peer communication and core features:

| Permission | Purpose |
| :--- | :--- |
| `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` | Required to discover and connect to nearby Android devices using Bluetooth Low Energy. |
| `NEARBY_WIFI_DEVICES`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | Required to discover and establish high-speed direct peer connections between devices without a Wi-Fi router. |
| `CAMERA` | Used exclusively for scanning QR invitation codes locally in real time. Video feeds and photos are processed in memory and never saved to disk or transmitted. |
| `POST_NOTIFICATIONS` | Used to notify you when new unique announcements arrive from nearby peers. |
| `FOREGROUND_SERVICE` (`connectedDevice`) | Used to maintain active peer connections and message relaying when the app is in the background or the screen is off. |

MeshBoard **never** requests access to your contacts, SMS, call logs, microphones, or GPS location tracking.

---

## 4. Third-Party Services and Advertising

- **Google Play Services Nearby Connections:** MeshBoard uses Google's Nearby Connections API to coordinate offline peer-to-peer physical radio handshakes. Nearby Connections operates locally between devices.
- **Advertising:** Any development advertising SDKs are disabled by default during active mesh communications. In production releases, no user tracking identifiers or telemetry are sold or shared.

---

## 5. Data Retention & Deletion

- Public messages are stored locally on your device for up to 7 days or up to a local storage quota of 2,000 messages.
- You can delete all local message history and leave the active network at any time directly through the app's Settings menu.
- Uninstalling MeshBoard immediately removes all locally saved databases, encryption tokens, and profile data from your device.

---

## 6. Contact Information

If you have questions or concerns regarding this Privacy Policy:
- **Company:** ViveScript Solutions LLC
- **Email:** privacy@vivescriptsolutions.com
- **Website:** https://www.vivescriptsolutions.com/
