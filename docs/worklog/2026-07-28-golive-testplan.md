# 2026-07-28 — GoLive-Testplan für die manuelle Abnahme nach dem Cutover

**Ziel:** Eine abhakbare Markdown-Checkliste bereitstellen, mit der der Auftraggeber am Tag nach
der Produktivumschaltung die gesamte Anlage einmal von Hand durchtestet – Normalverhalten,
Grenzfälle und Fehlerfälle –, ohne unnötige Wartezeiten und ohne Rollback-Proben.

## Erledigt

- Neue Datei [`deploy/GOLIVE-TESTPLAN.md`](../../deploy/GOLIVE-TESTPLAN.md): manueller
  Abnahmelauf über einen Arbeitstag, gegliedert in elf Blöcke (Vorbereitung, Grundzustand,
  Portal-Admin, Terminal-Normalbetrieb, Terminal-Grenz-/Fehlerfälle, Zwei-Terminal-
  Nebenläufigkeit, Ausfall-/Offline-Szenarien, Fernwartung/Betrieb/Alarmierung,
  Benachrichtigungen, Benutzerportal, Abschluss/Aufräumen) mit **176** einzeln abhakbaren
  Prüfpunkten (davon 28 Vorbereitung und 16 Aufräumen, also ~132 echte Prüfungen), je mit
  Ergebniszeile, dazu Befundliste und Tagesplan.
- Verweise gesetzt: `deploy/CUTOVER-RUNBOOK.md` (Kap. 5 Post-Cutover) und
  `docs/kb/08-test-plan.md` (Abgrenzung automatisiert ↔ manuell) zeigen auf den neuen Plan.
- „Aktueller Stand" in `docs/kb/README.md` und `CHANGELOG.md` fortgeschrieben.

## Entscheidungen

- **Ablage unter `deploy/`, nicht unter `docs/`.** Der Plan ist ein Betriebsdokument, das am
  Cutover-Tag neben Runbook, Smoke, Monitoring und Backup benutzt wird – nicht Teil des
  Wissenssystems. `docs/kb/08-test-plan.md` bleibt der Plan der *automatisierten* Suiten und
  verweist nur noch hinüber.
- **Kein Rollback-Üben** (Vorgabe des Auftraggebers). Statt Rollback-Testschritten gibt es nur
  einen kurzen Abschnitt „Abbruchkriterien", der auf den Entscheidungsbaum in Runbook Kap. 4
  verweist. Die fünf Kriterien sind bewusst auf Geld-, Schalt- und Verfügbarkeitsfehler
  beschränkt.
- **Auf genau zwei Terminals zugeschnitten** (Vorgabe des Auftraggebers): es gibt einen eigenen
  Block für Zwei-Terminal-Nebenläufigkeit (gleichzeitige Buchung desselben Nutzers,
  Guthaben-Wettlauf über beide Standorte, Standort-Trennung), aber keine Chargen-/
  Mehr-Standort-Annahmen.
- **Wartezeiten unter ~10 Minuten** über zwei Hebel: (1) vier kurzlebige `TEST-`Programme mit
  Maximaldauer 4–6 Minuten, die in Block 0 angelegt und in Block J wieder entfernt werden;
  (2) der Kniff, die Maschine für das Auto-Ende am eigenen Schalter auszuschalten, statt einen
  vollen Waschgang abzuwarten – der Leistungsabfall löst dieselbe Erkennung aus. Punkte mit
  echter Wartezeit sind mit ⏱ markiert und tragen einen Hinweis, welcher Prüfpunkt in der
  Zwischenzeit läuft.
- **Benachrichtigungen früh scharfschalten** (Block 0 statt Block H), damit jeder Lauf des
  Tages eine Meldung erzeugt und Block H auf Zählen/Doppelversand statt auf Erstinbetriebnahme
  prüfen kann.

## Beim Schreiben am Code verifiziert (und in den Plan eingeflossen)

- **Auto-Ende-Frist** ist `max(0, program.earliestAutoEnd − Laufzeit) + device.autoEndWaitTime`
  (`ClientExecution#getEarliestAutoEnd`), nicht schlicht „Wartezeit"; steigt die Leistung
  wieder, wird das geplante Ende verworfen (`ExecutionManager#onPowerMeasurementAvailable`).
- **Die „Freie Zeit" eines Programms wird nicht von der abgerechneten Dauer abgezogen** – sie
  entscheidet nur, ob der Lauf ganz kostenlos ist (`PricingService#getPrice`). Als eigener
  Prüfpunkt aufgenommen (C10/D15), weil die Erwartung leicht andersherum gebildet wird.
- **Der Haken „Bei Fertigstellung Email an …" auf der Terminal-Bestätigungsseite wird nirgends
  ausgewertet** (`ConfirmationViewController`: `emailNotificationCheckBox.isSelected()` kommt
  im Code nicht vor). Maßgeblich ist allein `users.email_notification` aus den
  Portal-Einstellungen. Als bewusster Prüfpunkt formuliert (H2: Haken *nicht* setzen, Mail muss
  trotzdem kommen).
- **Der Geräte-Scan schaltet alle 20 Sekunden fremdeingeschaltete Steckdosen wieder aus**
  (`ExecutionManager`-Konstruktor) – als Prüfpunkt D16 aufgenommen.
- Terminal-Pfade/-Marker (`/opt/elwasys`, `.terminal-ready`, `.run-sh-pending-restart`,
  `offline-snapshot.json`, `offline-journal.jsonl[.deadletter]`, `log/stdout`/`errout`,
  Kappung bei 5 MiB) gegen `deploy/terminal/run-sh.lib.sh` und `Client-Raspi/setup.sh` geprüft.

## Offen / nächster Schritt

- Der Plan ist geschrieben, aber **noch nicht gelaufen** – er wird am Cutover-Tag selbst zum
  ersten Mal benutzt. Befunde daraus gehören in die Befundliste im Dokument und anschließend
  als Issues ins Repo.
- Die Generalprobe-Punkte aus [Spec 0001](../specs/0001-finale-review.md), die *vor* dem
  Cutover liegen (Migrations-Dry-Run mit Produktivdatenkopie, Backup-Restore-Probe,
  Release-Einfrieren), deckt dieser Plan bewusst nicht ab – er setzt hinter dem Cutover an.
  Restore-Probe und Rollback-Proben bleiben auf ausdrücklichen Wunsch außen vor.

## Referenzen

- `deploy/GOLIVE-TESTPLAN.md`, `deploy/CUTOVER-RUNBOOK.md`, `deploy/monitoring/README.md`
- `docs/kb/08-test-plan.md`, `docs/kb/03-modules.md`, `docs/specs/0001-finale-review.md`
