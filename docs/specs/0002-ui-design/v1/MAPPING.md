# Mapping: Prototyp → Vaadin Flow (Java)

Ordnet jedes Element des Prototyps (`Waschportal Admin.dc.html`) der Flow-Klasse zu, die es umsetzt bzw. umsetzen soll. Bestehende Klassen sind mit Pfad unter `backend/src/main/java/org/kabieror/elwasys/backend/` genannt; **NEU** markiert, was der Prototyp gegenüber dem Ist-Stand ergänzt.

## Rahmen

| Prototyp | Flow |
| --- | --- |
| Blauer Kopfbalken, 50 px | `AppLayout` + `ui/ElwasysAppShell` (Inline-CSS `portal-theme.css`, `vaadin-app-layout::part(navbar)`) |
| Hamburger-Icon | `DrawerToggle` |
| Titel „Waschportal" | `H1` mit `addClassName("admin-layout-title")` bzw. `"user-layout-title"` |
| Benutzermenü rechts (Einstellungen / Passwort ändern / Logout) | `ui/component/UserMenuBar` → `MenuBar` mit `MenuBarVariant.LUMO_TERTIARY_INLINE`, `AuthenticationContext#logout` |
| Dunkle Sidebar, aktiver Punkt mit blauem Balken | `SideNav` / `SideNavItem` in `ui/admin/AdminLayout`, Styling über `vaadin-side-nav-item[current]::part(link)` |
| Menüpunkte Dashboard/Benutzer/…/Offline-Vorfälle | `SideNavItem(label, View.class, VaadinIcon.X.create())` |
| Menüpunkt „Übersicht" (User) | `ui/user/UserLayout` |
| Grauer Inhaltsbereich | `--elwa-content-bg` in `portal-theme.css` |

## Listenansichten

Alle fünf Stammdatenlisten erben von `ui/admin/AbstractAdminListView<T>`: Toolbar (`H2` + `Button` „Neu" mit `ButtonVariant.LUMO_PRIMARY`), `Grid<T>` über die volle Fläche, Aktionsspalte, Löschbestätigung, `UiBroadcaster`-Registrierung.

| Prototyp | Flow |
| --- | --- |
| Tabellenkopf + Zeilen | `Grid#addColumn(...).setHeader(...)`, Zebra über `::part(even-row-cell)` |
| Badge „Aktiv"/„Gesperrt", „Frei"/„Besetzt", „Aktiviert"/„Deaktiviert" | `Span` + `getElement().getThemeList().add("badge success"/"badge error"/"badge contrast")` |
| Aktionsspalte Bearbeiten/Löschen | `AbstractAdminListView#actionButtons`, `Icon(VaadinIcon.EDIT/TRASH)`, Löschen mit `LUMO_ERROR, LUMO_TERTIARY` |
| Benutzer: € / Umsätze zusätzlich | `AdminUsersView#actionButtons` (`addComponentAtIndex(1|2, …)`), Spaltenbreite `actionColumnWidth() = "190px"` |
| Warndreieck in der Benutzerliste | `AdminUsersView#expiredExecutionsWarning` → `ExpiredExecutionsDialog` |
| Geldbeträge / Zeitpunkte | `ui/component/PortalFormats#currency` / `#dateTime` |
| **NEU** Sortierpfeile in den Spaltenköpfen | `Grid.Column#setSortable(true)` – im Ist-Stand bereits gesetzt für Name/Username/Gruppe (Benutzer), Position/Name/Standort (Geräte), Name/Typ (Programme), Name (Gruppen, Standorte). Prototyp ergänzt Guthaben, Offline-Vorfälle und die Dashboard-Historien. |
| **NEU** Filterfeld in der Toolbar | `TextField` mit `ValueChangeMode.LAZY` + `ListDataProvider#setFilter`, alternativ Filterzeile über `HeaderRow` (`Grid#appendHeaderRow`). Für lazy geladene Grids (Dashboard-Historie, `AdminDashboardView#buildHistoryGrid`) stattdessen Filter in die Query des `CallbackDataProvider` durchreichen. |

Zuordnung Ansicht → Klasse: Benutzer `AdminUsersView`, Benutzergruppen `AdminUserGroupsView`, Programme `AdminProgramsView`, Geräte `AdminDevicesView`, Standorte `AdminLocationsView`, Offline-Vorfälle `AdminOfflineIncidentsView`.

## Dashboard (`ui/admin/AdminDashboardView`)

| Prototyp | Flow |
| --- | --- |
| Hinweisstreifen offene Offline-Vorfälle | `Div` mit `dashboard-incident-banner` + `RouterLink(AdminOfflineIncidentsView.class)`, `refreshIncidentBanner()` |
| Standort-Kopfzeile mit Verbindungsbadge und Log/Neustart | `ui/admin/LocationMaintenanceHeader` (`TerminalMaintenanceService#isConnected/requestLog/requestRestart`) |
| Gerätekarte mit farbigem oberen Rand | `VerticalLayout` mit `dashboard-device-panel` + `device-status-free/-occupied/-disabled` |
| Zeile „Programm · Nutzer · Restzeit" | `buildRunningInfo(...)` |
| Historie je Gerät (14 em hoch) | `buildHistoryGrid(...)`, lazy über `PageRequest` + `Sort.by(start DESC, id DESC)` |
| Live-Aktualisierung | `ui/push/UiBroadcaster` (im Prototyp nicht simuliert) |

## User-Portal (`ui/user/UserDashboardView`)

| Prototyp | Flow |
| --- | --- |
| Kacheln „Guthaben" / „Letzte Einzahlung" | `buildSparkTile(...)`, CSS-Klasse `dashboard-spark` |
| Tabelle „Buchungen" | `Grid<CreditAccountingEntryEntity>`, Spalten Datum (12 em) / Betrag (8 em) / Buchungstext |

Hinweis aus dem Prototyp: Kopf- und Datenzeilen brauchen dieselbe Schriftgröße, sonst laufen `em`-Spaltenbreiten auseinander. In Flow erledigt das `vaadin-grid` selbst.

## Dialoge

Gemeinsames Gerüst: `ui/component/AbstractFormDialog` (Titel, modal, feste Breite, Fußzeile „Abbrechen" + Primäraktion).

| Prototyp | Flow | Breite |
| --- | --- | --- |
| Gerät bearbeiten/erstellen | `ui/admin/dialog/DeviceFormDialog` – `FormLayout` mit `ResponsiveStep("0",1)/("30em",2)`, `MultiSelectComboBox` für Programme/Gruppen | 45 em |
| Benutzer bearbeiten/erstellen | `ui/admin/dialog/UserFormDialog` – 1-spaltig, `TextArea` Kartennummern, Checkbox Passwortversand | 35 em |
| Programm bearbeiten/erstellen | `ui/admin/dialog/ProgramFormDialog` – `RadioButtonGroup` Typ schaltet Preis ↔ Grundgebühr/Zeitpreis/Intervall (`updateTypeFieldVisibility`), Dauerfelder = interne `DurationField` (IntegerField + ComboBox) | 45 em |
| Gruppe bearbeiten/erstellen | `ui/admin/dialog/UserGroupFormDialog` – Rabattierung Keiner/Fix/Faktor, drei `MultiSelectComboBox` (Standorte/Geräte/Programme) | 45 em |
| Standort bearbeiten/erstellen | `ui/admin/dialog/LocationFormDialog` – Name, Gruppen, Offline-Maximaldauer (`IntegerField`, min 1) | 40 em |
| Guthaben von … | `ui/admin/dialog/CreditTopUpDialog` – `RadioButtonGroup` Ein-/Auszahlung, `BigDecimalField`, `setDisableOnClick(true)` auf „Buchen" | 28 em |
| Umsätze von … | `ui/admin/dialog/CreditHistoryDialog` – rein lesend, `setResizable(true)` | 50 × 35 em |
| Verfallene Ausführungsaufträge | `ui/admin/dialog/ExpiredExecutionsDialog` – Erklärtext, „Alle abrechnen", je Zeile Abrechnen/Löschen | 60 em |
| Einstellungen | `ui/component/UserSettingsDialog` – Email, Email-Benachrichtigung, Pushover-Key | 35 em |
| Passwort ändern | `ui/component/ChangePasswordDialog` – drei `PasswordField` | 22 em |
| Passwort zurücksetzen | `ui/login/PasswordForgotDialog` – `EmailField`, neutrale Rückmeldung | 22 em |
| Log-Viewer | `ui/admin/dialog/LogViewerDialog` | – |
| Ja/Nein-Rückfrage | `ui/component/ConfirmDeleteDialog` → `ConfirmDialog` mit „Ja"/„Nein", `setConfirmButtonTheme("error primary")` | – |
| Toast unten links | `ui/component/Notifications#showSuccess/#showError` | – |

## Login (`ui/login/LoginView`)

Karte mit blauem oberem Rand = `vaadin-login-form` + `.login-view` aus `portal-theme.css`; Texte über `LoginI18n` (Header „Waschportal" / „Bitte melden Sie sich an.", Form „Login", „Benutzername", „Passwort", „Passwort vergessen?"). Der Umschalter „Als normaler Benutzer anmelden" existiert nur im Prototyp – in Flow entscheidet `ui/RootView` anhand der Rolle.

## Validierung

Feldfehler laufen im Ist-Stand über `ui/component/FormValidation` (`require`, `check`, `reject`, `isValidEmail`); der Prototyp zeigt keine Fehlerzustände.
