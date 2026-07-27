# UI-Design v1 – Waschportal (Vaadin-Flow-Nachbau)

Klickbarer HTML-Prototyp des Portals als Vorlage für die Flow-Umsetzung. Stand: 2026-07-27.

## Öffnen

`Waschportal Admin.dc.html` im Browser öffnen (`support.js` muss daneben liegen).

## Inhalt

- **Rahmen**: AppLayout (blaue Navbar #4488dd, DrawerToggle, UserMenuBar), dunkle SideNav mit `[current]`-Balken – `ui/admin/AdminLayout.java`, `resources/portal-theme.css`
- **Admin-Views**: Dashboard (Standort-Header mit Verbindungsbadge + Log/Neustart, Gerätekarten mit Statusrand, Verlaufs-Grid), Benutzer, Benutzergruppen, Programme, Geräte, Standorte, Offline-Vorfälle
- **User-Portal**: Übersicht mit Guthaben-/Letzte-Einzahlung-Kacheln und Buchungstabelle – `ui/user/*`
- **Dialoge**: Gerät, Benutzer, Programm, Gruppe, Standort, Guthaben, Umsätze, Verfallene Ausführungen, Einstellungen, Passwort ändern, Passwort zurücksetzen, Log-Viewer, Ja/Nein-Bestätigung
- **Login**: `ui/login/LoginView.java`

## Ergänzungen gegenüber dem Ist-Stand

Sortierbare Spalten (dort, wo der Java-Code `setSortable(true)` setzt, plus Guthaben/Historien) und ein Filterfeld je Liste. In Flow entspricht das `Grid.Column#setSortable` bzw. einem `ListDataProvider`-Filter.

## Nicht enthalten

Live-Updates über `UiBroadcaster`/Push, echte Validierung, Serverfehlerpfade. Daten sind Demo-Daten.
