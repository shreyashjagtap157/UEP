# Frontend Design System

## Scope

The web client uses React, TypeScript, Vite and a shared CSS stylesheet. The current application surface is styled through:

- `apps/web/src/styles/tokens.css` — primitive, semantic and component design tokens.
- `apps/web/src/styles/global.css` — global reset, compatibility aliases, shared component rules and responsive layouts.
- `apps/web/src/app/App.tsx` — persisted theme and density preferences, application shell and overview composition.
- `apps/web/index.html` — dark theme bootstrap before React mounts.

`global.css` imports `tokens.css` first. Keep token definitions centralized; component styles should consume semantic variables rather than define parallel color palettes.

## Three-tier token contract

### Tier 1: primitives

Primitive tokens represent raw values and are the only place where the neutral, accent and status palettes should be authored.

- Neutral palette: `--primitive-neutral-0` through `--primitive-neutral-10`, plus `--primitive-white`.
- Accent palette: `--primitive-accent-50` through `--primitive-accent-900`.
- Status palettes: `--primitive-success-*`, `--primitive-warning-*` and `--primitive-danger-*`.
- Typography: `--font-sans`, `--font-mono`, `--font-size-*` and `--tracking-*`.
- Spacing: `--space-*`, based on a 4px grid.
- Shape and motion: `--radius-*` and `--motion-*`.

Do not copy a primitive hex value into a component selector. Add or adjust a primitive here, then expose the intended role through a semantic token.

### Tier 2: semantic tokens

Semantic variables describe intent, not palette position. The default theme is dark:

- `--surface-canvas`, `--surface-elevated`, `--surface-raised`, `--surface-interactive` and `--surface-pressed`.
- `--border-subtle`, `--border-default` and `--border-strong`.
- `--text-primary`, `--text-secondary`, `--text-muted` and `--text-on-accent`.
- `--action-primary-bg`, `--action-primary-fg`, `--action-primary-hover` and `--action-primary-soft`.
- `--status-success-*`, `--status-warning-*` and `--status-danger-*`.

The dark palette is assigned to `:root` and `:root[data-theme="dark"]`. The light palette is assigned to `:root[data-theme="light"]`. A new theme must map semantic roles to existing primitives; it must not require rewriting component selectors.

Legacy aliases such as `--bg-canvas`, `--bg-surface`, `--accent`, `--danger-soft` and `--success-text` remain temporarily for existing workspace components. They resolve to semantic variables. New component CSS should use the semantic names directly; remove compatibility aliases only after all consumers have migrated.

### Tier 3: component defaults

Component-level defaults are intended for local overrides without reaching back into raw palette values:

- `--button-height` and `--button-padding-inline`
- `--control-height`
- `--card-padding`
- `--component-radius`

A component may override one of these variables at its own scope when the product interaction genuinely requires a different size. Avoid introducing a component token for a single decorative value when an existing spacing, radius or type token already expresses the need.

## Theme and density behavior

The HTML document starts with `data-theme="dark"`, preventing an initial light flash before React starts. `App` supports three persisted preferences:

- **Dark** sets `document.documentElement.dataset.theme` to `dark`.
- **Light** sets it to `light`.
- **System** follows `prefers-color-scheme` and subscribes to preference changes.

The preference is stored under `uep-theme`. The system option is retained as a preference rather than being replaced with its current resolved mode.

Content density is independently persisted under `uep-density` and applied as `data-density="compact|comfortable|spacious"`. Density changes the shared padding, gap and row-height aliases; it must not alter application state or business logic.

## Layout and surface principles

The authenticated overview is a responsive Bento grid. Its hero, daily schedule, identity metrics, assigned roles and academic summary occupy explicit grid areas. If the current identity cannot view the daily schedule, the overview falls back to a single-column composition without leaving an empty grid track.

Shared panels and cards use elevated or raised semantic surfaces, hairline borders and restrained elevation. The overview hero is the intentional focus surface and may use the low-contrast ambient glow and top-edge highlight. Do not add heavy shadows to ordinary cards or use decorative glows on every panel.

Metrics use `--font-mono` and tabular numerals. Heading tracking uses the tight tracking token. Labels and metadata use the muted semantic text role with sufficient contrast against their surface.

## Component styling rules

1. Use semantic color variables for foregrounds, backgrounds, borders, focus states and statuses. Raw hex, RGB, HSL or named palette values do not belong in component stylesheets.
2. Use `--space-*` for padding, margin and layout gaps. Use type and radius tokens for typography and corner shape.
3. Use the shared transition tokens for interactive controls. The standard easing is a 150ms cubic-bezier transition.
4. Retain visible keyboard focus. The shared `:focus-visible` treatment derives from `--border-strong`; component focus rules must not erase the focus shadow.
5. Preserve `prefers-reduced-motion`; avoid essential information that is only communicated through animation.
6. Keep ARIA labels, roles, live regions, disabled states and keyboard behavior independent of visual classes. Styling must not become an authorization or interaction mechanism.
7. Do not alter authentication, permission filtering, API contracts, routing, query state or workspace workflows as part of a visual-only change.

## Verification

From the repository root, the frontend qualification commands are:

```bash
npm --prefix apps/web ci --ignore-scripts
npm --prefix apps/web run lint
npm --prefix apps/web test
npm --prefix apps/web run build:production
```

The production build requires the real OIDC environment contract used by `.github/workflows/ci.yml`. The current GitHub Actions web job runs dependency installation, lint, web tests, Playwright test discovery and the production build.

Source-level design checks should confirm:

- No raw hex/RGB/HSL color values remain in `global.css`.
- Every `var(--...)` reference resolves to a declared token.
- Component font sizes, spacing declarations and border radii consume the centralized scales.
- Both theme values render correctly and System follows OS preference changes.
- The overview remains usable at narrow mobile widths and wide desktop widths.
- Keyboard focus remains visible and reduced-motion preferences are respected.

Rendered browser review remains necessary for final visual acceptance; production compilation alone does not prove pixel-level layout quality.
