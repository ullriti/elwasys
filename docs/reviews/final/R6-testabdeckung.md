# R6 — Testabdeckung (Prüffrage 9)

|              |                                            |
| ------------ | ------------------------------------------ |
| **Track**    | R6 (finale Review, docs/specs/0001-finale-review.md) |
| **Datum**    | 2026-07-23                                 |
| **Methode**  | Statische Analyse (kein Testlauf, keine Fixes) — Code-Inventar gegen docs/kb/08-test-plan.md und docs/kb/06-ui-tests.md abgeglichen |

## 1. Gesamturteil

Die Testabdeckung ist **angemessen bis leicht auf der sicheren Seite** — für ein Projekt kurz
vor dem ersten Feldeinsatz mit Geld-/Offline-Pfaden ist das begründet. Die dokumentierten
Zahlen in `docs/kb/06-ui-tests.md`/`08-test-plan.md` sind code-verifiziert korrekt (Backend
270 `@Test`/51 Klassen, Client 77 `@Test`/29 Klassen, Portal-E2E 27 `test()` inkl. 4 Smoke —
minimale Abweichungen zu den in der KB genannten Zahlen sind Zähl-Artefakte, keine Doku-Fehler).
Die kritischen Geld-/Idempotenz-/Concurrency-/Offline-Replay-Pfade sind auffällig gut
abgedeckt (mehrere dedizierte Concurrency- und Idempotenz-Testklassen, deterministische
Zeitstempel-Fixtures). Zwei konkrete Lücken verdienen Aufmerksamkeit vor dem Feldeinsatz: der
deCONZ-WS-Reconnect (Issue #19, laut KB „behoben") hat keinen eigenen Regressionstest, und ein
Test (`CreditServiceAccountingHistoryTest`) verlässt sich noch auf `Thread.sleep()` zur
Reihenfolge-Erzeugung, obwohl genau dieses Muster andernorts (Issue #40) bereits durch
deterministische Zeitstempel ersetzt wurde. Redundanz ist kein Problem — keine trivialen
Getter-Tests, keine sinnlosen Doppelungen gefunden; die einzige nennenswerte Über-Abdeckung
ist eine sehr feinkörnige Zeiteinheiten-Testmethode im Legacy-`InactivitySchedulerTest`.

## 2. Suiten-Inventar

| Suite | Ort | Umfang (code-gezählt) | Laufzeit-Infrastruktur |
|---|---|---|---|
| **Client TestFX/JUnit** (Charakterisierung + E2E) | `Client-Raspi/src/test/` | 77 `@Test` in 29 Klassen (41 Testdateien inkl. Simulatoren/Helfer) | TestFX + Xvfb, `run-ui-tests.sh` |
| **Client echtes E2E** (Teilmenge obiger Suite) | `Client-Raspi/src/test/.../application/*E2ETest.java` | ~20 `*E2ETest`-Klassen (fhem- und deCONZ-Varianten, Offline-Robustheit, Idempotenz, kleine UI) | echtes Backend + `FhemSimulator`/`DeconzSimulator`, `run-client-e2e.sh` |
| **Cross-Component E2E** | `backend/src/test/.../ws/TerminalMaintenanceRealClientE2ETest.java` | 5 `@Test` (per `backend/pom.xml`-Exclude aus dem normalen `mvn test` ausgeschlossen) | echter Client-Jar als Subprozess + echter Backend-Spring-Kontext, `run-cross-component-e2e.sh` |
| **Backend JUnit** | `backend/src/test/java/` | 270 `@Test` in 51 Klassen (57 Testdateien; ohne die separat laufende Cross-Component-Klasse ~265 in der Standard-Suite) | Testcontainers (CI) bzw. lokales PostgreSQL (`run-backend-tests.sh`) |
| **Portal Playwright E2E** | `backend/e2e/tests/` | 23 `test()` (login 2 / admin 3 / admin-crud 12 / dashboard 1 / user-portal 5) | echtes Vaadin-Flow-Portal im produktionsgebauten Backend-Jar, frische DB je Lauf |
| **Post-Deploy-Smoke** | `backend/e2e/tests-smoke/` | 4 `test()`, strikt read-only | läuft gegen bereits deployte Umgebung, Rollout-Gate |
| **Terminal-Update-Trockentests** | `deploy/terminal/auto-update-selftest.sh` | 6 benannte Fälle (#34-A/B/C, #62-A/B, + Basis) | Bash, komplett offline mit Fakes, Teil der CI (`.github/workflows/ci.yml`) |
| **Helm-Lint/Template** | CI-Job `helm` | Struktur-/Render-Smoke, kein funktionaler Testfall | `helm lint` + `helm template` |

Alle Suiten außer Post-Deploy-Smoke und Helm-Lint laufen bei jedem PR in `.github/workflows/ci.yml`.

## 3. Szenarien-Matrix

Legende: ✅ abgedeckt (Testklasse/-datei genannt) · 🟡 teilweise · ❌ nicht abgedeckt.

| Szenario | Client | Cross-Comp. | Backend-JUnit | Portal-E2E | Urteil |
|---|---|---|---|---|---|
| RFID-Login gültig | ✅ `ClientUsageE2ETest`/`ClientUsageDeconzE2ETest` | – | ✅ `CardLoginControllerTest.successfulCardLoginReturnsUserDataIncludingCredit` | – | ✅ |
| RFID-Login: unbekannte Karte | ✅ `ClientLoginVariantsE2ETest.unknown_card_is_rejected` | – | ✅ `CardLoginControllerTest.unknownCardIdReturns404` | – | ✅ |
| RFID-Login: gesperrter Nutzer | ✅ `ClientLoginVariantsE2ETest.blocked_user_card_is_rejected` | – | ✅ `CardLoginControllerTest.blockedUserIsRejectedWith403` | – | ✅ |
| RFID-Login: Gruppe am Standort nicht erlaubt | ✅ `ClientLoginVariantsE2ETest.user_from_disallowed_group_is_rejected` | – | ✅ `CardLoginControllerTest.userWhoseGroupIsNotAllowedAtThisLocationIsRejectedWith403` | – | ✅ |
| RFID-Login: Regex-Metazeichen/Malformed Card-ID | – | – | ✅ `CardLoginControllerTest.regexMetacharacterCardIdIsRejectedWith400` | – | ✅ |
| Programmstart mit Guthaben (FIXED) | ✅ `ClientUsageE2ETest`/`-Deconz` | – | ✅ `ExecutionServiceTest`, `ExecutionControllerTest` | ✅ P8 (Aufladung) | ✅ |
| Programmstart ohne/zu wenig Guthaben | ✅ `ClientInsufficientCreditE2ETest` (C9) | – | ✅ `ExecutionServiceTest`/`CreditService*` | – | ✅ |
| DYNAMIC-Programm (Preisanzeige) | ✅ `ClientDynamicProgramE2ETest` (C14) | – | ✅ `PricingService`-Pfad über `ExecutionServiceTest` | ❌ (P12 nur FIXED-Anlage) | 🟡 |
| Programm-Ende-Erkennung (Auto-Ende) | ✅ `ClientAutoEndE2ETest`/`-Deconz` (C11) | – | – (fachlich clientseitig) | – | ✅ |
| Abbruch laufender Ausführung | ✅ `ClientAbortExecutionE2ETest`/`-Deconz` (C12) | – | – | – | ✅ |
| Fortsetzen unterbrochener Ausführung (Resume) | ✅ `ClientResumeExecutionE2ETest` (C13) | – | – | – | ✅ |
| Standortfremdes/deaktiviertes Gerät ausgeblendet | ✅ `ClientDeviceVisibilityE2ETest` (C16) | – | ✅ `DeviceControllerTest` | – | ✅ |
| Doppelstart selbes Gerät (Concurrency) | – | – | ✅ `ExecutionControllerConcurrencyTest.concurrentStartsOnTheSameDeviceCreateExactlyOneExecution` | – | ✅ |
| Doppelstart selber Nutzer, versch. Geräte (Guthaben-Reservierung) | – | – | ✅ `ExecutionControllerConcurrencyTest.concurrentStartsForTheSameUserOnDifferentDevicesReserveCreditOnlyOnce` | – | ✅ |
| Konkurrierendes Finish (Doppelabrechnung) | – | – | ✅ `ExecutionControllerConcurrencyTest.concurrentFinishesBookThePriceExactlyOnce` | – | ✅ |
| Idempotenz Start/Finish (gleicher Key) | ✅ `ClientOfflineReplayIdempotencyE2ETest` | – | ✅ `ExecutionControllerIdempotencyTest` (7 Fälle inkl. Key-Länge, Operation-Mismatch, Replay nach Reset) | – | ✅ |
| Konkurrierende Auszahlung (Überziehungsschutz) | – | – | ✅ `CreditServiceConcurrencyTest.concurrentPayoutsNeverOverdrawTheAccount` | – | ✅ |
| Guthaben auf-/abbuchen inkl. Validierung | – | – | ✅ `CreditServiceAmountValidationTest` | ✅ P8/P23/P24 | ✅ |
| Offline-Betrieb: laufende Ausführung übersteht Ausfall | ✅ `ClientOfflineRobustnessE2ETest` (C15a) | – | ✅ `ExecutionControllerOfflineReplayTest` | – | ✅ |
| Offline-Betrieb: neue Buchung im Zeitfenster | ✅ `ClientOfflineRobustnessE2ETest` (C15b) | – | ✅ `ExecutionControllerOfflineReplayTest` | – | ✅ |
| Offline-Betrieb: Zeitfenster abgelaufen | ✅ `ClientOfflineRobustnessE2ETest` (C15c) | – | ✅ (Backdating-Grenzfälle) | – | ✅ |
| Offline-Replay: Dead-Letter bei Schreibfehler (#69) | ✅ `OfflineGatewayReplayTest` (Unit) | – | – | – | ✅ (clientseitig, korrekt verortet) |
| Offline-Replay: Uhren-Plausibilität/Backdating (#67) | ✅ `OfflineGatewayClockPlausibilityTest` | – | ✅ `ExecutionControllerOfflineReplayTest` (Zukunft/zu alt/Grenzfälle) | – | ✅ |
| Offline-Replay: Geister-Execution-Kompensation (#68) | – | – | 🟡 (kein Grep-Treffer auf „ghost"/„compensat" außerhalb des Backdating-Tests — siehe Finding) | – | 🟡 |
| Backend nicht erreichbar ohne Snapshot | ✅ `ClientDatabaseErrorE2ETest` (C15) | – | – | – | ✅ |
| Benachrichtigung E-Mail | – | – | ✅ `NotificationServiceEmailTest`, `ExecutionNotificationTransactionalTest` (AFTER_COMMIT) | – | ✅ (Unit/Mock-Ebene; reale Zustellbarkeit bewusst Generalprobe) |
| Benachrichtigung Push (Pushover) | – | – | ✅ `NotificationServicePushoverTest` | – | ✅ |
| Portal-CRUD Benutzer/Gruppen/Geräte/Programme (FIXED) | – | – | ✅ Service-Tests je Entität | ✅ P6/P9/P10/P12 | ✅ |
| Portal-CRUD Standort bearbeiten | – | – | ✅ `LocationServiceTest` | ✅ P14 | ✅ |
| Löschen mit Bestätigung + „in Verwendung"-Guard | – | – | ✅ `DeviceServiceTest`/`ProgramServiceTest`/`UserGroupServiceDeleteGuardTest`/`LocationServiceTest` (`EntityInUseException`) | ✅ P13/P25 (nur Lösch-UI, Guard-Fall nicht separat per E2E) | 🟡 |
| RouteAccess/Rechte (Admin vs. User) | – | – | ✅ `RouteAccessAnnotationsTest` (Classpath-Scan), `VaadinPortalSecurityTest` | ✅ P15/P18 | ✅ |
| Portal-Login: falsches Passwort / Brute-Force-Sperre | – | – | ✅ `ElwasysAuthenticationProviderBruteForceTest` | ✅ P3 | ✅ |
| Passwort-Reset: Anfrage/Token/Ablauf/geteilte E-Mail | – | – | ✅ `PasswordResetServiceTest` (8 Fälle) | 🟡 P19/P26 nur Dialog/Invalid-Key, kein Playwright-Roundtrip mit gültigem Token | 🟡 |
| Eigenes Passwort ändern (Nutzer-Portal) | – | – | ✅ `PasswordServiceTest` | ✅ P16 | ✅ |
| Fernwartung: Log/Status/Neustart | – | ✅ `TerminalMaintenanceRealClientE2ETest` (5 Fälle) | ✅ `TerminalWebSocketTest`, `TerminalMaintenanceServiceLocationScopeTest` | ✅ P21/P22 (= Cross-Component) | ✅ |
| Fernwartung: Standort-Scope-Prüfung (#26) | – | – | ✅ `TerminalMaintenanceServiceLocationScopeTest` | – | ✅ |
| deCONZ-WS-Reconnect nach Verbindungsabbruch (#19) | ❌ keine dedizierte Testklasse gefunden | – | – | – | ❌ (siehe Finding) |
| Terminal-Update/Watchdog (inkl. Endlosschleifen-Schutz #34, SHA-Prüfung #62) | – | – | – (eigene Shell-Suite) | – | ✅ `auto-update-selftest.sh` (in CI) |
| Health-Indicators (Terminal-Konnektivität, offene abgelaufene Executions) | – | – | ✅ `TerminalConnectivityHealthIndicatorTest`, `ExpiredExecutionsHealthIndicatorTest` | – | ✅ |
| Purge-Job Idempotenz-Keys (Retention) | – | – | ✅ `IdempotencyKeyRetentionSchedulerTest` (2 Fälle, deterministische Zeitstempel) | – | ✅ |
| Demo-Seeder (Produktiv-Schutz + Idempotenz) | – | – | ✅ `DemoDataSeederTest`, `DemoDataSeederGuardTest` | – | ✅ |

## 4. Findings

- **mittel** · `backend/src/test/java/org/kabieror/elwasys/backend/service/CreditServiceAccountingHistoryTest.java:54,76,79` ·
  Der Test verlässt sich auf drei `Thread.sleep(5)`-Aufrufe, um eine eindeutige zeitliche
  Reihenfolge der Buchungen (`findByUser_IdOrderByDateDesc`) zu erzwingen. Genau dieses Muster
  wurde in `ExecutionServiceTest.startExecutionSetsStartTimeOnlyOnce` bereits unter Issue #40
  bewusst durch feste, weit auseinanderliegende `LocalDateTime`-Werte ersetzt (Kommentar dort:
  „deterministisch statt Thread.sleep"). `CreditService.inpayment`/`payout` erlauben aber keinen
  clientseitig vorgegebenen Zeitstempel, sodass ein reiner API-Fix hier nicht greift — auf einer
  langsamen/überlasteten CI-Maschine ist ein knapper 5-ms-Abstand potenziell zu kurz.
  **Empfehlung**: entweder den Zeitstempel in `CreditAccountingEntryEntity`/`CreditService` für
  Tests injizierbar machen (Uhr wie andernorts, z. B. `MutableClock` in `AbstractBackendIT`) oder
  ersatzweise nach `id DESC` sekundär sortieren, um die Reihenfolge unabhängig vom Zeitstempel
  zu garantieren.
- **hoch** · `Client-Raspi/src/main/org/kabieror/elwasys/raspiclient/devices/deconz/DeconzEventListener.java:35-165`
  (Reconnect-Logik mit exponentiellem Backoff) ·
  Für den unter Issue #19 als „behoben" geführten deCONZ-WS-Reconnect nach Verbindungsabbruch
  existiert keine dedizierte Testklasse (Grep über `Client-Raspi/src/test` liefert für
  „reconnect" nur Treffer in Offline-Robustheit-Kommentaren, keine deCONZ-spezifische
  Testklasse). AGENTS.md verlangt für jeden Bugfix einen Regressionstest, der ohne den Fix
  fehlschlägt — dieser fehlt hier. Ein Reconnect-Fehlverhalten (z. B. wieder fehlender
  Backoff-Reset nach erfolgreicher Wiederverbindung) würde von keiner automatisierten Suite
  erkannt.
  **Empfehlung**: Testklasse ergänzen, die über `DeconzWebSocketServer`/`DeconzSimulator` die
  Verbindung gezielt schließt und beweist, dass (a) der Client automatisch neu verbindet und
  (b) danach wieder Leistungsmessungen empfängt (`ClientAutoEndDeconzE2ETest` als Vorbild für
  den Leistungsmess-Pfad).
- **mittel** · `backend/e2e/tests/admin-crud.spec.ts` (P12, Zeile ~306) ·
  Die Portal-E2E-Suite testet Programm-Anlage nur für den Preistyp FIXED. Der zweite,
  fachlich wichtige Preistyp DYNAMIC (Grundgebühr + Zeitpreis, clientseitig in C14 nur als
  Anzeige geprüft) hat keinen eigenen Portal-Formular-Test — ein kaputtes DYNAMIC-Anlageformular
  (z. B. fehlende Zeitpreis-Validierung) würde nicht auffallen.
  **Empfehlung**: einen zusätzlichen `test()` in `admin-crud.spec.ts` für die Anlage eines
  DYNAMIC-Programms ergänzen (P12b).
- **niedrig** · `Client-Raspi/src/test/org/kabieror/elwasys/raspiclient/ui/scheduler/InactivitySchedulerTest.java:23-43` ·
  `testTimeUnits()` prüft in einer einzigen Methode vier Zeiteinheiten (Nanosekunden bis
  Sekunden) mit `Thread.sleep`-Wartezeiten von wenigen Millisekunden bis 55ns-Bereich — ein
  Legacy-Test aus der Vor-Modernisierungs-Ära (Autor „Oliver Kabierschke", nicht im
  AP1–AP6-Muster). Die knappen Margen (z. B. `sleep(52)` gegen einen ~50-ms-Timer) sind auf
  einer langsamen/überlasteten CI-Maschine ein Flakiness-Risiko; inhaltlich deckt der Test aber
  sinnvolles Verhalten ab (Auto-Logout-Timing), keine Streichung nötig.
  **Empfehlung**: bei Gelegenheit (kein eigenes Arbeitspaket) auf einen deterministischen
  Test-Clock/Scheduler-Mock statt Wanduhr-`sleep` umstellen, analog zu den neueren
  Zeitstempel-Fixtures im Backend.
- **niedrig** · `backend/e2e/tests/user-portal.spec.ts` (P19/P26) ·
  Der volle Passwort-Reset-Roundtrip mit einem **gültigen** Token wird nur auf JUnit-Ebene
  (`PasswordResetServiceTest.resetPasswordWithAValidTokenSetsTheNewPasswordAndConsumesTheToken`)
  geprüft; die Playwright-Suite deckt nur das Öffnen des Dialogs und die Ablehnung eines
  ungültigen Keys ab. Das ist eine bewusste, vertretbare Aufteilung (ein Token aus einer
  realen Mail im E2E-Lauf abzugreifen wäre aufwändig), aber die UI-Seite des Erfolgspfads
  (neues Passwort setzen über `/reset-password?key=...` mit echtem Key) bleibt ungetestet.
  **Empfehlung**: nur nachziehen, falls die UI dieses Formulars sich ändert; aktuell kein
  Blocker.
- **niedrig** · `backend/src/test/java/org/kabieror/elwasys/backend/api/ExecutionControllerOfflineReplayTest.java` ·
  Für die unter #68 behobene Geister-Execution-Kompensation (kompensierender `abort` bei
  START ok/FINISH fachlich abgelehnt) liefert eine gezielte Grep-Suche nach „ghost"/„compensat"
  keinen eindeutigen Treffer außerhalb der Backdating-Grenzfälle in derselben Klasse — es ist
  möglich, dass dieser Pfad innerhalb bestehender Tests indirekt mitläuft, aber ohne
  eigenständigen, benannten Testfall ist das nicht auf den ersten Blick nachvollziehbar.
  **Empfehlung**: vor dem Feldeinsatz kurz verifizieren (z. B. mit dem `backend`-Agenten), ob
  ein Test explizit den Kompensationspfad (Execution nach abgelehntem Replay-Finish landet im
  Zustand „abgebrochen", nicht als Zombie „laufend") abdeckt; falls nicht, ergänzen.

## 5. Urteil je Suite

| Suite | Urteil | Begründung (kurz) |
|---|---|---|
| Client TestFX/JUnit (Charakterisierung) | **angemessen** | Isolierte State-Machine-/FXML-Tests decken das Nötige ab, keine Redundanz. |
| Client echtes E2E (fhem + deCONZ) | **angemessen, an der oberen Grenze** | Doppelte Testklassen je Gateway sind bewusst und begründet (fachlich unterschiedliche Pfade), keine Streichung sinnvoll — aber deCONZ-Reconnect fehlt (Finding oben). |
| Cross-Component E2E | **angemessen** | 5 gezielte Fälle für Log/Status/Neustart/Timeout/Scope reichen für den schmalen Fernwartungs-Kanal. |
| Backend JUnit | **angemessen, gut gewichtet** | Concurrency-/Idempotenz-/Offline-Replay-Pfade auffällig gründlich (mehrere dedizierte Klassen), keine trivialen Tests gefunden; eine Determinismus-Lücke (Thread.sleep) und eine mögliche Nachweislücke (#68) offen. |
| Portal Playwright E2E | **angemessen, knapp** | Deckt Kernpfade + die vier nachträglich ergänzten #22/#50-Fälle ab; DYNAMIC-Programm-Anlage und der Guard-Fall beim Löschen fehlen als E2E-Fall (Backend-seitig aber abgesichert). |
| Post-Deploy-Smoke | **angemessen** | Bewusst schlank (4 read-only Checks) für ein Rollout-Gate, kein Ausbau nötig. |
| Terminal-Update-Trockentests | **angemessen** | Sechs benannte Fälle decken exakt die dokumentierten Issues (#34/#62) ab, keine Lücke erkennbar. |

## 6. Hinweis zur Methodik

Rein statische Analyse: Testquellen gelesen, `@Test`/`test()` gezählt und gegen die KB-Zahlen
abgeglichen, gezielt nach `Thread.sleep`, `Random`, `now()`-Aufrufen und `@Order`/
`@TestMethodOrder` gegrept. Keine Tests ausgeführt, keine Fixes vorgenommen.
