# 2026-07-22 — Portal-Design wiederhergestellt und an das Terminal angeglichen

**Ziel:** Das Vaadin-Flow-Portal sah nach der Phase-3-Umstellung „nicht mehr schön" aus
(Auftraggeber-Rückmeldung, Referenzbilder Alt- vs. Neu-Portal beigelegt). Das vertraute
Erscheinungsbild wiederherstellen und Terminal/Portal optisch als ein Produkt auftreten
lassen. Rein kosmetisch – Verhalten unverändert (E2E-Suite als Maßstab).

## Erledigt
- **Portal-Styling wiederhergestellt** (Commit `4f59491`): Befund – der Phase-3-Neubau hatte
  in allen Views bereits CSS-Klassen-Hooks (`admin-layout-title`, `dashboard-device-panel`,
  `login-view` …), aber **kein Stylesheet, das sie stylt** → nackte Lumo-Standardoptik. Wieder
  hergestellt: blauer Kopfbalken, dunkle Sidebar mit hervorgehobenem Aktiv-Punkt (blauer
  Linksbalken; Vaadin markiert ihn mit `[current]`, nicht `[active]`), gerahmte/gezebra-te
  Tabellen, hellgrauer Inhalt, Login als Karte mit blauem Oberrand.
- **Bewusst KEIN kompiliertes `@Theme`**: ein eigenes Theme/`@CssImport` erzwingt einen
  anwendungsspezifischen Frontend-Bundle-Build, der bei Vaadin 24.10.x den Online-Lizenzcheck
  gegen vaadin.com auslöst (Sandbox/CI: Proxy 403 → Build-Abbruch, real reproduziert).
  Stattdessen zur Laufzeit dokumentweit injiziertes Inline-Stylesheet
  (`ElwasysAppShell#configurePage` → `backend/src/main/resources/portal-theme.css`); wirkt über
  Lumo-Custom-Properties + `::part()` bis in die Web-Components, ohne das Standard-Bundle zu
  verlassen.
- **Palette an das Terminal angeglichen** (Commit `44908e2`): Blau `#4488dd` + Status-Grün/
  -Rot/-Grau wie `Client-Raspi/.../ui/medium/MainForm.css`. Terminal und Portal nutzten vorher
  drei verschiedene Blautöne (Terminal `#4488dd`, Alt-Portal Vaadin-7-Valo ~`#197de1`, erste
  Wiederherstellung AdminLTE `#3c8dbc`).
- **Dashboard-Gerätekarten** (Commits `44908e2`, `ce9cfa8`): von fest `24em` auf responsiv
  50 %/100 % (wie Alt-Portal `Portal/.../dashboard.scss`), status-farbiger Oberrand (frei/
  besetzt/deaktiviert wie die Terminal-Kacheln), und der Container `.dashboard-device-list` auf
  volle Breite, damit zwei Karten die gesamte Inhaltsbreite ausfüllen (vorher rechts Platz
  ungenutzt, Tabellen abgeschnitten).

## Entscheidungen
- Portal-Styling wird als **Laufzeit-Inline-CSS** statt als kompiliertes Vaadin-Theme
  ausgeliefert (umgeht den Lizenzcheck beim Frontend-Bundle-Build). Festgehalten in
  docs/kb/05-migration-plan.md, Risikotabelle „Vaadin-Lizenzpflicht" (Update 2026-07-22).
- Die **Terminal-Palette** ist die gemeinsame Farbreferenz für beide Oberflächen.

## Offen / nächster Schritt
- Keine offenen Punkte. Weitere Feinabstimmung (exakter Blauton, Titel-Versionsnummer im
  Header) ist bei Bedarf ein CSS-Einzeiler.

## Referenzen
- docs/kb/05-migration-plan.md (Änderungslog „Portal-Design …" + Risikotabelle),
  docs/kb/06-ui-tests.md („Portal-Design zur Laufzeit (kein kompiliertes Theme)")
