# 05 – Modernisierungsplan (lebendes Dokument)

> Dieses Dokument wird laufend fortgeschrieben. Es hält die **Rahmenbedingungen**, die
> vollständige **Komponenten-Inventur**, die **Zielarchitektur**, die **Reihenfolge** der
> Schritte und den **Fortschritt** fest.
>
> Stand 2026-07-20: Phase 0 (Sicherheitsnetz) und Phase 1 (Fundament: Build &
> Konsolidierung) sind abgeschlossen. Auf Basis der neuen Vorgaben des Auftraggebers wurde
> der Plan von einer reinen „Upgrade-Liste“ zu einem Zielarchitektur-getriebenen
> Modernisierungsplan überarbeitet. **Phase 2 ist abgeschlossen**: AP1 (Backend-Gerüst +
> Flyway-Baseline), AP2 (JPA-Entities/Repositories + Geschäftslogik-Portierung:
> Abrechnung, Berechtigungen, Preisberechnung, Execution-Lebenszyklus), AP3 (Auth:
> Argon2id-Hashing + SHA1-Migrationspfad, Login-/Session-Handling), AP4 (REST-API v1 +
> Standort-Token-Auth + WebSocket-Endpunkt für Terminals), AP5 (Benachrichtigungsdienst
> SMTP/Pushover im Backend) und AP6 (Deployment: Dockerfile, docker-compose, Helm Chart,
> TLS-Konzept) sind implementiert; die abschließende QA-Review (Diff-Review aller
> Phase-2-Commits + volle Testsuiten inkl. Parallelbetriebs-Beweis auf der geteilten
> E2E-DB) fand **keine Befunde** - Phase 2 gilt damit formal als abgeschlossen. **Phase 3
> (Portal-Neubau) hat mit AP1 begonnen**: Vaadin-Flow-Grundgerüst im Backend (Login-View,
> Rollen-Weiterleitung nach Login, Admin-/Benutzer-Layout mit Navigation, Platzhalter-Views,
> Security-Integration ohne Änderung an Terminal-API/WebSocket) ist umgesetzt, Backend-Suite
> **116/116** grün. Offener Punkt für AP2: Vaadins Lizenzpflicht im Dev-Modus (siehe
> Risikotabelle/„Offene Fragen“ unten) blockiert in dieser Sandbox-Umgebung Dev-Modus-Starts
> und Browser-getriebene Tests gegen einen echten laufenden Server. Nächster Schritt: Phase 3
> AP2 (Stammdaten-Views: Dashboard, Users, UserGroups, Devices, Programs, Locations).

## Rahmenbedingungen (Vorgaben des Auftraggebers, 2026-07-20)

**Gesetzt (bleibt):**
1. **Java** auf der Serverseite („Java Backend“).
2. **PostgreSQL** als Datenbank (inkl. Bestandsdaten – Nutzer, Guthaben, Historie).
3. **Terminal-Clients bleiben Raspberry Pis mit Touch-Display.**

**Frei (kann neu gedacht werden):** alles andere – Frameworks, Schnittstellen,
Deployment, Portal-Technologie, interne Struktur.

**Harte Nebenbedingung:** **Die Nutzer dürfen sich nicht umstellen müssen.**
- Terminal: identischer Bedienfluss (RFID-Login → Gerät wählen → Programm wählen →
  bestätigen → Maschine läuft → Auto-Ende → Benachrichtigung). Das bestehende UI-Layout
  bleibt erhalten; die E2E-Suite aus Phase 0 fixiert dieses Verhalten.
- Portal: gleiche Aufgaben und Arbeitsabläufe für Admins (Nutzer/Gruppen/Geräte/Programme/
  Standorte/Guthaben verwalten, Logs einsehen, Terminal fernwarten). Die Optik darf sich
  ändern, die Abläufe und Funktionen nicht.

## Leitgedanken

1. **Erst absichern, dann umbauen.** ✅ erledigt: reproduzierbarer Build + verhaltens­fixierende
   UI-/E2E-Tests (Client 21 Tests, Portal 18 Tests, Cross-Component grün) + PR-CI.
2. **Strangler-Muster statt Big Bang.** Das neue Backend entsteht **parallel** zum Bestand
   auf derselben Datenbank. Komponenten werden einzeln umgehängt (erst Portal, dann Client);
   der Bestand bleibt bis zur Ablösung lauffähig.
3. **Kleine, überprüfbare Schritte.** Jeder Schritt ist einzeln baubar/testbar und wird
   committet; die CI bleibt durchgehend grün.
4. **Verhalten bewahren.** Die vorhandenen E2E-Tests sind der Maßstab: Nutzer-sichtbares
   Verhalten ändert sich nicht (Ausnahme: explizit beauftragte Änderungen).

## Komponenten-Inventur (vollständig, mit Modernisierungs-Entscheidung)

Erneute Prüfung aller Komponenten am 2026-07-20, damit nichts vergessen wird.
Legende: **Neu** = wird durch Neuentwicklung ersetzt · **Modern.** = bleibt, wird
modernisiert · **Bleibt** = unverändert/nur gepflegt · **Weg** = entfällt · **?** =
Entscheidung des Auftraggebers offen.

### Common (Bibliothek, Java 8)
| Komponente | Ist | Entscheidung |
|---|---|---|
| Datenmodell (`User`, `Device`, `Program`, `Execution`, `CreditAccountingEntry`, …) | POJOs mit DB-Mapping von Hand | **Neu** – wandert als Entities/DTOs ins Backend |
| `DataManager` (direkter JDBC-Zugriff, von Client *und* Portal genutzt) | zentraler SQL-Zugriff, handgeschrieben | **Weg** – ersetzt durch Backend-Persistenz + REST-API; stirbt mit dem letzten Direkt-DB-Zugriff |
| `ConfigurationManager`, `FormatUtilities`, `Utilities` (`APP_VERSION`) | Hilfsklassen | **Modern.** – nur noch clientseitig nötig, Rest ins Backend |
| Maintenance-Protokoll (`maintenance/*`: Messages, `MaintenanceServer`/`-Client`) | eigenes Nachrichtenprotokoll über WebSocket, Portal wählt Client über in DB registrierte IP an | **Neu** – Richtung umkehren: Client verbindet sich ausgehend zum Backend (siehe Zielarchitektur) |
| `database-init.sql`, `database-upgrade/*.sql` (Schema 0.4.0, manuelles Upgrade über `config.db.version`) | handgepflegte SQL-Skripte | **Neu** – Flyway-Migrationen; Baseline = Bestands­schema, Daten bleiben erhalten |
| ISO-Warn-SVGs (W001–W003) | Ressourcen | **Bleibt** |

### Client-Raspi (JavaFX-Terminal, Java 16)
| Komponente | Ist | Entscheidung |
|---|---|---|
| `application/` (`Main`, `ElwaManager`-Singleton, `SingleInstanceManager`) | Singleton-zentriert, harte Kopplung | **Modern.** – DI einführen (Voraussetzung für isolierte Tests), Ablauf unverändert |
| `ui/medium/` (800×480, Haupt-UI: FXML, State-Machine, Views) | JavaFX 20, FXML | **Bleibt** (JavaFX/Java aktualisieren; Layout & Bedienfluss unverändert – Nutzer-Vorgabe) |
| `ui/small/` (320×240-Variante) | zweite UI-Größe | **Bleibt** – laut Auftraggeber noch im Einsatz (2026-07-20); wird in Phase 4 mit modernisiert und bekommt mind. Smoke-Test-Abdeckung |
| `ui/scheduler/` (Auto-Logout, `BacklightManager`) | Timer + sysfs-Backlight | **Bleibt** (Verhalten fixiert durch Tests) |
| `executions/` (`ExecutionManager`, `ExecutionFinisher`, Auto-Ende) | Geschäftslogik im Client, schreibt direkt in DB | **Modern.** – Ablauf bleibt im Client (Nähe zur Hardware/Leistungsmessung), Persistenz/Abrechnung über Backend-API |
| `devices/deconz/` (REST + WebSocket zu deCONZ, Leistungsmessung, Registrierung) | unirest 1.x + HttpComponents | **Modern.** – bleibt lokal auf dem Raspi (ConBee2 steckt dort); HTTP-Client auf `java.net.http` |
| `devices/FhemDevicePowerManager` (fhem-Gateway) | Alternative zu deCONZ (`ElwaManager` wählt beim Start: `deconz.server` gesetzt → deCONZ, sonst `fhem.server`); dazu `fhem_*`-Spalten in `devices` + Felder im Portal-DeviceWindow | **Modern.** – beide Gateways sind laut Auftraggeber im Einsatz (2026-07-20) und bleiben unterstützt; HTTP-Umstellung auf `java.net.http` betrifft beide Pfade; E2E-Tests künftig mit **beiden** Simulatoren (fhem-Sim existiert, deCONZ-Sim wird ergänzt) |
| `io/` (`CardReader` über `TelnetClient`, RFID-Events) | Telnet-basierte Leseranbindung | **Bleibt** (Hardware-Anbindung unverändert) |
| Benachrichtigungen (Commons Email/SMTP, Pushover-Client im Client) | Terminal verschickt E-Mail/Push selbst, braucht SMTP-Credentials | **Neu** – wandert ins Backend (zentral; Terminal meldet nur Ereignisse) |
| `configuration/` (`elwasys.properties`, `LocationManager`) | DB-Zugangsdaten auf jedem Terminal | **Modern.** – statt DB-Credentials: Backend-URL + Terminal-Token |
| `MaintenanceServerManager` (Server-Rolle des Clients) | Client lauscht auf Port 3591, registriert IP in DB | **Neu** – ausgehende Dauerverbindung zum Backend ersetzt Server-Rolle |
| Build (fat-jar via assembly-plugin), `setup.sh` (284 Zeilen, interaktiv), `run-*.sh` | funktionsfähig | **Modern.** – fat-jar-Auslieferung bleibt; `setup.sh` fragt künftig Backend-URL/Token statt DB/SMTP ab |
| `res/` (logback.xml, FontAwesome, Bilder) | ok | **Bleibt** (Logback aktualisieren) |

### Portal (Vaadin 7 WAR, Java 8)
| Komponente | Ist | Entscheidung |
|---|---|---|
| Gesamtes Vaadin-7/GWT-Frontend (`WaschportalUI`, Layouts, 6 Views, ~15 CRUD-Fenster, SCSS-Theme, Widgetset) | Vaadin 7.6.8 (2016), GWT 2.7, nicht mehr wartbar | **Neu** – kompletter Neubau auf modernem Stack (siehe Technologie-Entscheidungen); Funktionsumfang 1:1 laut Fenster-/View-Inventar |
| `WashportalManager`, `SessionManager` | Session-/Login-Logik im Portal, SHA1-Passwörter | **Neu** – Auth ins Backend (Argon2id, Session/Token) |
| `MaintenanceConnectionManager` (Fernwartung: Status, Logs, Restart) | Portal → Client direkt | **Neu** – Funktionen bleiben (Status/Logs/Restart im Admin-UI), laufen aber über das Backend |
| `events/`-Listener (Live-Updates zwischen Sessions) | Vaadin-Push | **Neu** – gleiche Funktion im neuen Stack |
| Passwort-Reset per E-Mail, Log-Viewer, ExpiredExecutions | Feature-Inventar | **Neu** – Feature-Parität ist Abnahmekriterium (Playwright-Suite portieren) |
| Jetty-Plugin-Deployment (WAR, `mvn jetty:run`) | veraltet | **Weg** – Portal wird Teil des Backend-Deployments |

### Datenbank (PostgreSQL – bleibt)
| Komponente | Ist | Entscheidung |
|---|---|---|
| Schema 0.4.0 (users, user_groups, devices, programs, executions, credit_accounting, locations, reservations, config, foreign_authkeys, n:m-Tabellen) | gewachsen, funktional | **Bleibt** – als Flyway-Baseline übernommen; Bestandsdaten bleiben |
| Passwörter SHA1 | unsicher | **Neu** – Argon2id + Migrationspfad (Re-Hash beim Login; Alt-Hashes markiert) |
| DB-Rollen `elwaclient1`/`elwaportal`/`elwaapi` mit Default-Passwörtern im Init-SQL | Sicherheitsrisiko; Rechtemodell auf DB-Ebene | **Neu** – nur noch das Backend spricht mit der DB (ein technischer User); Terminal-Rechte werden API-seitig durchgesetzt; Default-Secrets entfallen |
| Spaltentypo `auto_end_power_threashold` | kosmetisch | **Modern.** – per Flyway-Migration umbenennen, sobald kein Alt-Code mehr direkt liest |
| `client_ip`/`client_port`/`client_uid` in `locations` | Registry für Maintenance-Anwahl | **Weg** – obsolet durch ausgehende Client-Verbindung |
| `foreign_authkeys` (Server-Föderation), `app_id`/`access_key`/`auth_key` + Trigger, `reservations`, DB-User `elwaapi` (mobile App, Code außerhalb des Repos) | App laut Auftraggeber nicht (mehr) relevant (2026-07-20); im Repo existieren nur DB-Reste + Auth-Key-Anzeige im Terminal-UI (UserSettings/Confirmation) | **Weg** – in Phase 5 entfernen (DB-User, Spalten, Tabellen, Trigger, UI-Anzeige) |

### Infrastruktur / Repo
| Komponente | Ist | Entscheidung |
|---|---|---|
| 3 lose POMs ohne Parent, Versions-Inkonsistenz (Common `0.0.0-local-development` vs. `0.3.4-SNAPSHOT`) | fehleranfällig | **Neu** – Aggregator-Parent-POM, einheitliche Versionen/Properties |
| Java-Level 8/16 gemischt | uneinheitlich | **Neu** – einheitlich **Java 21 LTS** |
| Testframeworks JUnit 4 + TestNG gemischt | uneinheitlich | **Neu** – JUnit 5 überall |
| `ci.yml` (PR-CI, 3 Jobs, grün) | ✅ vorhanden | **Modern.** – an neue Modulstruktur anpassen |
| `maven-publish.yml` (Release: sed-Versionsersetzung, fat-jar als Release-Asset) | Hack, aber funktional | **Modern.** – Release-Flow auf Parent-POM-Versionierung umstellen; zusätzlich Backend-Artefakt (Container-Image) veröffentlichen |
| `kb/` + `cloud-init` (Remote-Build/Test-Umgebung), `run-*.sh` | ✅ Phase 0 | **Bleibt** – laufend fortschreiben |
| `CNAME` (elwasys.de, GitHub Pages), `doc/deconz`, LICENSE, READMEs | ok | **Bleibt** – Doku am Ende auf Zielarchitektur aktualisieren |

## Zielarchitektur

Kernidee: **Ein zentrales Java-Backend** (Spring Boot) wird alleiniger Eigentümer der
Datenbank und der Geschäftslogik. Portal-UI und Terminals sind Clients dieses Backends.
Der Direkt-DB-Zugriff von zwei Anwendungen aus – heute die Wurzel vieler Probleme
(doppelte Logik, DB-Credentials auf jedem Terminal, DB-seitiges Rechtemodell, IP-Registry
für Fernwartung) – entfällt.

```
┌────────────────────────────┐          ┌─────────────────────────────────────┐
│  Raspi-Terminal (bleibt)    │          │  elwasys-Backend (NEU, Spring Boot) │
│  JavaFX-Touch-UI, Java 21   │          │  Java 21, ein Deployment            │
│                             │   REST   │                                     │
│  - RFID-Login               │◀────────▶│  - Geschäftslogik (Abrechnung,      │
│  - Gerät/Programm wählen    │  + WS    │    Berechtigungen, Programm-Ende)   │
│  - deCONZ/Zigbee lokal      │(ausgehend│  - REST-API (Terminals, App)        │
│  - Leistungsmessung         │ v. Term.)│  - Admin-Portal (Web-UI)            │
└────────────┬───────────────┘          │  - Benachrichtigungen (SMTP/Push)   │
             │                          │  - Fernwartung (über die bestehende │
             ▼                          │    WS-Verbindung des Terminals)     │
   ConBee2/deCONZ → Steckdosen          │  - Auth (Argon2id, Terminal-Tokens) │
                                        └──────────────────┬──────────────────┘
        Admin-Browser ──────── HTTPS ─────────────────────▶│
                                                           ▼
                                                ┌────────────────────┐
                                                │  PostgreSQL         │
                                                │  (bleibt, Flyway)   │
                                                └────────────────────┘
```

Wesentliche Änderungen gegenüber heute:

1. **Ein DB-Client statt drei.** Nur das Backend spricht SQL (Spring Data JPA, Flyway für
   Migrationen). Die DB-User `elwaclient1`/`elwaapi` samt Default-Passwörtern entfallen.
2. **Terminal spricht API statt SQL.** Das Terminal authentifiziert sich mit einem
   Standort-Token, lädt Geräte/Programme/Nutzerdaten über REST und meldet Ereignisse
   (Start, Leistungswerte-Ende, Abbruch). Die **Hardware-Nähe bleibt im Terminal**:
   deCONZ-Steuerung, Leistungsmessung, Ende-Erkennung und RFID laufen weiter lokal –
   das Terminal bleibt bei Netz-Schluckauf bedienfähig für laufende Vorgänge.
3. **Fernwartung ohne IP-Registry.** Das Terminal hält eine ausgehende
   WebSocket-Verbindung zum Backend (NAT-/Firewall-freundlich). Status, Logs und Restart
   laufen als Nachrichten über diese Verbindung; `client_ip/-port` in der DB entfallen.
4. **Benachrichtigungen zentral.** E-Mail (SMTP) und Pushover verschickt das Backend.
   Terminals brauchen keine SMTP-Zugangsdaten mehr.
5. **Portal ist Teil des Backends.** Ein Deployment (Container oder systemd-Dienst) statt
   WAR+Jetty; TLS über Reverse Proxy (z. B. Caddy/nginx) oder direkt.
6. **Mobile App / Drittzugriff** (falls gewünscht): dieselbe REST-API mit eigenen Scopes –
   ersetzt den direkten `elwaapi`-DB-Zugang.

## Technologie-Entscheidungen (Empfehlungen)

| Thema | Empfehlung | Begründung / Alternative |
|---|---|---|
| Java | **21 LTS** (Toolchain im Parent-POM) | aktuelles LTS, von JavaFX & Spring Boot 3 getragen; 25 LTS später als Drop-in |
| Backend-Framework | **Spring Boot 3.x** | De-facto-Standard, WebSocket/Security/Scheduling/Mail an Bord; Spring-WebSocket ist im Client heute schon Dependency. Alternative: Quarkus (kein Team-Vorteil erkennbar) |
| Persistenz | **Spring Data JPA + Flyway** | Bestandsschema als Baseline-Migration; Entities ersetzen das handgeschriebene Mapping des `DataManager` |
| Portal-UI | **Vaadin Flow 24** (im Backend eingebettet) – ✅ vom Auftraggeber bestätigt (2026-07-20) | bleibt reines Java (Vorgabe „Java Backend“, kein separater Frontend-Stack zu pflegen), ideal für CRUD-Admin-UIs, Push eingebaut; konzeptuelle Nähe zum Bestand erleichtert Feature-Parität |
| Terminal-UI | **JavaFX beibehalten** (aktuelles JavaFX, Java 21) | UI/FXML und Bedienfluss bleiben unverändert → Nutzer merken nichts; TestFX-Suite bleibt gültig. Alternative „Chromium-Kiosk + Web-UI“ verworfen: neuer Stack, Touch-/Offline-Verhalten riskanter, kein Nutzer-Mehrwert |
| HTTP im Terminal | `java.net.http` (JDK) | ersetzt HttpComponents 4.x + unirest 1.x (deCONZ **und** Backend-API) |
| Passwort-Hashing | **Argon2id** (Spring Security) | SHA1-Ablösung; transparente Migration: beim ersten erfolgreichen Login re-hashen, Rest per Admin-Reset |
| API-Auth | Terminal: statisches Token pro Standort (rotierbar); Admins: Session-Login | einfach, offline-fähig konfigurierbar; OAuth/OIDC bewusst vermieden (Overkill) |
| Tests | JUnit 5, Testcontainers (Postgres) im Backend; TestFX (Client) und Playwright (Portal) weiterführen | E2E-Suiten aus Phase 0 sind das Abnahme-Sicherheitsnetz |
| Deployment Backend | Container-Image; Betrieb per **docker-compose** (Backend + Postgres) oder **Kubernetes** (Helm Chart wird mitgeliefert) – ✅ vom Auftraggeber festgelegt (2026-07-20) | Raspi-Terminal weiterhin fat-jar + systemd via `setup.sh` |

## Roadmap

### Phase 0 – Verständnis & Absicherung *(abgeschlossen 2026-07-20)*
- [x] KB anlegen, Projekt erforschen, Übersicht dokumentieren
- [x] Cloud-Init/Remote-Umgebung für Build & (headless) UI-Tests
- [x] UI-Tests Client (TestFX/Xvfb) – **21 Tests grün** (C1–C16 + Cross-Component P21/P22)
- [x] UI-Tests Portal (Playwright E2E) – **18 Tests grün** (P1–P20)
- [x] Reproduzierbarer Build aller drei Module; PR-CI (3 Jobs) grün
- [x] Zustandsübergänge der Client-State-Machine über die E2E-Suite abgesichert

### Phase 1 – Fundament (Build & Konsolidierung) *(abgeschlossen 2026-07-20)*
Ziel: einheitliche, moderne Basis, auf der das neue Backend-Modul aufsetzen kann.
- [x] Aggregator-Parent-POM (Module: Common, Client-Raspi, Portal; einheitliche Versionen,
      `dependencyManagement`, Properties); Release-Workflow (`maven-publish.yml`) auf
      Parent-Versionierung umstellen *(erledigt 2026-07-20, siehe „Änderungslog“ unten)*
- [x] Java-Level vereinheitlichen auf **21** (Common 8 → 21 ✅; Client-Raspi 16 → 21 ✅;
      Portal bleibt bewusst 8-kompatibel gebaut, bis es in Phase 3 abgelöst ist – Vaadin 7/GWT
      baut unter JDK 21 mit Sprachlevel 8 problemlos, Sprachlevel bleibt explizit eingefroren)
      *(erledigt 2026-07-20)*
- [x] Testframeworks vereinheitlichen (JUnit 5; TestNG-Reste migrieren) – ✅ erledigt
      2026-07-20: einzige TestNG-Testklasse (`InactivitySchedulerTest`) nach JUnit 5
      migriert und von `src/main` (wurde von Surefire nie ausgeführt!) nach `src/test`
      verschoben; TestNG- **und** die inzwischen ungenutzte JUnit-4-Dependency aus
      Client-Raspi/pom.xml entfernt. Common hat nur einen vollständig auskommentierten
      JUnit-Test (`MaintenanceConnectionTest`, kein `@Test` aktiv) – nichts zu migrieren,
      bleibt vorerst so dokumentiert.
- [x] `ElwaManager`-Singleton per DI entkoppeln → isolierte Charakterisierungs-Tests der
      State-Machine (`MainFormStateManager`) nachziehen (aus Phase 0 übernommen) – ✅ erledigt
      2026-07-20: minimaler DI-Seam (package-private Test-Konstruktor `MainFormController`,
      der das Verdrahten mit `ElwaManager.instance`/`InactivityScheduler` überspringt), 12 neue
      JUnit-5-Tests ohne TestFX/Xvfb/DB decken alle Zustandsübergänge der State-Machine ab.
      Korrektur (QA-Review 2026-07-20): Voll-Suite ist **37/37** grün (21 vorher + 12 neu +
      4 migrierte `InactivityScheduler`-Tests, die vorher fälschlich nicht mitgezählt waren –
      siehe die beiden „33/33"-Korrekturen im Änderungslog/Status-Log unten)
- [x] CI an Parent-POM angepasst *(2026-07-20)*: die bestehende 3-Job-Struktur
      (Common/Client/Portal, kleinstes Risiko, Verhalten unverändert) wurde beibehalten statt
      auf einen kombinierten Reactor-Job umzustellen; alle Stellen, die Common isoliert
      installieren (CI-Job, `run-ui-tests.sh`, `run-client-e2e.sh`,
      `run-cross-component-e2e.sh`, `start-portal.sh`, SessionStart-Hook), bauen jetzt über
      `mvn -f pom.xml install -pl Common -am`, damit die Parent-POM mit ins lokale Repo
      installiert wird (sonst schlägt die Abhängigkeitsauflösung von `common` in
      Client-Raspi/Portal fehl)
- [x] **QA-Review-Fix (2026-07-20)**: Zwei echte Regressionen gefunden und behoben, die die
      lokale Verifikation nicht aufgedeckt hätte, weil die Remote-Dev-Umgebung bereits
      systemweit JDK 21 hat:
      1. `.github/workflows/ci.yml` (alle 3 Jobs) und `.github/workflows/maven-publish.yml`
         setzten weiterhin JDK 17 auf (`actions/setup-java`), obwohl Common/Client-Raspi seit
         diesem Arbeitspaket mit `maven.compiler.release=21` bauen – ein JDK 17 kann
         `--release 21` nicht bedienen, echte GitHub-Actions-Läufe wären mit
         `invalid target release: 21` fehlgeschlagen. Auf JDK 21 (Liberica) angehoben.
      2. `Client-Raspi/setup.sh` installiert auf frisch provisionierten Raspberry-Pi-Terminals
         `bellsoft-java17-runtime-full` (armhf) – das Client-fat-jar hat durch den
         Sprachlevel-Sprung aber jetzt Bytecode-Major-Version 65 (Java 21) und würde auf
         einem Java-17-JRE mit `UnsupportedClassVersionError` abstürzen. Auf
         `bellsoft-java21-runtime-full` angehoben (für armhf verfügbar, inkl. LibericaFX).
         **Restrisiko** (nicht Teil dieses Fixes, siehe Risikotabelle unten): bereits im Feld
         befindliche Terminals, die nicht erneut `setup.sh` durchlaufen, haben weiterhin nur
         ein Java-17-JRE installiert – ein reines Fat-Jar-Update ohne JRE-Upgrade auf diesen
         Geräten würde den Terminal-Start brechen. Muss vor dem nächsten Client-Release
         geklärt sein (z. B. JRE-Upgrade-Schritt in die Update-Prozedur aufnehmen).

### Phase 2 – Backend-Gerüst (parallel zum Bestand) *(abgeschlossen 2026-07-20)*
Ziel: neues Modul `backend` läuft produktionsnah **neben** Client & Portal auf derselben DB.
- [x] Spring-Boot-Modul `backend` anlegen (Java 21, Actuator/Health, Logging) *(AP1,
      2026-07-20)*
- [x] Flyway-Baseline aus `database-init.sql` + bestehenden Upgrade-Skripten erzeugen;
      Upgrade-Mechanismus über `config.db.version` stilllegen *(AP1, 2026-07-20)*
- [x] JPA-Entities für das Bestandsschema + Repositories; Geschäftslogik aus
      `Common.DataManager`/Portal/Client schrittweise portieren (Abrechnung, Berechtigungen,
      Preisberechnung) – mit Unit-Tests gegen Testcontainers *(AP2, 2026-07-20)*
- [x] Auth: Argon2id-Hashing + SHA1-Migrationspfad; Login-/Session-Handling *(AP3,
      2026-07-20)*
- [x] REST-API v1 für Terminal-Anwendungsfälle (Login per Karte, Geräte-/Programmliste,
      Execution starten/beenden/abbrechen, Guthaben) + Standort-Token-Auth *(AP4,
      2026-07-20)*
- [x] WebSocket-Endpunkt für Terminals (Ereignisse, Fernwartungskanal) *(AP4, 2026-07-20 –
      Fundament: Verbindungsregistry, Heartbeat, HELLO/HELLO_ACK und
      STATUS_REQUEST/STATUS_RESPONSE als Gerüst; volle Fernwartungs-Portierung folgt
      Phase 3/4)*
- [x] Benachrichtigungsdienst (SMTP, Pushover) im Backend *(AP5, 2026-07-20 –
      vollständig implementiert/getestet, aber hinter `elwasys.notifications.enabled`
      [Default AUS] und von keinem produktiven Ablauf aufgerufen; Scharfschaltung mit
      echten Ereignissen folgt Phase 4, siehe kb/03-modules.md und „Entscheidungen“)
- [x] Deployment: Dockerfile + docker-compose (Backend + Postgres) **und** Helm Chart
      für Kubernetes; TLS-Konzept (Compose: Reverse Proxy; K8s: Ingress) *(AP6,
      2026-07-20 - letztes Arbeitspaket der Phase-2-Roadmap)*
- [x] **QA-Review der gesamten Phase 2** *(2026-07-20, ohne Befunde – Details siehe
      Änderungslog „Phase 2 QA-Review + Abschluss")*

### Phase 3 – Portal-Neubau
Ziel: Admin-Portal als Teil des Backends, Feature-Parität, altes Portal abgeschaltet.
- [x] Vaadin-Flow-UI im Backend: Login, Layout-Gerüst (Public/User/Admin) *(AP1, 2026-07-20 –
      Grundgerüst: Login-View, Rollen-Weiterleitung, Admin-/Benutzer-Layout mit
      Navigation, Platzhalter-Views; Auftraggeber-Vorgabe des Vaadin-Lizenzmodells ist ein
      offenes Risiko für AP2, siehe „Offene Punkte/Risiken Phase 3 AP1" unten)
- [ ] Views mit Feature-Parität laut Inventar: Dashboard, Users, UserGroups, Devices,
      Programs, Locations, Guthaben (CreditAccounting inkl. Unveränderlichkeit der
      Buchungen), UsersDashboard
- [ ] Dialoge/Funktionen: Passwort ändern/zurücksetzen (E-Mail-Flow), UserSettings,
      ExpiredExecutions, Log-Viewer, Fernwartung (Status/Logs/Restart über Backend-Kanal)
      – Admin-Ansichten zuerst; Nutzer-Selbstbedienungsbereich zuletzt (wird laut
      Auftraggeber kaum genutzt, bleibt aber funktional)
- [ ] Live-Updates zwischen Sessions (ersetzt `events/`-Listener + Vaadin-Push)
- [ ] Playwright-E2E-Suite (P1–P20) auf das neue Portal portieren → Abnahmekriterium
- [ ] Altes Portal-Modul stilllegen (Code bleibt bis Phase 5 im Repo)

### Phase 4 – Terminal-Modernisierung
Ziel: gleicher Bedienfluss, neuer Unterbau; kein Direkt-DB-Zugriff mehr vom Raspi.
- [ ] Client auf Java 21 + aktuelles JavaFX heben (UI/FXML unverändert)
- [ ] Datenzugriff auf REST-API umstellen (`DataManager`-Nutzung raus); Konfiguration:
      Backend-URL + Token statt DB-Credentials
- [ ] Maintenance umdrehen: ausgehende WS-Verbindung zum Backend ersetzt
      `MaintenanceServerManager` + IP-Registrierung in `locations`
- [ ] Benachrichtigungen aus dem Client entfernen (macht jetzt das Backend)
- [ ] Gateway-Anbindungen (deCONZ **und** fhem) auf `java.net.http` umstellen
      (unirest/HttpComponents raus); beide Gateways bleiben unterstützt
- [ ] deCONZ-Simulator für die Testharness bauen; E2E-Kernszenarien mit **beiden**
      Gateway-Simulatoren fahren (fhem-Sim existiert bereits)
- [ ] Robustheit: Verhalten bei Backend-Nichterreichbarkeit definieren und testen
      (laufende Executions lokal zu Ende führen, Ereignisse nachmelden); zusätzlich
      **Offline-Buchungen** für eine definierte Zeitspanne (Auftraggeber-Wunsch
      2026-07-20, siehe Konzeptskizze „Offline-Buchungen am Terminal“ unten)
- [ ] `ui/small` (320×240) mit modernisieren (bleibt im Einsatz) und mind. per Smoke-Test
      absichern (bisher deckt die E2E-Suite nur die Medium-UI ab)
- [ ] TestFX-/E2E-Suite (C1–C16, P21/P22) gegen den neuen Unterbau grün → Abnahmekriterium
- [ ] `setup.sh` aktualisieren (fragt Backend-URL/Token; kein DB/SMTP-Setup mehr)

### Phase 5 – Ablösung, Härtung, Aufräumen
- [ ] Alt-Portal-Modul und `Common.DataManager`/Maintenance-Altprotokoll entfernen;
      Common auf das Nötigste schrumpfen oder auflösen
- [ ] DB härten: User `elwaclient1`/`elwaapi` + Default-Passwörter entfernen; Grants nur
      noch für den Backend-User; Admin-Seed ohne Default-Passwort (Setup-Wizard/CLI)
- [ ] Flyway-Migration: `auto_end_power_threashold` → `auto_end_power_threshold`,
      `client_ip/-port/-uid/-last_seen` aus `locations` entfernen
- [ ] App-Reste (`elwaapi`) entfernen: DB-User, Spalten `app_id`/`access_key`/`auth_key`
      inkl. Trigger, Tabellen `reservations` + `foreign_authkeys`, Auth-Key-Anzeige im
      Terminal-UI (UserSettings-/Confirmation-View)
- [ ] Release-Pipeline final: Terminal-fat-jar + Backend-Image je Release
- [ ] Doku-Endstand: READMEs, kb/, setup-Anleitungen auf Zielarchitektur

### Phase 6 – Produktivumschaltung (Cutover)
Ziel: das **bestehende** Produktiv-Setup (physische Raspi-Terminals im Feld + laufendes
Portal/DB) auf die neue Architektur umstellen – ohne Datenverlust und mit klarem
Rollback-Pfad, statt nur eine neue Umgebung danebenzustellen.
- [ ] Migrationsskripte für den Produktivbestand: über die Flyway-Baseline (Phase 2) hinaus
      alles vorbereiten, was der eigentliche Umzug bestehender Daten braucht (z. B.
      Standort-Tokens für Terminals anlegen statt DB-Credentials, veraltete
      `locations`-Registrierungen bereinigen); dazu ein Rückbau-/Rollback-Skript für den
      Fall eines abgebrochenen Cutovers
- [ ] Terminals neu aufsetzen: alle im Feld befindlichen Raspberry-Pi-Terminals auf den
      neuen Unterbau bringen (Java 21, Backend-URL/Token statt DB-Credentials). Löst dabei
      das in Phase 1 dokumentierte Restrisiko auf: im Feld läuft bislang nur ein
      Java-17-JRE, das neue Client-Jar braucht Sprachlevel 21 – JRE-Upgrade ist daher
      zwingender erster Schritt vor jedem Rollout eines mit Sprachlevel 21 gebauten
      Release-Jars auf ein Bestandsgerät (siehe Risikotabelle)
- [ ] Upgrade-Skript für Terminals (`update.sh` o. ä.): künftige Updates vereinfachen (neues
      fat-jar laden, Dienst neu starten), ohne dass vor Ort jedes Mal das komplette
      (interaktive) `setup.sh` erneut durchlaufen werden muss
- [ ] Optional, aber empfohlen: Auto-Update mit Rollback für Terminals – der Client prüft
      periodisch (oder auf Anstoß des Backends) auf eine neue Version, lädt sie, wechselt
      um und verifiziert den erfolgreichen Start (State-Machine erreicht `SELECT_DEVICE`
      innerhalb einer Frist); schlägt das fehl, automatischer Rollback auf die zuvor
      bekannt funktionierende Version (altes Jar + Konfiguration bleiben bis zur nächsten
      erfolgreichen Aktualisierung als Fallback erhalten) – wichtig, weil Terminals
      unbeaufsichtigt im Feld stehen und ein fehlgeschlagenes Update ohne Rollback ein
      Gerät lahmlegen würde
- [ ] Portal/Backend brauchen **kein** eigenes Upgrade-/Rollback-Skript: Rollout und
      Rollback laufen über die gewählte Betriebsplattform (Docker-Compose-Redeploy bzw.
      Kubernetes-Rolling-Update/Helm-Rollback, siehe Zielarchitektur „Betriebsmodell
      Backend“ – das übernehmen die Plattformen selbst). Stattdessen reicht hier eine
      automatisierte Smoke-Test-Suite, die nach jedem Deployment automatisch läuft und die
      Funktionsfähigkeit bestätigt (z. B. Health-/Actuator-Endpoint + eine schlanke
      Teilmenge der Playwright-Suite gegen die frisch deployte Umgebung), bevor ein Rollout
      als erfolgreich gilt bzw. bevor manuell/automatisch zurückgerollt wird
- [ ] Cutover-Reihenfolge festlegen und proben (Strangler-Muster aus den Leitgedanken
      beibehalten: z. B. zuerst Portal/Backend umstellen und beobachten, dann Terminals
      schrittweise, nicht alle auf einmal); Wartungsfenster/Nutzerkommunikation planen,
      falls für den Umschaltzeitpunkt nötig

## Konzeptskizze: Offline-Buchungen am Terminal (Detailausarbeitung folgt)

Auftraggeber-Wunsch (2026-07-20): Ist das Backend nicht erreichbar, sollen Terminals für
eine **definierte Zeitspanne eigenständig neue Buchungen akzeptieren** und diese nach
Wiederverbindung übermitteln – über das bloße „laufende Executions lokal zu Ende führen“
(Phase-4-Roadmap) hinaus. Grobkonzept als Grundlage für die spätere Detailausarbeitung:

1. **Lokaler Daten-Snapshot**: Das Terminal hält einen periodisch aktualisierten Snapshot
   der für Buchungen nötigen Daten seines Standorts (Nutzer mit Kartennummern, Guthaben,
   Sperr-Status, Geräte/Programme/Preise, Berechtigungen) mit Zeitstempel. Kein Ablegen
   von Passwort-Hashes auf dem Terminal.
2. **Offline-Entscheidung**: Kartenlogin, Berechtigungs- und Guthabenprüfung laufen gegen
   den Snapshot; lokal aufgelaufene Offline-Buchungen werden vom gecachten Guthaben
   abgezogen. Regeln bewusst konservativ (z. B. ablehnen, wenn der Snapshot älter als das
   konfigurierte Maximum ist; optionaler Sicherheitsabschlag aufs Guthaben).
3. **Persistentes Ereignis-Journal**: Start/Ende/Abbruch werden mit terminal-generierten
   Idempotenz-Schlüsseln (UUID) und Original-Zeitstempeln lokal persistiert
   (neustartfest, in Reihenfolge).
4. **Nachmeldung (Replay)**: Nach Wiederverbindung überträgt das Terminal das Journal in
   Reihenfolge über die REST-API; das Backend verbucht nachträglich (Executions +
   Abrechnung mit Original-Zeitstempeln) und dedupliziert über den Idempotenz-Schlüssel.
   Die API v1 braucht dafür voraussichtlich additive Erweiterungen (Idempotenz-Header
   bzw. Replay-/Snapshot-Endpunkte) – bei Phase-4-Beginn einplanen.
5. **Zeitfenster**: Konfigurierbare maximale Offline-Dauer (z. B.
   `offline.max-duration`); danach lehnt das Terminal neue Buchungen ab (Fehlbild wie
   heute bei DB-Ausfall, C15), laufende Vorgänge werden weiterhin lokal beendet.
6. **Konfliktbehandlung**: Gerätekonflikte sind ausgeschlossen (ein Gerät hängt physisch
   an genau einem Terminal – Single Writer). Guthaben ist eventual consistent; ein durch
   Offline-Buchungen negativ gewordenes Guthaben wird beim Replay normal verbucht.
   Benachrichtigungen versendet das Backend erst beim Replay (oder verwirft zu alte –
   festzulegen).

**Offene Detailfragen** (bei der Ausarbeitung klären): Länge des Zeitfensters,
Sicherheitsabschlag ja/nein, Umgang mit zwischenzeitlich gesperrten Nutzern,
Uhren-Drift der Terminals, Verschlüsselung des Snapshots/Journals auf dem Gerät.

## Risiken & Gegenmaßnahmen

| Risiko | Gegenmaßnahme |
|---|---|
| Terminal fällt aus, wenn Backend down (heute: wenn DB down) | Backend + DB auf demselben Host/Compose-Stack; laufende Waschvorgänge werden lokal zu Ende geführt und nachgemeldet (Phase 4); Health-Checks + systemd-Restart |
| Feature-Verlust beim Portal-Neubau | Fenster-/View-Inventar (siehe oben) als Checkliste; portierte Playwright-Suite als Abnahme |
| Verhaltensänderung am Terminal | UI/FXML unangetastet; TestFX-Suite muss vor/nach jedem Schritt grün sein |
| SHA1→Argon2-Migration sperrt Nutzer aus | Re-Hash beim ersten Login (SHA1 wird verifiziert, dann ersetzt); Admin-Reset-Flow bleibt |
| Parallelbetrieb Alt/Neu auf einer DB (Phase 2–4) | Backend anfangs nur lesend/additiv; Schreibpfade erst umstellen, wenn der jeweilige Alt-Pfad abgeschaltet wird; keine Schema-Brüche vor Phase 5 |
| Vaadin-7-Portal baut nicht unter Java 21 (Phase 1) | Portal bis zur Ablösung auf altem Sprachlevel einfrieren – es wird ohnehin ersetzt |
| Bereits im Feld befindliche Raspi-Terminals haben nur ein Java-17-JRE (aus einem früheren `setup.sh`-Lauf), das Client-fat-jar baut seit Phase 1 aber mit Sprachlevel 21 (Bytecode-Major 65) | `setup.sh` installiert bei Neu-Provisionierung jetzt `bellsoft-java21-runtime-full` (2026-07-20 gefixt); der Update-Pfad für bereits provisionierte Geräte (JRE-Upgrade vor dem ersten Sprachlevel-21-Release) ist Teil der Terminal-Neuaufsetzung in Phase 6 |
| Terminal-Auto-Update (Phase 6) schlägt fehl oder bricht mitten im Update ab – Gerät steht unbeaufsichtigt im Feld | Vorherige, bekannt funktionierende Version (Jar + Konfiguration) bleibt als Fallback erhalten; Update gilt erst nach verifiziertem erfolgreichem Start (State-Machine erreicht `SELECT_DEVICE`) als abgeschlossen, sonst automatischer Rollback |
| Cutover auf das neue Produktiv-Setup (Phase 6) verliert Bestandsdaten oder legt Terminals/Portal lahm | Migrationsskripte + eigenes Rollback-Skript für den Cutover; Strangler-Reihenfolge (erst Portal/Backend, dann Terminals schrittweise) statt Big-Bang-Umstellung aller Komponenten gleichzeitig |
| **Vaadin-Lizenzpflicht im Dev-Modus** (gefunden Phase 3 AP1, 2026-07-20): Vaadin 24.10.x verlangt beim ersten `VaadinServlet#init()` im Dev-Modus einen Online-Lizenzcheck gegen vaadin.com ("This Vaadin version requires an extended maintenance subscription") – die 24-Linie gilt inzwischen als „Extended Maintenance" (kostenpflichtig über die freie Community-Support-Periode hinaus), unabhängig davon, ob ein kommerzielles Add-on genutzt wird. Diese Sandbox-/Build-Umgebung hat keinen Netzwerkzugriff auf vaadin.com (Proxy liefert `403`) und kann daher weder online validieren noch einen Offline-Schlüssel abrufen – `mvn spring-boot:run` (Dev-Modus) scheitert hier beim Servlet-Start; ein erzwungener Produktionsmodus umgeht den Lizenzcheck zwar, findet dann aber kein Produktions-Frontend-Bundle (nur im `production`-Maven-Profil gebaut). | Betrifft NICHT die automatisierte Testsuite (Tests laufen mit `vaadin.productionMode=true`, keiner ruft eine Vaadin-UI-Route auf, siehe Surefire-Konfiguration in `backend/pom.xml`) – 116/116 Backend-Tests grün. Betrifft aber (a) manuelles interaktives Ausprobieren der UI in dieser Sandbox und (b) jeden künftigen JUnit-Test, der über einen echten eingebetteten Servlet-Container eine Vaadin-Route real rendern lassen will, sowie (c) eine spätere Playwright-E2E-Suite gegen einen hier laufenden Server. **Für AP2/AP3 zu klären (Auftraggeber-Entscheidung nötig, siehe „Offene Fragen" unten)**: entweder (1) einen Offline-Entwicklungsschlüssel besorgen (einmaliger Zugriff auf vaadin.com von außerhalb dieser Sandbox nötig, `mvn com.vaadin:vaadin-maven-plugin:download-offline-license`), (2) eine Vaadin-Extended-Maintenance-Subscription abschließen, oder (3) production-Bundle-Build + Deployment als primären Verifikationsweg etablieren (kein Dev-Modus-Lizenzcheck bei reinem Produktionsbetrieb) und Browser-/Playwright-Tests nur gegen eine echte, lizenzierte Umgebung (z. B. CI mit Internetzugang) fahren. |

## Entscheidungen (Auftraggeber)
- **2026-07-19**: UI-Tests parallel für Client (TestFX) **und** Portal (E2E) aufbauen. ✅
- **2026-07-20**: **Fix bleiben:** Java-Backend, PostgreSQL, Raspi-Terminals mit
  Touch-Display. **Alles andere darf neu gedacht werden.** Nutzer dürfen sich nicht
  umstellen müssen (Bedienfluss/Funktionen bleiben).
- **2026-07-20**: **Vaadin Flow** als Portal-Stack bestätigt.
- **2026-07-20**: **Kleines Display (320×240) ist noch im Einsatz** → `ui/small` bleibt
  und wird mit modernisiert.
- **2026-07-20**: **Mobile App (`elwaapi`) ist nicht relevant** (Idee wurde verworfen, von
  Nutzern nie verwendet) → alle App-Reste werden in Phase 5 entfernt.
- **2026-07-20**: **fhem UND deCONZ sind beide im Einsatz** → beide Gateways bleiben
  unterstützt; E2E-Tests künftig mit beiden Simulatoren (deCONZ-Simulator wird ergänzt).
- **2026-07-20**: **Nutzungsprofil Portal**: Im Wesentlichen loggen sich nur
  Verwalter/Admins ins Portal ein; normale Nutzer verwenden ausschließlich die Terminals.
  → Beim Portal-Neubau liegt der Fokus auf den Admin-Ansichten; der
  Nutzer-Selbstbedienungsbereich (Login, eigene Einstellungen, Passwort ändern) bleibt
  funktional, hat aber niedrigere Parity-Priorität.
- **2026-07-20**: **Gestaltungsrahmen Portal-Neubau**: Die **Struktur** (Aufbau der
  Ansichten, Arbeitsabläufe) bleibt im Wesentlichen erhalten – auch Admins sind Nutzer,
  die sich nicht umstellen sollen. **UX-Verbesserungen sind aber ausdrücklich erwünscht**
  (Bedienung nutzerfreundlicher machen, umständliche Abläufe vereinfachen), solange die
  gewohnte Struktur wiedererkennbar bleibt.
- **2026-07-20**: **Betriebsmodell Backend**: Betrieb als **Docker-Compose-Stack oder
  Kubernetes**; neben Compose ist ein **Helm Chart vorzubereiten**.
- **2026-07-20**: **Neue Phase 6 „Produktivumschaltung“ ergänzt**: Der eigentliche Umbau des
  bestehenden Produktiv-Setups (nicht nur eine neue Umgebung danebenstellen) ist ein
  eigener Schritt nach Phase 5. Terminals brauchen ein eigenes Upgrade-Skript (optional mit
  Auto-Update + Rollback), weil sie unbeaufsichtigt im Feld stehen. Portal/Backend brauchen
  **kein** eigenes Upgrade-/Rollback-Skript, weil das Docker/Kubernetes/Helm ohnehin selbst
  übernehmen – dort reichen automatisierte Smoke-Tests nach jedem Deployment.
- **2026-07-20 (AP3, Auth)**: **SHA1→Argon2id-Re-Hash beim Login ist implementiert, aber
  hinter dem Konfig-Flag `elwasys.auth.rehash-on-login` (Default: **AUS**) versteckt.**
  Begründung: solange das Alt-Portal parallel läuft (Leitplanke „Backend anfangs nur
  lesend/additiv; Schreibpfade erst umstellen, wenn der Alt-Pfad abgeschaltet wird“, siehe
  Rahmenbedingungen), verifiziert und **schreibt** `Portal/.../SessionManager#login` bzw.
  `common.User#checkPassword`/`#changePassword` weiterhin SHA1-Hashes direkt in dieselbe
  `users.password`-Spalte, per reinem String-Vergleich
  (`this.password.equals(Utilities.sha1(password))`). Würde das neue Backend beim Login
  automatisch auf Argon2id re-hashen, würde die Spalte für denselben Nutzer auf ein Format
  umgestellt, das der Alt-Code nicht mehr versteht - der Nutzer wäre im Alt-Portal
  ausgesperrt, obwohl er sich dort weiterhin einloggen können muss (harte
  Rahmenbedingung: Nutzer dürfen sich nicht umstellen müssen). Der komplette
  Migrationspfad (Format-Erkennung, SHA1-Verifikation, transaktionaler Re-Hash) ist
  fertig implementiert und durch Integrationstests bewiesen (siehe
  `ElwasysAuthenticationProviderRehashEnabledTest`, die das Flag gezielt für sich selbst
  einschaltet und dabei explizit nachweist, dass der Alt-Portal-SHA1-Vergleich nach der
  Migration fehlschlagen würde) - es fehlt nur das Aktivieren des Flags, das planmäßig
  erst beim Portal-Cutover (Phase 3, wenn das Alt-Portal abgeschaltet wird) passiert.
- **2026-07-20 (AP3, Auth)**: **Bestandsspalte `users.password` (`VARCHAR(50)`) war zu
  klein für Argon2id und wurde per additiver Flyway-Migration
  (`V2__widen_users_password_column.sql`) auf `VARCHAR(255)` erweitert.** Befund: Argon2id-
  Strings mit Spring Securitys empfohlenen Parametern
  (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`:
  `$argon2id$v=19$m=16384,t=2,p=1$<22-Zeichen-Salt>$<43-Zeichen-Hash>`) sind empirisch
  gemessen konstant **97 Zeichen** lang - mehr als das Doppelte der bisherigen
  Spaltenbreite von 50 Zeichen (die exakt für die bisherigen 40-Zeichen-SHA1-Hex-Hashes
  ausgelegt war). Ein `ALTER TABLE ... TYPE VARCHAR(255)` ist additiv/abwärtskompatibel:
  der Alt-Code liest/schreibt die Spalte nur als String ohne eigene Längenprüfung, ein
  40-Zeichen-SHA1-Hash passt weiterhin klaglos hinein - der Parallelbetrieb bleibt
  unangetastet. Das ist zugleich ein Beispiel für die in AP1 getroffene Festlegung
  „künftige Schemaänderungen laufen ausschließlich über weitere Flyway-Migrationen (V2,
  V3, …)“: `V2` wird beim ersten Flyway-Lauf gegen eine Bestands-DB automatisch nach dem
  `baselineOnMigrate`-Baseline-Schritt mit angewendet (siehe den Kommentar-Zusatz in
  `backend/verify-schema-baseline.sh`, der diese neue, erwartete Divergenz zum reinen
  Alt-Weg-Schema dokumentiert - das AP1-Skript vergleicht Alt-Weg-Schema gegen den vollen
  Flyway-Migrationsstand und würde ab jetzt einen erwarteten Diff melden, siehe dortiger
  Kommentar).
- **2026-07-20 (AP3, Auth)**: **Gesperrte Benutzer werden beim NEUEN Backend-Login aktiv
  abgewiesen - bewusste Abweichung vom Alt-Portal-Verhalten, nicht dessen 1:1-Nachbildung.**
  Beobachtung beim Nachlesen des Alt-Codes: `Portal/.../SessionManager#login` prüft
  `user.isBlocked()` NICHT (`WashportalManager.instance.getDataManager().getUsers()`
  filtert nur `WHERE deleted=FALSE`) - ein gesperrter Nutzer konnte sich im Alt-Portal also
  weiterhin per Passwort einloggen; `blocked` wirkt im Bestand ausschließlich beim
  Terminal-Kartenlogin (`Client-Raspi/.../MainFormController#onCardDetected`) und in den
  Ausführungs-/Standort-Berechtigungen (`PermissionService`, AP2). Der Auftrag für AP3
  verlangt jedoch explizit „gesperrte/deaktivierte Nutzer werden abgewiesen“ für das neue
  Portal-Login-Fundament - `ElwasysAuthenticationProvider` setzt das um. Das ist damit eine
  bewusste, hier dokumentierte Verhaltensverschärfung des künftigen Admin-Portals
  gegenüber dem exakten Alt-Verhalten (nicht „Verhalten bewahren“ im engeren Sinn), deren
  Auswirkung aber gering ist: das Nutzungsprofil des Portals ist laut Auftraggeber ohnehin
  „im Wesentlichen nur Admins“ (siehe Entscheidung oben), und ein gesperrter Admin ist ein
  seltener/erwarteter Ausnahmefall. Gelöschte Nutzer (`deleted=true`) bleiben dagegen 1:1
  wie im Alt-Code ausgeschlossen (der Alt-Code lädt sie über die
  `WHERE deleted=FALSE`-Klausel gar nicht erst).

- **2026-07-20 (AP4, Terminal-API)**: **API-Auth-Header: `Authorization: Bearer <token>`**
  (Standard-HTTP-Mechanismus) statt eines proprietären Headers (z. B.
  `X-Elwasys-Terminal-Token`). Begründung: nativ von HTTP-Clients/-Bibliotheken unterstützt
  (inkl. `java.net.http`, das der Client laut Technologie-Entscheidung „HTTP im Terminal“
  ohnehin nutzen soll), funktioniert unverändert für den WebSocket-Handshake (ein
  Java-WebSocket-Client kann beim Handshake beliebige Header setzen), kein Mehrwert durch
  einen eigenen Header.
- **2026-07-20 (AP4, Terminal-API)**: **Standort-Token-Speicherung: nur SHA-256-Hash, nie
  Klartext; Rotation über mehrere gleichzeitig aktive Tokens pro Standort.** Ein einfacher
  Hash (kein Argon2/bcrypt wie bei Benutzerpasswörtern) genügt bewusst, weil das Token selbst
  schon ein hochentropisches Zufallsgeheimnis ist (32 Byte `SecureRandom`), keine
  Wörterbuchangriffs-Zielscheibe wie ein von Menschen gewähltes Passwort. Mehrere aktive
  Tokens pro Standort ermöglichen Rotation ohne Ausfallfenster (neues Token anlegen, Terminal
  umstellen, altes per `revoked_at` widerrufen) statt eines einzelnen, blind überschriebenen
  Tokens. Verwaltungspfad in Phase 2 bewusst minimal (kein Admin-UI, das kommt mit dem
  Portal-Neubau in Phase 3): `TerminalTokenCliRunner` unter dem Profil `token-cli`, siehe
  kb/04-build-and-run.md.
- **2026-07-20 (AP4, Terminal-API)**: **Standort-Scope wird als `404` durchgesetzt, nicht als
  `403`.** Ein Gerät/eine Ausführung eines anderen Standorts wird von der API wie ein
  unbekanntes Objekt behandelt (`DeviceNotFoundException`/`ExecutionNotFoundException`) statt
  mit einer expliziten Zugriffsverweigerung - damit verrät die API keine Existenz von
  Objekten an fremden Standorten. Entspricht fachlich dem Client-E2E-Fall C16
  (standortfremdes Gerät erscheint schlicht nicht in der Liste).
- **2026-07-20 (AP4, Terminal-API)**: **`GET /api/v1/users/{id}/credit` ist bewusst NICHT auf
  den Standort des Terminal-Tokens beschränkt** (anders als Geräte/Executions) - Guthaben ist
  eine personenbezogene, standortunabhängige Größe, genau wie im Alt-Code
  (`User#getCredit()`), der ebenfalls keine Standortbindung kennt.
- **2026-07-20 (AP4, WebSocket)**: **Nachrichtenformat JSON mit explizitem Typ- und
  Versionsfeld** (`TerminalWsMessage{v, type, id, payload}`), damit das Protokoll künftig
  erweitert werden kann, ohne bestehende Clients zu brechen. Phase 2 implementiert nur das
  Fundament (HELLO/HELLO_ACK, PING/PONG-Heartbeat, STATUS_REQUEST/STATUS_RESPONSE als
  Gerüst); Log-/Restart-Nachrichten (fachliche Referenz `Common.maintenance.GetLogRequest`/
  `RestartAppRequest`) sind als reservierte Typen angelegt, ihre inhaltliche Portierung folgt
  in Phase 3/4 mit der vollen Fernwartungs-Ablösung.
- **2026-07-20 (AP4, WebSocket)**: **Der WebSocket-Endpunkt liegt unter `/api/v1/**`**
  (`/api/v1/terminal-ws`), damit derselbe Standort-Token-Sicherheitsfilter wie bei den
  REST-Endpunkten greift - der Handshake ist zunächst eine normale HTTP-Anfrage und
  durchläuft dieselbe Sicherheitskette, kein separater Auth-Mechanismus für WebSockets nötig.

- **2026-07-20 (AP5, Benachrichtigungsdienst)**: **`elwasys.notifications.enabled`
  (Default: AUS), analog zu `elwasys.auth.rehash-on-login` (AP3) - kritisch, kein
  Doppelversand.** Solange Client-Raspi im Parallelbetrieb (Phase 2-4) weiterhin selbst
  E-Mails/Pushover-Nachrichten verschickt (`ExecutionFinisher` im Alt-Code bleibt
  unverändert lauffähig), darf das neue Backend nicht zusätzlich versenden. Der Dienst
  (`NotificationService`) ist vollständig implementiert und getestet, wird aber von
  KEINEM produktiven Ablauf aufgerufen - die Verdrahtung mit echten Ereignissen (Terminal
  meldet „Programm beendet"/„abgebrochen" über die API, dabei wird der Alt-Versand
  abgeschaltet) kommt in Phase 4.
- **2026-07-20 (AP5, Benachrichtigungsdienst)**: **Scope „SMTP + Pushover" bewusst NICHT
  auf den dritten Alt-Kanal (elwaApp/Ionic-Push) und die Portal-Passwort-E-Mails
  ausgeweitet.** Vollständiges Alt-Inventar in kb/03-modules.md. Der elwaApp-Kanal
  (`https://api.ionic.io/push/notifications` in `ExecutionFinisher`) hängt an der laut
  Auftraggeber nicht mehr relevanten mobilen App (`elwaapi`), deren Reste in Phase 5
  entfernt werden - eine Portierung wäre Arbeit an einem bereits zum Abbau vorgesehenen
  Feature. Die beiden Portal-E-Mail-Trigger (Passwort vergessen/Admin setzt neues
  Passwort, `PasswordForgotWindow`/`UserWindow`) hängen am neuen Portal-Login-Flow
  (Reset-Key-Generierung/-URL), den es vor Phase 3 (Portal-Neubau) nicht gibt - die
  Roadmap ordnet „Passwort ändern/zurücksetzen (E-Mail-Flow)" bereits dort ein. Beide
  Aussparungen sind in kb/03-modules.md tabellarisch mit Alt-Fundstelle dokumentiert,
  damit sie in der jeweiligen Folgephase nicht vergessen werden.
- **2026-07-20 (AP5, Benachrichtigungsdienst)**: **Wichtiger Fallstrick im Alt-Code
  aufgedeckt und im Code/Test dokumentiert**: die Spalte `users.push_notification`
  (`UserEntity#isPushNotification()`) ist NICHT das Pushover-Opt-in, sondern das Opt-in
  für den nicht portierten elwaApp/Ionic-Kanal (`User#isPushEnabled()` im Alt-Code liest
  denselben Wert). Das tatsächliche Pushover-Opt-in ergibt sich im Alt-Code
  ausschließlich daraus, ob `pushover_user_key` gesetzt/nicht-leer ist. Wer diese beiden
  Spalten verwechselt, würde Pushover-Nachrichten fälschlich an- oder abschalten -
  `NotificationServicePushoverTest#pushNotificationOptInColumnDoesNotGatePushover` ist
  ein dedizierter Regressionstest dafür.
- **2026-07-20 (AP5, Benachrichtigungsdienst)**: **spring-boot-starter-mail/JavaMailSender
  statt commons-email (Alt-Code) - SMTP-Konfiguration über Standard-`spring.mail.*`
  statt einer eigenen Konfigurationsklasse.** Bringt die JavaMailSender-Autokonfiguration
  kostenlos mit (inkl. TLS/Auth-Unterstützung); Mapping zu den Alt-`ConfigurationManager`-
  Feldern in `application.yml` dokumentiert. Einzige Ausnahme: die Absenderadresse
  (`smtp.senderAddress` im Alt-Code) hat kein Standard-Spring-Äquivalent und liegt daher
  unter der eigenen `NotificationsProperties`. Nebenwirkung entdeckt und behoben: der
  Actuator registriert bei `spring-boot-starter-mail` auf dem Klassenpfad automatisch
  einen Mail-Health-Indikator, der den Health-Endpoint ohne konfigurierten SMTP-Server auf
  `DOWN` zieht (`BackendApplicationTest`/`SecurityConfigTest` schlugen deshalb zunächst
  fehl) - `management.health.mail.enabled: false` deaktiviert ihn, da er ohne
  scharfgeschalteten Dienst kein aussagekräftiges Signal wäre.
- **2026-07-20 (AP5, Benachrichtigungsdienst)**: **Pushover-Client selbst geschrieben
  (`java.net.http`) statt der Alt-Bibliothek `com.github.sps.pushover.net:pushover-client`
  (unmaintained, letztes Release 2015, transitiv auf altem Apache HttpClient 4.x) - analog
  zur Technologie-Entscheidung „HTTP im Terminal: java.net.http". Die exakte Formular-Form
  (Feldnamen/-werte/-Reihenfolge) wurde durch Disassemblieren (`javap -c`) der Alt-
  Bibliotheksklasse `PushoverRestClient#pushMessage` hergeleitet, nicht geraten - siehe
  `PushoverClient`-Javadoc für die vollständige Herleitung. Die im Alt-Aufruf fest
  verdrahteten Werte `url="http://waschportal.hilaren.de"` und `url_title="Waschportal"`
  wurden unverändert (nicht konfigurierbar) übernommen, auch wenn die URL laut
  Domainnamen technisch eine Bestandsvorgänger-Installation zu sein scheint - reine
  Parität, kann bei Bedarf beim Scharfschalten in Phase 4 überdacht werden. Der
  API-Token ist - anders als im Alt-Code, wo er hartkodiert im Quelltext steht
  (`WashguardConfiguration#getPushoverApiToken`) - bewusst konfigurierbar gemacht
  (`elwasys.notifications.pushover.api-token`, Default leer statt eines Secrets im Code).
- **2026-07-20 (AP5, Benachrichtigungsdienst)**: **Alt-vs-Neu-Paritätsnachweis für
  E-Mail per Quellcode-Zitat statt Aufruf der echten Alt-Routine** (anders als die
  SHA1-Parität in AP3). Begründung: `Utilities#sendEmail(String, String, User)` verlangt
  ein `org.kabieror.elwasys.common.User` mit gesetzter `email`; der einzige DB-lose
  Konstruktor (`User.getTestUser(String)`) liefert eine leere E-Mail-Adresse und
  `emailNotification=false`, ein vollständiges `User` lässt sich ohne eine echte
  `DataManager`/DB-Anbindung nicht bauen. `NotificationServiceEmailTest` zitiert daher die
  Betreff-/Body-Zusammensetzung wörtlich aus `ExecutionFinisher#executeAction()` (im Test
  als Kommentar neben der Assertion) und prüft gegen einen echten lokalen Test-SMTP
  (GreenMail) byte-genau. Für Pushover ist keine entsprechende Einschränkung nötig, da
  `PushoverClient` unabhängig vom Alt-`User`-Typ ist - dort verifiziert ein eingebetteter
  JDK-`HttpServer`-Mock die aus dem disassemblierten Alt-Bytecode hergeleitete Form.

- **2026-07-20 (AP6, Deployment)**: **Docker-Build-Kontext ist die Repo-Wurzel, nicht
  `backend/`.** Begründung: das Backend-Modul hängt am Aggregator-Parent-POM und wird über
  den Root-Reactor gebaut (derselbe Fallstrick wie in kb/04-build-and-run.md dokumentiert:
  ein isolierter `backend/`-Kontext hätte weder die Parent-POM noch `Common` zur Verfügung).
  `backend/Dockerfile` kopiert daher gezielt nur, was wirklich gebraucht wird: alle vier
  Modul-`pom.xml`-Dateien (Maven muss beim Parsen des Reactors jedes `<module>` lesen können,
  auch wenn `-pl`/`-am` die tatsächliche Ausführung auf `Common`+`backend` beschränkt), aber
  NUR `Common/src` und `backend/src` als Quellcode - `Client-Raspi/src`/`Portal/src` werden
  für den Backend-Image-Build nicht gebraucht (durch einen lokalen Testaufbau verifiziert:
  ein Reactor-Build mit denselben zwei mvn-Befehlen läuft fehlerfrei durch, wenn `Client-
  Raspi/src`/`Portal/src` komplett fehlen, siehe Abschlussbericht dieses Arbeitspakets).
  Zwei mvn-Aufrufe exakt wie in kb/04-build-and-run.md dokumentiert (`install -pl Common -am
  -DskipTests`, danach `package -pl backend -DskipTests`) - keine neue Build-Variante.
- **2026-07-20 (AP6, Deployment)**: **docker-compose liefert für Neuinstallationen eine
  eigene, leere PostgreSQL-16-Instanz aus - OHNE `database-init.sql` zu mounten.** Ein
  mitgeliefertes `database-init.sql` als Postgres-`docker-entrypoint-initdb.d`-Skript hätte
  sich mit dem bereits über `POSTGRES_DB`/`POSTGRES_USER` angelegten Datenbanknamen
  gebissen (`CREATE DATABASE elwasys` schlägt fehl, wenn die DB schon existiert) und wäre
  ohnehin eine doppelte Schemaverwaltung neben Flyway gewesen. Stattdessen übernimmt die
  Flyway-Baseline-Migration (`V1__baseline_schema_0_4_0.sql`, siehe AP1) beim allerersten
  Start des Backend-Containers gegen die leere DB die komplette Schemaanlage - exakt der
  bereits getestete "leere DB"-Pfad aus kb/02-data-model.md/kb/04-build-and-run.md, kein
  neuer Codepfad. Für die Variante "Anbindung an eine Bestands-DB" wird der mitgelieferte
  `postgres`-Service stattdessen einfach nicht gestartet und `ELWASYS_DB_URL/_USER/_PASSWORD`
  zeigen auf die externe Instanz - `baselineOnMigrate` übernimmt sie unverändert (siehe
  `.env.example`-Kommentar in `deploy/compose/`).
- **2026-07-20 (AP6, Deployment)**: **TLS-Konzept Compose: optionales Overlay
  (`docker-compose.proxy.yml`) mit Caddy statt eines fest eingebauten Reverse Proxys.** Das
  Basis-Compose-File terminiert bewusst kein TLS selbst (reiner HTTP-Zugriff auf Port 8080) -
  passend für lokale Entwicklung/Tests oder den Betrieb hinter einem bereits vorhandenen
  externen Reverse Proxy/Load Balancer. Wer TLS direkt von Compose aus haben will, aktiviert
  das Caddy-Overlay (`docker compose -f docker-compose.yml -f docker-compose.proxy.yml up`) -
  Caddy übernimmt automatisches Let's-Encrypt-TLS ohne manuelle Zertifikatsverwaltung, siehe
  Kopf-Kommentar der Datei. Bewusst schlank (ein Service, eine Caddyfile) statt eines
  vollständigen Ingress-artigen Gateways - das Konzept "TLS-Terminierung vor einem reinen
  HTTP-Backend" ist der Punkt, nicht die Werkzeugwahl (dieselbe Rolle übernimmt in Kubernetes
  der Ingress-Controller, siehe Helm-Chart-Entscheidung unten). Technischer Fallstrick dabei
  gefunden: Compose führt Listen wie `ports` standardmäßig zusammen (nicht ersetzt) - ein
  einfaches `ports: []` im Overlay hätte die 8080-Veröffentlichung aus dem Basis-File NICHT
  aufgehoben (mit `docker compose ... config` nachvollzogen); der `!reset`-Merge-Tag der
  Compose-Spezifikation behebt das (verifiziert, siehe Validierung im Abschlussbericht).
- **2026-07-20 (AP6, Deployment)**: **Helm-Chart liefert bewusst KEIN PostgreSQL-Sub-Chart
  mit (weder fest verdrahtet noch als optionale Bitnami-Abhängigkeit) - externe/bereits
  vorhandene Datenbank ist der dokumentierte Regelfall, ein separates `helm install` des
  Bitnami-Postgres-Charts daneben die empfohlene Neuinstallations-Variante.** Begründung
  (ausführlich in `deploy/helm/elwasys-backend/values.yaml` unter `database:`
  dokumentiert): (1) das Backend ist laut Zielarchitektur der EINZIGE DB-Client - eine im
  selben Helm-Release an den Pod-Lifecycle gekoppelte Postgres-Instanz würfe die Owner-
  ship-/Lifecycle-Frage, die die Migration gerade auflöst, auf Kubernetes-Ebene erneut auf;
  (2) Bestandsinstallationen haben bereits eine laufende Postgres-DB mit echten
  Nutzerdaten - "externe DB" ist der Produktivfall, nicht die Ausnahme; (3) eine optionale
  `condition:`-gesteuerte Bitnami-Sub-Chart-Abhängigkeit hätte in dieser Sandbox-Umgebung
  nicht gegen echtes Helm verifiziert werden können (kein Netzwerkzugriff auf
  Chart-Repositories, siehe Validierungsergebnisse im Abschlussbericht) und hätte eine
  ungeprüfte Versionsabhängigkeit auf eine dritte Chart-Version eingeführt - unbegründetes
  Risiko für ein Nice-to-have, das dem Auftrag „keine Overkill-Featureitis" widerspräche.
  ConfigMap/Secret sind getrennt (DB-Passwort, SMTP-Passwort, Pushover-Token im Secret;
  Rest in der ConfigMap), `database.existingSecret` erlaubt das Referenzieren eines extern
  verwalteten Secrets statt eines vom Chart selbst erzeugten.
- **2026-07-20 (AP6, Deployment)**: **TLS-Konzept Kubernetes: Ingress optional (Default
  AUS), mit cert-manager-Annotation als empfohlenem, aber nicht erzwungenem Weg.** Der Chart
  geht von einem bereits im Cluster vorhandenen Ingress-Controller aus (nicht Teil dieses
  Charts) und dokumentiert zwei Wege zum TLS-Zertifikat (siehe `values.yaml`-Kommentar bei
  `ingress:`): automatisch über `cert-manager.io/cluster-issuer`-Annotation + einen bereits
  vorhandenen `ClusterIssuer`, oder ein selbst verwaltetes/extern angelegtes TLS-Secret mit
  demselben `tls[].secretName` (kein Unterschied für den Ingress-Controller). Liveness/
  Readiness zeigen bewusst auf den einfachen, immer öffentlich erreichbaren
  `/actuator/health`-Endpoint (nicht die Kubernetes-spezifischen Unterpfade
  `/actuator/health/liveness`/`-readiness`, die Spring Boot nur bei erkannter
  Kubernetes-Umgebung automatisch freischaltet) - vermeidet jede Annahme über dieses
  Auto-Erkennungsverhalten, ohne `application.yml` anzufassen.
- **2026-07-20 (AP6, Deployment)**: **CI baut das Image nur (kein Push); Release-Workflow
  (`maven-publish.yml`) veröffentlicht es zusätzlich nach GHCR mit dem eingebauten
  `GITHUB_TOKEN` (kein zusätzliches Secret nötig - `packages: write` war bereits gesetzt).**
  Andere Registries (Docker Hub o.ä.) würden ein zusätzliches, hier bewusst nicht angelegtes
  Secret brauchen - das bleibt ein offener Punkt für eine spätere Phase, falls gewünscht
  (siehe Abschlussbericht/offene Punkte).

- **2026-07-20 (nach Phase-2-Abschluss)**: **Offline-Buchungen am Terminal gewünscht**:
  Bei nicht erreichbarem Backend sollen Terminals für eine definierte Zeitspanne
  eigenständig Buchungen akzeptieren und später übermitteln. Konzeptskizze siehe
  Abschnitt „Offline-Buchungen am Terminal“; Detailausarbeitung + Umsetzung in Phase 4
  (Roadmap-Punkt „Robustheit“ entsprechend erweitert).

## Offene Fragen / mit Auftraggeber klären
- **Vaadin-Lizenzmodell** (seit Phase 3 AP1, 2026-07-20, siehe Risikotabelle oben für die
  technische Herleitung): Vaadin 24 gilt inzwischen als „Extended Maintenance" und verlangt im
  Dev-Modus einen Online-Lizenzcheck. Wie soll das für Entwicklung/Betrieb gehandhabt werden –
  Extended-Maintenance-Subscription kaufen, Offline-Entwicklungsschlüssel besorgen, oder
  Verifikation ausschließlich über Produktionsmodus-Builds/eine Umgebung mit Internetzugang zu
  vaadin.com? Beeinflusst, wie AP2/AP3 in dieser Sandbox-Umgebung getestet werden können.

## Änderungslog
| Datum | Änderung |
|-------|----------|
| 2026-07-19 | Erstfassung des Plans erstellt |
| 2026-07-20 | **Phase 0 abgeschlossen** (Build + UI/E2E-Sicherheitsnetz steht: Client 21, Portal 18, Cross-Component grün); PR-CI (vorgezogen) grün |
| 2026-07-20 | **Plan überarbeitet zur Zielarchitektur-Fassung**: Rahmenbedingungen des Auftraggebers aufgenommen (Java-Backend, Postgres, Raspi-Terminals fix; Nutzerverhalten unverändert); vollständige Komponenten-Inventur mit Entscheidung je Komponente; Zielarchitektur „zentrales Spring-Boot-Backend, Portal integriert, Terminal über API“; Roadmap neu geschnitten (Phasen 1–5) |
| 2026-07-20 | **Entscheidungen eingearbeitet**: Vaadin Flow bestätigt; `ui/small` bleibt (Display im Einsatz); App-Reste (`elwaapi`) werden entfernt; fhem-Frage präzisiert (inkl. Abhängigkeit der Testharness vom fhem-Simulator) |
| 2026-07-20 | **Restentscheidungen eingearbeitet**: fhem UND deCONZ bleiben beide unterstützt (E2E künftig mit beiden Simulatoren, deCONZ-Sim in Phase 4); App-Entfernung bestätigt; Nutzungsprofil Portal dokumentiert (nur Admins → Admin-Views priorisiert). Einzige offene Frage: Betriebsmodell Backend |
| 2026-07-20 | **Letzte Grundsatzfragen entschieden**: Portal-Struktur bleibt erhalten, UX-Verbesserungen erwünscht; Betrieb als Docker-Compose-Stack oder Kubernetes (Helm Chart vorbereiten). Keine offenen Grundsatzfragen mehr – Phase 1 kann starten |
| 2026-07-20 | **Phase 1: Testframeworks vereinheitlicht** – `InactivitySchedulerTest` (einzige TestNG-Klasse) nach JUnit 5 migriert und von `src/main` nach `src/test` verschoben (lief zuvor gar nicht unter Surefire); TestNG- und ungenutzte JUnit-4-Dependency aus Client-Raspi/pom.xml entfernt |
| 2026-07-20 | **Phase 1 (Build/Backend-Teil)**: Aggregator-Parent-POM (`/pom.xml`) angelegt (gemeinsame Version `0.0.0-local-development`, `dependencyManagement` für postgresql/logback/slf4j-api/commons-email, `maven.compiler.release=21`-Default); Common/Client-Raspi/Portal erben jetzt davon (groupId/version nicht mehr redundant, common-Dependency via `${project.version}`). Common auf Java 21 gehoben (reine POJO/JDBC-Bibliothek, keine Quelländerungen nötig); Portal friert Sprachlevel weiterhin explizit auf 1.8 ein (Vaadin 7/GWT 2.7). Wichtiger Build-Fallstrick gefunden und behoben: `mvn -f Common/pom.xml install` installiert die Parent-POM NICHT mit ins lokale Repo, daher überall auf `mvn -f pom.xml install -pl Common -am` umgestellt (CI-Common-Job, `run-ui-tests.sh`, `run-client-e2e.sh`, `run-cross-component-e2e.sh`, `Portal/e2e/scripts/start-portal.sh`, SessionStart-Hook). Release-Workflow (`maven-publish.yml`) auf `mvn versions:set` umgestellt statt sed-Hack über mehrere POMs (APP_VERSION in Utilities.java bleibt eine Java-Konstante, weiterhin per sed gesetzt, aber `-iE`-Tippfehler behoben, der eine stille Backup-Datei erzeugte). Alle drei Module bauen grün (`mvn package`/`install`) |
| 2026-07-20 | **Phase 1 (Client-Raspi Java 21 + ElwaManager-DI)**: Client-Raspi-Sprachlevel 16 → 21 gehoben (keine Quelländerungen nötig); minimaler DI-Seam für `ElwaManager` eingeführt (package-private Test-Konstruktor `MainFormController(boolean wireToElwaManager)`, der das Verdrahten mit `ElwaManager.instance`/`InactivityScheduler` in Tests überspringt, Produktionsverhalten unverändert); 12 neue isolierte JUnit-5-Charakterisierungstests für `MainFormStateManager` (kein TestFX/Xvfb/DB nötig). Volle Client-Suite grün (~~33/33~~ **37/37** – die 33 zählte die 4 migrierten `InactivityScheduler`-Tests nicht mit, siehe QA-Review-Eintrag unten) |
| 2026-07-20 | **Phase 1 QA-Review** (dieser Durchgang): Diff-Review aller Commits gegen CLAUDE.md/Roadmap; DI-Seam in `MainFormController` verifiziert (Produktionspfad `this(true)` unverändert, Verhalten 1:1 wie vorher); 12 neue State-Machine-Tests stichprobenartig gelesen – isoliert, kein `ElwaManager.instance`, kein DB-/Netzwerkzugriff; Portal-Bytecode nach dem Build tatsächlich als Class-Major-Version 52 (Java 8) verifiziert (nicht vom Parent-Default 21 überschrieben). Volle Testsuiten grün: Client 37/37 (`run-ui-tests.sh`), Cross-Component 3/3, Portal-E2E 18/18 (Playwright), `mvn package` für Client und Portal grün. **Zwei echte Regressionen gefunden und behoben** (Details oben unter Phase-1-Roadmap "QA-Review-Fix" sowie in 04-build-and-run.md): (1) CI/Release-Workflows setzten noch JDK 17 auf, obwohl Common/Client-Raspi jetzt Sprachlevel 21 verlangen – auf JDK 21 angehoben; (2) `setup.sh` installierte auf neu provisionierten Raspi-Terminals noch ein Java-17-JRE, das das jetzt mit Sprachlevel 21 gebaute Client-fat-jar nicht mehr ausführen könnte – auf `bellsoft-java21-runtime-full` angehoben (Restrisiko für bereits im Feld befindliche, nicht neu provisionierte Geräte bleibt in der Risikotabelle dokumentiert). Zusätzlich zwei Dokumentationskorrekturen: Test-Anzahl 33/33 → 37/37 in kb/05 und kb/README.md; `CLAUDE.md`-Abschnitt „Aktueller Stand" von „Phase 0 abgeschlossen/Phase 1 nächster Schritt" auf „Phase 1 abgeschlossen" aktualisiert. Phase 1 wird hiermit formal als abgeschlossen markiert. |
| 2026-07-20 | **Neue Roadmap-Phase 6 „Produktivumschaltung” ergänzt** (auf Auftraggeber-Wunsch): eigener Schritt nach Phase 5 für den tatsächlichen Umbau des bestehenden Produktiv-Setups (nicht nur eine neue Umgebung danebenstellen) – Migrationsskripte für den Bestand, Terminal-Neuaufsetzung (löst das Java-17-Restrisiko aus Phase 1 endgültig auf), ein Upgrade-Skript für Terminals sowie optional Auto-Update mit Rollback (Terminals stehen unbeaufsichtigt im Feld). Für Portal/Backend explizit **kein** eigenes Upgrade-/Rollback-Skript vorgesehen, da Docker-Compose/Kubernetes/Helm Rollout und Rollback selbst übernehmen – dort genügen automatisierte Smoke-Tests nach jedem Deployment. Risikotabelle um zwei neue Einträge (Terminal-Auto-Update-Fehlschlag, Cutover-Datenverlust) ergänzt |
| 2026-07-20 | **Phase 2 AP1: Backend-Gerüst + Flyway-Baseline**. Neues Modul `backend/` im Root-Reactor (`org.kabieror.elwasys:backend`, erbt `elwasys-parent`, Java 21 per Default). Spring Boot **3.5.16** per BOM-Import (`spring-boot-dependencies` in `dependencyManagement`) eingebunden statt über `spring-boot-starter-parent` – ein zweiter Parent ist in Maven nicht möglich, das Modul bleibt Kind von `elwasys-parent`. **Wichtiger Fallstrick dabei gefunden**: ein BOM-Import überschreibt *nie* eine Version, die schon irgendwo in der Vererbungskette explizit gepinnt ist (das ist Absicht, damit man einzelne BOM-Versionen gezielt übersteuern kann) – `elwasys-parent`s eigene `dependencyManagement` pinnt `logback-classic`/`-core` (1.2.9) und `slf4j-api` (1.7.12) fest, beides zu alt für Spring Boot 3.5 (dessen Logging-Integration verlangt Logback ≥ 1.5 für das `Configurator`-SPI; Fehlerbild war ein `AbstractMethodError` beim Testkontext-Boot). Fix: `backend/pom.xml` deklariert explizite Overrides für `logback-classic`/`-core` (1.5.34), `slf4j-api` (2.0.18) und `postgresql` (42.7.11) – exakt die Versionen, die `spring-boot-dependencies:3.5.16` selbst managt –, **vor** dem BOM-Import in der eigenen `dependencyManagement`; Common/Client-Raspi/Portal sind unberührt, da sie diese BOM nicht importieren. Starter: web, actuator, jdbc, validation, test; dazu `flyway-core` + `flyway-database-postgresql` + `postgresql`-Treiber (JPA bewusst **nicht** – kommt erst im nächsten Arbeitspaket). `spring-boot-maven-plugin` (repackage) baut ein lauffähiges Jar (`elwasys-backend.jar`); Actuator-Health unter `/actuator/health` erreichbar; Logging läuft über das von Spring Boot mitgelieferte Logback, Level/Datenbank-Zugangsdaten über `application.yml` mit Umgebungsvariablen-Overrides (`ELWASYS_DB_URL`/`_USER`/`_PASSWORD`, Defaults passend zur lokalen Dev-Umgebung).<br><br>**Flyway-Baseline** (`backend/src/main/resources/db/migration/V1__baseline_schema_0_4_0.sql`): 1:1-Übernahme von `Common/resources/database-init.sql` (ohne die psql-only `CREATE DATABASE`/`\connect`-Zeilen, die bei einer über die JDBC-URL bereits ausgewählten Ziel-DB keinen Sinn ergeben) – **nicht** separat aus den beiden `database-upgrade/*.sql`-Skripten zusammengesetzt, weil `database-init.sql` selbst schon deren Endzustand (0.4.0: `deconz_uuid`-Spalte, aktuelle `device_program_rel`-FKs, `db.version`-Seed `0.4.0`) enthält – ein frischer Lauf von `database-init.sql` und ein frischer Lauf der Baseline sind daher per Konstruktion bereits derselbe Endzustand, siehe Verifikation unten. Einzige inhaltliche Änderung: die vier `CREATE GROUP`/`CREATE USER`-Anweisungen (Rollen `elwaclients`/`elwaclient1`/`elwaportal`/`elwaapi`) sind in einen `DO`-Block mit Existenzprüfung (`pg_roles`) gefasst, weil PostgreSQL-Rollen Cluster-weit (nicht pro Datenbank) sind – ohne Guard würde ein zweiter Lauf gegen denselben Cluster (z. B. eine zweite, neue DB neben der Bestands-DB) mit „role already exists” fehlschlagen; das Ergebnis (Rollen mit denselben Rechten) ist identisch. Der Spaltentypo `auto_end_power_threashold` bleibt bewusst erhalten (Umbenennung erst Phase 5).<br><br>**`config.db.version`-Mechanismus untersucht und stillgelegt**: Recherche in Common/Client-Raspi/Portal (`grep` über alle drei Module) zeigt, dass **kein** Java-Code den Wert von `config.db.version` je liest – `DataManager`/`ConfigurationManager` kennen das DB-`config`-Schlüssel/Wert-Paar gar nicht, es gibt keinen automatischen Upgrade-Mechanismus im Code, der Wert wurde offenbar rein informativ von Hand von den SQL-Skripten selbst gepflegt (Upgrade-Skripte wurden vom Betreiber manuell per `psql -f` ausgeführt). Verhalten bewahren heißt hier: der Seed-Wert `db.version = '0.4.0'` bleibt in der Flyway-Baseline erhalten (Alt-Code könnte ihn theoretisch lesen, auch wenn aktuell nicht der Fall), aber er wird ab sofort **nicht mehr fortgeschrieben** – zukünftige Schemaänderungen laufen ausschließlich über weitere Flyway-Migrationen (V2, V3, …), `Common/resources/database-upgrade/*.sql` wird nicht mehr gepflegt (Dateien bleiben als historisches Artefakt im Repo liegen). Details siehe kb/02-data-model.md.<br><br>**Verifiziert** (nicht nur behauptet), mit dem lokalen PostgreSQL 16 dieser Umgebung: `backend/verify-schema-baseline.sh` (neu, reproduzierbar) legt eine DB über den Alt-Weg an (`database-init.sql`), lässt eine zweite, leere DB von der gebauten Backend-Jar per Flyway migrieren, vergleicht `pg_dump --schema-only`-Dumps beider DBs (nach Herausfiltern der zufälligen `\restrict`/`\unrestrict`-Tokens von `pg_dump` ≥ 16 und Ausschluss von Flyways eigener `flyway_schema_history`-Tabelle per `pg_dump -T`) und prüft zusätzlich `baselineOnMigrate` gegen die Alt-Weg-DB (Health-Endpoint UP, `flyway_schema_history` zeigt genau eine `BASELINE`-Zeile bei Version 1, `admin`-Nutzer unverändert). Ergebnis: **schema-identisch** (keine Abweichung außer der erwarteten Flyway-Historientabelle) und Backend startet sauber gegen die Bestands-DB. Details/Kommandos im Abschlussbericht dieses Arbeitspakets.<br><br>**Tests**: JUnit 5 (`backend/src/test/.../BackendApplicationTest.java`) fährt den vollen Spring-Kontext inkl. Flyway-Migration gegen ein echtes PostgreSQL hoch und prüft Health-Endpoint + Baseline-Schema/Seed-Daten. **Testcontainers als Default** (läuft unverändert in CI, wo ein Docker-Daemon verfügbar ist) mit Override über `ELWASYS_TEST_JDBC_URL`/`_DB_USER`/`_DB_PASSWORD` (System-Property oder Env), mit dem die Tests stattdessen gegen eine lokale PostgreSQL-Instanz laufen – notwendig, weil diese Remote-Entwicklungsumgebung **keinen** Docker-Daemon hat (verifiziert: `docker ps` schlägt mit „no such file or directory” fehl). `backend/run-backend-tests.sh` (Muster: `Client-Raspi/run-ui-tests.sh`) bereitet lokal eine frische Testdatenbank vor und setzt den Override; in dieser Umgebung über dieses Skript **grün** ausgeführt (2/2 Tests). Diese Entscheidung (Testcontainers-Default + lokaler Override statt z. B. „nur lokales PG überall”) wurde getroffen, damit die CI (mit Docker) unverändert dem Spring-Boot-Standardmuster folgt, während lokale/Sandbox-Umgebungen ohne Docker trotzdem lauffähig bleiben, ohne zwei komplett getrennte Testsuiten pflegen zu müssen.<br><br>**CI**: vierter Job „Backend” in `.github/workflows/ci.yml` (ubuntu-24.04, JDK 21 Liberica wie die anderen drei Jobs) ergänzt. Läuft `mvn -f pom.xml test -pl backend` **mit** Testcontainers (kein Local-PG-Setup nötig) – anders als der Client-Job, der lokales PostgreSQL seedet, weil GitHub-Actions-Runner (anders als diese Sandbox) einen Docker-Daemon mitbringen; das ist der im Vorfeld vom Koordinator verifizierte Umgebungsunterschied. `backend` hat keine Reactor-Abhängigkeit auf `common` (kein `-am`/kein Common-Build im Job nötig).<br><br>**Build-Verifikation**: `mvn -f pom.xml install -DskipTests` (alle vier Module, inkl. `backend`) grün; zusätzlich isoliert `mvn -f pom.xml install -pl Common -am -DskipTests && mvn -f Client-Raspi/pom.xml package -DskipTests && mvn -f Portal/pom.xml package -DskipTests` grün – Common/Client-Raspi/Portal unverändert und unbeeinträchtigt (0 Quelländerungen an diesen drei Modulen in diesem Arbeitspaket). |
| 2026-07-20 | **Phase 2 AP2: JPA-Entities + Repositories, Geschäftslogik-Portierung (Abrechnung, Berechtigungen, Preisberechnung, Execution-Lebenszyklus)**. `spring-boot-starter-data-jpa` zu `backend/pom.xml` hinzugefügt (Version über das bereits importierte Spring-Boot-BOM, siehe AP1). **7 Entities** unter `backend/.../domain/` (1:1 aufs Bestandsschema, siehe kb/02-data-model.md): `UserGroupEntity`, `UserEntity`, `LocationEntity`, `DeviceEntity` (Spaltentypo `auto_end_power_threashold` bewusst erhalten), `ProgramEntity`, `ExecutionEntity`, `CreditAccountingEntryEntity`, dazu `ConfigEntity` (Vollständigkeit, ungenutzt – siehe kb/02). Die vier n:m-Tabellen (`locations_valid_user_groups`, `devices_valid_user_groups`, `programs_valid_user_groups`, `device_program_rel`) sind als `@ManyToMany`+`@JoinTable` modelliert, nicht als eigene Entity-Klassen (Standard-JPA-Praxis für reine Verknüpfungstabellen ohne Zusatzspalten). Postgres-native Enums (`DISCOUNT_TYPE`, `PROGRAM_TYPE`, `TIME_UNIT_TYPE`) werden über Hibernates `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` gebunden (löst das bekannte Problem, dass eine simple `@Enumerated(STRING)`-Bindung gegen eine Postgres-ENUM-Spalte mit „column is of type … but expression is of type character varying" fehlschlägt); die neuen Java-Enums (`DiscountType`, `ProgramType`, `TimeUnitType`) tragen bewusst dieselben Konstantennamen wie die DB-Werte (anders als der Alt-Code, der z. B. `DiscountType.Fix`/`Factor`/`None` heißt). `spring.jpa.hibernate.ddl-auto=none` explizit gesetzt (Rahmenbedingung: Schema kommt ausschließlich von Flyway).<br><br>**7 Spring-Data-Repositories** unter `backend/.../repository/`: `UserGroupRepository`, `LocationRepository`, `DeviceRepository`, `ProgramRepository`, `UserRepository` (inkl. `findByCardId` – parametrisierte Nachbildung der Alt-Code-Regex-Suche `card_ids ~ '(?n)^cardId$'` per natívem `@Query`, gleiche Matching-Semantik ohne String-Konkatenation/SQL-Injection-Risiko), `ExecutionRepository`, `CreditAccountingEntryRepository`.<br><br>**4 Services** unter `backend/.../service/`, jeweils 1:1-Portierung mit Quellenverweis: `PricingService` (aus `Common.Program#getPrice`/`#getDynamicPrice`), `CreditService` (aus `Common.User#loadCredit`/`#payExecution`/`#inpayment`/`#payout`), `PermissionService` (aus den inline UI-Prüfungen in `Client-Raspi/.../MainFormController#onCardDetected`, `DeviceListEntry#applyUserStyle`, `Common.Device#getPrograms(User)` – diese Logik steckt im Alt-Code NICHT in einer wiederverwendbaren Common-Methode, sondern direkt in den JavaFX-Controllern), `ExecutionService` (aus `Common.Execution#start/stop/reset/getPrice/isExpired` sowie den DB-Anteilen von `Common.DataManager#newExecution/getNotFinishedExecutions/getRunningExecution/getExecutions/getLastUser` und `Client-Raspi/.../ExecutionManager`/`ExecutionFinisher` – hardwarenahe Teile [Leistungsmessung, Steckdose schalten, Email/Pushover-Benachrichtigungen] bleiben bewusst im Terminal, siehe Zielarchitektur).<br><br>**Entscheidung – EAGER statt LAZY für alle fachlich genutzten Assoziationen**: Der Alt-`DataManager` lädt beim Holen eines Objekts immer sofort alle referenzierten Objekte mit (`Device#update()` lädt z. B. `programs`/`validUserGroups` unconditional, `User#load()` lädt die `UserGroup` immer synchron) – es gibt dort de facto kein „lazy" Nachladen. Diese Entities bilden das nach (`FetchType.EAGER` für `UserEntity.group`, `DeviceEntity.location/validUserGroups/programs`, `ProgramEntity.validUserGroups`, `LocationEntity.validUserGroups`, `ExecutionEntity.device/program/user`, `CreditAccountingEntryEntity.user`). Nebeneffekt: da AP2 laut Auftrag noch keine Web-/REST-Schicht einführt (die REST-API folgt erst in AP4), gibt es noch keine natürliche Transaktions-/Session-Grenze, die `LAZY` sauber absichern würde (Open-Session-in-View ist bewusst deaktiviert, siehe `application.yml`) – EAGER vermeidet `LazyInitializationException`s in den Tests UND ist die treuere Nachbildung des Alt-Verhaltens. Kann in einem späteren Arbeitspaket mit gezielten Fetch-Joins/DTO-Projektionen an der REST-Grenze verfeinert werden, wenn Performance das nahelegt.<br><br>**Entscheidung – Alt-vs-Neu-Vergleichstests umgesetzt** (stärkster Äquivalenz-Nachweis, wie im Auftrag vorgeschlagen): `common` als **test-scope**-Dependency in `backend/pom.xml` ergänzt (nur Testklassenpfad, keine Laufzeit-Abhängigkeit – das Backend hat weiterhin sein eigenes Datenmodell, siehe kb/03). `LegacyDataManagerFactory` (Test-Support) baut eine echte Alt-Code-`DataManager` gegen dieselbe Test-DB auf (Umweg über ein `ThreadLocal`, weil `ConfigurationManager`s Konstruktor seine Properties bereits synchron lädt, bevor Unterklassen-Felder gesetzt werden könnten). `PricingServiceParityTest` und `CreditServiceParityTest` lesen dieselbe committete DB-Zeile einmal über den Alt-Code (`Program#getPrice`, `User#getCredit`) und einmal über `PricingService`/`CreditService` und vergleichen **bitgenau** (Wert UND `BigDecimal`-Skala, per `toPlainString()`). Das deckte einen echten, bestätigten Nebeneffekt der `new BigDecimal(double)`-Verwendung im Alt-Code auf (siehe „Beobachtungen" unten) und bewies dessen exakte Nachbildung. Für `PermissionService` ist kein direkter Alt-Code-Vergleichstest möglich/sinnvoll: die Berechtigungsregeln stecken im Alt-Code nicht in einer aufrufbaren Common-Methode, sondern direkt in JavaFX-UI-Controllern (die ein laufendes JavaFX-Toolkit bräuchten) – hier sind es stattdessen Charakterisierungstests, deren Erwartungswerte direkt aus dem zitierten Alt-Code-Quelltext hergeleitet sind (siehe `PermissionServiceTest`-Javadoc).<br><br>**Beobachtungen** (fragwürdiges/überraschendes Alt-Verhalten, bewusst 1:1 übernommen, nicht „korrigiert"): (1) `User#loadCredit` zieht den Maximalpreis **jeder** `finished=false`-Ausführung eines Nutzers vom Guthaben ab – **unabhängig davon, ob sie überhaupt gestartet wurde** (kein `start IS NOT NULL`-Filter, anders als bei `getNotFinishedExecutions`); eine gerade erst angelegte, noch nicht gestartete Ausführung mindert das Guthaben also schon vor. (2) `Execution#reset()` setzt trotz des Methodennamens `finished=TRUE` (nicht `FALSE`) und nullt `start`/`stop` – wird im Client nur aufgerufen, wenn das Einschalten der Steckdose nach Anlegen der Ausführung fehlschlägt; die Ausführung soll dann als „erledigt/verworfen", nicht als „noch offen" gelten. (3) `User#payExecution` prüft `price.equals(BigDecimal.ZERO)` (skalasensitiv!) statt `compareTo`: ein FIXED-Programm mit einer in der DB als `0.00` (Skala 2) gepflegten Grundgebühr erzeugt trotzdem einen Buchungssatz über `0.00`, während ein durch die Freiminuten-Regel auf `BigDecimal.ZERO` (Skala 0, expliziter Literal im Code) reduzierter Preis **keinen** Buchungssatz erzeugt – durch einen Parity-Test bewiesen (`fixedProgramZeroFlagfallStillHasScaleTwoNotBigDecimalZero`). (4) Rabattberechnung nutzt `new BigDecimal(double)` statt `BigDecimal.valueOf(double)` – bei „krummen" `discount_value`s (z. B. `0.1`) entstehen dadurch sehr lange Nachkommastellen (Binärdarstellungsfehler von `double`), was der neue `PricingService` bewusst identisch nachbildet (Test `discountTypeFactorReproducesBinaryFloatingPointArtifact` beweist, dass Alt und Neu exakt denselben „unsauberen" Wert liefern). (5) `Device#getPrograms(User)` filtert NICHT zusätzlich auf `program.isEnabled()` – ein deaktiviertes, aber dem Gerät zugeordnetes und für die Gruppe freigegebenes Programm bleibt im Client wählbar; `PermissionService#getAvailablePrograms` bildet das identisch nach. (6) App-Relikt-Spalten (`app_id`/`access_key`/`auth_key` auf `users`) sind wie in den Rahmenbedingungen gefordert nicht gemappt; der DB-Trigger `user_authkey_trigger` befüllt `auth_key` unabhängig davon bei jedem INSERT automatisch, das wurde in `run-backend-tests.sh` verifiziert (keine NOT-NULL-Verletzung).<br><br>**Entscheidung – `credit_accounting.date`**: die DB pflegt hier einen `CURRENT_TIMESTAMP`-Default, den der Alt-Code nie explizit überschreibt. `CreditAccountingEntryEntity` setzt `date` stattdessen bewusst per Anwendungs-Uhr (`LocalDateTime.now()`), um die (Hibernate-versionsabhängige) Komplexität einer nachträglichen DB-generierten-Wert-Rücklesung zu vermeiden – semantisch identisch („Zeitpunkt der Buchung"), ein Unterschied entstünde nur bei nennenswertem Uhren-Versatz zwischen Anwendungs- und DB-Host (in Produktion: ein Deployment, ein Host/Cluster).<br><br>**Tests**: 27/27 grün (`backend/run-backend-tests.sh`) – 2 aus AP1 (`BackendApplicationTest`) + 25 neu: `PricingServiceParityTest` (8, inkl. Alt-vs-Neu), `CreditServiceParityTest` (2, inkl. Alt-vs-Neu), `PermissionServiceTest` (3, Charakterisierung), `ExecutionServiceTest` (9, Lebenszyklus-Charakterisierung), `UserRepositoryCardIdTest` (3, Regex-Kartennummernsuche). `run-backend-tests.sh` baut jetzt zuerst `Common` (`mvn -f pom.xml install -pl Common -am -DskipTests`), da `backend` seit diesem Arbeitspaket eine Test-Scope-Abhängigkeit auf `common` hat; der CI-Job „Backend" in `.github/workflows/ci.yml` bekam denselben zusätzlichen Schritt.<br><br>**Build-Verifikation**: `mvn -f pom.xml install -DskipTests` (alle vier Module) grün; isoliert `mvn -f Client-Raspi/pom.xml package -DskipTests` und `mvn -f Portal/pom.xml package -DskipTests` grün – 0 Quelländerungen an Common/Client-Raspi/Portal in diesem Arbeitspaket (nur `backend/`, `.github/workflows/ci.yml`, `kb/`). |
| 2026-07-20 | **Phase 2 AP3: Auth (Argon2id-Hashing + SHA1-Migrationspfad, Login-/Session-Handling)**. `spring-boot-starter-security` + `org.bouncycastle:bcprov-jdk18on:1.80` (von Spring Securitys `Argon2PasswordEncoder` als Kryptografie-Provider benötigt, bewusst nicht vom Spring-Boot-BOM verwaltet – offiziell als „optional, selbst hinzufügen" dokumentiert) zu `backend/pom.xml` ergänzt, dazu `spring-security-test` (test-scope, für `SecurityConfigTest`). Neues Package `backend/.../auth/`: `AuthProperties` (`elwasys.auth.rehash-on-login`, Default `false`), `PasswordVerificationService` (Format-Erkennung Argon2id vs. SHA1-Legacy, `verify`/`encodeNew`), `ElwasysUserPrincipal` (`UserDetails`-Implementierung ohne echten Passwort-Hash in der Session), `ElwasysAuthenticationProvider` (fachlicher Nachfolger von `Portal/.../SessionManager#login` + `common.User#checkPassword`, siehe dessen Javadoc für die 1:1 aus dem Alt-Code nachvollzogenen Regeln), `SecurityConfig` (`SecurityFilterChain`: `/actuator/health` öffentlich, alles andere `authenticated()`, Formular-Login).<br><br>**Befund – Bestandsspalte `users.password` (`VARCHAR(50)`) zu klein für Argon2id**: empirisch mit `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` gemessen (`$argon2id$v=19$m=16384,t=2,p=1$<22-Zeichen-Salt>$<43-Zeichen-Hash>`) sind die erzeugten Strings konstant **97 Zeichen** lang, mehr als das Doppelte der Spaltenbreite. Additive Flyway-Migration `V2__widen_users_password_column.sql` (`ALTER TABLE users ALTER COLUMN password TYPE VARCHAR(255)`) behoben – siehe „Entscheidungen" oben für die vollständige Abwägung (abwärtskompatibel, Alt-Code prüft die Länge nicht selbst). `backend/verify-schema-baseline.sh` um einen Kommentar ergänzt, der die dadurch neu entstehende (erwartete) Schema-Divergenz zwischen Alt-Weg- und Flyway-DB dokumentiert, ohne die Skriptlogik selbst anzufassen (außerhalb des AP3-Auftrags).<br><br>**SHA1-Altformat, exakt nachvollzogen** (`Common.Utilities#sha1`, aufgerufen von `common.User#checkPassword`/`#changePassword`): reines SHA-1 ohne Salt, Hex-kodiert (Kleinbuchstaben, 40 Zeichen) über `s.getBytes()` (Plattform-Default-Charset). `PasswordVerificationService` bildet das über `MessageDigest.getInstance("SHA-1")` + explizites UTF-8 nach (dokumentierte Annahme: auf allen betroffenen Alt-/Neu-JVMs ohnehin der Plattform-Default) und vergleicht die Bytes konstant (`MessageDigest.isEqual`) statt `String#equals` wie im Alt-Code (Auftrag: „Timing-/Sicherheitsbasics beachten"). Parity-Test (`PasswordVerificationServiceParityTest`) ruft die echte `Utilities.sha1`-Routine (aus `common`, test-scope) für mehrere Passwörter inkl. eines mit Nicht-ASCII-Zeichen auf und beweist, dass der neue Service den erzeugten Hash akzeptiert.<br><br>**Beobachtung – Alt-Portal-Login prüft `isBlocked()` nicht**: `SessionManager#login` iteriert über `getDataManager().getUsers()`, was nur `WHERE deleted=FALSE` filtert; ein gesperrter Nutzer konnte sich im Alt-Portal also weiterhin per Passwort einloggen (`blocked` wirkt im Bestand nur beim Terminal-Kartenlogin und in `PermissionService`). `ElwasysAuthenticationProvider` weist gesperrte Nutzer dagegen aktiv ab – bewusste, im Provider-Javadoc und oben unter „Entscheidungen" dokumentierte Verschärfung für das neue Portal-Fundament (Auftrag AP3 verlangt das explizit), keine 1:1-Verhaltensbewahrung.<br><br>**Re-Hash-Migrationspfad**: bei erfolgreicher SHA1-Verifikation und `elwasys.auth.rehash-on-login=true` wird der Hash transaktional (`@Transactional` auf `authenticate`) auf Argon2id migriert und per `UserRepository#save` persistiert; das Flag ist per Default `false` (Details/Begründung siehe „Entscheidungen" oben). Erfolgreiche Logins aktualisieren zusätzlich `last_login` (1:1 wie `common.User#updateLastLogin`, vom Alt-Portal nach jedem erfolgreichen Login aufgerufen). Admin-Rolle kommt 1:1 aus `users.is_admin` (`ROLE_ADMIN` + `ROLE_USER`, sonst nur `ROLE_USER`).<br><br>**`SecurityConfig`**: eine einzige `SecurityFilterChain` mit `ElwasysAuthenticationProvider` als alleiniger Authentifizierungsquelle; `/actuator/health` `permitAll()`, alles andere `authenticated()` mit Formular-Login. Bewusst so gehalten (siehe Klassen-Javadoc), dass AP4 für die Terminal-Standort-Token-Auth eine EIGENE, zustandslose `SecurityFilterChain` (eigener `securityMatcher`, z. B. `/api/v1/**`, niedrigere `@Order`-Zahl) danebenstellen kann, ohne diese Klasse zu ändern – noch keine fachlichen HTTP-Endpunkte in diesem Arbeitspaket (folgen in AP4).<br><br>**Tests**: 52/52 grün (`backend/run-backend-tests.sh`, davon 27 aus AP1/AP2 unverändert + 25 neu): `PasswordVerificationServiceTest` (5, reine Unit-Tests ohne Spring-Kontext), `PasswordVerificationServiceParityTest` (2, Alt-vs-Neu über die echte `Utilities.sha1`-Routine), `ElwasysAuthenticationProviderTest` (12, DB-Integrationstests: SHA1-Login, Hash bleibt bei Flag=aus byte-identisch SHA1, Argon2id-Login, `last_login`-Update, Groß-/Kleinschreibung, Admin-Rolle, falsches Passwort, unbekannter/gesperrter/gelöschter Nutzer, verstümmelter Hash, leere Zugangsdaten), `ElwasysAuthenticationProviderRehashEnabledTest` (2, Flag gezielt per `@TestPropertySource` eingeschaltet: Migration auf Argon2id, danach weiterhin einloggbar, UND expliziter Beweis, dass der Alt-Portal-SHA1-Vergleich gegen den migrierten Hash fehlschlagen würde), `SecurityConfigTest` (4, MockMvc/`webEnvironment=MOCK`: Actuator-Health öffentlich, alles andere verlangt Anmeldung, Formular-Login mit gültigen/ungültigen Zugangsdaten). Root-Reactor-Build (`mvn -f pom.xml install -DskipTests`, alle vier Module) sowie isolierter Client-Raspi-/Portal-Build weiterhin grün, 0 Quelländerungen an Common/Client-Raspi/Portal in diesem Arbeitspaket (nur `backend/`, `kb/`). |
| 2026-07-20 | **Phase 2 AP4: REST-API v1 + Standort-Token-Auth + WebSocket-Endpunkt für Terminals**. Neue Abhängigkeiten in `backend/pom.xml`: `spring-boot-starter-websocket`, `springdoc-openapi-starter-webmvc-ui:2.8.6` (nicht vom Spring-Boot-BOM verwaltet, fest gepinnt); Modul-Property `maven.compiler.parameters=true` (siehe Fallstrick unten).<br><br>**Standort-Token-Persistenz** (additive Migration `V3__create_terminal_tokens.sql`, siehe kb/02-data-model.md): neue Tabelle `terminal_tokens` (`location_id` → `locations`, `token_hash` VARCHAR(64) unique, `label`, `created_at`, `revoked_at`, `last_used_at`); keine Änderung an Bestandstabellen. `TerminalTokenEntity`/`TerminalTokenRepository`/`TerminalTokenService` (Package `backend/.../auth/terminal/`): Tokens werden als 32-Byte-`SecureRandom`, Base64-URL-kodiert mit Präfix `elwt_`, erzeugt; nur ihr SHA-256-Hash landet in der DB, das Klartext-Token existiert nur im Rückgabewert von `createToken` (`IssuedTerminalToken`) und wird vom `TerminalTokenCliRunner` (Profil `token-cli`, `application-token-cli.yml` setzt `spring.main.web-application-type: none`) einmalig auf `stdout` ausgegeben. Mehrere aktive Tokens pro Standort erlaubt (Rotation ohne Ausfallfenster). Vollständige Design-Begründung siehe „Entscheidungen" oben.<br><br>**Terminal-Sicherheitskette** (`backend/.../auth/terminal/TerminalApiSecurityConfig`): eigene, zustandslose `SecurityFilterChain` für `/api/v1/**` mit `@Order(1)` (höhere Priorität als AP3s Catch-all-Kette, die unverändert bleibt), `TerminalTokenAuthenticationFilter` liest `Authorization: Bearer <token>`, prüft über `TerminalTokenService`, setzt bei Erfolg eine `TerminalAuthenticationToken`/`TerminalPrincipal` (Standort-Kontext) in den `SecurityContext` - bei Fehlschlag antwortet der Filter selbst mit `401` + `ProblemDetail`.<br><br>**Zwei nicht offensichtliche Fallstricke beim Aufbau der zweiten Sicherheitskette gefunden und behoben** (Details siehe Javadoc von `TerminalApiSecurityConfig`): (1) **jede Spring-Bean vom Typ `Filter` wird von Spring Boot zusätzlich automatisch als globaler Servlet-Filter für ALLE Pfade registriert** (nicht nur innerhalb der eigenen `SecurityFilterChain`, in die sie per `addFilterBefore` eingehängt ist) - das hätte den Token-Filter auch vor `/actuator/health` und die AP3-Kette gehängt und dort die AP3-Tests mit `401` statt `200` scheitern lassen (tatsächlich beim ersten Testlauf so aufgetreten: `SecurityConfigTest.actuatorHealthIsPubliclyAccessible` schlug mit `401` fehl). Fix: eine zusätzliche `FilterRegistrationBean` mit `setEnabled(false)` unterdrückt genau diese globale Auto-Registrierung. (2) **`HttpSecurity#securityMatcher(String...)` löst über eine Spring-MVC-Erkennung auf** und baut dafür einen `MvcRequestMatcher`, der die Bean `mvcHandlerMappingIntrospector` braucht - diese existiert nur in einem vollen Web-MVC-Kontext, nicht in den `webEnvironment=NONE`-Tests dieses Moduls (`AbstractBackendIT`), obwohl `spring-boot-starter-web` auf dem Klassenpfad liegt (tatsächlich aufgetreten: **alle** `AbstractBackendIT`-Tests scheiterten beim Kontext-Aufbau mit `BeanInstantiationException`/`NoSuchBeanDefinitionException` für `mvcHandlerMappingIntrospector`, sobald die zweite Kette einen `securityMatcher("/api/v1/**")`-String-Aufruf enthielt). Fix: expliziter `new AntPathRequestMatcher("/api/v1/**")` statt der String-Overload, umgeht die MVC-Erkennung vollständig.<br><br>**REST-API v1** (Package `backend/.../api/`, siehe kb/03-modules.md für die vollständige Endpunktliste): `CardLoginController` (`POST /api/v1/card-login`, fachlicher Nachfolger von `MainFormController#onCardDetected`: unbekannte Karte → `404`, gesperrter Nutzer → `403`, Standort nicht zugelassen → `403`, sonst `200` mit `UserDto` inkl. Guthaben), `LocationController` (`GET /api/v1/locations/me`), `DeviceController` (`GET /api/v1/devices`/`/{id}`, Standort-Scope + `PermissionService`-gefilterte Programme + `usableByUser`/`occupied`-Flags), `ExecutionController` (`POST /api/v1/executions` mit voller Berechtigungs-/Guthabenprüfung vor dem Anlegen, `POST .../finish`/`.../abort`/`.../reset`, `GET .../{id}`), `UserController` (`GET /api/v1/users/{id}/credit`, bewusst NICHT standortgebunden - siehe „Entscheidungen"). DTOs (Java Records, `api/dto/`) statt Entity-Serialisierung an der API-Grenze - vermeidet die AP2-EAGER-Assoziationen ungefiltert/rekursiv zu serialisieren. Fehlerbilder einheitlich als RFC-7807-`ProblemDetail` über eine `ApiException`-Hierarchie (`api/exception/`, je Typ HTTP-Status + `type`-URI `urn:elwasys:<slug>`) + zentraler `@RestControllerAdvice` (`ApiExceptionHandler`, nur für `org.kabieror.elwasys.backend.api` aktiv). Standort-Scope strikt durchgesetzt über `TerminalScopeGuard` (Gerät/Ausführung eines fremden Standorts → `404`, siehe „Entscheidungen" für die Begründung - orientiert an Client-E2E-Fall C16). API-Dokumentation über springdoc-openapi (`/v3/api-docs`, `/swagger-ui.html`) - liegt bewusst NICHT unter `/api/v1/**` und damit hinter der AP3-Catch-all-Kette (Login-pflichtig), nicht hinter der Token-Kette.<br><br>**WebSocket-Endpunkt** (`/api/v1/terminal-ws`, Package `backend/.../ws/`, siehe kb/03-modules.md für das vollständige Protokoll): liegt unter `/api/v1/**`, damit derselbe Standort-Token-Filter auch den Handshake absichert (der Handshake ist zunächst eine normale HTTP-Anfrage). `TerminalHandshakeInterceptor` übernimmt den bereits authentifizierten `TerminalPrincipal` aus dem `SecurityContext` in die `WebSocketSession`-Attribute. Nachrichtenformat: JSON mit explizitem Typ- und Versionsfeld (`TerminalWsMessage{v, type, id, payload}`, `id` korreliert Anfrage/Antwort). Phase-2-Fundament: `HELLO`/`HELLO_ACK` (Verbindungsaufbau), `PING`/`PONG` (Heartbeat), `STATUS_REQUEST`/`STATUS_RESPONSE` (Gerüst - Server antwortet mit Standort-Metadaten statt echtem Terminal-Status); `LOG_REQUEST`/`LOG_RESPONSE`/`RESTART_REQUEST` sind als reservierte Typen angelegt (fachliche Referenz `Common.maintenance.GetLogRequest`/`GetLogResponse`/`RestartAppRequest`), werden aber noch mit `ERROR{reason:"not-implemented"}` beantwortet - die volle Fernwartungs-Portierung folgt in Phase 3/4. `TerminalConnectionRegistry` (in-memory, genau eine aktive Session pro Standort) ersetzt fachlich die alte `client_ip`/`client_port`-Registrierung in `locations`. `TerminalHeartbeatScheduler` (`@Scheduled(fixedRate=30s)`) pingt alle verbundenen Terminals und schließt Verbindungen ohne `PONG` innerhalb von 90s.<br><br>**Dritter Fallstrick gefunden und behoben**: Spring Boots Standard-`TaskScheduler` für `@Scheduled` nutzt NICHT-Daemon-Threads - beim manuellen Testen des `TerminalTokenCliRunner` (`--spring.profiles.active=token-cli`) hing der Prozess nach getaner Arbeit unbegrenzt (Timeout beim Testen bestätigt), weil der Heartbeat-Scheduler-Thread trotz `spring.main.web-application-type=none` am Leben blieb. Fix: `TerminalWebSocketConfig` (trägt `@EnableScheduling`) bekam `@Profile("!token-cli")` - im CLI-Profil lädt weder WebSocket noch Heartbeat, der Prozess beendet sich nach dem `ApplicationRunner` sauber (manuell nachverifiziert: Token erzeugen UND widerrufen laufen jeweils in unter 10s durch und terminieren von selbst).<br><br>**Tests**: 44 neue, alle grün (`backend/run-backend-tests.sh`, jetzt **96/96** gesamt: 52 aus AP1-AP3 unverändert + 44 neu): `TerminalTokenServiceTest` (8, Erzeugung/Prüfung/Rotation/Widerruf, Hash-statt-Klartext-Nachweis), `TerminalApiSecurityTest` (6, 401 bei fehlendem/unbekanntem/widerrufenem Token, 200 bei gültigem, Terminal-Token ohne Zugriff auf die AP3-Kette), `CardLoginControllerTest` (4), `DeviceControllerTest` (7, inkl. Standort-Scope/C16-Analogie, Programm-Filterung, `occupied`/`usableByUser`), `ExecutionControllerTest` (13, voller Lebenszyklus + alle Fehlerfälle inkl. `device-occupied`/`insufficient-credit`, orientiert an C9/C16), `TerminalWebSocketTest` (6, Handshake mit/ohne Token, HELLO/HELLO_ACK, PING/PONG, STATUS_REQUEST/STATUS_RESPONSE, unimplementierter Typ → ERROR - über den JDK-eigenen `java.net.http`-WebSocket-Client, keine zusätzliche Testabhängigkeit). Neue Test-Basisklasse `support/AbstractApiIT` (MockMvc über beide Sicherheitsketten, Fixture-Helfer).<br><br>**Vierter, rein build-technischer Fallstrick**: `@RequestParam`/`@PathVariable` ohne explizit angegebenen Namen (z.B. `@RequestParam Integer userId` statt `@RequestParam("userId") Integer userId`) werfen zur Laufzeit eine `IllegalArgumentException` ("parameter name information not available via reflection"), wenn der Compiler nicht mit `-parameters` übersetzt - der Parent-POM setzt dieses Flag nicht (wird von Common/Client-Raspi nicht gebraucht). Fix: `maven.compiler.parameters=true` als Modul-Property in `backend/pom.xml`. **Zusätzliche Falle dabei**: der Maven-Compiler-Plugin (inkrementeller Compiler) erkannte die reine Properties-Änderung zunächst NICHT als Grund für eine Neukompilierung - ein `mvn test-compile` ohne vorheriges `mvn clean` kompilierte weiterhin die alten, ohne `-parameters` gebauten Klassen (verifiziert per `javap -v`: keine `MethodParameters`-Attribute trotz gesetzter Property), die Tests schlugen weiterhin mit `500` fehl, bis ein `mvn clean` davorgeschaltet wurde. Für künftige `pom.xml`-Compiler-Konfigurationsänderungen in diesem Modul: sicherheitshalber `mvn clean` mit einplanen.<br><br>**Build-Verifikation**: `mvn -f pom.xml install -DskipTests` (alle vier Module) grün; isoliert `mvn -f Client-Raspi/pom.xml package -DskipTests` und `mvn -f Portal/pom.xml package -DskipTests` grün - 0 Quelländerungen an Common/Client-Raspi/Portal in diesem Arbeitspaket (nur `backend/`, `kb/`). Manuell verifiziert: Server startet gegen die Test-DB, `/actuator/health` liefert `200` ohne Anmeldung, `/v3/api-docs` verlangt eine Anmeldung (`302` zur Login-Seite) wie beabsichtigt; `TerminalTokenCliRunner` erzeugt und widerruft Tokens gegen eine echte laufende Instanz und terminiert danach sauber. |
| 2026-07-20 | **Phase 2 AP5: Benachrichtigungsdienst (SMTP, Pushover) im Backend**. Neues Package `backend/.../notification/`: `NotificationsProperties` (`elwasys.notifications.enabled`, Default `false` – kritisch, kein Doppelversand, siehe „Entscheidungen"), `NotificationService` (1:1-Portierung der Benachrichtigungslogik aus `ExecutionFinisher#executeAction()`, Methoden `notifyExecutionFinished`/`notifyExecutionAborted`, Betreff/Texte wortgleich zum Alt-Code inkl. eines dort vorhandenen Leerzeichen-Tippfehlers), `PushoverClient` (`java.net.http`, Formular-Request 1:1 aus dem disassemblierten Bytecode der Alt-Bibliothek `pushover-client:1.0.0` hergeleitet). Vollständiges Alt-Notification-Inventar recherchiert (Client `ExecutionFinisher`: E-Mail/Pushover/elwaApp-Push je Ende/Abbruch; Portal `PasswordForgotWindow`/`UserWindow`: Passwort-E-Mails) und tabellarisch in kb/03-modules.md festgehalten – portiert sind die beiden E-Mail-/Pushover-Trigger (Ende, Abbruch), bewusst NICHT portiert der elwaApp/Ionic-Kanal (mobile App laut Auftraggeber irrelevant, Reste fallen in Phase 5 weg) und die beiden Portal-Passwort-E-Mails (hängen am neuen Portal-Login-Flow, laut Roadmap ohnehin Phase 3). **Wichtiger Fallstrick im Alt-Code aufgedeckt**: `users.push_notification` ist NICHT das Pushover-Opt-in, sondern das Opt-in für den nicht portierten elwaApp-Kanal – das tatsächliche Pushover-Opt-in ist ausschließlich ein nicht-leerer `pushover_user_key` (dedizierter Regressionstest `pushNotificationOptInColumnDoesNotGatePushover`). E-Mail-Transport über `spring-boot-starter-mail`/`JavaMailSender` (Standard-`spring.mail.*`-Properties statt eigener Konfigurationsklasse) statt commons-email; dabei eine Actuator-Nebenwirkung gefunden und behoben (Mail-Health-Indikator zog den Health-Endpoint ohne konfigurierten SMTP-Server auf `DOWN`, `management.health.mail.enabled: false` deaktiviert ihn). Pushover-API-Token ist – anders als im hartkodierten Alt-Code – über `elwasys.notifications.pushover.api-token` konfigurierbar (Default leer). Keine Schema-Änderung nötig (Empfänger-/Opt-in-Spalten existierten bereits, siehe AP2/`UserEntity`).<br><br>**Tests**: 11 neue, alle grün (`backend/run-backend-tests.sh`, jetzt **107/107** gesamt: 96 aus AP1-AP4 unverändert + 11 neu): `NotificationServiceEmailTest` (5, echter lokaler Test-SMTP via GreenMail – Betreff/Body als wörtliches Alt-Code-Zitat, da `Utilities#sendEmail` sich ohne echte DB-Anbindung nicht mit einer echten E-Mail-Adresse isoliert aufrufen lässt, siehe „Entscheidungen"), `NotificationServicePushoverTest` (5, eingebetteter JDK-`HttpServer`-Mock – exakte Formular-Parameter inkl. Regressionstest für den `push_notification`-Fallstrick), `NotificationsPropertiesDefaultTest` (1, voller Spring-Kontext, beweist Default AUS ohne gesetzte Umgebungsvariable). Root-Reactor-Build (`mvn install -DskipTests`, alle vier Module) sowie isolierter Client-Raspi-/Portal-Build weiterhin grün, 0 Quelländerungen an Common/Client-Raspi/Portal in diesem Arbeitspaket (nur `backend/`, `kb/`). |
| 2026-07-20 | **Phase 2 AP6: Deployment (Dockerfile, docker-compose, Helm Chart, TLS-Konzept) - letztes Arbeitspaket der Phase-2-Roadmap**. **`backend/Dockerfile`** (Build-Kontext = Repo-Wurzel, siehe „Entscheidungen"): Multi-Stage (Maven-Build → schlankes `eclipse-temurin:21-jre-jammy`-Runtime), non-root User (UID/GID 1000, fest angelegt für Kubernetes-`securityContext`), `HEALTHCHECK` gegen `/actuator/health` (curl gezielt für diesen einen Zweck installiert). Layer-Caching über zwei mvn-Aufrufe (exakt aus kb/04-build-and-run.md: `install -pl Common -am -DskipTests`, dann `package -pl backend -DskipTests`) mit `RUN --mount=type=cache,target=/root/.m2/repository` (BuildKit-Cache, `# syntax=docker/dockerfile:1`); erste COPY-Schicht nur die vier Modul-`pom.xml`-Dateien (+ `dependency:go-offline`), zweite COPY-Schicht nur `Common/src`+`backend/src` - Quellcodeänderungen an Client-Raspi/Portal invalidieren diesen Layer nicht. `.dockerignore` (Repo-Wurzel) filtert Git/IDE/Target-Verzeichnisse sowie Client-Raspi/Portal-Quellcode (nur deren `pom.xml` wird gebraucht, siehe „Entscheidungen").<br><br>**`deploy/compose/`**: `docker-compose.yml` (Backend + PostgreSQL 16, benanntes Volume `pgdata`, Healthchecks + `depends_on: condition: service_healthy`, `restart: unless-stopped`), `.env.example` (Platzhalter, keine echten Secrets, dokumentiert beide DB-Varianten), optionales TLS-Overlay `docker-compose.proxy.yml` + `Caddyfile` (automatisches Let's-Encrypt-TLS via Caddy, `!reset`-Merge-Tag um die Backend-Portveröffentlichung sauber aufzuheben - siehe „Entscheidungen" für den dabei gefundenen Compose-Merge-Fallstrick).<br><br>**`deploy/helm/elwasys-backend/`**: `Chart.yaml`, `values.yaml` (zugleich Values-Dokumentation - jeder Abschnitt kommentiert, keine separate README.md, siehe unten), `templates/` (`deployment.yaml`, `service.yaml`, `ingress.yaml`, `configmap.yaml`, `secret.yaml`, `serviceaccount.yaml`, `_helpers.tpl`, `NOTES.txt`). ConfigMap/Secret getrennt (DB-/SMTP-Passwort und Pushover-Token im Secret, Rest in der ConfigMap); `database.existingSecret` erlaubt ein extern verwaltetes Secret statt eines vom Chart erzeugten; SMTP-Passwort/Pushover-Token-`secretKeyRef`s sind `optional: true` (beide Kanäle sind per Default aus/leer, ein externes Secret muss diese Schlüssel nicht zwingend enthalten). Liveness/Readiness gegen `/actuator/health`. Ingress optional (Default aus), TLS über cert-manager-Annotation ODER ein selbst verwaltetes TLS-Secret (siehe „Entscheidungen"). **Kein PostgreSQL-Sub-Chart** (weder fest verdrahtet noch als optionale Bitnami-Abhängigkeit) - ausführliche Begründung in „Entscheidungen" und im `values.yaml`-Kommentar bei `database:`.<br><br>**CI** (`.github/workflows/ci.yml`): Backend-Job um einen `docker build -f backend/Dockerfile -t elwasys-backend:ci .`-Schritt ergänzt (Build-only, kein Push) - beweist in einer echten Docker-Umgebung (GitHub-Actions-Runner haben einen Docker-Daemon, diese Sandbox nicht, siehe „Umgebungsbefunde"), dass das Dockerfile tatsächlich baubar ist. **Release-Workflow** (`.github/workflows/maven-publish.yml`): zusätzliche Schritte nach dem bestehenden Jar-Build - Image bauen und nach GHCR veröffentlichen (`ghcr.io/<owner>/elwasys-backend:<tag>` + `:latest`), Anmeldung über den eingebauten `GITHUB_TOKEN` (kein Zusatz-Secret nötig, `packages: write` war bereits gesetzt), Image-Name dynamisch klein geschrieben (`github.repository_owner` durch `tr` statt hartkodiertem Owner). Nutzt denselben, bereits per `versions:set` auf die Release-Tag-Version gehobenen Arbeitsbaum als Docker-Build-Kontext (Jar-Version und Image-Tag stimmen überein).<br><br>**Validierung** (Details/exakte Kommandos im Abschlussbericht dieses Arbeitspakets): `docker compose config` für Basis- UND TLS-Overlay-Compose grün (inkl. Verifikation der externen-DB-Variante über Env-Var-Override und des `!reset`-Fixes für die Portveröffentlichung); **Helm tatsächlich beschafft** (weder `apt`/`snap` noch `get.helm.sh`/GitHub-Releases waren über den Proxy erreichbar - `helm.sh/helm/v3/cmd/helm@v3.15.4` stattdessen erfolgreich per `go install` über den in der `noProxy`-Liste freigegebenen `proxy.golang.org` gebaut) und damit `helm lint`/`helm template` (Default-Values, `ingress.enabled=true`, `database.existingSecret=...`) **grün** ausgeführt - keine rein manuelle Chart-Prüfung nötig. Dockerfile selbst bleibt daemonlos nicht baubar (kein Docker-Daemon in dieser Umgebung, siehe „Umgebungsbefunde") - stattdessen die exakten mvn-Befehle aus dem Dockerfile lokal nachvollzogen (inkl. eines eigens angelegten Reactor-Testaufbaus OHNE `Client-Raspi/src`/`Portal/src`, um die Docker-Kontext-Annahme "nur deren `pom.xml` wird gebraucht" zu beweisen) sowie das gebaute Jar gegen eine frische, leere lokale PostgreSQL-Instanz gestartet (`/actuator/health` → `{"status":"UP"}`, Flyway-Baseline lief durch) - simuliert exakt den `HEALTHCHECK` des Containers. Backend-Suite weiterhin **107/107** grün (`backend/run-backend-tests.sh`), Root-Reactor-Build (`mvn install -DskipTests`, alle vier Module) weiterhin grün - 0 Quelländerungen an Common/Client-Raspi/Portal in diesem Arbeitspaket (nur `backend/Dockerfile`, `.dockerignore`, `deploy/`, `.github/workflows/*.yml`, `kb/`). |
| 2026-07-20 | **Phase 2 QA-Review + Abschluss** (Koordinator, dieser Durchgang): Diff-Review aller Phase-2-Commits (137 Dateien, ~10.500 Zeilen) gegen CLAUDE.md/Roadmap; Stichproben-Tiefenprüfung der kritischen Stellen: `PasswordVerificationService` (SHA1-Format-Nachbildung, Argon2id, Fehlerrobustheit), `TerminalTokenService` (SHA-256-Hash-Speicherung, Rotation), Flyway V2/V3 (additiv, gut begründet), Standort-Scope-Durchsetzung (zentraler `TerminalScopeGuard`, von Device-/ExecutionController genutzt), Benachrichtigungs-Flag-Gating, Dockerfile (Root-Reactor-Fallstrick korrekt gelöst), `backend/pom.xml` (`common` nur test-scoped). **Kein Alt-Modul verändert**: `git diff a323c17..HEAD -- Common Client-Raspi Portal` ist leer. **Alle Suiten grün** (in dieser Remote-Umgebung ausgeführt): Backend 107/107 (`backend/run-backend-tests.sh`, lokales PostgreSQL), Root-Reactor-Build aller vier Module (`mvn install -DskipTests`), Client-UI/E2E 37/37 (`run-ui-tests.sh`), Cross-Component 3/3, Portal-E2E 18/18 (Playwright). **Parallelbetriebs-Beweis** (zusätzlich zur AP1-Schemaäquivalenz): die Backend-Flyway-Migrationen V1(Baseline)–V3 wurden auf die geteilte E2E-Datenbank `elwasys` angewendet (BASELINE-Übernahme der Bestands-DB, dann V2 `users.password`→`VARCHAR(255)`, V3 `terminal_tokens`); danach liefen Client-Suite (37/37) und Portal-E2E (18/18) erneut vollständig grün - Alt-Client und Alt-Portal arbeiten nachweislich unverändert auf einer vom Backend migrierten DB (inkl. P16 „Passwort ändern", d. h. SHA1-Schreiben in die geweitete Spalte funktioniert). **Deployment-Validierung nachvollzogen**: `docker compose config` für Basis + TLS-Proxy-Overlay grün (Pflicht-Env-Variablen per Design, `.env.example` vorhanden); Helm lint/template war in AP6 mit echtem helm 3.15.4 verifiziert. **Ergebnis: keine Befunde.** Phase 2 wird hiermit formal als abgeschlossen markiert; nächster Schritt: Phase 3 (Portal-Neubau). |
| 2026-07-20 | **Offline-Buchungs-Konzept aufgenommen** (Auftraggeber-Wunsch nach Phase-2-Abschluss): Terminals sollen bei Backend-Ausfall für eine definierte Zeitspanne eigenständig Buchungen akzeptieren und nachmelden. Konzeptskizze (Snapshot, Offline-Entscheidung, Idempotenz-Journal, Replay, Zeitfenster, Konfliktbehandlung) + Entscheidung in kb/05; Phase-4-Roadmap-Punkt „Robustheit" erweitert; API-v1-Erweiterungsbedarf (Idempotenz/Replay/Snapshot-Endpunkte) für Phase-4-Beginn vorgemerkt |
| 2026-07-20 | **Phase 3 AP1: Vaadin-Flow-Grundgerüst im Backend (Login, Layout-Gerüst Public/User/Admin)**. `vaadin-bom` (Version **24.10.8**) + `vaadin-spring-boot-starter` in `backend/pom.xml` ergänzt (BOM-Import analog zum Spring-Boot-BOM aus AP1, siehe dort); zwei bewusste Abhängigkeits-Ausschlüsse aus `vaadin-spring-boot-starter`: `hilla` (TypeScript/React-Gegenstück zu Flow, nicht Auftrag – ohne Ausschluss wirft `RequestUtil#isAllowedHillaView` in jedem Test ohne echten Servlet-Container eine `IllegalStateException`, hätte die komplette Backend-Testsuite gebrochen) und `collaboration-engine` (kommerzielles, hier ungenutztes Pro-Add-on). Neues Maven-Profil `production` (aktiv mit `-Pproduction`) für den produktiven Frontend-Bundle-Build (`vaadin-maven-plugin:build-frontend`, `vaadin.productionMode=true`); der Default-Build (`mvn package` ohne Profil) bleibt schnell (nur `prepare-frontend`, kein npm-Bundling). Surefire-Konfiguration erzwingt `vaadin.productionMode=true` für ALLE Testläufe dieses Moduls (System-Property) – Begründung siehe unten.<br><br>**UI-Struktur** (neues Paket `backend/.../ui/`, siehe kb/03-modules.md für die vollständige Liste): `login/LoginView` (Route `/login`, `@AnonymousAllowed`, Vaadins `LoginForm` mit deutschen Texten 1:1 aus dem Alt-Portal `PublicLayout`/`SessionManager`: Titel „Login"/„Waschportal", Felder „Benutzername"/„Passwort", Fehlermeldung „Login fehlgeschlagen"/„Bitte prüfen Sie die Anmeldedaten…"), `RootView` (Route `""`, `@PermitAll`, leitet nach Login je nach Rolle auf `AdminDashboardView` bzw. `UserDashboardView` weiter – Nachfolger von `WaschportalUI#loadSessionContent`), `admin/AdminLayout` (`AppLayout` mit `SideNav`: Dashboard/Benutzer/Benutzergruppen/Programme/Geräte/**Standorte** – „Standorte" ist NEU als eigener Menüpunkt, im Alt-Portal nur über einen Dashboard-Dialog erreichbar, siehe Entscheidungen unten) + 6 Platzhalter-Views (`@RolesAllowed("ADMIN")`, Inhalte folgen AP2/AP3), `user/UserLayout` (ein Menüpunkt „Übersicht", analog zum schlanken Alt-Portal-`UserLayout`) + `UserDashboardView` (`@RolesAllowed("USER")`, Platzhalter), `component/UserMenuBar`/`PlaceholderView` (gemeinsame Bausteine). "Passwort ändern"/"Einstellungen" (im Alt-`MainMenu`) sind bewusst NICHT Teil dieses Grundgerüsts – folgen mit den Stammdaten-Views in AP2/AP3.<br><br>**Security-Integration** (`SecurityConfig`): statt `formLogin(Customizer.withDefaults())` jetzt `http.with(VaadinSecurityConfigurer.vaadin(), c -> c.loginView(LoginView.class).anyRequest(AuthorizedUrl::authenticated))` – bindet `LoginView` als Login-Ziel, übernimmt das Freischalten der Login-Route + aller Vaadin-internen statischen Ressourcen für nicht angemeldete Anfragen und aktiviert Vaadins routenweise `NavigationAccessControl` (`@RolesAllowed`/`@PermitAll`/`@AnonymousAllowed` an den Views). Der `AuthenticationManager` (unverändert `ElwasysAuthenticationProvider`) bleibt derselbe – Login-Verhalten (inkl. der AP3-Verschärfung „gesperrte Nutzer werden abgewiesen") unverändert, nur der Login-Bildschirm ist jetzt eine Vaadin-View statt Spring Securitys generischer Default-Seite. Terminal-API-Kette (`TerminalApiSecurityConfig`, `/api/v1/**`) und WebSocket-Endpunkt bleiben UNBERÜHRT (eigene, niedrigere `@Order`-Kette, siehe AP4) – dedizierter Regressionstest (`TerminalApiSecurityTest`, unverändert grün) beweist das.<br><br>**Zwei nicht offensichtliche Fallstricke gefunden und behoben** (siehe Javadoc `SecurityConfig`/`AbstractBackendIT`): (1) Vaadins Spring-Autokonfiguration (`SpringBootAutoConfiguration`, `SpringSecurityAutoConfiguration`, `VaadinScopesConfig`) ist NICHT auf `@ConditionalOnWebApplication` eingeschränkt und versucht daher auch in den bestehenden `webEnvironment=NONE`-Tests (`AbstractBackendIT`, AP2) einen `WebApplicationContext`/eine `ServletRegistrationBean<SpringServlet>` zu autowiren – hätte ALLE Service-/Repository-/Auth-Tests aus AP2/AP3 gebrochen, nicht nur neue Vaadin-Tests. Fix: `AbstractBackendIT` schließt alle drei Vaadin-Autokonfigurationsklassen explizit aus (`spring.autoconfigure.exclude`, sinnvoll für diese reinen Nicht-Web-Tests). (2) `VaadinSecurityConfigurer` braucht für den Login-Routen-Pfad zwingend einen echten `ServletContext`-Bean (`getServletContextPath()`) – in einem Nicht-Web-Kontext wäre das eine `NullPointerException` bei der `SecurityFilterChain`-Bean-Erzeugung gewesen. Fix: `@ConditionalOnWebApplication` NUR auf der `securityFilterChain`-Bean-Methode (nicht auf der ganzen Klasse, sonst hätte `TerminalApiSecurityConfig` seinen `HttpSecurity`-Bean von `@EnableWebSecurity` verloren).<br><br>**Wichtiger Befund – Vaadin-Lizenzpflicht im Dev-Modus** (siehe Risikotabelle oben für die vollständige Herleitung und den Klärungsbedarf): Vaadin 24.10.x verlangt beim ersten `VaadinServlet#init()` im Dev-Modus einen Online-Lizenzcheck gegen vaadin.com ("This Vaadin version requires an extended maintenance subscription") – unabhängig von den beiden oben genannten Ausschlüssen. Diese Sandbox-Umgebung hat keinen Netzwerkzugriff auf vaadin.com (Proxy liefert `403`), daher scheitert `mvn spring-boot:run` (Dev-Modus) hier beim Servlet-Start; ein erzwungener Produktionsmodus umgeht den Lizenzcheck, findet dann aber (mangels gebautem `-Pproduction`-Bundle) kein Frontend („Unable to find index.html..."). **Deshalb bewusst KEIN JUnit-Test ergänzt, der über einen echten eingebetteten Servlet-Container (`RANDOM_PORT`) eine Vaadin-UI-Route tatsächlich rendern lässt** – ein solcher Test könnte hier unabhängig vom Code nicht grün laufen. Stattdessen: Sicherheits-Filterebene über MockMvc (`VaadinPortalSecurityTest`, kein echter Container nötig) + reiner Reflection-Test der `@RolesAllowed`/`@PermitAll`/`@AnonymousAllowed`-Zuordnung je View (`RouteAccessAnnotationsTest`, kein Spring-Kontext nötig). Ein vollständiger Browser-/JS-getriebener Login-Durchstich (Vaadins `LoginForm` ist eine clientseitig gerenderte Web-Komponente, kein Server-HTML-Formular) bleibt der späteren Playwright-Suite vorbehalten.<br><br>**Tests**: 9 neue, alle grün (`backend/run-backend-tests.sh`, jetzt **116/116** gesamt: 107 aus Phase 2 unverändert + 9 neu): `VaadinPortalSecurityTest` (5, `webEnvironment=MOCK`: geschützte Routen (`/`, `/admin`) leiten nicht angemeldete Anfragen um, Formular-Login über die Vaadin-Login-Route authentifiziert gültige Zugangsdaten UND weist gesperrte Nutzer ab, Logout leitet zur Login-Seite um), `RouteAccessAnnotationsTest` (4, reine Reflection: `LoginView`→`@AnonymousAllowed`, `RootView`→`@PermitAll`, alle 6 Admin-Views→`@RolesAllowed("ADMIN")`, `UserDashboardView`→`@RolesAllowed("USER")`). `.gitignore` um Vaadins auto-regenerierte Frontend-Tooling-Ausgabe (`backend/src/main/frontend/generated/`, `node_modules`, `package*.json` etc.) ergänzt – nie hand-editiert, wird bei jedem Build neu erzeugt.<br><br>**Build-Verifikation**: `mvn -f backend/pom.xml package -DskipTests` (Dev-Modus, ohne `-Pproduction`) grün; `mvn -f pom.xml install -pl Common -am -DskipTests` + isolierter `mvn -f Client-Raspi/pom.xml package -DskipTests`/`mvn -f Portal/pom.xml package -DskipTests` grün – 0 Quelländerungen an Common/Client-Raspi/Portal in diesem Arbeitspaket (nur `backend/`, `.gitignore`, `kb/`). |
