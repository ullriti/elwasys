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
- **Reservierungen** von Geräten (Alt-Feature; laut Auftraggeber nicht mehr relevant, Reste
  in Phase 5 AP4 entfernt)
- **Web-Portal** zur Administration (Nutzer, Gruppen, Geräte, Programme, Standorte, Guthaben)
  – seit Phase 3 als Vaadin-Flow-UI in das zentrale Backend eingebettet, kein eigenständiges
  Portal-Modul mehr
- **Fernwartung** (Status/Log/Neustart) – seit Phase 4 über eine vom Terminal ausgehende
  WebSocket-Verbindung zum Backend, nicht mehr über eine direkte Portal-Client-Verbindung
- Mehrere **Standorte** (Locations), je Standort ein Client-Terminal

## Komponenten-Bild (High Level, Zielarchitektur seit Phase 5)

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
└───────────┬────────────┘        └────────────┬─────────────────────┘
            │                                   │
            │        ┌──────────────────┐       │
            └╌╌╌╌╌╌╌▶│   PostgreSQL DB   │◀──────┘
                     │  (gemeinsam)      │
                     └──────────────────┘

  Client ⇄ Zigbee-Gateway (deCONZ/ConBee2)  bzw.  fhem  → Funksteckdosen
```

- **Client-Raspi**: die JavaFX-Anwendung auf dem Raspberry Pi (das Terminal) – seit Phase 4
  ohne Direkt-DB-Zugriff, ausschließlich über die Backend-REST-API und eine ausgehende
  WebSocket-Verbindung (auch für Fernwartung).
- **backend**: das zentrale Spring-Boot-Backend – REST-API/WebSocket für die Terminals UND
  das eingebettete Admin-Portal-UI (Vaadin Flow), Notifications, Flyway-verwaltetes Schema.
  Es gibt seit Phase 5 AP1 kein eigenständiges Portal-Modul mehr (das Vaadin-7-Alt-Portal ist
  vollständig entfernt).

Das früher eigenständige **Common**-Modul (kleine gemeinsame Bibliothek: Enum-Typ, Format-/
Konfigurationshilfen) wurde im Phase-5-Nachtrag **aufgelöst**; seine 6 verbliebenen Klassen
(Package `org.kabieror.elwasys.common`) liegen jetzt unverändert direkt im Client-Raspi-Modul.
Nur das Terminal nutzt sie zur Laufzeit – das Backend hat ein eigenes Datenmodell und brauchte
Common nie produktiv.

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

## Ausgangslage vor der Modernisierung (historisch)

> Dieser Abschnitt beschreibt bewusst den **Stand vor Beginn** der Modernisierung (Grundlage
> für den Migrationsplan) und ist nicht der aktuelle Zustand. Der aktuelle Stand (Phase 5, Root-
> Reactor mit 2 Modulen, kein Alt-Portal und kein eigenständiges Common-Modul mehr) ist oben im
> „Komponenten-Bild" sowie in
> [01-architecture.md](01-architecture.md) und [05-migration-plan.md](05-migration-plan.md)
> beschrieben.

- Multi-Modul-Maven-Projekt (3 separate `pom.xml`, **kein** Aggregator-Parent-POM).
- **Technologisch veraltet** an mehreren Stellen:
  - Portal auf **Vaadin 7.6.8** (2016) + **GWT 2.7** + **Java 8**, WAR auf Jetty.
  - Client auf **JavaFX 20**, gemischte Java-Level (Common: Java 8, Client: Java 16).
  - Alte HTTP-Clients (Apache HttpComponents, unirest 1.x), alte Postgres-Treiber je Modul
    unterschiedlich.
- Tests: nur vereinzelt (TestNG/JUnit gemischt), **keine** UI-Tests, **keine** CI-Tests
  (CI baut nur bei Release ein JAR).
- Ca. **21.800 Zeilen Java** über 162 Dateien (Client 89, Common 37, Portal 36).

Details und der konkrete Modernisierungsplan: siehe [05-migration-plan.md](05-migration-plan.md).
