# CIRK — Offline Mesh Network

[![License: MIT](https://img.shields.io/badge/License-MIT-teal.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android_8.0%2B-blue.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack_Compose_M3-green.svg)](https://developer.android.com/jetpack/compose)

> **CIRK** *(pronounced “sirk”)*  
> **Your people. Your network. Anywhere.**  
> *A circle of people connected by a network of nodes, even when conventional connectivity is unavailable.*

**CIRK** is a youth-friendly, decentralized, offline-first peer-to-peer mesh networking application for Android. Built for campus groups, crowded festivals, outdoor expeditions, clubs, and local community resilience during network outages.

---

## 1. Product Family Vision

CIRK is designed not as a grim survivalist utility, but as a vibrant, social, community-first network family:

- **CIRK Connect (Current Core):** Offline-first mesh messaging, community bulletin boards, network relaying, and decentralized store-and-forward coordination.
- **CIRK Map (Conceptual Extension):** Downloaded offline vector maps, community meeting points, landmark pins, and rendezvous coordinates.
- **CIRK Ready (Conceptual Extension):** Offline guides, supply checklists, first aid references, and packing essentials.

---

## 2. Key Highlights & Core Principles

- **Zero Cloud Infrastructure:** No remote servers, Firebase Cloud Messaging, WebSockets, cellular data, or online accounts are required.
- **Real Multi-Hop Store-and-Forward Relaying:** Eligible announcements propagate across chains of connected devices (`A → B → C → D`). Recipients receive announcements without needing direct line-of-sight to the original author.
- **Host Disconnect Tolerance:** If the original network Host disconnects, authorized Relay Nodes continue forwarding messages and maintaining the local mesh without interruption.
- **Verifiable QR Invitations:** Create scoped invitations offline (`JOIN_CLIENT`, `JOIN_RELAY_NODE`, `JOIN_RELAY_NODE_AND_DELEGATE`) with cryptographic delegation tokens.
- **Bounded Resources & Battery Efficiency:** Strict duplicate suppression cache, bounded priority queues, 1,000-character plain text limits, and battery-conscious discovery mode.
- **Local SQLite / Room Persistence:** Local message history, seen caches, and pending relay queues survive device reboots without leaking data.

---

## 3. Brand Identity & Visual System

- **Brand Name:** CIRK (pronounced “sirk”)
- **Tagline:** Your people. Your network. Anywhere.
- **Logo Symbol:** An open orbital blue ring (`#60A5FA`) connecting three vibrant community nodes:
  - Top Node: Fresh Green (`#34D399`)
  - Right Node: Warm Amber (`#FBBF24`)
  - Left Node: Electric Pink (`#F472B6`)
  - Center Node: Pure White (`#F9FAFB`)
- **Theme:** High-contrast technical dark theme on Deep Navy (`#0B132B`).

---

## 4. Multi-Hop Protocol Envelope & Priorities

Each packet is serialized into a compact JSON payload conforming to Protocol Version 1:

| Priority | Level | Description | Hop Limit |
| :--- | :--- | :--- | :--- |
| **0** | Control | Node approvals, handshakes, shutdown notices | Up to 1024 |
| **1** | Host Bulletins | High-priority bulletins from the network creator | Up to 1024 |
| **2** | Relay Bulletins | Bulletins authored by authorized relay nodes | Up to 1024 |
| **3** | Member Posts | Local participant announcements | **Max 3 hops** |

---

## 5. Physical Multi-Phone Testing Guide (3+ Devices)

1. **Setup:** Install CIRK on three Android phones (Phone A, Phone B, Phone C) with Bluetooth and Wi-Fi enabled.
2. **Create Network:** On Phone A, tap **"Create CIRK Network as Host"**. Enter Name `"Campus Circle"`.
3. **Authorize Relay:** On Phone A, tap **QR Invitation** and select **"Relay"**. Phone B scans the QR code.
4. **Relay Extension:** Move Phone C away from Phone A (out of Phone A's direct radio range). On Phone B, tap **QR Invitation** and select **"Member"**. Phone C scans Phone B's QR code.
5. **Verify Multi-Hop Relay (`A → B → C`):** Post an announcement on Phone A. Watch it propagate to Phone B and immediately forward to Phone C with the tag: `"Relayed via 1 hop"`.
6. **Host Loss Continuity:** Place Phone A into Airplane Mode. Post on Phone B. Phone C receives the announcement under `"Host offline — CIRK continuing"`.

---

## 6. Developer & Copyright

**Developer:** ViveScript Solutions LLC  
**Copyright:** © 2026 ViveScript Solutions LLC. All rights reserved.  
**Website:** [https://www.vivescriptsolutions.com/](https://www.vivescriptsolutions.com/)  
**License:** MIT License (Source code). App name, logo, icons, and branding are proprietary trademarks of ViveScript Solutions LLC.
