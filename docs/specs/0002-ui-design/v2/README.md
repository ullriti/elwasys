# UI-Design v2 – Waschportal

Modernisierter Prototyp auf Basis von v1 (`../v1/`). Markenfarben unverändert: #4488dd, Sidebar #222d32.

## Öffnen

`Waschportal Admin v2.dc.html` im Browser öffnen (`support.js` muss daneben liegen).

## Dateien

- `Waschportal Admin v2.dc.html` – klickbarer Prototyp (Admin- und Benutzerbereich, alle Dialoge)
- `portal-theme-v2.css` – drop-in-Ersatz für `backend/src/main/resources/portal-theme.css`
- `TOKENS.md` – Token-Deltas v1 → v2
- `MAPPING.md` – was sich in den Flow-Klassen ändert (ergänzt `../v1/MAPPING.md`)

## Wesentliche Änderungen

- Grids ohne Zebra-Streifen und Spaltentrenner, weißer Kopf mit Versalien-Labels, Zeilen-Hover
- Weichere Radien, flachere Schatten, hellerer Inhaltshintergrund, größere Bedienelemente
- Navbar 64 px mit Logo-Mark, Ansichtsnamen und Benutzer-Chip mit Initialen-Avatar
- Seitenleiste ab 900 px dauerhaft offen, Navigationspunkte als Pillen mit blauem Aktivzustand
- Dialoge mit Schließen-Kreuz, feststehendem Kopf/Fuß und Abschnittsgliederung (Gerät, Programm)
- Dashboard-Gerätekarten mit Statuspunkt, Kennzahlen-Panel und Fortschrittsbalken
- Login als zweigeteilter Bildschirm mit Markenfläche
