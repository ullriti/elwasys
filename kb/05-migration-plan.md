# 05 – Modernisierungsplan (lebendes Dokument)

> Dieses Dokument wird laufend fortgeschrieben. Es hält die **Rahmenbedingungen**, die
> vollständige **Komponenten-Inventur**, die **Zielarchitektur**, die **Reihenfolge** der
> Schritte und den **Fortschritt** fest.
>
> Stand 2026-07-20: Phase 0 (Sicherheitsnetz) ist abgeschlossen. Auf Basis der neuen
> Vorgaben des Auftraggebers wurde der Plan von einer reinen „Upgrade-Liste“ zu einem
> Zielarchitektur-getriebenen Modernisierungsplan überarbeitet.

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

### Phase 1 – Fundament (Build & Konsolidierung)
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
      JUnit-5-Tests ohne TestFX/Xvfb/DB decken alle Zustandsübergänge der State-Machine ab
- [x] CI an Parent-POM angepasst *(2026-07-20)*: die bestehende 3-Job-Struktur
      (Common/Client/Portal, kleinstes Risiko, Verhalten unverändert) wurde beibehalten statt
      auf einen kombinierten Reactor-Job umzustellen; alle Stellen, die Common isoliert
      installieren (CI-Job, `run-ui-tests.sh`, `run-client-e2e.sh`,
      `run-cross-component-e2e.sh`, `start-portal.sh`, SessionStart-Hook), bauen jetzt über
      `mvn -f pom.xml install -pl Common -am`, damit die Parent-POM mit ins lokale Repo
      installiert wird (sonst schlägt die Abhängigkeitsauflösung von `common` in
      Client-Raspi/Portal fehl)

### Phase 2 – Backend-Gerüst (parallel zum Bestand)
Ziel: neues Modul `backend` läuft produktionsnah **neben** Client & Portal auf derselben DB.
- [ ] Spring-Boot-Modul `backend` anlegen (Java 21, Actuator/Health, Logging)
- [ ] Flyway-Baseline aus `database-init.sql` + bestehenden Upgrade-Skripten erzeugen;
      Upgrade-Mechanismus über `config.db.version` stilllegen
- [ ] JPA-Entities für das Bestandsschema + Repositories; Geschäftslogik aus
      `Common.DataManager`/Portal/Client schrittweise portieren (Abrechnung, Berechtigungen,
      Preisberechnung) – mit Unit-Tests gegen Testcontainers
- [ ] Auth: Argon2id-Hashing + SHA1-Migrationspfad; Login-/Session-Handling
- [ ] REST-API v1 für Terminal-Anwendungsfälle (Login per Karte, Geräte-/Programmliste,
      Execution starten/beenden/abbrechen, Guthaben) + Standort-Token-Auth
- [ ] WebSocket-Endpunkt für Terminals (Ereignisse, Fernwartungskanal)
- [ ] Benachrichtigungsdienst (SMTP, Pushover) im Backend
- [ ] Deployment: Dockerfile + docker-compose (Backend + Postgres) **und** Helm Chart
      für Kubernetes; TLS-Konzept (Compose: Reverse Proxy; K8s: Ingress)

### Phase 3 – Portal-Neubau
Ziel: Admin-Portal als Teil des Backends, Feature-Parität, altes Portal abgeschaltet.
- [ ] Vaadin-Flow-UI im Backend: Login, Layout-Gerüst (Public/User/Admin)
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
      (laufende Executions lokal zu Ende führen, Ereignisse nachmelden)
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

## Risiken & Gegenmaßnahmen

| Risiko | Gegenmaßnahme |
|---|---|
| Terminal fällt aus, wenn Backend down (heute: wenn DB down) | Backend + DB auf demselben Host/Compose-Stack; laufende Waschvorgänge werden lokal zu Ende geführt und nachgemeldet (Phase 4); Health-Checks + systemd-Restart |
| Feature-Verlust beim Portal-Neubau | Fenster-/View-Inventar (siehe oben) als Checkliste; portierte Playwright-Suite als Abnahme |
| Verhaltensänderung am Terminal | UI/FXML unangetastet; TestFX-Suite muss vor/nach jedem Schritt grün sein |
| SHA1→Argon2-Migration sperrt Nutzer aus | Re-Hash beim ersten Login (SHA1 wird verifiziert, dann ersetzt); Admin-Reset-Flow bleibt |
| Parallelbetrieb Alt/Neu auf einer DB (Phase 2–4) | Backend anfangs nur lesend/additiv; Schreibpfade erst umstellen, wenn der jeweilige Alt-Pfad abgeschaltet wird; keine Schema-Brüche vor Phase 5 |
| Vaadin-7-Portal baut nicht unter Java 21 (Phase 1) | Portal bis zur Ablösung auf altem Sprachlevel einfrieren – es wird ohnehin ersetzt |

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

## Offene Fragen / mit Auftraggeber klären
*Derzeit keine – alle Grundsatzfragen sind entschieden (siehe „Entscheidungen“).*

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
| 2026-07-20 | **Phase 1 (Client-Raspi Java 21 + ElwaManager-DI)**: Client-Raspi-Sprachlevel 16 → 21 gehoben (keine Quelländerungen nötig); minimaler DI-Seam für `ElwaManager` eingeführt (package-private Test-Konstruktor `MainFormController(boolean wireToElwaManager)`, der das Verdrahten mit `ElwaManager.instance`/`InactivityScheduler` in Tests überspringt, Produktionsverhalten unverändert); 12 neue isolierte JUnit-5-Charakterisierungstests für `MainFormStateManager` (kein TestFX/Xvfb/DB nötig). Volle Client-Suite grün (33/33) |
