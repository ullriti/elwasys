# v2 – Token-Deltas gegenüber v1

Was sich zwischen `portal-theme.css` (Ist-Stand) und `portal-theme-v2.css` ändert. Markenfarben bleiben: Kopfbalken/Akzent **#4488dd**, Sidebar **#222d32**, Statusfarben (frei/besetzt/deaktiviert) unverändert.

## Lumo-Custom-Properties

| Token | v1 | v2 | Wirkung |
| --- | --- | --- | --- |
| `--lumo-border-radius-s` | 2px | 6px | Chips, kleine Flächen |
| `--lumo-border-radius-m` | 3px | 8px | Felder, Icon-Buttons |
| `--lumo-border-radius-l` | 3px | 12px | Karten, Grids, Dialoge |
| `--lumo-size-s` | 1.75rem | 2rem | kleine Buttons |
| `--lumo-size-m` | 2.25rem | 2.5rem | Felder und Buttons |
| `--lumo-size-l` | 2.75rem | 2.75rem | unverändert (Login-Felder) |
| `--lumo-header-text-color` | Lumo-Default | `#101828` | Überschriften |
| `--lumo-body-text-color` | Lumo-Default | `#1f2429` | Fließtext |
| `--lumo-secondary-text-color` | Lumo-Default | `#667085` | Labels, Hilfetexte |
| `--lumo-tertiary-text-color` | Lumo-Default | `#98a2b3` | Platzhalter |
| `--lumo-contrast-5pct` | Lumo-Default | `#f8fafc` | Zeilen-Hover, Ghost-Buttons |
| `--lumo-contrast-10pct` | Lumo-Default | `#f2f4f7` | Segmented-Control-Spur, Chips |
| `--lumo-contrast-20pct` | Lumo-Default | `#e6e8ec` | Rahmen |
| `--lumo-box-shadow-xs` | – | `0 1px 2px rgba(16,24,40,.06)` | Karten, Grid |
| `--lumo-box-shadow-m/l` | Lumo-Default | weiter und weicher | Menüs, Dialoge |

## Eigene Variablen

| Token | v1 | v2 |
| --- | --- | --- |
| `--elwa-content-bg` | `#ecf0f5` | `#f4f6f9` |
| `--elwa-box-border` | `#d2d6de` | `#e6e8ec` |
| `--elwa-sidebar-active` | `#1e282c` + blauer Balken links | `#4488dd` (Pille) |
| `--elwa-sidebar-hover` | `#1e282c` | `#1a2429` |
| `--elwa-row-hover` | – | `#f8fafc` |
| `--elwa-sidebar-dark`, `--elwa-header-blue`, `--elwa-status-*` | | unverändert |

## Vaadin-Feld-/Button-Properties (neu in v2)

| Token | Wert | Wirkung |
| --- | --- | --- |
| `--vaadin-input-field-background` | `#ffffff` | Felder outlined statt grau gefüllt |
| `--vaadin-input-field-border-width` | `1px` | |
| `--vaadin-input-field-border-color` | `#d0d5dd` | |
| `--vaadin-input-field-border-radius` | `8px` | |
| `--vaadin-input-field-height` | `2.5rem` | |
| `--vaadin-button-border-radius` | `10px` | |
| `--vaadin-button-font-weight` | `600` | |

Fokus-Ring (`box-shadow: 0 0 0 3px rgba(68,136,221,.18)`) kommt in Lumo bereits über `--lumo-primary-color-50pct`; wenn er kräftiger sein soll, `--vaadin-focus-ring-width: 3px` und `--vaadin-focus-ring-color: rgba(68,136,221,.35)` setzen.

## Strukturelle Regeln (nicht nur Token)

- **Grid**: Kopfzeile weiß mit Unterlinie, Versalien-Label (`text-transform:uppercase`, 0.75rem, `letter-spacing:.04em`); `::part(even-row-cell)` wieder weiß (keine Zebra-Streifen); `::part(cell)` ohne `border-inline-end`; Zeilen-Hover über `::part(row):hover`.
- **Sidebar**: `vaadin-side-nav-item::part(link)` mit `margin:.15rem .6rem`, `border-radius:9px`; aktiver Punkt farbig gefüllt statt `inset 3px 0 0 0` Balken.
- **Navbar**: `min-height:64px` (v1: 50px), Trennung über `inset 0 -1px 0 rgba(255,255,255,.16)` statt Schlagschatten.
- **Dialoge**: Overlay `border-radius:16px`, Backdrop `rgba(16,24,40,.5)` + `blur(3px)`, Fußzeile mit Oberlinie und `#fcfcfd`.
- **Login**: kein Kartenrahmen mehr auf grauem Grund, sondern zweigeteilter Bildschirm (Markenfläche `#222d32` links, Formular auf Weiß rechts). In Flow heißt das: `LoginView` wird zu einem `HorizontalLayout` aus Brand-Panel und `LoginForm`, das CSS entfernt nur noch Rahmen/Schatten der Form.

## Was der Prototyp zusätzlich zeigt

Diese Punkte sind Layout-Änderungen in den Views, keine reinen Token:

- Geräte- und Programm-Dialog in Abschnitte gegliedert (Stammdaten / Gateway-Anbindung / Automatisches Ende / Zuordnung bzw. Stammdaten / Preis / Laufzeit / Zuordnung), Kopf und Fuß stehen fest, nur der Inhalt scrollt.
- Programm-Typ als Segmented Control (`RadioButtonGroup`, Theme-Variante nach Wahl) statt zweier Radiobuttons.
- Sortierbare Spaltenköpfe und ein Filterfeld je Liste (siehe `MAPPING.md` in v1).
