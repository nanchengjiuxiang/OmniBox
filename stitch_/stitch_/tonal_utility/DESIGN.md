---
name: Tonal Utility
colors:
  surface: '#fdf8ff'
  surface-dim: '#ddd8e3'
  surface-bright: '#fdf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f7f1fc'
  surface-container: '#f1ecf7'
  surface-container-high: '#ece6f1'
  surface-container-highest: '#e6e0eb'
  on-surface: '#1c1b22'
  on-surface-variant: '#494551'
  inverse-surface: '#312f37'
  inverse-on-surface: '#f4eff9'
  outline: '#7a7582'
  outline-variant: '#cbc4d2'
  surface-tint: '#6750a4'
  primary: '#4f378a'
  on-primary: '#ffffff'
  primary-container: '#6750a4'
  on-primary-container: '#e0d2ff'
  inverse-primary: '#cfbcff'
  secondary: '#625b71'
  on-secondary: '#ffffff'
  secondary-container: '#e8def9'
  on-secondary-container: '#686177'
  tertiary: '#633b48'
  on-tertiary: '#ffffff'
  tertiary-container: '#7d5260'
  on-tertiary-container: '#ffcbda'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e9ddff'
  primary-fixed-dim: '#cfbcff'
  on-primary-fixed: '#22005d'
  on-primary-fixed-variant: '#4f378a'
  secondary-fixed: '#e8def9'
  secondary-fixed-dim: '#ccc2dc'
  on-secondary-fixed: '#1e192b'
  on-secondary-fixed-variant: '#4a4358'
  tertiary-fixed: '#ffd9e3'
  tertiary-fixed-dim: '#eeb8c8'
  on-tertiary-fixed: '#31111d'
  on-tertiary-fixed-variant: '#633b48'
  background: '#fdf8ff'
  on-background: '#1c1b22'
  surface-variant: '#e6e0eb'
typography:
  display-lg:
    fontFamily: Roboto Flex
    fontSize: 57px
    fontWeight: '400'
    lineHeight: 64px
    letterSpacing: -0.25px
  display-md:
    fontFamily: Roboto Flex
    fontSize: 45px
    fontWeight: '400'
    lineHeight: 52px
    letterSpacing: 0px
  headline-lg:
    fontFamily: Roboto Flex
    fontSize: 32px
    fontWeight: '400'
    lineHeight: 40px
    letterSpacing: 0px
  headline-lg-mobile:
    fontFamily: Roboto Flex
    fontSize: 28px
    fontWeight: '400'
    lineHeight: 36px
    letterSpacing: 0px
  headline-md:
    fontFamily: Roboto Flex
    fontSize: 28px
    fontWeight: '400'
    lineHeight: 36px
    letterSpacing: 0px
  headline-sm:
    fontFamily: Roboto Flex
    fontSize: 24px
    fontWeight: '400'
    lineHeight: 32px
    letterSpacing: 0px
  title-lg:
    fontFamily: Roboto Flex
    fontSize: 22px
    fontWeight: '500'
    lineHeight: 28px
    letterSpacing: 0px
  title-md:
    fontFamily: Roboto Flex
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 24px
    letterSpacing: 0.15px
  title-sm:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.1px
  body-lg:
    fontFamily: Roboto Flex
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.5px
  body-md:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.25px
  body-sm:
    fontFamily: Roboto Flex
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.4px
  label-lg:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.1px
  label-md:
    fontFamily: Roboto Flex
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
  label-sm:
    fontFamily: Roboto Flex
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-tablet: 1.5rem
  gutter-desktop: 1.5rem
  margin: 1rem
  margin-tablet: 1.5rem
  margin-desktop: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

The design system establishes an authentic Material 3 (Material You) expressive experience for an offline-first utility suite. It targets power users, developers, and daily device owners who demand prompt, zero-latency execution across utilities (e.g., file conversions, network diagnostics, unit computations, device sensors, cryptographic hashing).

The visual aesthetic balances pure utility with Android's fluid, tactile warmth. By leveraging tonal container stacking, deliberate dynamic color pairings, and standard M3 geometric primitives, the interface feels deeply integrated with the modern Android OS rather than feeling like a disconnected web wrapper.

Key design attributes:
- **System Native:** Direct adherence to Material 3 motion, spatial scale, and tonal elevation paradigms.
- **Offline-First Clarity:** Crisp visual states highlighting local execution speed, zero network dependency, and data sovereignty.
- **Tactile Softness:** Fluid transitions, pill-shaped interactive anchors, and soft corner radiuses (up to 28px) that balance high-density information with visual comfort.

## Colors

The color architecture directly incorporates Google Material 3 color roles and tonal palettes. While light mode serves as the baseline, full dual-mode support is mandatory for nighttime utility operations.

### Light Theme Roles
- **Primary Tone:** `#6750A4` (Key actions, active pill indicators, prominent focus rings)
- **On-Primary:** `#FFFFFF`
- **Primary Container:** `#EADDFF` (High-priority tool modules, active filter chips)
- **On-Primary Container:** `#21005D`
- **Secondary / Container:** `#625B71` / `#E8DEF8` (Supporting metadata, utility category tags)
- **On-Secondary / On-Secondary Container:** `#FFFFFF` / `#1D192B`
- **Tertiary / Container:** `#7D5260` / `#FFD8E4` (Accent badges, diagnostic warnings, battery/sensor alerts)
- **On-Tertiary Container:** `#31111D`
- **Surface Container Lowest:** `#FFFFFF` (Card surfaces resting on grouped containers)
- **Surface Container Low:** `#F7F2FA` (Background canvas for search & dense listings)
- **Surface Container:** `#F3EDF7` (Standard canvas background)
- **Surface Container High:** `#ECE6F0` (Bottom bar fill, modal sheet surfaces)
- **Surface Container Highest:** `#E6E0E9` (Input track fills, dividers)
- **Outline & Outline Variant:** `#79747E` / `#CAC4D0` (Structural borders, non-active switches, card outlines)
- **Error & Error Container:** `#B3261E` / `#F9DEDC` (Destructive actions, failed diagnostics)

### Dark Theme Equivalents
- **Primary / Primary Container:** `#D0BCFF` / `#4F378B`
- **On-Primary / On-Primary Container:** `#381E72` / `#EADDFF`
- **Surface Container:** `#211F26`
- **Surface Container Low / Lowest:** `#1D1B20` / `#0F0D13`
- **Surface Container High / Highest:** `#2B2930` / `#36343B`
- **Outline & Outline Variant:** `#938F99` / `#49454F`
- **Error Container:** `#8C1D18`

## Typography

Typography relies on `Roboto Flex` (with seamless platform fallback to `Google Sans` / `Roboto`) to provide precise legibility across technical outputs, conversion calculations, and localized utility labels.

Rules for hierarchical application:
- **Display & Headline Roles:** Reserved for category overview screens, numeric calculation readouts (e.g., hash checksum matches, compass bearings), and empty-state illustrations.
- **Title Roles:** Applied to tool card headers, dialogue actions, and top app bar page titles.
- **Body Roles:** Used for tool descriptions, parameter options, inline instructions, and monospace calculation outputs.
- **Label Roles:** Strictly bound to buttons, navigation bar labels, pill indicators, and chip text.

## Layout & Spacing

The layout model is anchored in an 8dp (0.5rem) spatial scale, supporting an adaptive multi-column grid across compact (phones), medium (foldables/small tablets), and expanded (tablets/desktops) form factors.

- **Mobile (<600dp):** Single column or 2-column grid for utility widgets. Outer screen horizontal padding is `margin` (16px), with `gutter` (16px) separation between tool tiles.
- **Tablet / Foldable (600dp–840dp):** 4 to 8 columns. Outer margins widen to `margin-tablet` (24px) with dual-pane support (e.g., tool categories list on the left, active calculation tool on the right).
- **Desktop / Large Displays (>840dp):** Up to 12 columns with max layout bounds clamped to 1200dp width. Bottom navigation automatically docks to the left edge as a vertical Navigation Rail.

## Elevation & Depth

Material 3 deprecates prominent directional drop shadows in favor of **Tonal Elevation Surfaces** tinted by the primary color role. When visual elevation increases, the component adopts a higher surface container tier rather than a darker drop shadow.

Elevation tiers:
- **Level 0 (Flat):** Surface Container (`#F3EDF7`). Base app background; zero shadow.
- **Level 1 (Low Cards / Search inactive):** Surface Container Low (`#F7F2FA`) with ambient tint: `box-shadow: 0px 1px 3px 1px rgba(103, 80, 164, 0.08), 0px 1px 2px 0px rgba(0, 0, 0, 0.12)`.
- **Level 2 (Active Cards / Pickers):** Surface Container (`#F3EDF7`) with `box-shadow: 0px 2px 6px 2px rgba(103, 80, 164, 0.10), 0px 1px 2px 0px rgba(0, 0, 0, 0.14)`.
- **Level 3 (Search Bar active / Dialogs / Bottom Sheets):** Surface Container High (`#ECE6F0`) with `box-shadow: 0px 4px 8px 3px rgba(103, 80, 164, 0.12), 0px 1px 3px 0px rgba(0, 0, 0, 0.14)`.
- **Level 4 & 5 (FAB / Modal Sheets):** Surface Container Highest (`#E6E0E9`) with maximum ambient diffusion.

## Shapes

The design system incorporates Material 3’s standard corner radius tokens:
- **shape-corner-extra-small (4px):** Checkbox boxes, snackbars.
- **shape-corner-small (8px):** Tooltip badges, segment buttons, mini status chips.
- **shape-corner-medium (12px):** Default input fields, tool parameter controls, grid action items.
- **shape-corner-large (16px):** Standard tool cards, dialogue boxes, bottom sheet top corners.
- **shape-corner-extra-large (28px):** Floating Action Buttons (FAB), docked search bars, modal cards, large utility cards.
- **shape-corner-full (9999px):** Navigation pill indicators, filter chips, circular action buttons, pill switches.

## Components

### 1. M3 Search Bar
- **Dimensions & Radius:** Height 56px, fully rounded at `shape-corner-extra-large` (28px).
- **Background & Color:** Inactive state uses `Surface Container High` (`#ECE6F0`); active/docked search switches to `Surface Container Lowest` (`#FFFFFF`).
- **Elements:** Leading search icon (`#49454F`), placeholder text styled with `body-lg` in `#79747E`, trailing utility voice/camera or offline-sync indicator badge.

### 2. M3 Buttons
- **Filled Button:** Height 40px, `shape-corner-full`, background `Primary` (`#6750A4`), label `On-Primary` (`#FFFFFF`) in `label-lg`.
- **Tonal Button:** Height 40px, `shape-corner-full`, background `Secondary Container` (`#E8DEF8`), label `On-Secondary Container` (`#1D192B`).
- **Outlined Button:** 1px border `Outline` (`#79747E`), background transparent, text `Primary` (`#6750A4`).

### 3. M3 Chips (Filter, Assist, Input)
- **Geometry:** Height 32px, `shape-corner-small` (8px) or `shape-corner-full`.
- **States:** Unselected filter chips carry an `Outline Variant` border (`#CAC4D0`) and transparent fill. Selected chips fill with `Primary Container` (`#EADDFF`), removing borders, with a leading checkmark in `On-Primary Container` (`#21005D`).

### 4. M3 Cards (Tool Tiles)
- **Filled Card:** Background `Surface Container Highest` (`#E6E0E9`), borderless, 16px radius (`shape-corner-large`).
- **Elevated Card:** Background `Surface Container Lowest` (`#FFFFFF`), 16px radius, Elevation Level 1.
- **Outlined Card:** Background `Surface Container Lowest` (`#FFFFFF`), border 1px `Outline Variant` (`#CAC4D0`), zero elevation.

### 5. Input Fields (Text Fields)
- **Filled Text Field:** Height 56px, rounded top corners (4px), bottom 1px active indicator line (`Primary` on focus), background `Surface Container Highest` (`#E6E0E9`).
- **Outlined Text Field:** Fully enclosed 16px or 12px rounded container with 1px border (`Outline` on blur, 2px `Primary` on focus). Floating label collapses into the top border cutout.

### 6. M3 Bottom Navigation Bar
- **Bar Architecture:** Height 80px, background `Surface Container` (`#F3EDF7`), zero border lines.
- **Pill Indicator:** Active destination is enclosed in an oblong pill indicator (height 32px, width 64px, `shape-corner-full`), filled with `Secondary Container` (`#E8DEF8`).
- **Icon & Typography:** Active icon `#1D192B` (`On-Secondary Container`); inactive icon `#49454F`. Label uses `label-md` (`font-weight: 500`), highlighting selected state with `On-Surface` (`#1D1B20`).

### 7. Selection Controls (Checkboxes & Radios)
- **Checkbox:** 18x18px, 4px rounded radius. Checked fill `Primary` with `On-Primary` check icon.
- **Switch:** Track width 52px, height 32px (`shape-corner-full`). Active track `Primary`, inactive track `Surface Container Highest` with `Outline` border. Thumb expands on interaction from 16px to 24px.