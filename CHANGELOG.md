# Changelog — CIRK

All notable changes to the CIRK project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.0] - 2026-10-14

### Changed
- **Rebranding to CIRK:** Completely rebranded application to **CIRK** (pronounced “sirk”).
  - **Tagline:** *"Your people. Your network. Anywhere."*
  - **Concept:** A circle of people connected by a network of nodes, even when conventional connectivity is unavailable.
- **Brand Geometry & Iconography:** Integrated official multi-node orbital logo (Sky Blue arc `#60A5FA`, Emerald `#34D399`, Amber `#FBBF24`, Pink `#F472B6`, and Center Node `#F9FAFB` on Navy `#0B132B`).
- **Color Theme Overhaul:** Applied CIRK dynamic color palette across Jetpack Compose Material 3 theme, role badges, status chips, and message cards.
- **Product Family Foundation:** Positioned core mesh bulletin functionality as **CIRK Connect**, establishing architectural room for future family expansions (CIRK Map, CIRK Ready).

---

## [1.0.0] - 2026-10-12

### Added
- Initial release of offline-first multi-hop store-and-forward mesh bulletin engine.
- Google Nearby Connections `P2P_CLUSTER` transport.
- Offline QR invitation system with CameraX scanning and ZXing bitmap generation.
- Room database with `Profile`, `Message`, `NetworkMetadata`, and `NodeAuthorization` entities.
- Full automated test suite (Tests A through K) covering multi-hop routing, duplicate suppression, and priority scheduling.
