# 13. Grundsatz "Verhalten bewahren" mit Strangler-Muster und E2E-Sicherheitsnetz

- **Status:** accepted
- **Datum:** 2026-07-20

## Kontext

Harte Nebenbedingung des Auftraggebers: **Die Nutzer dürfen sich nicht umstellen müssen.**
Am Terminal bleibt der Bedienfluss (RFID-Login → Gerät → Programm → bestätigen → Lauf →
Auto-Ende → Benachrichtigung) und das UI-Layout erhalten; im Portal bleiben Aufgaben und
Arbeitsabläufe für Admins gleich (die Optik darf sich ändern). Ein "Big Bang" wäre
riskant, weil der Bestand durchgehend lauffähig bleiben muss.

## Entscheidung

Nutzer-sichtbares Verhalten wird als Grundsatz bewahrt; Abweichungen sind nur explizit
beauftragt. Der Umbau folgt dem **Strangler-Muster**: das neue Backend entsteht parallel
zum Bestand auf derselben DB, Komponenten werden einzeln umgehängt (erst Portal, dann
Client), in kleinen, einzeln baubaren/testbaren Schritten. Die **E2E-/UI-Suiten sind der
Maßstab** (TestFX am Client, Playwright am Portal, Cross-Component-Fernwartung) und laufen
in der PR-CI. Neues Verhalten kommt anfangs additiv/lesend hinzu; Schreibpfade werden erst
umgestellt, wenn der Alt-Pfad abgeschaltet wird (daher die Konfig-Flags in ADR 7 und 9).

## Konsequenzen

- Geringes Risiko: Bestand bleibt bis zur Ablösung lauffähig, CI bleibt durchgehend grün.
- Bewusst dokumentierte, kleine Verhaltensverschärfungen sind möglich (z. B. gesperrte
  Nutzer werden am neuen Portal-Login abgewiesen).
- Mehr Zwischenschritte/Flags als bei einem Big-Bang-Umbau.

Herkunft: docs/kb/05-migration-plan.md, Abschnitte Rahmenbedingungen, Leitgedanken und
Entscheidungen.
