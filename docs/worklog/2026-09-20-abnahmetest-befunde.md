# 2026-09-20 — Befunde aus dem Abnahmetest nach der Produktivumschaltung

**Ziel:** Die beim Abnahmetest nach dem Cutover gefundenen Fehler beheben, gebündelt für eine
Version (Auftraggeber-Entscheidung: nicht einzeln nachschieben).

## Der teuerste Fund: N+1 in der Buchungshistorie

Nach dem Login als **Nicht-Admin** hing das Portal minutenlang. Vollständige Herleitung in
[ADR 0027](../architecture/0027-n-plus-1-in-der-buchungshistorie.md); hier nur, was für die
Arbeitsweise zählt:

**Drei Sackgassen vor der Ursache** — jede belegbar widerlegt statt „wahrscheinlich nicht":
Traefik blockiere den Vaadin-Push (nein, Upgrade liefert 101; das beobachtete 501 war ein
HTTP/2-Artefakt des Testwerkzeugs — über HTTP/2 gibt es kein WebSocket-Upgrade), der
Vaadin-Lizenzcheck blockiere den Aufbau (nein, nur im Dev-Modus), `X-Forwarded-Proto` komme
falsch an (nein, Cookie trägt `Secure`, Redirect zeigt auf `https://`).

**`pg_stat_activity` zeigte nichts.** Genau das machte den Fehler so schwer greifbar: Jede
Einzelabfrage ist millisekundenschnell, es sind nur Hunderte. Wer nach „der einen langsamen
Abfrage" sucht, findet nichts und schließt daraus das Falsche.

**Der Thread-Dump war das Werkzeug**, nicht das letzte Mittel: `kill -3` an die JVM schreibt
ihn nach stdout, also direkt in `kubectl logs` — ohne `jcmd`/`jstack`, die im JRE-Image fehlen,
und ohne Actuator-Endpunkt. Ein Thread stand seit 1241 s in
`CreditService.getAccountingEntries` und wartete auf PostgreSQL.

**Lehre:** Bei „hängt, aber nichts im Log und nichts in der DB" gehört der Thread-Dump an den
Anfang der Diagnose, nicht ans Ende.

## Erledigt

- `CreditAccountingEntryEntity.execution` auf `LAZY` (statt `@EntityGraph` — die Verknüpfung
  wird nirgends gelesen, siehe ADR).
- Regressionstest `CreditServiceAccountingHistoryFetchTest`: zählt die abgesetzten Statements
  über Hibernates Statistik.
- HikariCP-Poolgröße explizit (20, überschreibbar), `connection-timeout` 10 s.
- `ClockConfig` auf die Systemzone, Regressionstest `ClockConfigTest`.
- fhem-Log-Rauschen: `logger.info` → `trace`, Meldung präzisiert.
- `upgrade-jre.sh`/`setup.sh` auf Java 17 (ADR 0026), Zielversion überschreibbar.
- Favicon als statische Ressource unter `META-INF/resources/icons/`.

## Zwei Tests, die sich verdient haben

- **Der N+1-Test zählt Statements, keine Millisekunden.** Eine Laufzeitschranke wäre mit wenigen
  Testzeilen auf einem schnellen Rechner auch bei N+1 grün geblieben.
- **Er legt ECHTE Ausführungen an.** Der bestehende `CreditServiceAccountingHistoryTest` setzt
  `execution = null`; bei `null` gibt es nichts nachzuladen, und der Test wäre auch gegen den
  zurückgedrehten Fix grün geblieben — also wertlos. Gegenprobe gefahren: mit `EAGER` meldet er
  11 Statements bei 10 Buchungen statt 1.

## Zwei eigene Fehlgriffe, dokumentiert weil lehrreich

- **Eine `application.yml` unter `src/test/resources` ERSETZT die aus `src/main/resources`,
  sie ergänzt sie nicht.** Der Versuch, dort nur die Testpoolgröße zu setzen, nahm den Tests
  die gesamte übrige Konfiguration: 204 Folgefehler. Der Wert sitzt jetzt in den
  `systemPropertyVariables` der Surefire-Konfiguration, wo er nichts verdeckt.
- **Gelöschte Testressourcen bleiben in `target/test-classes` liegen.** Nach dem Entfernen der
  Datei war der Fehler unverändert — bis die Altkopie im Zielverzeichnis weg war. Bei
  unerklärlich unveränderten Testergebnissen zuerst dort nachsehen.

## Bewusst NICHT geändert

- **Die Header-Zeile des Terminals.** Rückmeldung war „kleiner als davor" — die Basis-Schriftgröße
  steht aber seit dem ersten Commit unverändert bei 12 px, und der Auftraggeber stellte später
  klar, dass nur **eines** der beiden Terminals betroffen ist. Beide haben 800×480; es ist also
  kein Software-Unterschied, sondern vermutlich eine andere Bildschirmdiagonale. Eine globale
  Vergrößerung hätte das unauffällige Gerät verschlechtert. Eine bereits umgesetzte Änderung
  (Höhe 40→52, Schrift 1→1.25em) wurde deshalb zurückgenommen.
- **Seitenaufteilung der Buchungshistorie.** Bis zu 1230 Zeilen gehen weiterhin auf einmal ins
  Grid — jetzt in einer Abfrage statt 1231. Für die Geräte-Historie ist das Muster schon da
  (Issue #30); die Buchungshistorie sollte folgen, aber nicht in diesem Fix.

## Nachgeprüft: keine weiteren N+1-Stellen

Alle 14 EAGER-Verknüpfungen gegen alle listenliefernden Repository-Methoden durchgesehen. Die
Geräte-Historie (bis 3475 Zeilen) ist bereits seitenweise gelöst (Issue #30), der
Terminal-`/snapshot` bündelt die Guthaben bereits (Issue #90). Die übrigen Listen sind
unkritisch, weil sich ihre Bezüge wiederholen und der Persistenzkontext sie abfängt — der
Unterschied zur Buchungshistorie, wo jede Zeile eine andere Ausführung trägt.
