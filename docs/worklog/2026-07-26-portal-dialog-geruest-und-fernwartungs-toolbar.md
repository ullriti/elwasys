# 2026-07-26 — Rest von #92: Dialog-Gerüst, Feldprüfungen, Fernwartungs-Toolbar (Portal)

**Ziel:** Die beim ersten Teil von #92 (Code-Qualität Portal, Track R3c der finalen Review,
Epic #94) bewusst offen gelassenen Punkte nachziehen: das gemeinsame Gerüst der Formular-Dialoge
samt Feldprüfungen, die Entzerrung von `AdminDashboardView` und die „niedrig"-Punkte des
Reports. Der erste Teil (`AbstractAdminListView`, `Notifications`, `PortalFormats`,
`PlaceholderView` entfernt) war bereits gemergt.

## Vorgehen

Im Portal-Kontext direkt bearbeitet (eine Komponente, keine Modulgrenze). Maßstab war
durchgehend **Verhaltensgleichheit**: identische Meldungstexte, identischer Prüfzeitpunkt (erst
beim Speichern), identischer DOM-Aufbau der Dialoge und des Dashboards — die Portal-E2E-Suite
prüft Meldungen, Dialogtitel und Grid-Zellen und ist damit der Beleg.

## Erledigt

### Dialog-Gerüst: `AbstractFormDialog`
Kopfzeile (Titel/modal/Breite) und Fußzeile („Abbrechen" + hervorgehobene Primäraktion) liegen
jetzt einmal in `ui/component/AbstractFormDialog`; die neun Formular-Dialoge (`UserFormDialog`,
`ProgramFormDialog`, `DeviceFormDialog`, `LocationFormDialog`, `UserGroupFormDialog`,
`CreditTopUpDialog`, `ChangePasswordDialog`, `UserSettingsDialog`, `PasswordForgotDialog`)
erben sie. Dazu die zwei wiederkehrenden Beschriftungsregeln des Alt-Portals als Helfer
(`entityTitle` → „Gerät bearbeiten"/„Gerät erstellen", `saveCaption` → „Speichern"/„Erstellen")
und `focusOnOpen` für die drei Dialoge, die beim Öffnen ein Feld fokussieren. Die drei rein
lesenden Dialoge (`CreditHistoryDialog`, `LogViewerDialog`, `ExpiredExecutionsDialog`) bleiben
außen vor: sie haben keine Abbrechen/Speichern-Leiste, ihnen bliebe nur eine geerbte Kopfzeile.

### Feldprüfungen: `FormValidation` statt `require*` je Dialog — **kein Binder**
Die pro Dialog nachgebauten `requireText`/`requireValue`/`requireBigDecimal`-Überladungen
(allein `DeviceFormDialog` hatte vier, die sich nur im Feldtyp unterschieden) sind durch
`ui/component/FormValidation` ersetzt: `require` (Pflichtfeld), `check` (beliebige Bedingung),
`reject` (Fehler steht bereits fest, z. B. gemeldet vom Service) und `isValidEmail` (die zuvor
in zwei Dialogen kopierte Regex). Neuer Unit-Test `FormValidationTest` (5 Fälle) sichert genau
die zwei Eigenschaften, auf die sich die Dialoge verlassen: eine bestandene Prüfung **löscht**
die Markierung wieder, und der Rückgabewert erlaubt das Verketten über alle Felder.

**Warum kein Vaadin-`Binder`** (Empfehlung des Reports, R3c Aspekt 3 Nr. 1): Der Umstieg wäre
hier nicht verhaltenserhaltend zu haben.
1. *Prüfzeitpunkt:* Der Binder bindet Validatoren an Wertänderungen und markiert Felder schon
   beim Tippen. Das Portal prüft — wie das Alt-Portal — erst beim Klick auf Speichern.
   `setValidatorsDisabled` wäre kein Ausweg, es schaltet auch die Prüfung beim `writeBean` ab.
2. *Kein Bean:* Die Dialoge schreiben nicht in ein Formular-Objekt, sondern rufen Services mit
   langen Parameterlisten (`programService.update(entity, name, type, flagfall, rate, …)`). Für
   den Binder bräuchte jeder Dialog eine künstliche Zwischen-Bean.
3. *Bedingte und zusammengesetzte Felder:* Preisfelder hängen am Programmtyp, Rabattfelder an
   der Rabattart, die Email-Pflicht am „Passwort zusenden"-Haken; `DurationField` (Zahl +
   Einheit) ist überhaupt kein `HasValue`.

Der Nutzen wäre reines Aufräumen gewesen, der Preis ein geändertes Bedienverhalten kurz vor dem
Feldeinsatz — deshalb der gemeinsame Helfer statt des Binders. `DurationField` implementiert
jetzt `HasValidation` und läuft damit über denselben Prüfweg wie die echten Eingabefelder.

### `AdminDashboardView` entzerrt
Die Fernwartungs-Kopfzeile je Standort (Name, Verbindungs-Badge, „Log anzeigen"/„Neustart" samt
Fehlermeldungen) liegt als `ui/admin/LocationMaintenanceHeader` in einer eigenen Komponente; die
View ist von 405 auf 321 Zeilen geschrumpft und trägt nur noch Geräte-Rendering und
Live-Update-Verdrahtung. Bewusst die **ganze** Kopfzeile statt nur der Knopfleiste: die
Komponente ist ein `HorizontalLayout` mit derselben CSS-Klasse `dashboard-location-header` und
demselben Kindaufbau, das gerenderte DOM ist damit unverändert (E2E/Cross-Component sind auf die
Klassen-Hooks angewiesen). Der Ladeindikator/Broadcaster-Zustand bleibt in der View: die
Kopfzeile fragt den Verbindungsstatus einmal beim Bauen ab und spricht danach nur noch auf
Knopfdruck mit dem Terminal — es gab keine Verzahnung, die zu schneiden gewesen wäre.

### „Niedrig"-Punkte des Reports
- Email-Regex als eine Konstante (`FormValidation.EMAIL_PATTERN`) statt zweier Kopien.
- `runSql(dbName, script)` in `backend/e2e/tests/helpers.ts`; `global-setup.ts`,
  `dashboard.spec.ts` und `offline-incidents.spec.ts` nutzen ihn (drei psql-Aufrufe → einer).
- `openEditDialog(page, rowName, buttonIndex, expectedTitle)` in `helpers.ts` ersetzt die drei
  lokalen `openEdit`-Closures in `admin-crud.spec.ts` (P7/P11/P14).
- Fully-qualified Typnamen durch Importe ersetzt (`Arrays`, `LocalDateTime`, `Optional`).
- Save-Pfade: statt rohem `e.getMessage()` jetzt `AbstractFormDialog#showFailure` — Ausnahme ins
  Server-Log, im Portal die fachliche Meldung plus Detailtext **nur wenn es einen gibt** (eine
  NPE ohne Meldung zeigte vorher „… null").
- Warum-Kommentar an der ersten `valid &= …`-Zeile jedes Dialogs (bitweises UND ist Absicht:
  alle Felder sollen markiert werden, nicht nur das erste).

## Entscheidungen

- **Kein Binder-Umstieg** (Begründung oben) — der Report-Punkt gilt damit als bewertet und
  begründet abgelehnt, nicht als offen.
- **Komponentenname `LocationMaintenanceHeader`** statt des im Report vorgeschlagenen
  `LocationMaintenanceToolbar`: ausgelagert ist die komplette Kopfzeile inklusive Standortname
  und Verbindungs-Badge, ein „Toolbar"-Name wäre irreführend.

## Verifikation

- `mvn -f backend/pom.xml package` (inkl. Tests) grün, Backend-Suite **305/305**
  (`run-backend-tests.sh`, davon 5 neu durch `FormValidationTest`).
- Portal-E2E **27/27** grün (`cd backend/e2e && npm test`), inklusive Produktionsmodus-Build
  (`mvn package -Pproduction`, den `scripts/start-backend.sh` fährt).

## Bewusst nicht gemacht

- Die drei lesenden Dialoge nicht auf die Basisklasse gezogen (siehe oben).
- Lazy `DataProvider` für die fünf Admin-CRUD-Grids (Report Struktur Nr. 3) — der Report selbst
  sieht keinen Handlungsbedarf bei Waschküchen-Datenmengen.
- `ResetPasswordView` behält seine eigene Prüf-Logik: sie ist eine Route, kein Dialog, und
  markiert beide Passwortfelder in einem Zug (`setInvalid(isEmpty())`) — mit `FormValidation`
  nachgebaut hätte sie sich nicht verkürzt.

## Nachtrag aus dem Review-Gate (Hauptkontext)

Der blockierende `code-reviewer`-Durchgang hat zwei fehlende Tests und mehrere Hinweise
gefunden; behoben bzw. bewusst benannt:

- **`showFailure` getestet:** die Textkomposition liegt jetzt als reine Funktion
  `AbstractFormDialog.failureText` vor und hat einen eigenen Test (`AbstractFormDialogTest`) –
  sie war die einzige echte Verhaltensänderung des Pakets und ungetestet.
- **Fernwartungs-Kopfzeile im E2E abgedeckt:** P20 prüft jetzt `.dashboard-location-header`,
  Standortname und die zwei Fernwartungs-Knöpfe. Vorher wäre eine verunglückte Auslagerung
  (fehlende CSS-Klasse, vertauschte Kindreihenfolge) grün durchgelaufen.
- **`FormValidation.reject`** hat einen Testfall bekommen (Service-Fehlerpfad).
- **Vor-Login-Dialog ohne Detailtext:** `PasswordForgotDialog` zeigt einem anonymen Besucher
  keine rohe Ausnahmemeldung mehr (Mailserver-/DB-Details); dafür gibt es die Variante
  `showFailure(message, cause, withDetail=false)`. Der Stacktrace steht weiterhin im Log.
- **Logger je konkreter Dialogklasse** statt an der Basisklasse, Log-Text englisch wie im
  übrigen Backend.
- **Benannte Verhaltensabweichung** (bisher als „rein strukturell" beschrieben, das stimmt an
  einer Stelle nicht): In `UserGroupFormDialog` löscht `FormValidation.require` die
  Fehlermarkierung eines nachgetragenen Rabattfeldes, während der Altcode sie stehen ließ.
  Sichtbar nur, wenn ein zweiter Fehler (leerer Name) den Dialog offenhält – dann ist das
  Rabattfeld jetzt grau statt weiter rot. Das ist die Behandlung der übrigen acht Dialoge;
  bewusst so übernommen statt die Inkonsistenz zu konservieren.
- **`runSql` schluckt psql-stdout** (`stdio` von `inherit` auf `ignore`), auch im
  `global-setup.ts`. Mit `-q` bleibt nur Rauschen aus; Fehler gehen weiterhin nach stderr und
  `ON_ERROR_STOP=1` bricht ab. Bewusst so belassen.
