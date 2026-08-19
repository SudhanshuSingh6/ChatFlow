---
name: Modern Desktop Communication
colors:
  surface: '#f7f9fb'
  surface-dim: '#d8dadc'
  surface-bright: '#f7f9fb'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f4f6'
  surface-container: '#eceef0'
  surface-container-high: '#e6e8ea'
  surface-container-highest: '#e0e3e5'
  on-surface: '#191c1e'
  on-surface-variant: '#424754'
  inverse-surface: '#2d3133'
  inverse-on-surface: '#eff1f3'
  outline: '#727785'
  outline-variant: '#c2c6d6'
  surface-tint: '#005ac2'
  primary: '#0058be'
  on-primary: '#ffffff'
  primary-container: '#2170e4'
  on-primary-container: '#fefcff'
  inverse-primary: '#adc6ff'
  secondary: '#505f76'
  on-secondary: '#ffffff'
  secondary-container: '#d0e1fb'
  on-secondary-container: '#54647a'
  tertiary: '#006947'
  on-tertiary: '#ffffff'
  tertiary-container: '#00855b'
  on-tertiary-container: '#f5fff6'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d8e2ff'
  primary-fixed-dim: '#adc6ff'
  on-primary-fixed: '#001a42'
  on-primary-fixed-variant: '#004395'
  secondary-fixed: '#d3e4fe'
  secondary-fixed-dim: '#b7c8e1'
  on-secondary-fixed: '#0b1c30'
  on-secondary-fixed-variant: '#38485d'
  tertiary-fixed: '#6ffbbe'
  tertiary-fixed-dim: '#4edea3'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#f7f9fb'
  on-background: '#191c1e'
  surface-variant: '#e0e3e5'
typography:
  display:
    fontFamily: Plus Jakarta Sans
    fontSize: 48px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.3'
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.4'
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.5'
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: '1'
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '600'
    lineHeight: '1'
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  container-max: 1440px
  gutter: 24px
  margin-desktop: 40px
  margin-tablet: 24px
  margin-mobile: 16px
---

## Brand & Style

The design system is engineered for a high-performance desktop communication environment, prioritizing clarity, efficiency, and a welcoming atmosphere. The brand personality is professional yet approachable—avoiding the coldness of traditional enterprise software while maintaining the rigor required for productivity.

The visual style follows a **Modern / Corporate** aesthetic with a subtle **Glassmorphism** influence for overlay elements. This ensures the interface feels lightweight even when managing high information density. Every interaction is designed to feel crisp and deliberate, utilizing ample whitespace to reduce cognitive load during long-form communication and complex task management.

## Colors

The palette is centered around a vibrant primary blue, chosen for its association with reliability and digital fluency. This is supported by a sophisticated slate secondary palette that handles the majority of the UI framework and secondary information.

- **Primary (#3b82f6):** Used for action-orientated elements, focus states, and key brand moments.
- **Secondary (#64748b):** Utilized for iconography, secondary text, and auxiliary UI components.
- **Success (#10b981):** A bright emerald for presence indicators and confirmation messages.
- **Neutral (#f8fafc):** A cool-toned background foundation that provides a clean canvas for content.

The default mode is light, emphasizing a high-contrast, paper-like reading experience for long-form text.

## Typography

This design system utilizes **Plus Jakarta Sans** for all typography levels to ensure a cohesive, modern, and friendly tone. The type scale is optimized for desktop environments, where larger displays allow for more expressive headlines and highly legible body text.

The hierarchy focuses on "Standard" and "Bold" weights to maintain clarity. Line heights are intentionally generous to improve readability in messaging threads and documentation. Letter spacing is slightly tightened on larger display sizes to maintain visual tension, while smaller labels receive a slight tracking increase for legibility at small sizes.

## Layout & Spacing

This design system employs a **Fluid Grid** with a strict 12-column structure and a maximum container width of 1440px for desktop screens. This prevents content from stretching too wide on ultra-wide monitors, maintaining optimal line lengths for reading.

The spacing rhythm is based on an **8px linear scale**, ensuring vertical and horizontal alignment across all components. 

- **Desktop (1024px+):** 12 columns, 24px gutters, 40px outer margins.
- **Tablet (768px - 1023px):** 8 columns, 20px gutters, 24px outer margins.
- **Mobile (Up to 767px):** 4 columns, 16px gutters, 16px outer margins.

For desktop layouts, prioritize sidebars for navigation and secondary actions, leaving the central column for primary communication threads.

## Elevation & Depth

This design system uses a combination of **Tonal Layers** and **Ambient Shadows** to create a clear visual hierarchy.

- **Level 0 (Surface):** The background layer (`#f8fafc`).
- **Level 1 (Card/Container):** Pure white backgrounds with a 1px border (`#e2e8f0`) and no shadow for structured content.
- **Level 2 (Popovers/Dropdowns):** Pure white backgrounds with a soft, diffused shadow (0px 10px 15px -3px rgba(0, 0, 0, 0.1)) to indicate temporary elevation over the UI.
- **Level 3 (Modals):** High-elevation shadows with a backdrop blur (12px) on the underlying content to focus the user's attention.

Shadows should never be pure black; they are tinted with the secondary slate color to maintain a soft, integrated appearance.

## Shapes

The shape language is consistently **Rounded**, using a 0.5rem (8px) base radius. This creates a soft, modern feel that aligns with the "clean and approachable" brand personality.

- **Standard Elements:** 8px (0.5rem) for buttons, inputs, and small cards.
- **Large Containers:** 16px (1rem) for main dashboard panels and large modals.
- **Interactive States:** On hover, elements do not change their border radius, but may exhibit a subtle scale increase (1.02x) to acknowledge mouse interaction.

## Components

### Buttons
Primary buttons use the primary blue hex with white text. For desktop, the standard height is 40px with 20px horizontal padding. Hover states are indicated by a slightly darker blue shade, and active states by a subtle inset shadow.

### Inputs
Text fields feature a 1px border (`#cbd5e1`) and an 8px radius. On focus, the border transitions to the primary blue with a 3px soft outer glow (halo) to clearly indicate keyboard focus.

### Chips/Tags
Used for status indicators or message categories. They feature a desaturated background version of the status color (e.g., light blue background with dark blue text) and a pill-shaped radius.

### Cards
Cards are the primary container for information clusters. They should use a white background, the standard 8px radius, and a subtle 1px border rather than a shadow to keep the "clean" aesthetic.

### Navigation
Desktop navigation should be persistent in a left-hand sidebar or a top header. Icons should be paired with labels in `label-md` typography to ensure there is no ambiguity for the user.