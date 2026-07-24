# 2026-07-24 — Finale Review Teil B (Qualität & Betrieb) + Synthese

**Ziel:** Abschluss der finalen Review nach [Spec 0001](../specs/0001-finale-review.md):
Tracks R3a–c (Code-Qualität), R4 (Doku), R5 (Betrieb), R7 (Repo-Hygiene) sowie die
Synthese über alle neun Tracks.

## Erledigt

- **R3a Backend** (`R3a-code-qualitaet-backend.md`): Qualität hoch, keine Hoch-Findings;
  Mittelfunde: `UserGroupService`-Triplikat, `SnapshotController` (Fachlogik im
  Controller + ungenutztes `getCredits`-Batch), `ExecutionController.start` (~90 Zeilen),
  `TerminalMaintenanceService`-Doppel-Map.
- **R3b Terminal** (`R3b-code-qualitaet-terminal.md`): solide, Altlast-Refactors benannt;
  **2 Hoch-Findings, beide gegenverifiziert:** `ExecutionManager:326` `add` statt
  `remove` (Listener-Leak) und `DeviceListEntry:446` Fall-Through `DISABLED`→
  `UNREGISTERED` (deaktivierte Geräte falsch dargestellt und wieder bedienbar).
- **R3c Portal** (`R3c-code-qualitaet-portal.md`): sauber geschichtet, E2E vorbildlich;
  Hauptbefund strukturelle Duplikation (5 Admin-Views/9 Dialoge, kein Vaadin-`Binder`).
- **R4 Doku** (`R4-dokumentation.md`): gut bis sehr gut; **Hoch-Finding (verifiziert):**
  `verify-cutover-migration.sh` erwartet Flyway-Historie nur bis V10, V11 existiert –
  Preflight würde fehlschlagen. Dazu Worklog-Index-Lücke, CHANGELOG-Länge, stale
  Kommentare, 2 kaputte Links.
- **R5 Betrieb** (`R5-betrieb.md`): Deployment-Baukasten stark; **2 Hoch-Findings:**
  kein verdrahteter Alarmkanal (nichts pollt `/operational`) und kein ausgearbeiteter/
  geprobter Restore-Weg. Mittelfunde u. a. Dead-Letter nur im Pi-Log, Compose baut lokal
  statt GHCR, NTP fehlt in `setup.sh`, Zert-Ablauf-Monitoring.
- **R7 Repo-Hygiene** (`R7-repo-hygiene.md`): sehr aufgeräumt; **Hoch-Finding
  (verifiziert):** `ELWASYS_PORTAL_BASE_URL` wird weder von Compose noch Helm
  durchgereicht → Reset-Mails verlinken auf `localhost:8080`.
- **Synthese** (`SYNTHESE.md`): Gesamturteil je der zehn Prüffragen, deduplizierte
  Prioritätenliste (7 Hoch-Findings H1–H7 + 2 Test-Pflichten vor Feldeinsatz),
  Arbeitspaket-Vorschlag FR-1 bis FR-5.

## Entscheidungen

- Keine Fixes in den Review-Sessions (laut Spec); Behebung über die vorgeschlagenen
  Arbeitspakete FR-1 (Terminal-Code-Fixes) und FR-2 (Betrieb) vor dem Feldeinsatz.

## Offen / nächster Schritt

- FR-1/FR-2 umsetzen (vor Feldeinsatz), dann FR-3; FR-4/FR-5 nach dem Feldeinsatz.
- Generalprobe nach Spec 0001 (Cutover-Probelauf, Restore-Probe, Ausfall-Drills,
  Alarm-Probe, Soak-Test, Vor-Ort-Kalibrierung), danach Pilotphase.

## Referenzen

- docs/reviews/final/ (alle 9 Reports + SYNTHESE.md), docs/specs/0001-finale-review.md
- ADR 0016/0017/0021 (H1-Kontext), deploy/CUTOVER-RUNBOOK.md (H4–H6)
