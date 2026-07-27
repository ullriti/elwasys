# 2026-07-27 — Cutover-Blocker: Bestandsterminals ohne Supervisor-Vertrag

**Anlass:** Frage des Auftraggebers vor der für nächste Woche geplanten Umstellung —
„Müssen wir noch was machen? Schau dir nochmal die offenen Issues an." Also zunächst
eine Triage der sieben offenen Issues gegen den tatsächlichen Cutover-Pfad, dann die
Umsetzung dessen, was vor dem Feldeinsatz stehen muss.

Zuschnitt: **direkt bearbeitet, nicht delegiert.** Die Änderung liegt fast vollständig
auf einer einzigen Naht (dem Startbefehl des Terminals: `setup.sh` ↔ `update.sh` ↔
Watchdog ↔ Release-Paket); ein einziger Kontext ist hier robuster als vier Übergaben.
Die drei Java-Zeilen aus #100 hängen inhaltlich an derselben Naht (die Locale-Flags im
Startbefehl), deshalb ebenfalls hier.

## Der Befund: ein Blocker, der in keinem Issue stand

Das Runbook (Schritt 3d) schickt Bestandsgeräte über `deploy/terminal/update.sh`. Das
Skript hängt den Jar-Symlink um und löst den Neustart per `sudo killall java` aus — im
Vertrauen auf die Supervisor-Schleife in `run.sh`.

**Die Schleife gibt es auf den Feldgeräten nicht.** Die dort laufende `run.sh` stammt
aus dem ursprünglichen `setup.sh` (Commit `2dee8ed`) und ist ein Einmalstart:

```bash
sudo killall java 2> /dev/null
java -Djavafx.platform=gtk -Dlogback.configurationFile=… \
        -Djavax.net.ssl.trustStore=… -jar raspi-client.latest.jar -verbose > log/stdout 2> log/errout
```

Der Supervisor-Loop kam erst mit Phase 6 AP3 dazu, und `update.sh` ließ `run.sh`
ausdrücklich unangetastet. Konsequenz beim Cutover: `killall java` beendet die JVM,
`run.sh` läuft aus, `~/.xsession` endet — **das Terminal bleibt dunkel**, bis jemand vor
Ort ist. Betroffen wäre auch der Auto-Rollback des Watchdogs, der auf demselben Vertrag
sitzt: genau der Rückweg, den man in einer Charge braucht, wenn etwas schiefgeht.

Issue #101 beschrieb die Lücke als still und kosmetisch („JVM-Flags erreichen alte
Terminals nicht"). Auf dem Cutover-Pfad ist sie es nicht.

Nebenbefund derselben Naht: das alte `setup.sh` richtete einen eigenen Truststore ein
und reichte ihn per `-Djavax.net.ssl.*` an die JVM; das heutige tut das nicht mehr. Ein
naives Überschreiben der `run.sh` hätte einem Gerät mit privater CA still das Vertrauen
zum Backend entzogen.

## Erledigt

### Gemeinsame Quelle für `run.sh` (#101, Weg 1 nach Auftraggeber-Entscheidung)

- Neu: [`deploy/terminal/run-sh.lib.sh`](../../deploy/terminal/run-sh.lib.sh) — der
  Generator plus die Bestandsaufnahme einer vorhandenen `run.sh` (Vertragsversion,
  Supervisor-Schleife vorhanden?, Truststore-Flags). `Client-Raspi/setup.sh` und
  `deploy/terminal/update.sh` ziehen ihn beide; eine Änderung am Startbefehl wirkt damit
  auf beiden Wegen.
- **Vertragsversion** als Markerzeile in der erzeugten Datei (`# elwasys-run-sh-contract: 2`).
  `update.sh` erneuert `run.sh`, wenn sie älter ist, und sichert die bisherige Fassung als
  `run.sh.v<n>.bak`.
- Geschrieben wird über Temp-Datei + `mv`. Das ist hier wesentlich, nicht kosmetisch:
  läuft die alte `run.sh` gerade, hält bash einen Datei-Offset auf ihr Inode — ein
  In-place-Überschreiben ließe den laufenden Supervisor an einer verschobenen
  Byte-Position weiterlesen.
- Truststore-Flags eines Altbestand-Geräts wandern beim Erneuern mit.
- `setup.sh` löst die Bibliothek auf zwei Wegen auf. Beim Schreiben fiel auf, dass die KB
  den Ersteinrichtungs-Weg als **One-Liner** dokumentiert
  (`bash <(curl … Client-Raspi/setup.sh)`) – dabei zeigt `BASH_SOURCE` auf `/dev/fd/*`, es
  gibt also gar kein Nachbarverzeichnis, und die erste Fassung der Änderung hätte genau
  diesen Weg abgebrochen. Jetzt: lokale Datei, wenn vorhanden, sonst Nachladen aus
  demselben Repo/Ref (`ELWA_SETUP_REF`). Beide Wege nachgestellt und geprüft.

### Der Altbestand wird nicht mehr dunkel (Exit 4)

Der Kern ist die Frage „läuft gerade ein Supervisor?" — und die ist **nicht** an der
Datei zu beantworten, sondern nur am Prozess. Deshalb:

- Findet `update.sh` eine vorhandene `run.sh` **ohne** Schleife, erneuert es sie, setzt
  den Marker `${ELWA_ROOT}/.run-sh-pending-restart` und endet mit **Exit 4** plus
  Anleitung — **ohne** Kill.
- Die erzeugte `run.sh` löscht diesen Marker als erste Amtshandlung, noch vor der
  Schleife. Sein Verschwinden ist die Quittung, dass der neue Supervisor wirklich läuft.
- Solange der Marker liegt, unterlässt `update.sh` den Kill auch bei jedem Folgelauf.
  Das ist der eigentliche Fallstrick: nach dem ersten Lauf *trägt* `run.sh` den aktuellen
  Vertrag, der laufende Prozess ist aber weiter der alte. Wer nur auf die Datei schaut,
  killt beim zweiten Cron-Lauf doch (Regressionstest #101-C).
- Eine **fehlende** `run.sh` zählt bewusst nicht als Altbestand — dann ist auch nichts aus
  ihr gestartet, was ein Kill treffen könnte.
- Der Watchdog behandelt Exit 4 weder als Rollback- noch als Fehlschlag-Fall (das Ziel-Jar
  ist korrekt verlinkt, es fehlt nur ein manueller Schritt), alarmiert aber bei jedem Lauf,
  bis er erledigt ist. Ein Rollback oder eine Fehlermarkierung wären hier aktiv schädlich.

### Terminal-Skripte ins Release-Paket (Nachfrage des Auftraggebers)

Bis hierher enthielt das Release nur `raspi-client-<version>.jar` + `.sha256`; die
Betriebsskripte mussten aus einem Repo-Checkout kopiert werden. Damit driftete das, was
ein Terminal *ausführt*, unkontrolliert von dem, was es *ausrollt* — und seit `update.sh`
die gemeinsame `run-sh.lib.sh` braucht, ist eine unvollständige Kopie ein harter Fehler
statt einer stillen Alterung.

- `release.yml` packt `update.sh`, `auto-update-watchdog.sh`, `upgrade-jre.sh`,
  `run-sh.lib.sh`, das README und eine `VERSION`-Datei reproduzierbar zu
  `elwasys-terminal-scripts-<version>.tar.gz` (+ `.sha256`) und hängt beides ans Release.
- `cat /opt/elwasys/bin/VERSION` verrät am Gerät, welcher Skript-Stand läuft.
- Bewusst **kein** Selbst-Update der Skripte: ein fehlerhaftes `update.sh`, das sich selbst
  ausrollt, träfe die ganze Flotte auf einmal.

### Anzeige-Locale vollständig festgenagelt (#100)

- `FormatUtilities.DISPLAY_LOCALE` ist jetzt die eine Stelle, an der die Regel steht;
  `formatCurrency` bindet sich daran.
- `ui/small/components/ProgramListItem:64` (Preis in der Programmliste) lief noch über die
  JVM-Locale — am kleinen Display standen damit `$12.34` in der Liste und `12,34 €` im
  Bestätigungsdialog direkt danach, für denselben Betrag.
- `ui/medium/.../DeviceListEntry` und `ConfirmationViewController` binden ihre
  Datums-Formatter an dieselbe Konstante (Entscheidung: fest deutsch, konsistent zur
  Währung, unabhängig vom Image).
- Das Locale-Flag im Startbefehl fängt das *nicht* ab — es käme über genau die `run.sh`,
  die Bestandsgeräte nie bekamen. Deshalb gehört der Fix in den Code, nicht ins Skript.

## Tests

- `deploy/terminal/auto-update-selftest.sh`: **24/24** (vorher 13). Neu die Fälle #101-A
  bis #101-F — Altbestand ohne Kill, Truststore-Übernahme, zweiter Lauf bei liegendem
  Marker, Marker-Quittung vor der Schleife, Positiv-Kontrolle v1-Supervisor (Kill *soll*
  laufen), Idempotenz bei aktuellem Vertrag.
- Neu `ProgramListItemLocaleTest`: setzt die Standard-Locale auf `Locale.US` und prüft die
  gerenderte Zelle. Gegen den Vor-Fix-Stand verifiziert — schlägt dort mit
  `expected: <12,34 €> but was: <$12.34>` fehl.
- Client-Suite und Backend-Suite grün (siehe PR).

Beim Schreiben der Selbsttests fielen zwei eigene Fehler auf, die ohne sie im Feld
gelandet wären: `grep` ohne Treffer riss unter `set -o pipefail` das ganze `update.sh`
mit runter (der Normalfall — kein Truststore), und eine fehlende `run.sh` landete
fälschlich im Altbestand-Zweig und blockierte damit den Neustart bei jedem Lauf.

## Triage der übrigen offenen Issues

| Issue | Cutover-relevant? |
|---|---|
| #104 Generalprobe | **Ja, das eigentliche Restrisiko** — zwölf Punkte, die niemand am Code abarbeiten kann. Punkt 6 (Soak-Test über mehrere Tage) kollidiert mit „nächste Woche", 8/9 (Stromprofile kalibrieren, Hardware vor Ort) sind der größte Zeitfresser. |
| #100 Locale | Behoben (siehe oben). |
| #101 run.sh | Behoben (siehe oben). |
| #97 Testharness | Punkt 1 war bereits erledigt: `start_test_backend` endet mit `exit 1` und wird ge-`source`d, das reißt `run-ui-tests.sh` unter `set -e` korrekt mit — kein stiller Fehlschlag mehr (empirisch nachgestellt). Punkt 2 geprüft: **kein** Gate hängt mehr am aggregierten `/actuator/health`; Smoke- und Cutover-Skripte nutzen durchgängig `liveness`/`readiness`. Offen bleibt nur Punkt 3 (Testdaten-Isolation zwischen Client- und Portal-Suite). |
| #103 Log-Platzhalter | Nicht cutover-relevant — breiter Diff über Altklassen ohne Funktionswirkung. Das Issue rät selbst davon ab, das kurz vor einem Feldeinsatz zu machen. |
| #102 Testflakiness | Nicht cutover-relevant. |
| #75 Repo-Umzug | Nur bei einem Umzug nach `kabieror/elwasys`. |

## Offen / für die Generalprobe

- **Truststore-Frage je Gerät** (neu, aus dem Nebenbefund): Trägt die alte `run.sh`
  `-Djavax.net.ssl.trustStore…`, nutzt das Gerät eine private CA. Die Flags wandern zwar
  mit, aber ob der Truststore zum *neuen* TLS-Terminierungspunkt passt, lässt sich nur vor
  Ort prüfen. Als Prüfpunkt in Runbook-Schritt 3d.3 aufgenommen.
- Der Exit-4-Weg ist offline trocken getestet (Fake-Restart/-pgrep), **nicht** auf echter
  Hardware. Der erste Charge-Lauf der Generalprobe ist damit auch der erste echte Test
  dieses Pfads — bewusst so, das ist genau Punkt 1 aus #104.
- #97 Punkt 3 (Testdaten-Isolation) bleibt offen.
