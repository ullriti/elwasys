# 2026-07-23 — AP5 (Portal: Performance, CRUD, Tests, Datenmodell): Umsetzung

**Ziel:** Das Pre-Launch-Arbeitspaket **AP5** (Epic #66) umsetzen — Portal skaliert mit der
Alt-DB, Lösch-/UX-Wächter vollständig, Test-Determinismus und Schema-Konsistenz. Ein PR gegen
`master`, Branch `claude/ap5-fortsetzung-ia6ysh`. Fachlich eng gekoppelte Backend-/Portal-Naht
(Service + Repository + Vaadin-View greifen je Issue ineinander) → bewusst in **einem**
Backend-Kontext bearbeitet statt über parallele Agenten (kein sauberer Schnitt, geteilte
Dateien wie `ExecutionService`/`ExecutionRepository`/`CreditService`).

## Auftraggeber-Entscheidungen (vorab geklärt)
- **#30**: Geräte-Historie im Dashboard per **Lazy-Pagination** (nicht feste Obergrenze) — die
  volle Historie bleibt erreichbar, ohne Full-Table-Load.
- **#60**: **komplett nach AP6 verschoben** (Kern-Fix ist betrieblich, überschneidet sich mit
  AP6-#32 Betriebskonzept). AP5 fasst #60 nicht an. **Hardware-Info des Auftraggebers festgehalten:
  die Steckdosen bleiben eingeschaltet, wenn das Terminal ausfällt** — d.h. die Maschine läuft im
  Totalausfall-Szenario unbeaufsichtigt weiter; für AP6/#32 relevant (Alerting/Runbook).

## Erledigt (Backend/Portal)

- **#30 Performance/Skalierung.** Admin-Dashboard-Historie lädt lazy seitenweise
  (`ExecutionService.getExecutions(device, Pageable)`/`countExecutions`, paginierte
  Repository-Query; `DashboardService.DeviceStatus` trägt die volle Historie nicht mehr;
  `AdminDashboardView.buildHistoryGrid` nutzt einen `CallbackDataProvider`, neueste zuerst). Der
  Preis (N+1 über lazy `program`/`user`/`group`) wird nur noch für sichtbare Zeilen berechnet.
  Guthaben-Spalte der Benutzerliste über `CreditService.getCredits(List)` in **zwei** Abfragen
  statt `2·N` (Buchungssummen gebündelt + Vor-Reservierungen gebündelt) — fachlich identisch zu
  `getCredit`.
- **#37 Indizes.** Additive Migration `V11` mit Indizes auf `executions(user_id)`,
  `executions(device_id)`, `credit_accounting(user_id)` (heiße Guthaben-/Historie-Pfade).
- **#38 Demo-Seeder-Wächter.** Abbruch, wenn das Admin-Passwort bereits gesetzt ist, der
  Demo-Marker `anna` aber fehlt (= produktive DB). Signal bewusst am Admin-Passwort (V7 leert es
  in frischen Installationen) statt an Zeilenzahlen — Letztere taugen nicht, da der geteilte
  Integrationstest-Bestand nie leer ist.
- **#39 Feldlängen.** `UserEntity.password` auf `length = 255` (DB-Spalte ist seit V2
  `VARCHAR(255)`); Soft-Delete-Username wird auf 50 Zeichen gekürzt (Präfix `#del<id>#` hat
  Vorrang) und ist idempotent (bereits gelöschte Benutzer werden nicht erneut präfigiert).
- **#40 Testdeterminismus.** `Thread.sleep(5)` in `ExecutionServiceTest` durch zwei feste,
  unterschiedliche `clientTimestamp` ersetzt; `waitForTimeout(200)` in `e2e/helpers.ts` durch
  Warten auf den gefilterten ComboBox-Eintrag.
- **#49 CRUD-/Doppelklick-Wächter.** `DeviceService.delete` wirft `EntityInUseException` bei
  laufender/abgelaufener, nicht abgeschlossener Ausführung (konsistent mit Standort/Programm/
  Gruppe); `AdminDevicesView` fängt sie ab. Löschen im `ExpiredExecutionsDialog` läuft jetzt über
  einen Bestätigungsdialog; `setDisableOnClick(true)` auf den geldbewegenden Primär-Buttons
  (`CreditTopUpDialog` „Buchen", `ExpiredExecutionsDialog` „Abrechnen"/„Alle abrechnen").
- **#50 Testabdeckung.** `RouteAccessAnnotationsTest` scannt jetzt den Classpath des `ui`-Pakets:
  jede `@Route`-View außerhalb `login/` muss `@RolesAllowed`/`@PermitAll` tragen und darf nie
  `@AnonymousAllowed` sein (eine neue, unabgesicherte View fällt automatisch durch). Neue E2E:
  Auszahlung + `NotEnoughCredit`-Pfad, Benutzer-Löschung, öffentlicher Reset-Link (ungültiger
  Schlüssel → neutrale Hinweismeldung, kein Formular).

## Entscheidungen / Restrisiken
- `getCredits` ist eine reine Performance-Optimierung mit **identischem** Ergebnis zu `getCredit`
  (durch `CreditServiceBatchCreditTest` abgesichert) — kein Verhaltenswechsel.
- #38-Wächter deckt den dokumentierten Schaden (Überschreiben des Admin-Passworts) präzise ab;
  eine frische Produktiv-DB **ohne** gesetztes Admin-Passwort ist kein realistischer Betriebszustand
  (Portal-Login wäre unmöglich) und daher bewusst nicht gesondert abgesichert.

## Tests
- Backend-Suite grün: **250 Tests, 0 Fehler** (`backend/run-backend-tests.sh`). Neu/erweitert:
  `CreditServiceBatchCreditTest`, `DemoDataSeederGuardTest`, `DeviceServiceTest` (#49),
  `UserServiceTest` (#39-Kürzung/Idempotenz), `ExecutionServiceTest` (paginierte Historie),
  `RouteAccessAnnotationsTest` (Classpath-Scan).
- Portal-E2E (Playwright) grün: **24 Tests** (inkl. der drei neuen aus #50).
- Review-Gate (`code-reviewer`) als blockierendes Gate gelaufen.

## Offen / nächster Schritt
- **AP6** (Deployment, Betrieb & Cutover, #31–#35/#62–#64) inkl. **#60** (Terminal-Totalausfall,
  betrieblich; Steckdosen bleiben an → unbeaufsichtigter Weiterlauf im Runbook/Alerting bedenken)
  und der Vaadin-Lizenz-🧩 (#33). **AP7** (KB) zuletzt.

## Referenzen
- Epic: https://github.com/ullriti/elwasys/issues/66 ; Issues #30/#37/#38/#39/#40/#49/#50 (AP5),
  #60 (→ AP6)
- Branch: `claude/ap5-fortsetzung-ia6ysh`
