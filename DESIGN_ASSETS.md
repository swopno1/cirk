# Design Assets & Visual Identity Specification — CIRK

**Application:** CIRK — Offline Mesh Network (`com.vivescriptsolutions.meshboard`)  
**Developer:** ViveScript Solutions LLC  
**Copyright:** © 2026 ViveScript Solutions LLC  
**Brand Pronunciation:** "sirk"  
**Tagline:** Your people. Your network. Anywhere.  
**Concept:** A circle of people connected by a network of nodes, even when conventional connectivity is unavailable.

---

## 1. Official CIRK Logo Specification

### 1.1 Vector Geometry
```xml
<svg viewBox="0 0 84 84" xmlns="http://www.w3.org/2000/svg">
  <circle cx="42" cy="42" r="24" fill="none" stroke="#60A5FA" stroke-width="5" stroke-dasharray="118 34" transform="rotate(-40 42 42)"/>
  <circle cx="42" cy="18" r="5.5" fill="#34D399"/>
  <circle cx="63" cy="51" r="5.5" fill="#FBBF24"/>
  <circle cx="22" cy="55" r="5.5" fill="#F472B6"/>
  <circle cx="42" cy="42" r="5" fill="#F9FAFB"/>
</svg>
```

### 1.2 Color Palette
- **CIRK Blue (Orbital Ring):** `#60A5FA` (Fresh Sky Blue)
- **CIRK Green (Top Node):** `#34D399` (Vibrant Emerald)
- **CIRK Amber (Right Node / Host):** `#FBBF24` (Warm Amber Gold)
- **CIRK Pink (Left Node / Member):** `#F472B6` (Electric Rose Pink)
- **CIRK Pure White (Center Node):** `#F9FAFB` (Central Connection Point)
- **CIRK Dark Navy (Background Base):** `#0B132B` (OLED-optimized deep navy)
- **CIRK Surface Variant:** `#1A2952`
- **CIRK High Surface:** `#24366B`

---

## 2. Adaptive Launcher Icons

- **Foreground:** `app/src/main/res/drawable/ic_launcher_foreground.xml` (Centered 66dp `ic_cirk_logo.xml`)
- **Background:** `app/src/main/res/drawable/ic_launcher_background.xml` (Solid Navy `#0B132B`)
- **Raster Fallbacks:** Rendered across all mipmap densities:
  - `mipmap-mdpi`: 48x48
  - `mipmap-hdpi`: 72x72
  - `mipmap-xhdpi`: 96x96
  - `mipmap-xxhdpi`: 144x144
  - `mipmap-xxxhdpi`: 192x192

---

## 3. Google Play Store Graphics Requirements

- **App Icon:** 512 x 512 px PNG with the CIRK orbital emblem on Deep Navy `#0B132B`.
- **Feature Graphic:** 1024 x 500 px. Centered CIRK logo with bold typography: *"CIRK — Your people. Your network. Anywhere."*
