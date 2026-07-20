# 05 – Modernisierungsplan (lebendes Dokument)

> Dieses Dokument wird laufend fortgeschrieben. Es hält die **Rahmenbedingungen**, die
> vollständige **Komponenten-Inventur**, die **Zielarchitektur**, die **Reihenfolge** der
> Schritte und den **Fortschritt** fest.
>
> Stand 2026-07-20: Phase 0 (Sicherheitsnetz) und Phase 1 (Fundament: Build &
> Konsolidierung) sind abgeschlossen. Auf Basis der neuen Vorgaben des Auftraggebers wurde
> der Plan von einer reinen „Upgrade-Liste“ zu einem Zielarchitektur-getriebenen
> Modernisierungsplan überarbeitet. Phase 2 ist gestartet: AP1 (Backend-Gerüst +
> Flyway-Baseline) und AP2 (JPA-Entities/Repositories + Geschäftslogik-Portierung:
> Abrechnung, Berechtigungen, Preisberechnung, Execution-Lebenszyklus) sind abgeschlossen,
> siehe Roadmap unten.

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

### Phase 2 – Backend-Gerüst (parallel zum Bestand)
Ziel: neues Modul `backend` läuft produktionsnah **neben** Client & Portal auf derselben DB.
- [x] Spring-Boot-Modul `backend` anlegen (Java 21, Actuator/Health, Logging) *(AP1,
      2026-07-20)*
- [x] Flyway-Baseline aus `database-init.sql` + bestehenden Upgrade-Skripten erzeugen;
      Upgrade-Mechanismus über `config.db.version` stilllegen *(AP1, 2026-07-20)*
- [x] JPA-Entities für das Bestandsschema + Repositories; Geschäftslogik aus
      `Common.DataManager`/Portal/Client schrittweise portieren (Abrechnung, Berechtigungen,
      Preisberechnung) – mit Unit-Tests gegen Testcontainers *(AP2, 2026-07-20)*
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
| 2026-07-20 | **Phase 1 (Client-Raspi Java 21 + ElwaManager-DI)**: Client-Raspi-Sprachlevel 16 → 21 gehoben (keine Quelländerungen nötig); minimaler DI-Seam für `ElwaManager` eingeführt (package-private Test-Konstruktor `MainFormController(boolean wireToElwaManager)`, der das Verdrahten mit `ElwaManager.instance`/`InactivityScheduler` in Tests überspringt, Produktionsverhalten unverändert); 12 neue isolierte JUnit-5-Charakterisierungstests für `MainFormStateManager` (kein TestFX/Xvfb/DB nötig). Volle Client-Suite grün (~~33/33~~ **37/37** – die 33 zählte die 4 migrierten `InactivityScheduler`-Tests nicht mit, siehe QA-Review-Eintrag unten) |
| 2026-07-20 | **Phase 1 QA-Review** (dieser Durchgang): Diff-Review aller Commits gegen CLAUDE.md/Roadmap; DI-Seam in `MainFormController` verifiziert (Produktionspfad `this(true)` unverändert, Verhalten 1:1 wie vorher); 12 neue State-Machine-Tests stichprobenartig gelesen – isoliert, kein `ElwaManager.instance`, kein DB-/Netzwerkzugriff; Portal-Bytecode nach dem Build tatsächlich als Class-Major-Version 52 (Java 8) verifiziert (nicht vom Parent-Default 21 überschrieben). Volle Testsuiten grün: Client 37/37 (`run-ui-tests.sh`), Cross-Component 3/3, Portal-E2E 18/18 (Playwright), `mvn package` für Client und Portal grün. **Zwei echte Regressionen gefunden und behoben** (Details oben unter Phase-1-Roadmap "QA-Review-Fix" sowie in 04-build-and-run.md): (1) CI/Release-Workflows setzten noch JDK 17 auf, obwohl Common/Client-Raspi jetzt Sprachlevel 21 verlangen – auf JDK 21 angehoben; (2) `setup.sh` installierte auf neu provisionierten Raspi-Terminals noch ein Java-17-JRE, das das jetzt mit Sprachlevel 21 gebaute Client-fat-jar nicht mehr ausführen könnte – auf `bellsoft-java21-runtime-full` angehoben (Restrisiko für bereits im Feld befindliche, nicht neu provisionierte Geräte bleibt in der Risikotabelle dokumentiert). Zusätzlich zwei Dokumentationskorrekturen: Test-Anzahl 33/33 → 37/37 in kb/05 und kb/README.md; `CLAUDE.md`-Abschnitt „Aktueller Stand" von „Phase 0 abgeschlossen/Phase 1 nächster Schritt" auf „Phase 1 abgeschlossen" aktualisiert. Phase 1 wird hiermit formal als abgeschlossen markiert. |
| 2026-07-20 | **Neue Roadmap-Phase 6 „Produktivumschaltung” ergänzt** (auf Auftraggeber-Wunsch): eigener Schritt nach Phase 5 für den tatsächlichen Umbau des bestehenden Produktiv-Setups (nicht nur eine neue Umgebung danebenstellen) – Migrationsskripte für den Bestand, Terminal-Neuaufsetzung (löst das Java-17-Restrisiko aus Phase 1 endgültig auf), ein Upgrade-Skript für Terminals sowie optional Auto-Update mit Rollback (Terminals stehen unbeaufsichtigt im Feld). Für Portal/Backend explizit **kein** eigenes Upgrade-/Rollback-Skript vorgesehen, da Docker-Compose/Kubernetes/Helm Rollout und Rollback selbst übernehmen – dort genügen automatisierte Smoke-Tests nach jedem Deployment. Risikotabelle um zwei neue Einträge (Terminal-Auto-Update-Fehlschlag, Cutover-Datenverlust) ergänzt |
| 2026-07-20 | **Phase 2 AP1: Backend-Gerüst + Flyway-Baseline**. Neues Modul `backend/` im Root-Reactor (`org.kabieror.elwasys:backend`, erbt `elwasys-parent`, Java 21 per Default). Spring Boot **3.5.16** per BOM-Import (`spring-boot-dependencies` in `dependencyManagement`) eingebunden statt über `spring-boot-starter-parent` – ein zweiter Parent ist in Maven nicht möglich, das Modul bleibt Kind von `elwasys-parent`. **Wichtiger Fallstrick dabei gefunden**: ein BOM-Import überschreibt *nie* eine Version, die schon irgendwo in der Vererbungskette explizit gepinnt ist (das ist Absicht, damit man einzelne BOM-Versionen gezielt übersteuern kann) – `elwasys-parent`s eigene `dependencyManagement` pinnt `logback-classic`/`-core` (1.2.9) und `slf4j-api` (1.7.12) fest, beides zu alt für Spring Boot 3.5 (dessen Logging-Integration verlangt Logback ≥ 1.5 für das `Configurator`-SPI; Fehlerbild war ein `AbstractMethodError` beim Testkontext-Boot). Fix: `backend/pom.xml` deklariert explizite Overrides für `logback-classic`/`-core` (1.5.34), `slf4j-api` (2.0.18) und `postgresql` (42.7.11) – exakt die Versionen, die `spring-boot-dependencies:3.5.16` selbst managt –, **vor** dem BOM-Import in der eigenen `dependencyManagement`; Common/Client-Raspi/Portal sind unberührt, da sie diese BOM nicht importieren. Starter: web, actuator, jdbc, validation, test; dazu `flyway-core` + `flyway-database-postgresql` + `postgresql`-Treiber (JPA bewusst **nicht** – kommt erst im nächsten Arbeitspaket). `spring-boot-maven-plugin` (repackage) baut ein lauffähiges Jar (`elwasys-backend.jar`); Actuator-Health unter `/actuator/health` erreichbar; Logging läuft über das von Spring Boot mitgelieferte Logback, Level/Datenbank-Zugangsdaten über `application.yml` mit Umgebungsvariablen-Overrides (`ELWASYS_DB_URL`/`_USER`/`_PASSWORD`, Defaults passend zur lokalen Dev-Umgebung).<br><br>**Flyway-Baseline** (`backend/src/main/resources/db/migration/V1__baseline_schema_0_4_0.sql`): 1:1-Übernahme von `Common/resources/database-init.sql` (ohne die psql-only `CREATE DATABASE`/`\connect`-Zeilen, die bei einer über die JDBC-URL bereits ausgewählten Ziel-DB keinen Sinn ergeben) – **nicht** separat aus den beiden `database-upgrade/*.sql`-Skripten zusammengesetzt, weil `database-init.sql` selbst schon deren Endzustand (0.4.0: `deconz_uuid`-Spalte, aktuelle `device_program_rel`-FKs, `db.version`-Seed `0.4.0`) enthält – ein frischer Lauf von `database-init.sql` und ein frischer Lauf der Baseline sind daher per Konstruktion bereits derselbe Endzustand, siehe Verifikation unten. Einzige inhaltliche Änderung: die vier `CREATE GROUP`/`CREATE USER`-Anweisungen (Rollen `elwaclients`/`elwaclient1`/`elwaportal`/`elwaapi`) sind in einen `DO`-Block mit Existenzprüfung (`pg_roles`) gefasst, weil PostgreSQL-Rollen Cluster-weit (nicht pro Datenbank) sind – ohne Guard würde ein zweiter Lauf gegen denselben Cluster (z. B. eine zweite, neue DB neben der Bestands-DB) mit „role already exists” fehlschlagen; das Ergebnis (Rollen mit denselben Rechten) ist identisch. Der Spaltentypo `auto_end_power_threashold` bleibt bewusst erhalten (Umbenennung erst Phase 5).<br><br>**`config.db.version`-Mechanismus untersucht und stillgelegt**: Recherche in Common/Client-Raspi/Portal (`grep` über alle drei Module) zeigt, dass **kein** Java-Code den Wert von `config.db.version` je liest – `DataManager`/`ConfigurationManager` kennen das DB-`config`-Schlüssel/Wert-Paar gar nicht, es gibt keinen automatischen Upgrade-Mechanismus im Code, der Wert wurde offenbar rein informativ von Hand von den SQL-Skripten selbst gepflegt (Upgrade-Skripte wurden vom Betreiber manuell per `psql -f` ausgeführt). Verhalten bewahren heißt hier: der Seed-Wert `db.version = '0.4.0'` bleibt in der Flyway-Baseline erhalten (Alt-Code könnte ihn theoretisch lesen, auch wenn aktuell nicht der Fall), aber er wird ab sofort **nicht mehr fortgeschrieben** – zukünftige Schemaänderungen laufen ausschließlich über weitere Flyway-Migrationen (V2, V3, …), `Common/resources/database-upgrade/*.sql` wird nicht mehr gepflegt (Dateien bleiben als historisches Artefakt im Repo liegen). Details siehe kb/02-data-model.md.<br><br>**Verifiziert** (nicht nur behauptet), mit dem lokalen PostgreSQL 16 dieser Umgebung: `backend/verify-schema-baseline.sh` (neu, reproduzierbar) legt eine DB über den Alt-Weg an (`database-init.sql`), lässt eine zweite, leere DB von der gebauten Backend-Jar per Flyway migrieren, vergleicht `pg_dump --schema-only`-Dumps beider DBs (nach Herausfiltern der zufälligen `\restrict`/`\unrestrict`-Tokens von `pg_dump` ≥ 16 und Ausschluss von Flyways eigener `flyway_schema_history`-Tabelle per `pg_dump -T`) und prüft zusätzlich `baselineOnMigrate` gegen die Alt-Weg-DB (Health-Endpoint UP, `flyway_schema_history` zeigt genau eine `BASELINE`-Zeile bei Version 1, `admin`-Nutzer unverändert). Ergebnis: **schema-identisch** (keine Abweichung außer der erwarteten Flyway-Historientabelle) und Backend startet sauber gegen die Bestands-DB. Details/Kommandos im Abschlussbericht dieses Arbeitspakets.<br><br>**Tests**: JUnit 5 (`backend/src/test/.../BackendApplicationTest.java`) fährt den vollen Spring-Kontext inkl. Flyway-Migration gegen ein echtes PostgreSQL hoch und prüft Health-Endpoint + Baseline-Schema/Seed-Daten. **Testcontainers als Default** (läuft unverändert in CI, wo ein Docker-Daemon verfügbar ist) mit Override über `ELWASYS_TEST_JDBC_URL`/`_DB_USER`/`_DB_PASSWORD` (System-Property oder Env), mit dem die Tests stattdessen gegen eine lokale PostgreSQL-Instanz laufen – notwendig, weil diese Remote-Entwicklungsumgebung **keinen** Docker-Daemon hat (verifiziert: `docker ps` schlägt mit „no such file or directory” fehl). `backend/run-backend-tests.sh` (Muster: `Client-Raspi/run-ui-tests.sh`) bereitet lokal eine frische Testdatenbank vor und setzt den Override; in dieser Umgebung über dieses Skript **grün** ausgeführt (2/2 Tests). Diese Entscheidung (Testcontainers-Default + lokaler Override statt z. B. „nur lokales PG überall”) wurde getroffen, damit die CI (mit Docker) unverändert dem Spring-Boot-Standardmuster folgt, während lokale/Sandbox-Umgebungen ohne Docker trotzdem lauffähig bleiben, ohne zwei komplett getrennte Testsuiten pflegen zu müssen.<br><br>**CI**: vierter Job „Backend” in `.github/workflows/ci.yml` (ubuntu-24.04, JDK 21 Liberica wie die anderen drei Jobs) ergänzt. Läuft `mvn -f pom.xml test -pl backend` **mit** Testcontainers (kein Local-PG-Setup nötig) – anders als der Client-Job, der lokales PostgreSQL seedet, weil GitHub-Actions-Runner (anders als diese Sandbox) einen Docker-Daemon mitbringen; das ist der im Vorfeld vom Koordinator verifizierte Umgebungsunterschied. `backend` hat keine Reactor-Abhängigkeit auf `common` (kein `-am`/kein Common-Build im Job nötig).<br><br>**Build-Verifikation**: `mvn -f pom.xml install -DskipTests` (alle vier Module, inkl. `backend`) grün; zusätzlich isoliert `mvn -f pom.xml install -pl Common -am -DskipTests && mvn -f Client-Raspi/pom.xml package -DskipTests && mvn -f Portal/pom.xml package -DskipTests` grün – Common/Client-Raspi/Portal unverändert und unbeeinträchtigt (0 Quelländerungen an diesen drei Modulen in diesem Arbeitspaket). |
| 2026-07-20 | **Phase 2 AP2: JPA-Entities + Repositories, Geschäftslogik-Portierung (Abrechnung, Berechtigungen, Preisberechnung, Execution-Lebenszyklus)**. `spring-boot-starter-data-jpa` zu `backend/pom.xml` hinzugefügt (Version über das bereits importierte Spring-Boot-BOM, siehe AP1). **7 Entities** unter `backend/.../domain/` (1:1 aufs Bestandsschema, siehe kb/02-data-model.md): `UserGroupEntity`, `UserEntity`, `LocationEntity`, `DeviceEntity` (Spaltentypo `auto_end_power_threashold` bewusst erhalten), `ProgramEntity`, `ExecutionEntity`, `CreditAccountingEntryEntity`, dazu `ConfigEntity` (Vollständigkeit, ungenutzt – siehe kb/02). Die vier n:m-Tabellen (`locations_valid_user_groups`, `devices_valid_user_groups`, `programs_valid_user_groups`, `device_program_rel`) sind als `@ManyToMany`+`@JoinTable` modelliert, nicht als eigene Entity-Klassen (Standard-JPA-Praxis für reine Verknüpfungstabellen ohne Zusatzspalten). Postgres-native Enums (`DISCOUNT_TYPE`, `PROGRAM_TYPE`, `TIME_UNIT_TYPE`) werden über Hibernates `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` gebunden (löst das bekannte Problem, dass eine simple `@Enumerated(STRING)`-Bindung gegen eine Postgres-ENUM-Spalte mit „column is of type … but expression is of type character varying" fehlschlägt); die neuen Java-Enums (`DiscountType`, `ProgramType`, `TimeUnitType`) tragen bewusst dieselben Konstantennamen wie die DB-Werte (anders als der Alt-Code, der z. B. `DiscountType.Fix`/`Factor`/`None` heißt). `spring.jpa.hibernate.ddl-auto=none` explizit gesetzt (Rahmenbedingung: Schema kommt ausschließlich von Flyway).<br><br>**7 Spring-Data-Repositories** unter `backend/.../repository/`: `UserGroupRepository`, `LocationRepository`, `DeviceRepository`, `ProgramRepository`, `UserRepository` (inkl. `findByCardId` – parametrisierte Nachbildung der Alt-Code-Regex-Suche `card_ids ~ '(?n)^cardId$'` per natívem `@Query`, gleiche Matching-Semantik ohne String-Konkatenation/SQL-Injection-Risiko), `ExecutionRepository`, `CreditAccountingEntryRepository`.<br><br>**4 Services** unter `backend/.../service/`, jeweils 1:1-Portierung mit Quellenverweis: `PricingService` (aus `Common.Program#getPrice`/`#getDynamicPrice`), `CreditService` (aus `Common.User#loadCredit`/`#payExecution`/`#inpayment`/`#payout`), `PermissionService` (aus den inline UI-Prüfungen in `Client-Raspi/.../MainFormController#onCardDetected`, `DeviceListEntry#applyUserStyle`, `Common.Device#getPrograms(User)` – diese Logik steckt im Alt-Code NICHT in einer wiederverwendbaren Common-Methode, sondern direkt in den JavaFX-Controllern), `ExecutionService` (aus `Common.Execution#start/stop/reset/getPrice/isExpired` sowie den DB-Anteilen von `Common.DataManager#newExecution/getNotFinishedExecutions/getRunningExecution/getExecutions/getLastUser` und `Client-Raspi/.../ExecutionManager`/`ExecutionFinisher` – hardwarenahe Teile [Leistungsmessung, Steckdose schalten, Email/Pushover-Benachrichtigungen] bleiben bewusst im Terminal, siehe Zielarchitektur).<br><br>**Entscheidung – EAGER statt LAZY für alle fachlich genutzten Assoziationen**: Der Alt-`DataManager` lädt beim Holen eines Objekts immer sofort alle referenzierten Objekte mit (`Device#update()` lädt z. B. `programs`/`validUserGroups` unconditional, `User#load()` lädt die `UserGroup` immer synchron) – es gibt dort de facto kein „lazy" Nachladen. Diese Entities bilden das nach (`FetchType.EAGER` für `UserEntity.group`, `DeviceEntity.location/validUserGroups/programs`, `ProgramEntity.validUserGroups`, `LocationEntity.validUserGroups`, `ExecutionEntity.device/program/user`, `CreditAccountingEntryEntity.user`). Nebeneffekt: da AP2 laut Auftrag noch keine Web-/REST-Schicht einführt (die REST-API folgt erst in AP4), gibt es noch keine natürliche Transaktions-/Session-Grenze, die `LAZY` sauber absichern würde (Open-Session-in-View ist bewusst deaktiviert, siehe `application.yml`) – EAGER vermeidet `LazyInitializationException`s in den Tests UND ist die treuere Nachbildung des Alt-Verhaltens. Kann in einem späteren Arbeitspaket mit gezielten Fetch-Joins/DTO-Projektionen an der REST-Grenze verfeinert werden, wenn Performance das nahelegt.<br><br>**Entscheidung – Alt-vs-Neu-Vergleichstests umgesetzt** (stärkster Äquivalenz-Nachweis, wie im Auftrag vorgeschlagen): `common` als **test-scope**-Dependency in `backend/pom.xml` ergänzt (nur Testklassenpfad, keine Laufzeit-Abhängigkeit – das Backend hat weiterhin sein eigenes Datenmodell, siehe kb/03). `LegacyDataManagerFactory` (Test-Support) baut eine echte Alt-Code-`DataManager` gegen dieselbe Test-DB auf (Umweg über ein `ThreadLocal`, weil `ConfigurationManager`s Konstruktor seine Properties bereits synchron lädt, bevor Unterklassen-Felder gesetzt werden könnten). `PricingServiceParityTest` und `CreditServiceParityTest` lesen dieselbe committete DB-Zeile einmal über den Alt-Code (`Program#getPrice`, `User#getCredit`) und einmal über `PricingService`/`CreditService` und vergleichen **bitgenau** (Wert UND `BigDecimal`-Skala, per `toPlainString()`). Das deckte einen echten, bestätigten Nebeneffekt der `new BigDecimal(double)`-Verwendung im Alt-Code auf (siehe „Beobachtungen" unten) und bewies dessen exakte Nachbildung. Für `PermissionService` ist kein direkter Alt-Code-Vergleichstest möglich/sinnvoll: die Berechtigungsregeln stecken im Alt-Code nicht in einer aufrufbaren Common-Methode, sondern direkt in JavaFX-UI-Controllern (die ein laufendes JavaFX-Toolkit bräuchten) – hier sind es stattdessen Charakterisierungstests, deren Erwartungswerte direkt aus dem zitierten Alt-Code-Quelltext hergeleitet sind (siehe `PermissionServiceTest`-Javadoc).<br><br>**Beobachtungen** (fragwürdiges/überraschendes Alt-Verhalten, bewusst 1:1 übernommen, nicht „korrigiert"): (1) `User#loadCredit` zieht den Maximalpreis **jeder** `finished=false`-Ausführung eines Nutzers vom Guthaben ab – **unabhängig davon, ob sie überhaupt gestartet wurde** (kein `start IS NOT NULL`-Filter, anders als bei `getNotFinishedExecutions`); eine gerade erst angelegte, noch nicht gestartete Ausführung mindert das Guthaben also schon vor. (2) `Execution#reset()` setzt trotz des Methodennamens `finished=TRUE` (nicht `FALSE`) und nullt `start`/`stop` – wird im Client nur aufgerufen, wenn das Einschalten der Steckdose nach Anlegen der Ausführung fehlschlägt; die Ausführung soll dann als „erledigt/verworfen", nicht als „noch offen" gelten. (3) `User#payExecution` prüft `price.equals(BigDecimal.ZERO)` (skalasensitiv!) statt `compareTo`: ein FIXED-Programm mit einer in der DB als `0.00` (Skala 2) gepflegten Grundgebühr erzeugt trotzdem einen Buchungssatz über `0.00`, während ein durch die Freiminuten-Regel auf `BigDecimal.ZERO` (Skala 0, expliziter Literal im Code) reduzierter Preis **keinen** Buchungssatz erzeugt – durch einen Parity-Test bewiesen (`fixedProgramZeroFlagfallStillHasScaleTwoNotBigDecimalZero`). (4) Rabattberechnung nutzt `new BigDecimal(double)` statt `BigDecimal.valueOf(double)` – bei „krummen" `discount_value`s (z. B. `0.1`) entstehen dadurch sehr lange Nachkommastellen (Binärdarstellungsfehler von `double`), was der neue `PricingService` bewusst identisch nachbildet (Test `discountTypeFactorReproducesBinaryFloatingPointArtifact` beweist, dass Alt und Neu exakt denselben „unsauberen" Wert liefern). (5) `Device#getPrograms(User)` filtert NICHT zusätzlich auf `program.isEnabled()` – ein deaktiviertes, aber dem Gerät zugeordnetes und für die Gruppe freigegebenes Programm bleibt im Client wählbar; `PermissionService#getAvailablePrograms` bildet das identisch nach. (6) App-Relikt-Spalten (`app_id`/`access_key`/`auth_key` auf `users`) sind wie in den Rahmenbedingungen gefordert nicht gemappt; der DB-Trigger `user_authkey_trigger` befüllt `auth_key` unabhängig davon bei jedem INSERT automatisch, das wurde in `run-backend-tests.sh` verifiziert (keine NOT-NULL-Verletzung).<br><br>**Entscheidung – `credit_accounting.date`**: die DB pflegt hier einen `CURRENT_TIMESTAMP`-Default, den der Alt-Code nie explizit überschreibt. `CreditAccountingEntryEntity` setzt `date` stattdessen bewusst per Anwendungs-Uhr (`LocalDateTime.now()`), um die (Hibernate-versionsabhängige) Komplexität einer nachträglichen DB-generierten-Wert-Rücklesung zu vermeiden – semantisch identisch („Zeitpunkt der Buchung"), ein Unterschied entstünde nur bei nennenswertem Uhren-Versatz zwischen Anwendungs- und DB-Host (in Produktion: ein Deployment, ein Host/Cluster).<br><br>**Tests**: 27/27 grün (`backend/run-backend-tests.sh`) – 2 aus AP1 (`BackendApplicationTest`) + 25 neu: `PricingServiceParityTest` (8, inkl. Alt-vs-Neu), `CreditServiceParityTest` (2, inkl. Alt-vs-Neu), `PermissionServiceTest` (3, Charakterisierung), `ExecutionServiceTest` (9, Lebenszyklus-Charakterisierung), `UserRepositoryCardIdTest` (3, Regex-Kartennummernsuche). `run-backend-tests.sh` baut jetzt zuerst `Common` (`mvn -f pom.xml install -pl Common -am -DskipTests`), da `backend` seit diesem Arbeitspaket eine Test-Scope-Abhängigkeit auf `common` hat; der CI-Job „Backend" in `.github/workflows/ci.yml` bekam denselben zusätzlichen Schritt.<br><br>**Build-Verifikation**: `mvn -f pom.xml install -DskipTests` (alle vier Module) grün; isoliert `mvn -f Client-Raspi/pom.xml package -DskipTests` und `mvn -f Portal/pom.xml package -DskipTests` grün – 0 Quelländerungen an Common/Client-Raspi/Portal in diesem Arbeitspaket (nur `backend/`, `.github/workflows/ci.yml`, `kb/`). |
