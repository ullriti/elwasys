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
- **Reservierungen** von Geräten
- **Web-Portal** zur Administration (Nutzer, Gruppen, Geräte, Programme, Standorte, Guthaben)
- **Fernwartung** (Maintenance-Verbindung zwischen Portal und Client)
- Mehrere **Standorte** (Locations), je Standort ein Client-Terminal

## Drei-Komponenten-Bild (High Level)

```
┌────────────────────────┐        ┌──────────────────────────┐
│  Raspberry-Pi-Client    │        │      Web-Portal           │
│  (JavaFX Touch-UI)      │        │      (Vaadin 7 Webapp)    │
│                         │        │                          │
│  - RFID-Login           │        │  - Admin-Dashboard        │
│  - Geräteauswahl        │        │  - Nutzer/Gruppen/Geräte  │
│  - Programm starten     │        │  - Programme/Standorte    │
│  - Zigbee schalten      │        │  - Guthaben verwalten     │
│  - Leistung messen      │        │  - Log-Viewer / Wartung   │
└───────────┬────────────┘        └────────────┬─────────────┘
            │                                   │
            │        ┌──────────────────┐       │
            └───────▶│   PostgreSQL DB   │◀──────┘
                     │  (gemeinsam)      │
                     └──────────────────┘
            │
            │  Maintenance-WebSocket (Portal ⇄ Client)
            └───────────────────────────────────────────

  Client ⇄ Zigbee-Gateway (deCONZ/ConBee2)  bzw.  fhem  → Funksteckdosen
```

- **Common**: gemeinsame Bibliothek (Datenmodell, DB-Zugriff, Maintenance-Protokoll),
  wird von Client und Portal als Maven-Dependency genutzt.
- **Client-Raspi**: die JavaFX-Anwendung auf dem Raspberry Pi (das Terminal).
- **Portal**: die Vaadin-Webanwendung zur Administration.

Client und Portal teilen sich **eine gemeinsame PostgreSQL-Datenbank**. Zusätzlich gibt es
eine direkte **Maintenance-Verbindung** (WebSocket-basiert) zwischen Portal und Client für
Fernwartung (Status abfragen, Logs holen, App neu starten).

## Hardware-Kontext

- **Raspberry Pi** mit offiziellem **7"-Touchscreen** (Terminal).
- **ConBee2**-USB-Stick als Zigbee-Koordinator, betrieben über **deCONZ** (Phoscon).
- **Zigbee-Funksteckdosen mit Energiezähler** (z. B. LIDL SilverCrest), vor die Maschinen
  gesteckt.
- Alternativ (ältere Variante): **fhem** als Gateway.
- Optional RFID-Kartenleser (über Telnet/serielle Schnittstelle angebunden).

## Aktueller Zustand (Ausgangslage für die Überarbeitung)

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
