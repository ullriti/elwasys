# R3a — Code-Qualität Backend (`backend/src/main/java` ohne `ui/`, plus `resources`)

> Track R3a der [Spec 0001](../../specs/0001-finale-review.md), Modell Opus, 2026-07-24.
> Geprüft wurden repräsentativ alle Pakete: api, service, auth, ws, notification,
> offline, health, domain, config, demo, events, exception, repository, resources.

## 1. Gesamturteil

Die Code-Qualität des Backends ist **hoch**. Die Schichtung
(domain/repository/service/api/auth/ws/health/notification) ist sauber und konsequent
durchgehalten, es gibt keine Gott-Klassen (größte Nicht-UI-Datei: `ExecutionService` mit
364 Zeilen, gut in kleine Ein-Zweck-Methoden zerlegt), Konstruktor-Injection durchgängig,
Records mit einheitlichem `of()`-Fabrikmuster für alle DTOs, und eine außergewöhnlich hohe
Nachvollziehbarkeit über Warum-Kommentare, die jede Portierungsentscheidung an Alt-Code,
Issue-Nummer und ADR knüpfen. Es gibt **keine Findings der Schwere hoch** — nichts im
geprüften Umfang behindert künftige Wartung spürbar oder ist riskant. Die
Verbesserungspotenziale sind ausnahmslos mittel/niedrig: einige echte Copy-Paste-Stellen
(v. a. `UserGroupService`), eine nicht wiederverwendete Batch-Abfrage
(`SnapshotController`), sowie kleinere Stil-Inkonsistenzen (Logger-Deklaration, `Locale`,
`.toList()`).

## 2. Positives (bewusst hervorgehoben)

- **Testbarkeit ist eingebaut, nicht nachträglich:** `RateLimiter` und
  `TerminalTokenService` bekommen eine `Clock` injiziert (deterministische Zeitfenster
  ohne `sleep`), `IdempotencyKeyRetentionScheduler.purgeOlderThan(threshold)` nimmt den
  Schwellwert explizit entgegen, `PushoverClient`/Reset-URLs sind konfigurierbar für
  Mock-Server. Das ist vorbildlich und deckt sich mit der Determinismus-Vorgabe aus
  AGENTS.md.
- **Nebenläufigkeit sauber gekapselt:** `AdvisoryLockService` mit `Propagation.MANDATORY`
  (deckt versehentlichen Autocommit-Aufruf sofort als Fehler auf), getrennte
  Advisory-Lock-Namensräume, `TerminalConnectionRegistry` kapselt die `WebSocketSession`
  bewusst intern. Konsistente, gut begründete Sperrstrategie.
- **Records + statisches `of()` durchgängig** (`ExecutionDto`, `UserDto`, `DeviceDto`,
  `VerificationResult`, `PushoverClient.Result`, die `DashboardService`-Records) — ein
  einheitliches, modernes Muster ohne Ausreißer. Ebenso die einheitliche
  RFC-7807-`ProblemDetail`-Fehlerabbildung.

## 3. Findings

### Aspekt 1 — Duplikate

- **mittel · `UserGroupService.java:129-184`** — `setValidLocations`, `setValidDevices`,
  `setValidPrograms` sind drei fast identische ~15-Zeilen-Methoden, die sich nur in
  Repository und `getValidUserGroups()`-Zugriff der Gegenseite unterscheiden (dieselbe
  „shouldBeValid/isValid → add/remove"-Schleife dreimal). **Empfehlung:** eine generische
  private Hilfsmethode `syncGroupMembership(Collection<E> entities, Set<Integer>
  targetIds, Function<E,Set<UserGroupEntity>> groupsAccessor, JpaRepository<E,?> repo,
  UserGroupEntity group)`; die drei öffentlichen Methoden reichen sie nur durch. Analog
  `findValidLocations/Devices/Programs` (Zeilen 186-201, drei identische Stream-Filter) —
  niedrig, gemeinsam mitziehbar.

- **mittel · `TerminalMaintenanceService.java:59-61, 169-221`** — zwei parallele Maps
  (`pendingRequests`, `pendingRequestLocations`) unter demselben Korrelationsschlüssel;
  das paarweise Aufräumen (`remove(id)` aus beiden) ist an fünf Stellen wiederholt und
  leicht auseinanderlaufsanfällig bei künftigen Änderungen. **Empfehlung:** eine einzelne
  `Map<String, PendingRequest>` mit `record PendingRequest(CompletableFuture<TerminalWsMessage>
  future, Integer locationId)`; dann ein einziges `remove` je Stelle.

- **niedrig · `NotificationService.java:175-187 vs. 250-258`** — `sendEmail` und
  `sendEmailOrThrow` bauen die `SimpleMailMessage` byte-für-byte identisch auf und
  unterscheiden sich nur im try/catch. **Empfehlung:** eine private
  `buildMessage(user, subject, content)` extrahieren; die zwei Methoden kapseln nur noch
  die Fehlerbehandlungs-Strategie. (Der Verhaltensunterschied — schlucken vs.
  weiterwerfen — ist bewusst und dokumentiert, bleibt erhalten.)

- **niedrig · `DeviceController.java:120-125 vs. 141-144`** — die Preis-Berechnung +
  `ProgramDto.of(...)` für ein Programm steht einmal inline in `toOverviewDto` (mit
  `null`-User) und einmal in `toProgramDto`. Geringe, aber vermeidbare Doppelung der
  Preis-je-Programm-Bildung.

### Aspekt 2 — Struktur / Verantwortlichkeiten

- **mittel · `ExecutionController.java:158-249`** — `start()` ist mit dem eingebetteten
  Idempotenz-Lambda ~90 Zeilen lang und trägt zwei Verantwortungen: die fachlichen
  Start-Wächter (Sperrung/Standort/Nutzbarkeit/Belegung/Guthaben, Zeilen 186-219) und die
  Ausführungs-/Zeitstempel-Anlage. Das ist die einzige nennenswert überlange Methode im
  Backend. **Empfehlung:** den Nicht-Replay-Wächterblock in eine private
  `applyStartGuards(device, program, request)` (oder in den `PermissionService`/einen
  `ExecutionStartValidator`) auslagern; die HTTP-/Idempotenz-Orchestrierung bliebe
  schlank und die Fachregeln würden isoliert testbar.

- **mittel · `SnapshotController.java:60-88`** — die Snapshot-Zusammenstellung
  (Programm-Dedup über `LinkedHashSet`, Nutzerfilterung nach zulässigen Gruppen,
  Guthabenanreicherung) ist reine Fachlogik im Controller. Sie ist ohne Web-Kontext nicht
  direkt testbar und passt eher in einen `SnapshotService` (analog `DashboardService`,
  der genau aus diesem Grund von Vaadin entkoppelt wurde). **Empfehlung:** Assembly in
  einen Service ziehen; der Controller reicht nur `terminal.locationId()` durch.

- **mittel · `SnapshotController.java:78-80` (Konsistenz/Wiederverwendung)** — die
  Guthabenermittlung ruft `creditService.getCredit(u)` je Nutzer in einem Stream über
  *alle* Standort-Nutzer auf (2 Abfragen × N). Für genau diesen Fall existiert bereits
  die gebündelte `CreditService.getCredits(List<UserEntity>)` (zwei Abfragen gesamt, in
  `AdminUsersView` genutzt, Issue #30). Hier wird die effiziente Variante nicht
  wiederverwendet — zwei Wege für dieselbe Aufgabe, der teure gewinnt. **Empfehlung:** im
  Snapshot `getCredits(users)` verwenden und die Map beim Mapping abgreifen.

### Aspekt 3 — Stand der Technik / Java 21

- **niedrig · `PricingService.java:43-52 und 84-99`** — klassische `switch`-Statements
  mit `break`; in `getPrice` liefert der `default`-Zweig `price = null`, das anschließend
  per `if (price == null) return null` behandelt wird (umständlich und null-lastig).
  **Empfehlung:** Switch-Expressions mit Arrow-Syntax auf die (nicht-null-)Enums
  `ProgramType`/`TimeUnitType`; der unmögliche Fall wird zum `throw` im `default` statt
  zu einem durchgereichten `null`. Das entfernt den `null`-Rückgabepfad, den mehrere
  Aufrufer (`DemoDataSeeder:304`) defensiv abfangen müssen. (1:1-Portierung bleibt
  semantisch erhalten.)

- **niedrig · `PermissionService.java:53`** — `.collect(Collectors.toList())`
  (veränderliche Liste, altes Idiom) als einzige Stelle; überall sonst wird
  `.stream()...toList()` (Java 16+) genutzt. **Empfehlung:** auf `.toList()`
  vereinheitlichen.

### Aspekt 4 — Lesbarkeit / Wartbarkeit

- **niedrig · Logger-Deklaration uneinheitlich** — teils `private static final Logger
  LOG = LoggerFactory.getLogger(X.class)` (z. B. `ApiExceptionHandler`,
  `TerminalTokenService`), teils `private final Logger logger =
  LoggerFactory.getLogger(getClass())` (`ExecutionController:114`,
  `NotificationService:79`, `IdempotencyService:58`, `ClientTimestampPolicy:40`). Kein
  Fehler, aber ein spiegelungswürdiges Muster i. S. v. AGENTS.md §5 („Bestehende Muster
  spiegeln"). **Empfehlung:** eine Variante als Konvention festlegen.

- **niedrig · Inline voll qualifizierte Typnamen statt Imports** —
  `DeviceController.java:106` (`@jakarta.validation.Valid`),
  `TerminalMaintenanceService.java:211/216`
  (`java.util.concurrent.ExecutionException`/`TimeoutException`),
  `TerminalConnectionRegistry.java:91/114`
  (`org.springframework.web.socket.WebSocketMessage`, `java.util.function.Consumer`),
  `ExecutionController.java:114` (`org.slf4j.Logger`). Stört den Lesefluss und weicht vom
  sonst konsequenten Import-Stil ab. **Empfehlung:** importieren.

- **niedrig · `toLowerCase()` ohne `Locale`** — `UserEntity.java:104/125` und
  `UserService.java:163` normalisieren Benutzernamen mit dem Default-Locale, während
  sicherheitsnahe Stellen (`ElwasysAuthenticationProvider:159`,
  `PasswordResetService:114`, `PasswordVerificationService:107`) bewusst `Locale.ROOT`
  verwenden. Da hier genau dieselben Benutzernamen (case-insensitiver Login-Schlüssel)
  verglichen werden, ist die Inkonsistenz unschön und trägt ein latentes Locale-Risiko
  (Türkisch-I). **Empfehlung:** an diesen drei Stellen ebenfalls `Locale.ROOT` (die
  tiefergehende Korrektheitsfrage gehört zu R2). Randnotiz: `UserService.java:163-164`
  normalisiert erst auf lowercase und vergleicht dann mit `equalsIgnoreCase` — der
  `IgnoreCase`-Teil ist danach redundant.

### Aspekt 5 — Konventionstreue (AGENTS.md §5)

- Bezeichner englisch, Kommentare deutsch, Spring-Schichtung, Flyway-only-Schema (V1–V11,
  additiv, sprechende Namen), keine Secrets in Code/`application.yml` (durchgängig
  `${ENV:default}`) — **alle eingehalten**. Kein Finding.
- **niedrig (Konsistenz) · `DeviceController.java:113-127`** — `toOverviewDto` sortiert
  Programme per `(a,b) -> a.getId().compareTo(b.getId())`; `SnapshotController.java:69`
  und `:75` verwenden dasselbe Lambda. Kein Fehler, aber ein wiederkehrendes
  Vergleichs-Lambda, das sich als `Comparator.comparing(ProgramEntity::getId)` bzw. eine
  gemeinsame Utility knapper und einheitlicher schreiben ließe.

---

**Kalibrierungshinweis:** Die Menge der Findings ist bewusst klein und durchweg
mittel/niedrig — das entspricht dem tatsächlichen Zustand. Für die finale
Feldeinsatz-Freigabe ist die Backend-Code-Qualität aus Wartbarkeitssicht **grün**; die
genannten Punkte sind lohnende Aufräum-Refactors (v. a. `UserGroupService`-Triplikat,
`SnapshotController`-N+1/Service-Extraktion, `ExecutionController.start`), aber kein
Freigabe-Blocker.
