# R1 – Zielerreichung (Track 1 der finalen Pre-Launch-Review)

|              |                                                       |
| ------------ | ----------------------------------------------------- |
| **Spec:**    | [`docs/specs/0001-finale-review.md`](../../specs/0001-finale-review.md) |
| **Prüffrage:** | Sind alle Ziele/Rahmenbedingungen des Auftraggebers erreicht (Roadmap + Entscheidungen in `docs/kb/05-migration-plan.md`)? |
| **Datum:**   | 2026-07-23                                             |
| **Methode:** | Abgleich Migrationsplan/ADRs gegen Code-Stichproben (kein reines Doku-Review) |

## 1. Gesamturteil

**Ja – die im Migrationsplan festgehaltenen Ziele und Auftraggeber-Vorgaben sind erreicht,
und die Kernbehauptungen sind im Code stichprobenartig nachweisbar** (Terminals ohne
Direkt-DB-Zugriff, DB-Rollenhärtung auf einen einzigen Anwendungs-User, Alt-Portal und
`Common`-Modul vollständig entfernt, Zeitzonen-/TLS-Gates real verdrahtet, Offline-
Replay-Konfiguration wie zugesagt portal-konfigurierbar). Die drei „gesetzten"
Rahmenbedingungen (Java-Backend, PostgreSQL, Raspi-Touch-Terminals) und die harte
Nebenbedingung „Nutzer dürfen sich nicht umstellen müssen" sind eingehalten; bewusste
Abweichungen davon (Reset-Neutralisierung #24, Passwort-Mindestlänge #44, gesperrte
Nutzer im neuen Portal-Login) sind als Auftraggeber-Entscheidungen dokumentiert und
sauber begründet. Die akzeptierten Restrisiken (Standort-Token, Klartext-Admin-Passwort-
Mail, Session-Invalidierung, Timing-Orakel, Vaadin-Lizenz) sind zwischen
`05-migration-plan.md` und den zugehörigen ADRs (0018/0019) konsistent, Issue #75
(Repo-Umzug) ist korrekt als offene Checkliste angelegt und Epic #66 ist als
`closed/completed` verifiziert. Einzige echte Lücke: zwei Einträge unter „Offene Fragen /
mit Auftraggeber klären" sind durch spätere Entscheidungen faktisch erledigt/gegenstandslos,
aber nie als solche markiert worden (Findings unten) – das ist ein Dokupflege-, kein
Sachmangel.

## 2. Abgleichstabelle

### Rahmenbedingungen (Vorgaben des Auftraggebers)

| Ziel/Vorgabe | Status | Beleg |
|---|---|---|
| Java-Backend bleibt gesetzt | **erreicht** | `backend/pom.xml` (Spring Boot 3, Java 21); `backend/src/main/java/...` |
| PostgreSQL bleibt gesetzt (inkl. Bestandsdaten) | **erreicht** | `backend/src/main/resources/db/migration/V1__baseline_schema_0_4_0.sql` (Baseline aus Bestandsschema, additive Folge-Migrationen V2–V11) |
| Terminals bleiben Raspberry Pi mit Touch-Display | **erreicht** | `Client-Raspi/` JavaFX-Module unverändert vorhanden (`ui/medium`, `ui/small`) |
| Nutzer dürfen sich nicht umstellen müssen (Terminal-Bedienfluss) | **erreicht** | UI/FXML unangetastet, `ClientSmallUiSmokeE2ETest` fixiert `ui/small`; `run-ui-tests.sh`/`run-client-e2e.sh` als Abnahme |
| Nutzer dürfen sich nicht umstellen müssen (Portal-Abläufe) | **erreicht**, mit dokumentierten bewussten Abweichungen | Playwright P1–P20 als Abnahme; Abweichungen (#24 Reset-Neutralisierung, #44 Mindestlänge, gesperrte Nutzer im neuen Login) explizit als Auftraggeber-Freigabe in „Entscheidungen" vermerkt (`05-migration-plan.md` Zeilen 756–855) |

### Zielarchitektur

| Ziel | Status | Beleg |
|---|---|---|
| Ein zentrales Spring-Boot-Backend als alleiniger DB-Eigentümer | **erreicht** | Root-`pom.xml` `<modules>` = nur `Client-Raspi`, `backend`; `backend/src/main/java/org/kabieror/elwasys/backend/{domain,repository,service,api,ui,auth,ws}` |
| Terminal spricht API statt SQL | **erreicht** | `Client-Raspi/pom.xml`: `postgresql`-Dependency ist `scope: test` (nur Testharness-Fixtures), Kommentar bestätigt „production code no longer touches the database"; `ApiClient.java` nutzt ausschließlich `java.net.http` + DTOs, keine JDBC-Importe |
| Fernwartung ohne IP-Registry, ausgehende WS-Verbindung | **erreicht** | `backend/.../ws/TerminalMaintenanceService.java`, `TerminalWsMessage.java`; `Client-Raspi/.../ws/TerminalWsMessage.java`; `client_ip/-port/-uid` per `V9__drop_obsolete_location_client_columns.sql` entfernt |
| Benachrichtigungen zentral im Backend | **erreicht** (scharfgeschaltet als Cutover-Schritt) | `NotificationService` implementiert + Client-Versandcode entfernt; Flag `elwasys.notifications.enabled` Default AUS im Repo, im `CUTOVER-RUNBOOK.md` als operativer AN-Schritt dokumentiert – konsistent mit „nächster Schritt: Live-Gang" |
| Portal ist Teil des Backend-Deployments | **erreicht** | kein separates Portal-Modul/-WAR mehr, `Portal/` nicht mehr im Repo (`git ls-files` liefert 0 Treffer) |
| Ein DB-Client statt drei (Rollenhärtung) | **erreicht** | `V6__harden_db_roles.sql` droppt `elwaclient1`/`elwaapi`/Gruppe `elwaclients`; `elwaportal` bleibt einziger Grant-Empfänger aus `V1` |

### Roadmap-Phasen 0–6

| Phase | Status | Beleg |
|---|---|---|
| Phase 0 – Absicherung | **erreicht** | E2E-/UI-Testharness vorhanden und lauffähig (`run-ui-tests.sh`, `backend/e2e`) |
| Phase 1 – Fundament | **erreicht** | Aggregator-Parent-`pom.xml`, einheitlich Java 21 (`maven.compiler.release`), JUnit 5 |
| Phase 2 – Backend-Gerüst | **erreicht** | JPA-Entities/Repositories, Flyway-Baseline, Auth/REST/WS-Fundament, Notification-Dienst (hinter Flag), Docker/Helm vorhanden |
| Phase 3 – Portal-Neubau | **erreicht** | Vaadin-Flow-Views unter `backend/.../ui/admin/`; Playwright P1–P20 vorhanden (`backend/e2e/tests`) |
| Phase 4 – Terminal-Modernisierung | **erreicht** | `ApiClient`, `TerminalWsMessage`, `java.net.http`-Gateways, Offline-Replay-Journal (siehe unten) |
| Phase 5 – Ablösung/Härtung | **erreicht** | `Portal/`- und `Common/`-Module aus dem Repo entfernt (git-verifiziert), App-Reste per `V10` entfernt, Auth-Key-UI-Anzeige nur noch als Javadoc-Erwähnung vorhanden, keine echte Anzeige mehr in `UserSettingsViewController`/`ConfirmationViewController` |
| Phase 6 – Cutover | **erreicht** (Werkzeuge vorhanden; Feldumschaltung selbst ist laut KB der nächste operative Schritt) | `deploy/cutover/`, `deploy/terminal/{update.sh,upgrade-jre.sh,auto-update-watchdog.sh}`, `deploy/smoke/post-deploy-smoke.sh`, `deploy/CUTOVER-RUNBOOK.md` |

### Offline-Buchungen (Konzeptskizze, Auftraggeber-Auflagen)

| Vorgabe | Status | Beleg |
|---|---|---|
| `offline.max-duration` über Portal konfigurierbar (Auftraggeber-Auflage) | **erreicht** | `LocationFormDialog.java`, `LocationService.java`, `SnapshotDto#offlineMaxDurationMinutes` |
| Kein Sicherheitsabschlag aufs Guthaben | **erreicht** | wie im Konzept beschrieben, keine Gegenmaßnahme im Code implementiert (bewusst) |
| Uhren-Drift-Toleranz ±5 Min., konfigurierbar | **erreicht** | `application.yml`: `clock-drift-tolerance: ${ELWASYS_OFFLINE_CLOCK_DRIFT_TOLERANCE:PT5M}` |
| Unverschlüsselter Snapshot/Journal (dokumentiertes Restrisiko) | **erreicht/dokumentiert** | Konzeptskizze + Entscheidung vom 2026-07-21 im Migrationsplan |

### Entscheidungen (Auftraggeber) – Stichproben

| Entscheidung | Status | Beleg |
|---|---|---|
| Betriebsmodell Backend: Compose **und** Helm | **erreicht** | `deploy/compose/docker-compose.yml`, `deploy/helm/elwasys-backend/` beide vorhanden |
| TLS ist Pflicht (2026-07-23) | **erreicht** | Compose bindet nur `127.0.0.1:8080` (Issue #35 im Kommentar), `deploy/cutover/01-preflight-check.sh`/`CUTOVER-RUNBOOK.md` erzwingen `https://` als Gate |
| Kanonisches Repo `ullriti/elwasys`, Rückumzug via Issue #75 | **erreicht** | Issue #75 über GitHub-API verifiziert: offen, vollständige Checkliste, korrekt referenziert; einzige verbleibende `kabieror/elwasys`-Treffer sind Java-Package-Namen (`org.kabieror.elwasys.*`), kein Repo-Bezug |
| Zeitzone fest auf Europe/Berlin | **erreicht** (als verdrahteter Default + Preflight-Gate, nicht hart erzwungen) | `backend/Dockerfile ENV TZ=Europe/Berlin`, Compose/Helm-Defaults `Europe/Berlin`; `01-preflight-check.sh` vergleicht Backend-/Terminal-TZ und warnt bei Abweichung (kein harter Abbruch) |
| DB-Rollenhärtung: `elwaportal` einziger App-User | **erreicht** | s. o. `V6__harden_db_roles.sql` |
| Standort-Token: nur SHA-256-Hash, Mehrfach-Token-Rotation, `revoked_at` | **erreicht** | `TerminalTokenEntity.java` Spalte `revoked_at`; `deploy/cutover/README.md` Zeile 91 verlangt Widerruf beim Gerätetausch |
| Vaadin-Lizenzrestrisiko akzeptiert (2026-07-23) | **erreicht/konsistent dokumentiert** | ADR 0019 ⇄ `05-migration-plan.md` „Restrisiken Betrieb & Deployment" decken sich wörtlich; ADR 0008 trägt einen expliziten Korrekturvermerk (Admin-UI-Versprechen zurückgenommen) |
| SHA1→Argon2id-Rehash hinter Flag bis Alt-Portal weg | **erreicht/obsolet-konsistent** | Flag existiert weiterhin im Code, Alt-Portal ist inzwischen entfernt – Flag-Aktivierung ist ein reiner Cutover-Schritt, keine offene Codefrage |
| App-Reste (`elwaapi`, `reservations`, `foreign_authkeys`) entfernt | **erreicht** | `V10__drop_app_remnants.sql` droppt Trigger/Funktionen/Spalten/Tabellen; Terminal-UI zeigt keinen Auth-Key mehr |

### Restrisiken – Konsistenzprüfung Migrationsplan ↔ ADR

| Restrisiko | Status | Beleg |
|---|---|---|
| Standort-Token-Blast-Radius (#43) | **konsistent dokumentiert** | `05-migration-plan.md` „Restrisiken Auth & Security" ⇄ ADR 0018 wortgleiche Kernaussagen; ADR 0008 korrigiert |
| Klartext-Admin-Passwort-Mail (#46) | **konsistent dokumentiert** | nur in `05-migration-plan.md` geführt (keine eigene ADR nötig, da keine 🧩-Entscheidung), Begründung nachvollziehbar |
| Session-Invalidierung bei Sperrung (#48) | **konsistent dokumentiert** | ebenso, technische Begründung im Plan detailliert genug für eine Nicht-ADR-Einordnung |
| Timing-Orakel Login/Reset (#24/#25) | **konsistent dokumentiert** | im Plan und in ADR 0018 „Konsequenzen" wortgleich referenziert |
| Case-only-Username-Kollision (#23) | **konsistent dokumentiert** | nur im Plan, technisch plausibel begründet |
| Vaadin-Lizenz produktiv (#33) | **konsistent dokumentiert** | ADR 0019 ⇄ Plan „Restrisiken Betrieb & Deployment" |
| Terminal-Totalausfall > maxDuration (#60) | **konsistent dokumentiert** | Plan referenziert `ExpiredExecutionsHealthIndicator.java` (existiert im Code) |
| Betriebliche Wächter setzen Alerting/Backup beim Betreiber voraus (#32) | **konsistent dokumentiert** | Plan verweist auf Runbook „Dauerbetrieb"; `TerminalConnectivityHealthIndicator.java`/`ExpiredExecutionsHealthIndicator.java` existieren |

### Offene Fragen / mit Auftraggeber klären

| Offene Frage | Status | Beleg |
|---|---|---|
| Vaadin-Lizenzmodell | **inhaltlich erledigt, aber Abschnitt nicht nachgezogen** | s. Finding F1 unten |
| Kommunikation an Endnutzer vor Portal-Umstellung (SHA1/Argon2id-Inkompatibilität mit Alt-Portal) | **gegenstandslos geworden, aber Abschnitt nicht nachgezogen** | s. Finding F2 unten – Alt-Portal existiert seit Phase 5 nicht mehr, die Frage setzt Parallelbetrieb voraus |
| Blockierender Fernwartungs-Request/Response-Aufruf | **offen** (korrekt als offen geführt) | `AdminDashboardView.showLog()`/`restart()` rufen `TerminalMaintenanceService` weiterhin synchron im Vaadin-Request-Thread auf (Code verifiziert, kein `CompletableFuture`/`@Push`-Umbau) |

## 3. Findings

- **niedrig · `docs/kb/05-migration-plan.md:1140–1153` (Abschnitt „Offene Fragen / mit
  Auftraggeber klären", Eintrag „Vaadin-Lizenzmodell")** · Die Frage wurde am 2026-07-23
  durch eine Auftraggeber-Entscheidung final beantwortet (Restrisiko akzeptieren, ADR
  0019, plus eigener Eintrag unter „Entscheidungen" und „Restrisiken Betrieb &
  Deployment"), der Eintrag im „Offene Fragen"-Abschnitt trägt aber keinen
  Erledigt/Verweis-Vermerk und liest sich weiterhin wie ein unbeantworteter
  Klärungsbedarf. Für eine *finale* Pre-Launch-Review ist das potenziell verwirrend
  (Doppelbuchung derselben Sache als „offen" und „entschieden"). **Empfehlung:** Eintrag
  mit einem kurzen „Erledigt 2026-07-23, siehe ADR 0019" schließen oder in den
  Änderungslog verschieben, analog zum Umgang mit dem aufgelösten Java-17-Restrisiko in
  der Risikotabelle.

- **niedrig · `docs/kb/05-migration-plan.md:1154–1161` (Eintrag „Kommunikation an
  Endnutzer vor Portal-Umstellung")** · Die Frage geht explizit von einem Parallelbetrieb
  „Nutzer, die BEIDE Portale abwechselnd nutzen" aus. Seit Phase 5 AP1 (2026-07-21) ist
  das Alt-Portal-Modul jedoch vollständig aus dem Repo entfernt (verifiziert: kein
  `Portal/`-Verzeichnis mehr, weder getrackt noch als Altlast liegen geblieben) – ein
  Parallelbetrieb existiert nicht mehr, die Frage ist damit gegenstandslos, wurde aber
  nie als erledigt/obsolet markiert. **Empfehlung:** Eintrag als „gegenstandslos seit
  Entfernung des Alt-Portals (Phase 5)" schließen, damit die Liste „Offene Fragen"
  tatsächlich nur noch echte Restpunkte zeigt (aktuell nur der dritte Eintrag,
  Fernwartungs-Blockierung, ist real offen).

- **niedrig · `Client-Raspi/src/main/org/kabieror/elwasys/raspiclient/application/ElwaManager.java:172–174`,
  `Client-Raspi/src/main/org/kabieror/elwasys/raspiclient/ui/AbstractMainFormController.java:93`** ·
  Beide Stellen deklarieren/fangen weiterhin `java.sql.SQLException`, obwohl das Terminal
  seit Phase 4 AP4/AP5 keinen JDBC-Zugriff mehr hat (verifiziert: `ApiClient` nutzt nur
  `java.net.http`, die `postgresql`-Dependency in `Client-Raspi/pom.xml` ist
  `scope: test`). Kein funktionaler Fehler (keine reale DB-Kopplung mehr, nur eine
  historische Exception-Signatur), aber ein kleiner Beleg dafür, dass die
  „kein-Direkt-DB-Zugriff"-Aufräumarbeit an dieser Stelle nicht ganz bis zum Signatur-
  Cleanup durchgezogen wurde. **Empfehlung:** bei nächster Gelegenheit (z. B. im Zuge der
  R3b-Code-Qualitäts-Nacharbeit) die tote `SQLException`-Deklaration entfernen, um jeden
  Anschein von Rest-DB-Kopplung zu beseitigen.

## 4. Nicht bestätigte Restrisiken / keine Abweichungen gefunden

Alle im Migrationsplan als „bewusst akzeptiert" geführten Restrisiken (Standort-Token,
Klartext-Admin-Mail, Session-Invalidierung, Timing-Orakel, Case-only-Kollision,
Vaadin-Lizenz, Terminal-Totalausfall, Betreiberpflicht Alerting/Backup) sind sowohl im
Migrationsplan als auch – soweit als 🧩-Auftraggeberentscheidung markiert – in den
zugehörigen ADRs (0008/0018/0019) wortgleich bzw. widerspruchsfrei geführt. Es wurden
keine Rahmenbedingungen aus Abschnitt „Rahmenbedingungen" gefunden, die im Code
stillschweigend nicht umgesetzt wurden. Issue #75 (Repo-Umzug) und Epic #66
(Pre-Launch-Review) sind über die GitHub-API verifiziert korrekt nachgehalten (#75 offen
mit vollständiger Checkliste, #66 `closed/completed`); keine weiteren offenen Issues im
Repository außer #75.
