# Google Play Data Safety Declaration Guidance — MeshBoard

This document details the exact, evidence-based answers required for the **Data Safety** questionnaire in Google Play Console for **MeshBoard** (`com.vivescriptsolutions.meshboard`).

---

## 1. Overall Data Collection Summary

| Question | Recommended Answer | Rationale from Codebase |
| :--- | :--- | :--- |
| **Does your app collect or share any user data?** | **No** (or "Data is processed locally on device only") | MeshBoard has no cloud servers, no analytics trackers, and no external databases. All database writes occur strictly within on-device SQLite (`meshboard_database`). |
| **Is all user data collected encrypted in transit?** | **Yes** (or N/A) | Physical Nearby Connections links use Google Play Services native P2P encryption (AES-128 via DTLS/TLS). |
| **Do you provide a way for users to request that their data be deleted?** | **Yes** | Users can tap "Clear Local Message History" in Settings, which wipes the database, or uninstall the app. |

---

## 2. Category-Specific Review

### 2.1 Personal Info (Name, Email, Phone, User IDs)
- **Collected:** No remote collection.
- **Local Profile:** The user chooses a local display name stored only in Room `user_profile` table.
- **Google Play Declaration:** Declare **No** for Personal Info collection, or if required to disclose local on-device identifiers, select:
  - Data Type: *User IDs (random profile UUID generated locally)*
  - Collected: Yes (On-device only)
  - Shared: No
  - Ephemeral processing: No (stored in local database)
  - Purpose: App functionality

### 2.2 Photos & Videos / Camera
- **Camera:** The Camera is accessed strictly in-memory by CameraX to decode QR codes in `QrCodeAnalyzer.kt`.
- **Declaration:** No images or photos are collected, recorded, saved, or shared.

### 2.3 Messages
- **Public Bulletin Messages:** Transmitted locally over peer-to-peer radio without transmitting to any remote cloud server.
- **Declaration:** Not shared with third parties or cloud servers.

### 2.4 Location
- MeshBoard **does not** collect, track, or record GPS location coordinates.
- Legacy `ACCESS_FINE_LOCATION` is declared with `android:maxSdkVersion="32"` solely because Android 11 and earlier required location permission for Bluetooth Low Energy discovery. Android 12+ uses `BLUETOOTH_SCAN` with `neverForLocation`.

---

## 3. Play Policy Compliance Checklist
- [x] Zero dynamic code loading (`dex`, `jar`, `so`)
- [x] No broad storage permissions (`READ_EXTERNAL_STORAGE` is NOT used)
- [x] App title strictly within 30 characters
- [x] Transparent public bulletin disclosures in Privacy Policy
