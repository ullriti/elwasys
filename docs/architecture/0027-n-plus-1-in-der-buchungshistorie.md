# 27. Buchungshistorie: LAZY statt EAGER, expliziter Verbindungspool

- **Status:** accepted
- **Datum:** 2026-09-20

## Kontext

Beim Abnahmetest nach der Produktivumschaltung hing das Portal nach dem Login als
**Nicht-Admin** minutenlang. Der Login selbst ging durch (serverseitig protokolliert), aber
die UIDL-Anfrage mit `ui-navigate` auf Route `/` kam nicht zurück. Gemessen: rund **20 Minuten**
für ein Konto mit 506 Buchungen.

Die Diagnose führte zunächst in drei Sackgassen — alle belegbar widerlegt: Traefik blockiert
den Vaadin-Push (nein, Upgrade liefert 101; das beobachtete 501 war ein HTTP/2-Artefakt des
Testwerkzeugs), der Vaadin-Lizenzcheck blockiert (nein, der läuft nur im Dev-Modus), oder
`X-Forwarded-Proto` kommt falsch an (nein, Cookie trägt `Secure`, Redirect zeigt auf `https://`).

**In `pg_stat_activity` war nichts zu sehen** — keine langsame Abfrage, nichts blockiert. Das
ist der eigentliche Grund, warum der Fehler so schwer zu fassen war: Jede Einzelabfrage ist
millisekundenschnell, es sind nur Hunderte davon. Sichtbar wurde es erst in einem
Thread-Dump (`kill -3` an die JVM, Ausgabe nach stdout):

```
UidlRequestHandler -> Router -> UserDashboardView.<init>(:146) -> .refresh(:201)
  -> CreditService.getAccountingEntries(:269)
  -> Hibernate EntitySelectFetchInitializer -> SingleIdLoadPlan.load
  -> PgPreparedStatement.executeQuery   (wartend, elapsed 1241 s)
```

Ursache: `CreditAccountingEntryEntity.execution` war `@ManyToOne(fetch = EAGER)`, die
abgeleitete Abfrage `findByUser_IdOrderByDateDescIdDesc` erzeugt aber nur ein einfaches SELECT
ohne JOIN. Hibernate lud deshalb je Ergebniszeile eine eigene Einzelabfrage nach — und weil
jede Buchung zu einer **anderen** Ausführung gehört, half der Persistenzkontext nicht.

**Warum es niemand vorher sah:** Das Administrator-Konto hat **null** Buchungen; der
Portal-Durchgang vor dem Cutover lief als Admin. Die Playwright-E2E-Suite arbeitet auf
Demo-Daten mit wenigen Zeilen. Der Fehler skaliert mit der Datenmenge und wurde erst durch die
übernommenen Bestandsdaten sichtbar.

**Ausmaß im Bestand** (35 Konten mit Passwort): 4 Konten über 600 Buchungen, 8 zwischen 300
und 599, 7 zwischen 100 und 299. Für rund ein Drittel der Bewohner war das Portal unbenutzbar.

**Verschärfung:** Ein zweiter Thread-Dump während einer Guthaben-Aufladung zeigte, dass die
Abfrage nicht nur beim Öffnen läuft, sondern **bei jedem Domain-Event erneut** —
`UiBroadcaster.onDomainEvent -> UserDashboardView.onAttach -> refresh(:201)`. Jeder hängende
Aufruf belegt dabei einen Tomcat-Worker **und eine Datenbankverbindung**. HikariCP lief auf
seinem Default von 10, nirgends konfiguriert (live gemessen: 2 aktiv, 8 idle). Zehn
gleichzeitige Dashboard-Aufrufe hätten den Pool erschöpft — den sich die **Terminal-API mit
dem Portal teilt**. Aus einem langsamen Portal wäre ein Ausfall der Waschküche geworden.

## Entscheidung

1. **`CreditAccountingEntryEntity.execution` wird `FetchType.LAZY`.**
2. **Die HikariCP-Poolgröße wird explizit gesetzt** (20, überschreibbar per
   `ELWASYS_DB_POOL_SIZE`), dazu ein `connection-timeout` von 10 s.

## Warum LAZY und nicht `@EntityGraph`

Beide beheben das N+1. `@EntityGraph(attributePaths = "execution")` würde daraus eine einzige
Abfrage mit JOIN machen — die Daten aber weiterhin laden. **Sie werden nirgends gebraucht:**
`getExecution()` wird im gesamten Haupt- und Testcode **kein einziges Mal** aufgerufen, und
beide Ansichten der Buchungshistorie (`UserDashboardView`, `CreditHistoryDialog`) zeigen nur
Datum, Betrag und Buchungstext. LAZY lädt gar nichts nach; ein späterer Anwendungsfall holt den
Bezug gezielt per `@EntityGraph` auf der jeweiligen Abfrage.

Das Risiko von LAZY — eine `LazyInitializationException` außerhalb der Transaktion — besteht
hier nicht, eben weil kein Aufrufer existiert.

## Konsequenzen

**Positiv**

- Die Buchungshistorie kostet eine feste Anzahl Abfragen statt einer je Zeile. Der
  Regressionstest zählt sie (`CreditServiceAccountingHistoryFetchTest`) und schlägt mit dem
  zurückgedrehten `EAGER` fehl (gemessen: 11 Statements bei 10 Buchungen statt 1).
- Der explizite Pool entkoppelt Portal und Terminal-API ein Stück weit voneinander.

**Negativ / bewusst akzeptiert**

- Die Liste ist weiterhin **unpaginiert**: bis zu 1230 Zeilen gehen auf einmal in das Grid. Das
  ist jetzt eine Abfrage statt 1231, bleibt aber unnötig viel Arbeit für eine Ansicht, die
  niemand bis ans Ende scrollt. Für die Geräte-Historie wurde das bereits gelöst (Issue #30,
  seitenweise). Die Buchungshistorie sollte demselben Muster folgen — bewusst nicht Teil
  dieser Änderung, um den Fix klein und schnell ausrollbar zu halten.
- Ein größerer Pool verdeckt Abfrageprobleme, statt sie zu lösen. Der Wert ist deshalb
  konservativ gewählt; der eigentliche Auslöser ist behoben.
- **In Tests muss der Pool klein bleiben** (2, gesetzt in der Surefire-Konfiguration): Die
  Suite hält viele Spring-Kontexte im Cache, jeder mit eigenem Pool. Mit dem Produktivwert lief
  sie gegen PostgreSQLs Standardgrenze von 100 Verbindungen — mit dem irreführenden Fehlerbild
  `Failed to load ApplicationContext` in unbeteiligten Testklassen.

## Nachgeprüft: andere N+1-Stellen

Eine Durchsicht aller 14 EAGER-Verknüpfungen gegen alle listenliefernden Repository-Methoden
ergab keine weitere akute Stelle:

| Abfrage | Bewertung |
|---|---|
| Geräte-Historie (bis 3475 Zeilen) | bereits seitenweise gelöst (Issue #30) |
| Terminal-`/snapshot` | Guthaben bereits gebündelt (Issue #90) |
| Nutzer-, Geräte-, Programmlisten | wenige **unterschiedliche** Bezüge, der Persistenzkontext fängt sie ab |

Der Unterschied zur Buchungshistorie ist genau dieser: Dort gehört zu jeder Zeile eine andere
Ausführung, hier wiederholen sich die Bezüge.
