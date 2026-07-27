# Mapping v2: Prototyp → Vaadin Flow (Java)

Ergänzt `../v1/MAPPING.md`. Hier steht nur, **was sich gegenüber v1 ändert** — alles nicht Genannte bleibt wie dort beschrieben. Styling-Werte siehe `TOKENS.md` und `portal-theme-v2.css`.

Pfade unter `backend/src/main/java/org/kabieror/elwasys/backend/`.

## Rahmen

| Prototyp v2 | Flow | Änderung gegenüber v1 |
| --- | --- | --- |
| Logo-Mark links neben dem Titel | `Div` mit `H1` in `AdminLayout#addToNavbar` (reine Deko, CSS-Quadrat mit Kreis) | neu |
| Trenner + Name der aktuellen Ansicht in der Navbar | `Span`, gesetzt in `AdminLayout` über `HasDynamicTitle`/`AfterNavigationObserver` (oder `RouterLayout#showRouterLayoutContent` auswerten) | neu |
| Benutzer-Chip mit Initialen-Avatar | `ui/component/UserMenuBar`: `MenuBar`-Root-Item mit `Avatar` (`com.vaadin.flow.component.avatar.Avatar`, `setAbbreviation`) + Name | v1: nur Name |
| Menü mit Kopfblock (Avatar, Name, Rolle), Icons, rot abgesetztem Logout | `SubMenu` mit `HorizontalLayout` als erstem, nicht klickbarem Item; `Hr` als Trenner; Items mit `VaadinIcon.COG` / `KEY` / `SIGN_OUT`; Logout-Item `getElement().getThemeList().add("error")` | v1: drei Textitems |
| Seitenleiste ab Desktop dauerhaft offen, `DrawerToggle` nur schmal | `AppLayout#setDrawerOpened(true)`; Toggle über CSS ab `min-width: 900px` ausblenden (`vaadin-drawer-toggle { display:none }` in der Media-Query) | v1: immer einklappbar |
| Navigationspunkte als Pillen, aktiver Punkt in Markenblau | rein CSS (`vaadin-side-nav-item::part(link)`), siehe `portal-theme-v2.css` | v1: dunkle Fläche + linker Balken |

## Login (`ui/login/LoginView`)

v1 war eine zentrierte Karte auf grauem Grund. v2 ist ein zweigeteilter Bildschirm:

- `LoginView` wird zu einem `HorizontalLayout` (`setSizeFull`, `setSpacing(false)`, `setPadding(false)`) mit zwei `flex: 1 1 26rem`-Hälften, damit es bei schmalem Fenster umbricht.
- Links: `VerticalLayout` mit `login-brand`-Klasse (Hintergrund `--elwa-sidebar-dark`), Logo-Mark, Wortmarke, ein Satz Fließtext.
- Rechts: die bestehende `LoginForm` mit unverändertem `LoginI18n`; `portal-theme-v2.css` entfernt Rahmen/Schatten der Form, damit sie wie ein reines Formular wirkt.
- „Passwort vergessen?" bleibt `LoginForm#addForgotPasswordListener` → `PasswordForgotDialog`.
- Der Umschalter „Als normaler Benutzer anmelden" existiert **nur im Prototyp** (Demozweck) — in Flow entscheidet weiterhin `ui/RootView` anhand der Rolle.

## Dashboard-Gerätekarten (`ui/admin/AdminDashboardView#populateDevicePanel`)

| Prototyp v2 | Flow |
| --- | --- |
| Statuspunkt + größerer Gerätename + Badge | `Span` mit CSS-Klasse `device-status-dot` (Farbe = bestehende `device-status-*`-Klasse), Name-`Span` unverändert, Badge unverändert |
| „Deaktiviert"-Chip bei `!device.isEnabled()` | zusätzlicher `Span` mit `getElement().getThemeList().add("badge contrast small")` |
| Laufende Ausführung als Panel mit Programm / Nutzer / Restzeit | `buildRunningInfo` liefert statt eines `Span` ein `Div` mit drei `VerticalLayout`-Paaren (Label + Wert) |
| Fortschrittsbalken der Restzeit | `ProgressBar` mit `setMin(0)`, `setMax(program.getMaxDurationSeconds())`, `setValue(verstrichene Sekunden)`; Farbe über `--lumo-primary-color` bzw. Statusklasse. **Neu** — braucht Start + Maximaldauer, beides bereits in `ExecutionEntity`/`ProgramEntity`. Wird der Balken nicht gewünscht, entfällt er ersatzlos. |
| Überschrift „Verlauf" + Anzahl | `Span`-Paar über dem Grid; Anzahl aus `ExecutionService#countExecutions(device)` (bereits für die Lazy-Pagination vorhanden) |
| Kompaktere Historie, tabellarische Ziffern | `grid.addThemeVariants(GridVariant.LUMO_COMPACT)` + CSS `font-variant-numeric: tabular-nums` auf den Dauer-/Preisspalten |

## Dialoge

Gemeinsam (`ui/component/AbstractFormDialog`):

- Schließen-Kreuz in der Kopfzeile: `Button` mit `VaadinIcon.CLOSE_SMALL`, `ButtonVariant.LUMO_TERTIARY_INLINE`, via `getHeader().add(...)`.
- Kopf und Fuß stehen fest, nur der Inhalt scrollt: Inhalt in ein `Div` mit `overflow:auto` legen (`Dialog` selbst bekommt eine feste `maxHeight`).
- Abschnitte: je Gruppe ein `Div` aus Überschrift (`Span`, Versalien-Label) und eigenem `FormLayout`.

| Dialog | v2-Struktur |
| --- | --- |
| `DeviceFormDialog` | vier Abschnitte: **Stammdaten** (Name, Position, Standort, Aktiviert) · **Gateway-Anbindung** (Fhem Name/Switch/Power, deCONZ UUID) · **Automatisches Ende** (Schwellwert, Wartezeit) · **Zuordnung** (Programme, Benutzergruppen). Feldsatz und Validierung unverändert. |
| `ProgramFormDialog` | vier Abschnitte: **Stammdaten** (Name, Aktiviert) · **Preis** (Typ + preisabhängige Felder) · **Laufzeit** (Maximaldauer, Freie Zeit, Frühester Abbruch, Auto-Ende) · **Zuordnung**. `updateTypeFieldVisibility()` bleibt unverändert. |
| Typ-Umschalter | `rgType` bleibt `RadioButtonGroup`, dargestellt als Segmented Control: `rgType.addThemeVariants(RadioGroupVariant.LUMO_HELPER_ABOVE_FIELD)` reicht dafür nicht — stattdessen CSS auf `vaadin-radio-button` (Spur `--lumo-contrast-10pct`, aktiver Button weiß mit Schatten). Alternativ `MenuBar`/`ButtonGroup`, dann aber Bindung und Validierung selbst führen. |
| Beträge mit €-Suffix | `BigDecimalField#setSuffixComponent(new Span("€"))` |
| Dauer als Zahl + Einheit | unverändert die interne `DurationField` aus `ProgramFormDialog` |
| Mehrfachauswahl | unverändert `MultiSelectComboBox`, nur Chip-Styling aus dem Theme |

## Listen

Unverändert gegenüber v1 (`AbstractAdminListView`), plus:

- Sortierbare Spaltenköpfe: `Grid.Column#setSortable(true)` — im Prototyp zusätzlich auf Guthaben, Offline-Vorfällen und den Dashboard-Historien.
- Filterfeld in der Toolbar: `TextField` mit `ValueChangeMode.LAZY` + `ListDataProvider#setFilter`. Für lazy geladene Grids den Filter in die Query des `CallbackDataProvider` reichen.
- Grids ohne Zebra-Streifen und ohne Spaltentrenner, Hover-Hervorhebung — reines CSS.

## Benutzerbereich (`ui/user/UserDashboardView`)

Kacheln ohne blauen Oberrand (nur noch Karte mit Schatten); Buchungstabelle zusätzlich sortierbar und filterbar. Struktur sonst unverändert.
