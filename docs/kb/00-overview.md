# 00 – Projektübersicht

## Was ist elwasys?

**elwasys** ist ein System zur Verwaltung und Abrechnung von Waschmaschinen (und ähnlichen
Geräten, z. B. Trocknern) in einer gemeinschaftlich genutzten Waschküche (z. B. Wohnheim,
Mehrfamilienhaus). Nutzer identifizieren sich per **RFID-Karte** an einem Terminal
(Raspberry Pi mit 7"-Touchdisplay), starten ein Waschprogramm auf Guthabenbasis (Pre-Paid),
und das System schaltet über **Zigbee-Funksteckdosen** die Maschine ein bzw. wieder aus.

Das Ende eines Programms wird über die **Leistungsmessung** der Funksteckdose erkannt
(herstellerunabhängig – wenn die Maschine keinen Strom mehr zieht, ist sie fertig).

Domain: `elwasys.de` (siehe `CNAME`). Ursprünglicher Autor: Oliver Kabierschke
(GitHub-Org `kabieror`). Lizenz siehe `LICENSE.md`.

## Kern-Features

- **RFID-Identifikation** der Nutzer am Terminal
- **Pre-Paid-Abrechnung** (Guthabenkonto pro Nutzer)
- **Programm-Ende-Erkennung** über Leistungsmessung (vendor-unabhängig)
- **Benachrichtigungen** per E-Mail und Push (Pushover), wenn die Wäsche fertig ist
- **Abrechnungsmodelle**: Festpreis oder zeitbasiert (Flagfall + Rate pro Zeiteinheit)
- **Feingranulare Berechtigungen**:
  - Sonderpreise pro Benutzergruppe (Rabatt: fix oder Faktor)
  - Zugriffssperre auf bestimmte Geräte pro Benutzergruppe
- **Web-Portal** zur Administration (Nutzer, Gruppen, Geräte, Programme, Standorte, Guthaben)
  – ein Vaadin-Flow-UI, das in das zentrale Backend eingebettet ist
- **Fernwartung** (Status/Log/Neustart) über eine vom Terminal ausgehende WebSocket-Verbindung
  zum Backend
- Mehrere **Standorte** (Locations), je Standort ein Client-Terminal

## Komponenten-Bild (High Level)

```
┌────────────────────────┐        ┌──────────────────────────────────┐
│  Raspberry-Pi-Terminal  │        │       Backend (Spring Boot)       │
│  (JavaFX Touch-UI)      │        │                                  │
│                         │        │  - REST-API v1 (Standort-Token)   │
│  - RFID-Login           │◀──────▶│  - eingebettetes Admin-Portal-UI  │
│  - Geräteauswahl        │  REST +│    (Vaadin Flow)                 │
│  - Programm starten     │  ausgeh│  - Benachrichtigungsdienst        │
│  - Zigbee schalten      │  ender │    (SMTP/Pushover)                │
│  - Leistung messen      │  WS    │                                  │
└────────────────────────┘        └────────────┬─────────────────────┘
                                               │
                                       ┌───────┴────────┐
                                       │  PostgreSQL DB │
                                       │  (nur Backend) │
                                       └────────────────┘

  Client ⇄ Zigbee-Gateway (deCONZ/ConBee2)  bzw.  fhem  → Funksteckdosen
```

- **Client-Raspi**: die JavaFX-Anwendung auf dem Raspberry Pi (das Terminal) – ohne
  Direkt-DB-Zugriff, ausschließlich über die Backend-REST-API und eine ausgehende
  WebSocket-Verbindung (auch für Fernwartung) mit dem Backend verbunden. Enthält auch die 6
  Utility-Klassen im Package `org.kabieror.elwasys.common` (Enum-Typ, Format-/
  Konfigurationshilfen), die nur das Terminal zur Laufzeit nutzt.
- **backend**: das zentrale Spring-Boot-Backend – REST-API/WebSocket für die Terminals UND
  das eingebettete Admin-Portal-UI (Vaadin Flow), Notifications, Flyway-verwaltetes Schema.
  Das Backend hat ein eigenes Datenmodell und keine Produktiv-Abhängigkeit auf `common`.

Backend und Terminals teilen sich **eine gemeinsame PostgreSQL-Datenbank**, aber nur das
Backend greift direkt zu (User `elwaportal`) – die Terminals sprechen ausschließlich die
Backend-API. Details: [01-architecture.md](01-architecture.md).

## Hardware-Kontext

- **Raspberry Pi** mit offiziellem **7"-Touchscreen** (Terminal).
- **ConBee2**-USB-Stick als Zigbee-Koordinator, betrieben über **deCONZ** (Phoscon).
- **Zigbee-Funksteckdosen mit Energiezähler** (z. B. LIDL SilverCrest), vor die Maschinen
  gesteckt.
- Alternativ (ältere Variante): **fhem** als Gateway.
- Optional RFID-Kartenleser (über Telnet/serielle Schnittstelle angebunden).

## Historie

- **2026-07-22** — Das früher eigenständige **Common**-Modul aufgelöst; seine 6 Klassen
  (Package `org.kabieror.elwasys.common`) liegen seither direkt im Client-Raspi-Modul
  ([Worklog Phase-5-Nachtrag](../worklog/2026-07-22-phase-5-nachtrag-common-und-schema.md)).
- **2026-07-21** — Alt-Bestand entfernt: eigenständiges Vaadin-7-Portal-Modul und das
  Alt-TCP-Maintenance-Protokoll; das **Reservierungs-Feature** (laut Auftraggeber nicht mehr
  relevant) wurde mit den übrigen App-Resten entfernt
  ([Worklog Phase 5](../worklog/2026-07-21-phase-5-aufraeumen.md)).
- **2026-07-21** — Terminal ohne Direkt-DB-Zugriff: Datenzugriff und Fernwartung laufen seither
  ausschließlich über Backend-REST-API und ausgehende WebSocket-Verbindung
  ([Worklog Phase 4](../worklog/2026-07-21-phase-4-terminal-modernisierung.md)).
- **2026-07-20** — Admin-Portal als in das Backend eingebettetes Vaadin-Flow-UI neu gebaut
  (ersetzt das eigenständige Portal) ([Worklog Phase 3](../worklog/2026-07-20-phase-3-portal-neubau.md)).
- **Ausgangslage vor der Modernisierung** (Multi-Modul-Projekt mit 3 `pom.xml`, Vaadin-7-Portal,
  Direkt-DB-Zugriff der Terminals, veralteter Tech-Stack) sowie der vollständige
  Modernisierungsverlauf: [05-migration-plan.md](05-migration-plan.md).
