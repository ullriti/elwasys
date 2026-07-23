# Synthese – Finale Review vor dem Feldeinsatz

> Zusammenführung aller neun Track-Reports der [Spec 0001](../../specs/0001-finale-review.md),
> erstellt vom Hauptagenten (Fable), 2026-07-24. Alle Hoch-Findings wurden vom
> Hauptagenten unabhängig am Code gegenverifiziert (Vermerke in den Track-Reports).

## 1. Gesamturteil je Prüffrage

| # | Prüffrage | Urteil | Track |
|---|-----------|--------|-------|
| 1 | Alle Ziele erreicht? | **Ja.** Alle Rahmenbedingungen, Roadmap-Phasen und Auftraggeber-Entscheidungen umgesetzt und code-verifiziert; Restrisiken konsistent dokumentiert. | R1 |
| 2 | Kritische Bugs/Schwachstellen? | **Keine kritischen; 3 Hoch-Findings** (1× Offline-Replay-Paar-Atomizität, 2× Terminal: Listener-Leak, Fall-Through-Switch). Geld-/Locking-/Auth-Pfade halten dem adversarialen Blick stand. | R2, R3b |
| 3 | Code-Qualität gut? | **Ja.** Backend hoch (keine Hoch-Findings), Terminal solide mit klar benanntem Altlast-Refactor-Bedarf (Gott-Klassen, Singleton), Portal sauber geschichtet mit struktureller Duplikation (Admin-Views/Dialoge, fehlender Binder). | R3a–c |
| 4 | Dokumentation gut? | **Gut bis sehr gut.** KB stimmt mit dem Code überein (>25 verifizierte Aussagen), Warum-Kommentare vorbildlich dosiert; Pflegelücken an den Rändern (Worklog-Index, CHANGELOG-Länge, 3 stale Kommentare, 2 kaputte Links). | R4 |
| 5 | Deployment gut überlegt? | **Ja, überdurchschnittlich reproduzierbar** (Multi-Stage non-root, Jar-SHA256, Cutover-Werkzeuge). Schwächen: Compose baut lokal statt GHCR-Release, Helm-Default-Tag leer, kein Image-Pinning. | R5 |
| 6 | Alerting gut überlegt? | **Konzept ja, Betrieb nein.** Health-Indicators und `/operational` sind sauber gebaut – aber **nichts pollt sie**; kein Alarmkanal erreicht einen Menschen. | R5 |
| 7 | Backup & Recovery gut überlegt? | **Halb.** Backup verpflichtend mit Retention; **Restore nur als Satz** – kein Schritt-für-Schritt-Weg, kein Skript, nie geprobt, RTO unbekannt. | R5 |
| 8 | Nachhaltiges Monitoring? | **Mechanik ja** (Purge-Job, Log-Rotation, TZ, Ledger bewusst unbegrenzt); Lücken bei NTP am Pi, Zertifikats-Erneuerung im Selbstverwaltungs-Pfad, Plattenplatz-Alarm. | R5 |
| 9 | Tests decken sinnvolle Szenarien? | **Angemessen, leicht auf der sicheren Seite.** Kritische Pfade gründlich; Lücken: deCONZ-Reconnect-Regressionstest, #68-Kompensationsfall, DYNAMIC-Programm-E2E, 2 Determinismus-Stellen. Keine wertlosen Tests. | R6 |
| 10 | Repo aufgeräumt? | **Ja, sehr.** Keine Altlasten des Alt-Portals/Common, Referenzen einheitlich, keine Artefakte/Secrets. Kleinfunde: totes Manifest, Env-Var-Lücke (→ Hoch-Finding unten), Doku-Kleinkram. | R7 |

**Gesamtbild:** Das System ist in Architektur, Korrektheit der Geldpfade, Code-Qualität
und Dokumentation bereit für den Feldeinsatz. Was fehlt, ist konzentriert auf **sieben
Hoch-Findings** – drei kleine Code-Fixes und vier Betriebs-/Konfigurationslücken – plus
die ohnehin geplante Generalprobe (Spec 0001, Abschnitt „Generalprobe"). Nichts davon
ist ein struktureller Rückschlag; alles ist in wenigen, kleinen Arbeitspaketen behebbar.

## 2. Priorisierte Findings

### VOR dem Feldeinsatz beheben (hoch)

| # | Finding | Fundstelle | Track |
|---|---------|-----------|-------|
| H1 | **Replay-Paar-Atomizität über Lauf-Grenzen:** START wird nach Erfolg sofort aus dem Journal entfernt; Kommunikationsabbruch vor dem FINISH macht diesen im Folgelauf zum Waisen → Execution bleibt „laufend", keine Abrechnung, Steckdose kann nach Terminal-Neustart wieder einschalten. Fix: START erst nach erfolgreichem Terminator-Replay entfernen (Server-Idempotenz macht das sicher). | `OfflineGateway.java:341-351, 403-415` | R2 |
| H2 | **Listener-Leak durch Copy-Paste:** `stopListenToExecutionStartedEvent` ruft `add` statt `remove` – Start-Listener-Liste wächst über Restarts, mehrfache `onExecutionStarted`-Aufrufe. | `ExecutionManager.java:326` | R3b (+R2) |
| H3 | **Fall-Through-Switch in der Gerätekachel:** `case DISABLED` ohne `break` fällt in `UNREGISTERED` – deaktivierte Geräte zeigen „Keine Steckdose" und werden wieder bedienbar (`setDisable(false)`). | `DeviceListEntry.java:446-466` | R3b (+R2) |
| H4 | **Kein verdrahteter Alarmkanal:** `/operational` liefert 503, aber nichts pollt – Backend/DB down, Terminal offline, abgelaufene Executions erreichen keinen Menschen. Fix: Cron→Pushover/Mail oder Uptime-Monitor als Pflicht-Schritt ins Runbook + einrichten. | Runbook Kap. 7b / `application.yml` | R5 |
| H5 | **Kein ausgearbeiteter Restore-Weg:** Backup ja, Wiederherstellung nur als Satz – Schritt-für-Schritt-Restore-Runbook (ggf. Skript) schreiben und **einmal real proben** (RPO/RTO festhalten). | Runbook Kap. 7a | R5 |
| H6 | **Cutover-Preflight veraltet:** `verify-cutover-migration.sh` erwartet Flyway-Historie nur bis V10, V11 existiert seit AP5 – der dokumentierte „21/21 PASS"-Lauf würde heute fehlschlagen. V11 nachziehen (und Erwartung künftig aus dem Migrationsordner ableiten). | `deploy/cutover/verify-cutover-migration.sh:157-174` | R4 |
| H7 | **`ELWASYS_PORTAL_BASE_URL` nicht durchgereicht:** weder Compose noch Helm setzen die Variable – Passwort-Reset-Mails (Feature default AN) verlinken in Produktion auf `http://localhost:8080`. In beide Deployment-Wege aufnehmen + Runbook-Gate. | `docker-compose.yml`, Helm `configmap.yaml`/`values.yaml` | R7 |

Dazu als Test-Pflicht (hoch, entsteht sinnvollerweise mit den Fixes):

- **T1** Regressionstest deCONZ-WS-Reconnect (#19) – einziger „behobener" Bug ohne Test (R6).
- **T2** Regressionstests zu H1–H3 (Replay-Abbruch zwischen START/FINISH; Listener-Abmeldung; DISABLED-Rendering) (R2/R3b/R6).

### Zeitnah nachziehen (mittel, kann kurz nach Feldeinsatz)

**Betrieb (R5):** Dead-Letter-/GEISTER-Fehler nur im lokalen Pi-Log → ans Backend/Health
melden · Compose auf GHCR-Release-Image statt lokalem Build · Helm-`image.tag`-Default
· NTP-Einrichtung/-Prüfung in `setup.sh` (Pi ohne RTC → Replay-Zeitstempel!) ·
Zert-Ablauf-Monitoring im Selbstverwaltungs-Pfad · Backup-Scope um Terminal-Properties/
Token/Secrets ergänzen.

**Tests (R6):** `Thread.sleep(5)` in `CreditServiceAccountingHistoryTest` deterministisch
machen · Portal-E2E für DYNAMIC-Programmanlage · `InactivitySchedulerTest`-Margen.

**Code-Qualität (R3):** `UserGroupService`-Triplikat · `SnapshotController`: Fachlogik in
Service + vorhandenes `getCredits`-Batch nutzen (2·N→2 Abfragen) · `ExecutionController.start`
Wächter extrahieren · `TerminalMaintenanceService` Doppel-Map → Record ·
FHEM-Log-Bug `e` statt `e1` (Ferndiagnose!) · Portal: `AbstractAdminListView` +
`Notifications`-/`PortalFormats`-Utility · `AdminDashboardView` Fernwartungs-Toolbar
auslagern · `displayError`-Konsolidierung · Währungsformatierung `ui/small`.

**Doku/Hygiene (R4/R7):** Worklog-Index-Eintrag `offline-replay-haertung-ii` nachtragen ·
CHANGELOG-Einträge auf 1–2 Zeilen kürzen · stale Kommentare (`LocationOccupiedException`,
`AdminDashboardView:77-81`, `SQLException`-Restspur) · 2 kaputte Links · totes
`MANIFEST.MF` · „Offene Fragen" im Migrationsplan schließen (Vaadin-Lizenz,
Endnutzer-Kommunikation).

### Kann warten (niedrig)

Sammelposten aus allen Tracks (Details in den Reports): Stil-Vereinheitlichungen
(Logger, Imports, `Locale.ROOT`, `.toList()`, Switch-Expressions), Vor-Java-21-Idiome im
Terminal-Altbestand (`Vector`, `printStackTrace`, String-Konkat-Logging), Tippfehler in
Bezeichnern, `OfflinePricing`-Parity-Test, Binder-Umstieg Portal, `PlaceholderView`
entfernen, `.env.example`-/`.dockerignore`-Kleinkram, Testzahl-Drift in KB.

## 3. Vorschlag Arbeitspakete

| AP | Inhalt | Zuständig | Wann |
|----|--------|-----------|------|
| **FR-1** | Code-Fixes H1–H3 + Regressionstests T2, dazu FHEM-Log-Bug (`e1`) | terminal | vor Feldeinsatz |
| **FR-2** | Betrieb: H4 Alerting verdrahten, H5 Restore-Runbook+Probe, H6 verify-Skript V11, H7 `PORTAL_BASE_URL`; dazu Compose/Helm-Image-Bezug | devops | vor Feldeinsatz |
| **FR-3** | Tests: T1 deCONZ-Reconnect, DYNAMIC-E2E, Determinismus-Stellen | terminal/portal | vor/kurz nach Feldeinsatz |
| **FR-4** | Qualitäts-Refactors (R3-Mittelfunde, gebündelt je Modul) | backend/terminal/portal | nach Feldeinsatz |
| **FR-5** | Doku-/Hygiene-Sammelpaket (R4/R7-Mittel- und Niedrigfunde) | – | nach Feldeinsatz |

Danach: **Generalprobe** nach Spec 0001 (Cutover-Probelauf, Migrations-Dry-Run,
Backup-Restore-Probe, Ausfall-Drills, Alarm-Probe, Soak-Test, Kalibrierung vor Ort) und
Pilotphase mit definierten Abbruchkriterien.

## 4. Methodik-Nachweis

- 9 Tracks laut Spec 0001; Teil A (R1/R2/R6) am 2026-07-23, Teil B (R3a–c/R4/R5/R7) am
  2026-07-24. Modelle: Tiefen-Tracks Opus, Breiten-Tracks Sonnet, Synthese Fable.
- Alle Hoch-Findings (H1–H3, H6, H7) vom Hauptagenten unabhängig am Code verifiziert;
  H4/H5 sind Abwesenheits-Befunde (kein Alarmkanal/kein Restore-Weg – per Suche belegt).
- Bewusst akzeptierte Restrisiken (Migrationsplan „Restrisiken", ADR 0018/0019) wurden
  nicht erneut gemeldet.
