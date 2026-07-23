# Spec 0001 — Finale Review vor dem Feldeinsatz

|              |                                            |
| ------------ | ------------------------------------------ |
| **Status:**  | Draft                                      |
| **Datum:**   | 2026-07-23                                 |
| **Autor:**   | Auftraggeber + Claude                      |

## Kontext / Problem

Die Modernisierung ist durch alle Roadmap-Phasen (0–6) gelaufen, die Pre-Launch-Review
(Epic #66, AP1–AP7) inkl. Follow-ups (#67–#69) ist abgeschlossen. Deren Fokus lag auf
Bugs/Schwachstellen und der KB-Aktualisierung. Vor dem ersten Feldeinsatz soll eine
**abschließende, breitere Review** mit frischem Blick laufen – nicht nur Korrektheit,
sondern Zielerreichung, Qualität, Doku, Betrieb und Testökonomie.

## Ziele

Die Review beantwortet zehn Prüffragen, jeweils mit begründetem Urteil und konkreten
Findings (Schwere, Fundstelle, Empfehlung):

1. **Zielerreichung:** Sind alle Ziele/Rahmenbedingungen des Auftraggebers erreicht
   (Roadmap + Entscheidungen in [`../kb/05-migration-plan.md`](../kb/05-migration-plan.md))?
2. **Kritische Bugs/Schwachstellen:** Gibt es noch kritische Fehler – insbesondere auf den
   Geld-, Offline-/Replay-, Auth- und Nebenläufigkeitspfaden (inkl. der in AP1–AP6
   eingebauten Fixes selbst)?
3. **Code-Qualität:** Wenig Duplikate, klare Struktur, Standard-Guidelines, Stand der
   Technik (Java 21), lesbar, wartbar?
4. **Dokumentation:** KB korrekt und angemessen, Javadoc/Kommentare weder zu lang noch zu
   kurz, restliche Doku (README, Runbook, ADRs, CHANGELOG) stimmig?
5. **Deployment:** Gut überlegt und reproduzierbar?
6. **Alerting:** Gut überlegt – und tatsächlich verdrahtet (erreicht ein Alarm einen Menschen)?
7. **Backup & Recovery:** Gut überlegt – und ist der Restore-Weg praktikabel beschrieben?
8. **Nachhaltiges Monitoring:** Trägt der Betrieb über Monate (Health, Logs, Retention)?
9. **Testabdeckung:** Decken die Suiten alle sinnvollen Szenarien ab – nicht zu viele,
   nicht zu wenige Tests?
10. **Repo-Hygiene:** Ist das Repo aufgeräumt – alles an seinem Platz, keine Altlasten,
    toten Dateien, verwaisten Skripte oder inkonsistenten Referenzen?

## Nicht-Ziele

- Keine Fixes innerhalb der Review-Sessions selbst – Findings werden erst gesammelt und
  priorisiert, dann in eigenen Arbeitspaketen behoben (kleine Doku-Tippfehler ausgenommen).
- Kein erneutes Volltiefen-Audit der bereits in #66 geprüften Einzel-Issues – frischer
  Blick statt Wiederholung.
- Keine Verhaltensänderungen (Rahmenbedingung des Auftraggebers).

## Vorgehen

### Tracks

| Track | Inhalt (Prüffragen) | Empfohlener Subagent | Schwerpunkt-Pfade |
|-------|--------------------|---------------------|-------------------|
| **R1** | Zielerreichung (1) | `general-purpose` | `docs/kb/05-migration-plan.md`, ADRs, Code-Stichproben |
| **R2** | Kritische Bugs/Schwachstellen (2) | `code-reviewer` | Geld-/Replay-/Auth-/Concurrency-Pfade in `backend/` + `Client-Raspi/` |
| **R3a** | Code-Qualität Backend (3) | `code-reviewer` | `backend/src/main/java` (ohne `ui/`) |
| **R3b** | Code-Qualität Terminal (3) | `code-reviewer` | `Client-Raspi/` |
| **R3c** | Code-Qualität Portal (3) | `code-reviewer` | `backend/.../ui/`, `backend/e2e/` |
| **R4** | Dokumentation (4) | `general-purpose` | `docs/`, Javadoc/Kommentare, `README.md`, `CHANGELOG.md` |
| **R5** | Betrieb: Deployment/Alerting/Backup/Monitoring (5–8) | `devops` | `deploy/`, Health-Indicators, Runbook, CI |
| **R6** | Testabdeckung (9) | `general-purpose` | alle Test-Suiten vs. Szenarienliste |
| **R7** | Repo-Hygiene (10) | `Explore`/`general-purpose` | gesamtes Repo |

### Modelle & Aufteilung

- **Hauptagent: Fable** (Synthese, Priorisierung, Bewertung der Findings – höchster
  Hebel, moderater Tokenanteil). **Subagenten: Opus** (die Fan-out-Lesearbeit ist der
  Token-Treiber; Opus reviewt stark genug und schont das 5x-Fenster).
- **Ein-Session-Variante (bevorzugt):** alle Tracks als Subagenten aus einer Session,
  Synthese am Ende. Reicht das Fenster nicht, Tracks gestaffelt fortsetzen.
- **Zwei-Session-Variante (Fallback):**
  - **Session A (Korrektheit):** R1, R2, R6
  - **Session B (Qualität & Betrieb):** R3a–c, R4, R5, R7
  - Danach **Synthese-Session** (oder Teil von B): Reports zusammenführen, priorisieren.

### Ergebnisablage

Jeder Track schreibt seinen Report nach `docs/reviews/final/<track>.md` und committet
auf den Review-Branch. Die Synthese fasst zu einer priorisierten Findings-Liste
(`docs/reviews/final/SYNTHESE.md`) zusammen; daraus werden Issues/Arbeitspakete.

Format je Finding: `Schwere (kritisch/hoch/mittel/niedrig) · Fundstelle (Datei:Zeile) ·
Beschreibung · Empfehlung`. Je Prüffrage zusätzlich ein Gesamturteil in 2–4 Sätzen.

## Prompts

Für die Zwei-Session-Variante (oder um einzelne Tracks in frischen Sessions zu fahren).
Jeder Prompt ist eigenständig; die Session liest zuerst `AGENTS.md` und den
„Aktueller Stand"-Snapshot in `docs/kb/README.md`.

### Prompt Session A — Korrektheit (R1, R2, R6)

```text
Finale Review vor dem Feldeinsatz, Teil A (Korrektheit) laut docs/specs/0001-finale-review.md.
Fahre die Tracks R1, R2 und R6 als parallele Subagenten (Modell: Opus) und schreibe je Track
einen Report nach docs/reviews/final/ (Format siehe Spec). Keine Fixes, nur Findings.

R1 – Zielerreichung: Gleiche docs/kb/05-migration-plan.md (Rahmenbedingungen, Roadmap-Phasen,
Abschnitt „Entscheidungen (Auftraggeber)", offene Fragen) und die ADRs gegen den Ist-Zustand
des Codes ab. Für jedes Ziel: erreicht / teilweise / offen, mit Beleg (Datei/Commit). Prüfe
besonders, ob bewusst akzeptierte Restrisiken wirklich dokumentiert sind und ob offene Punkte
(z. B. Issue #75 Repo-Umzug) korrekt nachgehalten werden.

R2 – Kritische Bugs (frische Augen): Adversarialer Blick ausschließlich auf die kritischen
Pfade: Abrechnung/Guthaben (Locking, Idempotenz), Offline-Replay (inkl. der Härtungen aus
ADR 0016/0021 – Fixes können neue Fehler einführen), Auth (Portal + Standort-Token),
Terminal-Nebenläufigkeit und WebSocket-Reconnects. Nur Findings der Schwere hoch/kritisch
melden; jedes Finding mit konkretem Fehlerszenario (Eingabe/Zustand → falsches Ergebnis).
Verifiziere jedes Finding vor Aufnahme (zweiter Subagent oder eigene Gegenprüfung).

R6 – Testabdeckung: Erstelle eine Szenarien-Matrix (fachliche Kernszenarien × vorhandene
Suiten: Client-UI/E2E, Cross-Component, Backend-JUnit, Playwright P1–P20) aus
docs/kb/08-test-plan.md und dem tatsächlichen Testbestand. Benenne (a) ungetestete sinnvolle
Szenarien, (b) redundante/wertarme Tests, (c) Determinismus-Risiken. Urteil: zu viel/zu
wenig/angemessen, je Suite.

Abschluss: Reports committen und pushen (Branch der Session), kurze Zusammenfassung mit den
Top-Findings im Chat.
```

### Prompt Session B — Qualität & Betrieb (R3, R4, R5, R7)

```text
Finale Review vor dem Feldeinsatz, Teil B (Qualität & Betrieb) laut
docs/specs/0001-finale-review.md. Fahre R3a, R3b, R3c, R4, R5 und R7 als parallele
Subagenten (Modell: Opus), je Track ein Report nach docs/reviews/final/ (Format siehe Spec).
Keine Fixes, nur Findings.

R3a/b/c – Code-Qualität (Backend ohne ui/ | Client-Raspi | Portal-ui/ + backend/e2e):
Duplikate (auch modulübergreifend – die Ex-Common-Klassen liegen in Client-Raspi),
Methoden-/Klassengrößen, Schichtung (Spring: domain/repository/service/api/ui/auth/ws),
Java-21-Idiomatik (records, switch-Pattern, Optional-Disziplin), Lesbarkeit, Wartbarkeit,
tote Pfade. Maßstab sind die Konventionen in AGENTS.md (Bezeichner Englisch, Kommentare
Deutsch, Warum-Kommentare). Bewusst KEINE Bug-Suche (macht Teil A).

R4 – Dokumentation: (a) KB 00–08 stichprobenartig gegen den Code verifizieren (je Artikel
mind. 3 Kernaussagen prüfen); (b) Javadoc/Kommentar-Dichte bewerten – zu lang, zu kurz,
Was-statt-Warum, veraltete Kommentare; (c) README (Wurzel, Englisch), CUTOVER-RUNBOOK,
CHANGELOG, ADR-Index, Worklog auf Konsistenz und Vollständigkeit. Urteil je Bereich.

R5 – Betrieb (Deployment/Alerting/Backup/Monitoring): Prüfe deploy/ (compose, helm,
terminal, cutover, smoke), die Custom-Health-Indicators und das Runbook-Kapitel
„Dauerbetrieb" gegen diese Fragen: Ist das Deployment reproduzierbar (Image-Digests,
Jar-SHA, Preflight)? Erreicht ein Alert tatsächlich einen Menschen (Kanal verdrahtet, nicht
nur 503)? Ist Backup UND Restore beschrieben und realistisch (RPO/RTO, getesteter
Restore-Weg)? Trägt das Monitoring über Monate (Log-Rotation, Retention/Purge, Plattenplatz,
Zertifikats-Ablauf, Watchdog)? Benenne Lücken konkret.

R7 – Repo-Hygiene: Ist alles an seinem Platz? Suche: Dateien/Skripte ohne Verweis oder
Funktion, Altlasten der Migration (Reste von Portal-alt/Common), Inkonsistenzen in Namen und
Referenzen (ullriti/elwasys überall?), .gitignore-Lücken, versehentlich committete Artefakte
oder große Binärdateien, doppelte/widersprüchliche Konfigurationen, Ablageorte entgegen dem
Verzeichnis-Guide in AGENTS.md.

Abschluss: Reports committen und pushen, kurze Zusammenfassung mit den Top-Findings im Chat.
```

### Prompt Synthese

```text
Synthese der finalen Review laut docs/specs/0001-finale-review.md: Lies alle Reports unter
docs/reviews/final/, dedupliziere, priorisiere (kritisch → niedrig) und schreibe
docs/reviews/final/SYNTHESE.md mit: (1) Gesamturteil je der zehn Prüffragen aus der Spec,
(2) priorisierte Findings-Liste mit Empfehlung „vor Feldeinsatz beheben" vs. „kann warten",
(3) Vorschlag für Arbeitspakete/Issues. Danach Worklog-Eintrag anlegen, „Aktueller Stand"
in docs/kb/README.md aktualisieren, committen und pushen. Nichts fixen.
```

## Abnahmekriterien

- [ ] Alle neun Track-Reports liegen unter `docs/reviews/final/` und beantworten ihre
      Prüffrage(n) mit Urteil + Findings im vereinbarten Format.
- [ ] `SYNTHESE.md` existiert mit priorisierter Liste und Vor-Feldeinsatz-Empfehlung.
- [ ] Findings der Schwere kritisch/hoch sind als Issues oder Arbeitspakete erfasst.
- [ ] Worklog-Eintrag + KB-Snapshot aktualisiert.

## Ergänzend: Generalprobe vor dem Feldeinsatz (außerhalb der Code-Review)

Nicht Teil der Review-Sessions, aber vor dem ersten Feldtest empfohlen – vieles davon ist
nur **vor Ort / mit echter Hardware** prüfbar:

1. **Cutover-Generalprobe:** `deploy/CUTOVER-RUNBOOK.md` einmal komplett auf einer
   Staging-Umgebung durchspielen, inkl. Rollback-Zweig; Zeiten notieren.
2. **Migrations-Dry-Run mit Produktivdaten-Kopie:** Abgleich danach (Guthaben-Summen je
   Nutzer, Nutzer-/Geräte-/Preislisten-Zahlen alt vs. neu).
3. **Backup-Restore-Probe:** Backup in leere DB zurückspielen, Backend dagegen starten,
   Stichproben. Ein ungetesteter Restore ist kein Backup.
4. **Ausfall-Drills (live):** Backend stoppen während laufender Wäsche (Offline-Replay in
   echt), Netzwerk trennen, DB stoppen, Terminal-Stromausfall mitten im Programm,
   Terminal-Neustart – erwartetes Verhalten je Fall vorher notieren.
5. **Alarm-Probe:** einen Health-Indicator absichtlich auslösen und prüfen, dass der Alarm
   einen Menschen erreicht (nicht nur den 503-Status).
6. **Soak-Test:** Backend + ein Terminal mehrere Tage durchlaufen lassen; Speicher,
   WS-Reconnects, Log-Wachstum, Purge-Job beobachten.
7. **Benachrichtigungen real:** E-Mail-Zustellbarkeit (SPF/DKIM, Spam-Ordner) und Push auf
   echtem Endgerät.
8. **Programm-Ende-Erkennung kalibrieren:** Stromprofile der echten Maschinen messen,
   Schwellwerte je Gerät validieren (Kurzprogramm, Startverzögerung, Schleudern).
9. **Hardware-Check vor Ort:** echte RFID-Karten aller Kartentypen, deCONZ-Reichweite,
   NTP/Uhrzeit auf den Pis (Sommerzeit), Touch-Kalibrierung.
10. **Pilotphase definieren:** ein Standort/eine Maschine zuerst; Abbruch-/Rollback-
    Kriterien und Beobachtungszeitraum vorher festlegen.
11. **Release einfrieren:** Version taggen, Image-Digest + Client-Jar-SHA festhalten,
    damit Feldtest und Repo-Stand eindeutig zuordenbar sind.
12. **Betrieb & Kommunikation:** Kurz-Runbook für Nicht-Techniker („Terminal reagiert
    nicht → …"), Support-Kontakt, Aushang/Nutzerinfo; Datenschutz-Kurzcheck (RFID-IDs,
    E-Mail-Adressen, Aufbewahrungsfristen ↔ Purge/Retention).

## Offene Fragen

- Ein- oder Zwei-Session-Variante (abhängig vom verfügbaren 5x-Fenster).
- Welche der Generalprobe-Punkte übernimmt der Auftraggeber selbst vs. werden als
  Arbeitspakete (z. B. Restore-Skript, Alarm-Kanal) vorbereitet?

## Referenzen

- Roadmap/Restrisiken: [`../kb/05-migration-plan.md`](../kb/05-migration-plan.md)
- Pre-Launch-Review: Epic #66 (AP1–AP7), ADRs 0016–0021
- Betrieb: [`../../deploy/CUTOVER-RUNBOOK.md`](../../deploy/CUTOVER-RUNBOOK.md)
