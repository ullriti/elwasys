# R3c – Code-Qualität Portal (`backend/.../ui/` und `backend/e2e/`)

> Track R3c der [Spec 0001](../../specs/0001-finale-review.md), Modell Sonnet, 2026-07-24.

## 1. Gesamturteil

Der Code ist funktional sauber geschichtet (UI ruft ausschließlich Services auf, keine
Geschäftslogik in Views/Dialogen), konsistent kommentiert und die E2E-Suite folgt
vorbildlich der in `docs/kb/06-ui-tests.md` dokumentierten Selektor-Strategie (keine
Sleeps, keine brittlen Selektoren). Der Hauptbefund ist strukturelle Duplikation: die fünf
`Admin*View`-Klassen und die neun Formular-Dialoge sind praktisch wortgleiche Kopien
voneinander (Toolbar, Broadcaster-Wiring, Notification-Handling, Feldvalidierung) statt
gemeinsamer Basisklassen/Helfer, und Vaadins `Binder` wird im gesamten Portal nicht
genutzt, obwohl er genau dieses Duplikat auflösen würde. Keiner der Befunde ist
verhaltensrelevant; alles ist als Refactoring sinnvoll, aber nicht blockierend.

## 2. Positives

- Saubere UI/Service-Trennung: keine Geschäftslogik (Preisberechnung, Locking,
  Guthaben-Regeln) ist in Views/Dialoge durchgesickert; jede Aktion delegiert an den
  passenden Service.
- Konsistente, korrekt symmetrische Live-Update-Verdrahtung (`UiBroadcaster#register` in
  `onAttach` / `Registration#remove` in `onDetach`) in allen 7 Views, inkl. gezieltem
  Einzel-Panel-Refresh statt Full-Reload im Dashboard (`AdminDashboardView#refreshDevice`).
- E2E-Suite: die dokumentierten Selektor-Fallstricke (`vaadin-grid` Light-DOM, Tooltip
  vs. ARIA-Name) sind korrekt in `helpers.ts` gekapselt
  (`gridRowCells`/`rowActionButton`/`pickCombo`), keine `sleep`/`waitForTimeout`,
  deterministisches Warten auf sichtbare Overlay-Einträge statt fixer Timeouts.
- Gute Nutzung von Java-21-Pattern-Matching-`switch` über sealed `DomainEvent`-Records
  (`AdminDashboardView:139-149`, `UserDashboardView:134-141`) und der lazy-paginierten
  `DataProvider`-Historie (`AdminDashboardView#buildHistoryGrid`, Issue #30) als Vorbild
  für State-of-the-Art-Vaadin-Nutzung.

## 3. Findings

### Aspekt 1 – Duplikate

1. **hoch ·** Die fünf `Admin*View`-Klassen (`AdminDevicesView`, `AdminUserGroupsView`,
   `AdminProgramsView`, `AdminLocationsView`, `AdminUsersView`) teilen ~70 fast
   identische Zeilen: Toolbar-Aufbau (`AdminDevicesView:65-77` ≈ die anderen vier),
   `onAttach`/`onDetach`-Broadcaster-Registrierung (`AdminDevicesView:82-99` ≈
   `AdminProgramsView:79-96` ≈ `AdminUserGroupsView:84-101` ≈ `AdminLocationsView:78-95`,
   nur der Event-Typ im Lambda unterscheidet sich), `actionButtons()`
   (Edit/Delete-Icon-Buttons) und identisches `EntityInUseException`-Handling im
   Lösch-Pfad (`AdminDevicesView:140-153` ≈ `AdminProgramsView:143-156` ≈
   `AdminLocationsView:126-139`). **Empfehlung:** gemeinsame abstrakte Basisklasse
   (z. B. `AbstractAdminListView<T>`) für Toolbar/Grid-Grundgerüst/Broadcaster-Wiring/
   Lösch-Bestätigung extrahieren – würde die fünf Klassen um geschätzt 250–300 Zeilen
   kürzen.

2. **mittel-hoch ·** `showError`/`showSuccess`-Notification-Helfer (identischer Body:
   `Notification.show(msg, 5000/4000, MIDDLE)` + Theme-Variante) sind wortgleich in
   mind. 6 Klassen dupliziert (`AdminDashboardView:267-275`, `UserFormDialog:206-226`,
   `ChangePasswordDialog:120-123`, `PasswordForgotDialog:82-90`,
   `ResetPasswordView:123-131`, `CreditTopUpDialog:133-136`); weitere 8 Stellen bauen
   dieselbe Notification sogar ohne Helfer inline (`AdminDevicesView:146-148`,
   `AdminProgramsView:149-151`, `AdminLocationsView:132-134`,
   `AdminUserGroupsView:150-152`, `ProgramFormDialog:181-184`,
   `DeviceFormDialog:185-187`, `LocationFormDialog:109-112`,
   `UserGroupFormDialog:191-194`). **Empfehlung:** eine kleine `Notifications`-Utility im
   `ui`-Paket (`showError(String)`/`showSuccess(String)`), analog zu
   `ConfirmDeleteDialog`, das bereits als gutes Vorbild existiert.

3. **mittel ·** Alle Formular-Dialoge (`UserFormDialog`, `ProgramFormDialog`,
   `DeviceFormDialog`, `LocationFormDialog`, `UserGroupFormDialog`, `CreditTopUpDialog`,
   `ChangePasswordDialog`, `UserSettingsDialog`, `PasswordForgotDialog`) bauen
   Kopf-/Fußzeile („Abbrechen"/„Speichern"|„Erstellen", `LUMO_PRIMARY`-Theming,
   `HorizontalLayout`-Footer) identisch von Hand nach (z. B. `UserFormDialog:96-101`,
   `ProgramFormDialog:124-127`, `DeviceFormDialog:147-150`, `LocationFormDialog:79-82`,
   `UserGroupFormDialog:121-124`). Zusätzlich re-implementiert jeder Dialog eigene,
   praktisch identische `requireText`/`requireValue`/`requireBigDecimal`/`requireDuration`-
   Validierungshelfer (`ProgramFormDialog:192-221`, `DeviceFormDialog:195-233`) statt
   eines gemeinsamen Bausteins. **Empfehlung:** siehe „Stand der Technik" Finding 1
   (Binder-Umstieg) – löst diese Duplikation strukturell mit auf.

4. **niedrig-mittel ·** Datums-/Währungsformatierung mehrfach dupliziert:
   `DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.GERMANY)`
   identisch in `AdminDashboardView:88-89`, `UserDashboardView:60-61`,
   `ExpiredExecutionsDialog:37-38`, `CreditHistoryDialog:23-24`;
   `NumberFormat.getCurrencyInstance(Locale.GERMANY)` an 7 Stellen
   (`AdminDashboardView:389`, `AdminUsersView:165`, `AdminProgramsView:108`,
   `AdminUserGroupsView:112`, `ExpiredExecutionsDialog:132`, `CreditHistoryDialog:44`,
   `UserDashboardView:168`). **Empfehlung:** gemeinsame `PortalFormats`-Utility im
   `ui`-Paket.

5. **niedrig ·** E-Mail-Regex `^[^@\s]+@[^@\s]+\.[^@\s]+$` dupliziert in
   `UserFormDialog:134` und `UserSettingsDialog:72`. **Empfehlung:** gemeinsame
   Konstante/Utility-Methode.

6. **niedrig ·** psql-Aufruf-Muster (`execFileSync('sudo', ['-u','postgres','psql', ...])`)
   fast identisch in `backend/e2e/global-setup.ts:68-71` und
   `backend/e2e/tests/dashboard.spec.ts:22-27`. **Empfehlung:** gemeinsamer
   `runSql(dbName, sql)`-Helfer in `helpers.ts`.

7. **niedrig ·** Mehrere lokale `openEdit`-Closures in `admin-crud.spec.ts`
   (Zeilen ~76-82, ~282-288, ~339-345) wiederholen dasselbe Muster (Edit-Button klicken,
   Dialogtitel prüfen, Dialog zurückgeben). **Empfehlung:** optional als
   `openEditDialog(page, rowName, buttonIndex, expectedTitle)` in `helpers.ts` bündeln.

### Aspekt 2 – Struktur

1. **mittel ·** `AdminDashboardView.java` (391 Zeilen) ist mit Abstand die größte
   UI-Klasse und vereint drei Verantwortlichkeiten: Geräte-/Standort-Rendering,
   Fernwartungs-Toolbar (Log/Neustart, Zeilen 200-265) und Live-Update-Wiring.
   **Empfehlung:** Fernwartungs-Toolbar (`buildLocationHeader`/`showLog`/`restart`) in
   eine eigene Komponente (`LocationMaintenanceToolbar`) auslagern.

2. **niedrig ·** `component/PlaceholderView.java` ist toter Code – kein Repo-weiter
   Treffer für eine Unterklasse (grep bestätigt nur die Deklaration selbst), obwohl der
   Klassen-Javadoc sie noch als „gemeinsame Basis für die Platzhalter-Views des
   Phase-3-Grundgerüsts" beschreibt; alle diese Views wurden inzwischen durch echte
   Inhalte ersetzt. **Empfehlung:** Klasse entfernen (oder Zweck im Javadoc klarstellen).

3. **niedrig ·** Nur `AdminDashboardView`s Historie-Grid nutzt einen lazy `DataProvider`
   (Issue #30); die fünf Admin-CRUD-Grids laden bei jedem `loadData()` die komplette
   Liste (`grid.setItems(list)`). Für Waschküchen-Datenmengen unkritisch, aber
   inkonsistent zum bereits vorhandenen Muster. **Empfehlung:** kein akuter
   Handlungsbedarf; im Blick behalten, falls die Benutzerliste stark wächst.

4. **positiv ·** Saubere Trennung Layout (`AdminLayout`/`UserLayout`) ↔ Views ↔ Dialoge;
   `RootView`/`ElwasysAppShell` sind schlank und einzeln verantwortlich.

### Aspekt 3 – Stand der Technik

1. **mittel ·** Kein einziger Vaadin `Binder`/`BeanValidationBinder` im gesamten
   `ui`-Paket (per grep verifiziert). Alle 9 Formular-Dialoge validieren Felder manuell
   (`setInvalid`/`setErrorMessage`) und mappen Entity↔Feld von Hand – funktional korrekt,
   aber nicht Stand der Vaadin-Flow-Technik und Hauptursache für die Duplikate unter
   Aspekt 1 Nr. 3. **Empfehlung:** mittelfristigen Umstieg auf `Binder<T>` (deklarative
   Validatoren, automatisches Feld↔Bean-Mapping) für neue/überarbeitete Dialoge erwägen;
   kein Sofort-Handlungsbedarf, da funktional unauffällig.

2. **niedrig ·** `valid &= requireX(...)` (bitweises UND statt kurzschließendem `&&`, um
   trotz eines ungültigen Felds alle weiteren Felder zu prüfen) wird in
   `ProgramFormDialog:148-158` und `DeviceFormDialog:154-162` ohne erklärenden Kommentar
   verwendet – ein versehentliches Refactoring zu `&&` würde die Validierung nach dem
   ersten Fehler abbrechen, ohne dass ein Test das zwangsläufig auffängt.
   **Empfehlung:** kurzer Warum-Kommentar an der ersten Verwendung.

3. **positiv ·** Pattern-Matching-`switch` über sealed `DomainEvent`-Records
   (`AdminDashboardView:139-149`, `UserDashboardView:134-141`), `switch`-Expressions
   statt if/else-Ketten (`ProgramFormDialog:138-144`, `UserGroupFormDialog:161-174`) –
   gute Java-21-Nutzung an den Stellen, wo es zählt.

### Aspekt 4 – Lesbarkeit/Wartbarkeit

1. **niedrig ·** Wiederholt fully-qualified Typnamen statt Import:
   `com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER` inline in
   `AdminDevicesView:72`, `AdminUserGroupsView:74`, `AdminProgramsView:69`,
   `AdminUsersView:109`, `AdminLocationsView:68`; `java.util.Arrays.stream` in
   `AdminUsersView:154`, `UserFormDialog:105`/`215`; `java.time.LocalDateTime` in
   `AdminDashboardView:372-373`. **Empfehlung:** durch normale Imports ersetzen.

2. **niedrig ·** Breiter `catch (RuntimeException e)`-Fallback in allen Save-Pfaden
   (`ProgramFormDialog:180-186`, `DeviceFormDialog:184-189`, `LocationFormDialog:108-114`,
   `UserGroupFormDialog:190-196`) reicht `e.getMessage()` direkt an den Admin durch. Für
   die dort tatsächlich möglichen Fälle unproblematisch, kann bei einem unerwarteten
   Programmierfehler (z. B. NPE mit `null`-Message) aber eine kryptische/leere Meldung
   zeigen. **Empfehlung:** optional generisches Fallback-Wording + Logging der Exception
   statt roh `e.getMessage()`.

3. **positiv ·** Durchgehend Warum-orientierte Javadoc-Kommentare mit Verweisen auf
   Alt-Portal-Pendants/Issues/ADRs (z. B. `AdminDashboardView`-Klassenkommentar,
   `UiBroadcaster`) erleichtern die Nachvollziehbarkeit der Migration erheblich.

### Aspekt 5 – Konventionstreue

1. Bezeichner Englisch/Kommentare Deutsch wird durchgehend eingehalten – keine Verstöße
   gefunden.
2. Schichtung UI → Service eingehalten; kein direkter Repository-/JPA-Zugriff aus der
   `ui`-Schicht beobachtet.
3. **positiv ·** E2E-Selektor-Strategie aus `docs/kb/06-ui-tests.md` wird vorbildlich
   befolgt (`gridRowCells`/`rowActionButton`/`pickCombo` statt brittler Selektoren, keine
   Sleeps/`waitForTimeout` – verifiziert: einziger Treffer ist ein Kommentar, der
   erklärt, warum das nicht mehr verwendet wird).
