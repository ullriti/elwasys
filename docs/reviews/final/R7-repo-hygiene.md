# R7 – Repo-Hygiene (Track 7 der finalen Pre-Launch-Review)

|              |                                                       |
| ------------ | ----------------------------------------------------- |
| **Spec:**    | [`docs/specs/0001-finale-review.md`](../../specs/0001-finale-review.md) |
| **Prüffrage:** | Ist das Repo gut aufgeräumt und alles an seinem Platz – keine Altlasten, toten Dateien, verwaisten Skripte oder inkonsistenten Referenzen? |
| **Datum:**   | 2026-07-23                                             |
| **Methode:** | Systematisches Durchsuchen (git ls-files, gezieltes Greppen mit Gegenprobe, Link-Check aller relativen Markdown-Links, Stichproben in Compose/Helm/CI/pom.xml) – keine oberflächlichen Einzeltreffer ohne zweite Prüfung übernommen |

## 1. Gesamturteil

**Das Repo ist insgesamt sehr aufgeräumt.** Alle 22 Shell-Skripte sind nachweislich
referenziert (Doku, CI oder andere Skripte), die Migrations-Altlasten (Alt-Portal,
`Common`-Modul, `DataManager`, Alt-DB-Zugriff) sind vollständig aus Build/Code entfernt und
nur noch als bewusst gekennzeichnete Historie in KB/Worklog/CHANGELOG sichtbar (per ADR 0020
zulässig), die Repo-/GHCR-Referenzen sind einheitlich auf `ullriti/elwasys`, es gibt keine
versehentlich committeten Build-Artefakte, `node_modules`, IDE-Dateien oder ungewöhnlich
große Binärdateien, und `.gitignore` deckt alle beobachteten Build-/Testartefakte ab. Es
fanden sich dennoch reale, kleinere Lücken: ein Produktions-relevanter Env-Var-Gap in
Compose **und** Helm (Passwort-Reset-Link zeigt ohne manuellen Eingriff immer auf
`localhost`), eine tote, aus der Vor-Assembly-Plugin-Zeit stammende `MANIFEST.MF`-Ressource
im Terminal-Modul, zwei kaputte relative Markdown-Links sowie ein Worklog-Eintrag, der nicht
im eigenen Index steht. Keiner dieser Punkte ist ein Blocker für den Feldeinsatz; der
Env-Var-Gap sollte aber vor dem ersten produktiven Compose-/Helm-Rollout behoben werden, weil
er sich sonst erst bemerken lässt, wenn ein Nutzer einen kaputten Passwort-Reset-Link
bekommt.

## 2. Findings

### Kategorie 1 – Tote/verwaiste Dateien

- **mittel · `Client-Raspi/res/META-INF/MANIFEST.MF`** · Diese statische Manifest-Datei
  stammt aus dem allerersten Commit (`git log --oneline -- Client-Raspi/res/META-INF/MANIFEST.MF`
  → nur `b7a6484 Init`) und wird über den in `Client-Raspi/pom.xml` als Resource
  eingebundenen `res/`-Ordner nach `target/classes/META-INF/MANIFEST.MF` kopiert. Das
  tatsächlich ausgelieferte Artefakt ist aber die vom `maven-assembly-plugin`
  (`jar-with-dependencies`) gebaute Fat-Jar, deren `Main-Class` über
  `<archive><manifest><mainClass>` in `Client-Raspi/pom.xml` (Zeile ~160) gesetzt wird –
  bestätigt durch `.github/workflows/maven-publish.yml`, das ausschließlich
  `raspi-client-<tag>-jar-with-dependencies.jar` veröffentlicht. Die statische
  `MANIFEST.MF` hat auf das ausgelieferte Artefakt keinen Einfluss mehr; aktuell stimmen
  beide Main-Class-Werte zufällig überein, bei künftigen Änderungen an einer Stelle ohne
  die andere entstünde aber eine irreführende, wirkungslose Doppelkonfiguration.
  **Empfehlung:** Datei entfernen (oder Kommentar ergänzen, warum sie trotzdem bleibt),
  einzige Quelle für `Main-Class` ist die Assembly-Plugin-Konfiguration.
- **mittel · `docs/worklog/README.md` (Index)** · Der Eintrag
  `docs/worklog/2026-07-23-offline-replay-haertung-ii.md` existiert, ist inhaltlich vollständig
  und wird korrekt aus `docs/architecture/README.md` (ADR 0021) und
  `docs/kb/README.md` („Aktueller Stand") verlinkt – fehlt aber in der eigenen
  Index-Tabelle in `docs/worklog/README.md` (geprüft per Abgleich aller
  `docs/worklog/*.md`-Dateinamen gegen die Indextabelle; einziger Treffer). Verstößt gegen
  die im Worklog-README selbst festgehaltene Regel „ein Eintrag je Session/Arbeitspaket,
  append-only im Index". **Empfehlung:** Zeile in der Indextabelle nachtragen (chronologisch
  zwischen `ap7-kb-ueberarbeitung` und `finale-review-teil-a`, je nach tatsächlicher
  Reihenfolge der Sessions).
- **niedrig · Local-only (kein Repo-Fund, nur zur Vollständigkeit dokumentiert):**
  `Common/target/…` liegt im Arbeitsverzeichnis dieser Session als Build-Rückstand aus einer
  früheren Zeit vor Auflösung des `Common`-Moduls. `git status`/`git ls-files` zeigen dafür
  **keinen** Treffer (durch `target/` in `.gitignore` sauber erfasst) – kein Repo-Hygiene-Fund,
  nur der Vollständigkeit halber erwähnt, damit niemand denkt, das sei übersehen worden.

### Kategorie 2 – Migrations-Altlasten

- **Keine Funde.** Alle Treffer für `Common`, `DataManager`, `Portal/` (als eigenständiges
  Modul), `commons-mail` u. Ä. wurden gegengeprüft: sie sind entweder (a) datierte
  Historie-Fußnoten in KB-Artikeln (laut ADR 0020 zulässige Ausnahme, z. B.
  `docs/kb/04-build-and-run.md:508`, `docs/kb/01-architecture.md:145`,
  `docs/kb/07-cloud-init.md:62`), (b) Warum-Kommentare, die eine *aktuelle* Entscheidung
  gegen den historischen Zustand begründen (z. B. `backend/pom.xml:169` „JavaMailSender statt
  Alt-Code `commons-mail`"), oder (c) die legitime Doppelbezeichnung „Portal/Backend" für das
  *aktuelle* eingebettete Portal (z. B. `deploy/CUTOVER-RUNBOOK.md`, `deploy/smoke/*`).
  `git ls-files | grep -i common` und `git ls-files | grep -i portal` liefern ausschließlich
  aktuelle Pfade (Client-Raspi-Common-Restklassen, Vaadin-`ui/`-Paket). Root-`pom.xml`,
  `backend/pom.xml`, CI und Dockerfile referenzieren nur noch die zwei realen Reactor-Module.
- **niedrig · `pom.xml:11`** · Die `<description>` des Aggregator-POMs lautet weiterhin
  „Aggregator/parent POM for the elwasys modules (**Common**, Client-Raspi, backend)" –
  das dritte, seit Phase-5-Nachtrag aufgelöste Modul wird hier noch als aktives Mitglied
  benannt, obwohl `<modules>` direkt darunter korrekt nur zwei Einträge hat. Rein kosmetisch
  (keine Build-Auswirkung), aber unnötig verwirrend beim Lesen der POM. **Empfehlung:**
  Beschreibung auf „Client-Raspi, backend (das frühere Common-Modul ist in Client-Raspi
  aufgegangen)" oder ähnlich aktualisieren.

### Kategorie 3 – Referenz-Konsistenz

- **Keine Funde bei `ullriti/elwasys` / GHCR:** `git grep -c "ullriti/elwasys"` liefert 23
  Treffer in Doku/Skripten/Compose/Helm, keiner davon widersprüchlich; alle `kabieror`-Treffer
  außerhalb von Java-Package-Namen sind bewusst dokumentierte Historie (CHANGELOG-Eintrag zur
  Vereinheitlichung, `docs/kb/00-overview.md` „Ursprünglicher Autor", Issue-#75-Verweise in
  `docs/kb/05-migration-plan.md`/`docs/kb/README.md`). `maven-publish.yml` leitet den
  GHCR-Image-Namen dynamisch aus `github.repository_owner` ab statt einen Owner hart zu
  kodieren – kann also nicht auseinanderlaufen. README-Setup-Befehl und Helm-`values.yaml`
  zeigen konsistent auf `ullriti/elwasys` bzw. `ghcr.io/ullriti/elwasys-backend`.
- **niedrig · `deploy/CUTOVER-RUNBOOK.md:15`** · Linktext `docs/kb/05-migration-plan.md`,
  aber Ziel-Href `../kb/05-migration-plan.md`. Da die Datei unter `deploy/` liegt, löst
  `../kb/…` auf `<Repo-Wurzel>/kb/05-migration-plan.md` auf – existiert nicht (richtig wäre
  `../docs/kb/05-migration-plan.md`). Automatisiert per Link-Check aller `.md`-Dateien
  gegen das Dateisystem verifiziert. **Empfehlung:** Href auf `../docs/kb/05-migration-plan.md`
  korrigieren.
- **niedrig · `docs/agent-setup.md:42`** · Tabellenzeile verlinkt `docs/kb/`
  auf `docs/kb/README.md`, obwohl die Datei selbst schon unter `docs/` liegt (die
  Nachbarzeilen der selben Tabelle verlinken korrekt relativ ohne `docs/`-Präfix, z. B.
  `worklog/README.md`, `specs/README.md`). Der Link löst auf `docs/docs/kb/README.md` auf und
  ist damit tot. **Empfehlung:** auf `kb/README.md` korrigieren (analog zu den Nachbarzeilen).
- Alle übrigen ca. 340 relativen `.md`-Querverweise im Repo wurden automatisiert gegen das
  Dateisystem aufgelöst; außer den beiden oben genannten war jeder Treffer gültig
  (inkl. ADR-Index, Spec-Index, Worklog-Index, KB-Inhaltsverzeichnis).

### Kategorie 4 – Nicht-hingehörendes

- **Keine Funde.** `git ls-files | grep -E "node_modules|/target/|\\.class$|\\.jar$|\\.log$"`
  und die Suche nach `.idea/`, `.DS_Store`, `.iml` liefern 0 Treffer. `.vscode/` ist laut
  `.gitignore`-Kommentar bewusst eingecheckt (geteilte Empfehlungen) und enthält nur
  harmlose `settings.json`/`extensions.json`. Größte getrackte Dateien
  (`git ls-files -z | xargs -0 du -b | sort -rn`) sind ausschließlich erwartbare Doku
  (`docs/kb/05-migration-plan.md`, 341 KB Text), zwei Screenshots (~120 KB, ~79 KB) und
  Font-/Bild-Assets des Terminals (≤ 112 KB) – keine Binärdatei sticht unangemessen heraus,
  keine Build-Artefakte oder Fremd-Repos.
- Kein Fund von hartkodierten Secrets/Tokens/Passwörtern in getrackten `.yml`/`.yaml`/
  `.properties`-Dateien (heuristischer Regex-Scan, alle Treffer waren Platzhalter wie
  `changeme-strong-password` oder `elwt_replace-with-a-real-token`).

### Kategorie 5 – `.gitignore`/`.env`

- **hoch · `deploy/compose/.env.example` + `deploy/helm/elwasys-backend/values.yaml` +
  `deploy/helm/elwasys-backend/templates/configmap.yaml`** · `elwasys.password-reset.portal-base-url`
  (`ELWASYS_PORTAL_BASE_URL` in `application.yml`, Default `http://localhost:8080`) wird in
  **keinem** der beiden Deployment-Wege gesetzt: `docker-compose.yml`s `backend`-Service
  reicht diese Variable nicht in seinem `environment:`-Block durch (anders als
  `ELWASYS_DB_*`/`ELWASYS_SMTP_*`/`ELWASYS_AUTH_REHASH_ON_LOGIN`, die alle explizit gelistet
  sind) – selbst ein Eintrag in `.env` hätte daher **keine Wirkung**, weil Compose
  Umgebungsvariablen aus `.env` nur für explizit referenzierte `${...}`-Platzhalter im
  Compose-File selbst einsetzt, nicht pauschal in den Container durchreicht. Das optionale
  TLS-Overlay (`docker-compose.proxy.yml`) setzt zwar `ELWASYS_PUBLIC_HOSTNAME` für Caddy,
  das ist aber eine andere Variable und erreicht das Backend nicht. Der Helm-Chart hat
  dieselbe Lücke: `templates/configmap.yaml` listet `ELWASYS_PORTAL_BASE_URL` ebenfalls
  nicht, `values.yaml` hat dafür keinen strukturierten Wert (nur den generischen
  `extraEnv: []`-Escape-Hatch, ohne Hinweis/Beispiel für gerade diesen Fall). Da
  `elwasys.password-reset.enabled` **standardmäßig AN** ist (siehe `application.yml`),
  würde ein produktiver Compose- oder Helm-Betrieb ohne manuellen `extraEnv`-Eintrag
  Passwort-Reset-Mails mit einem Link auf `http://localhost:8080/...` verschicken – für
  echte Empfänger nutzlos bzw. irreführend. **Empfehlung:** `ELWASYS_PORTAL_BASE_URL` in
  `docker-compose.yml` (`backend.environment`) und in `deploy/helm/elwasys-backend/templates/configmap.yaml`
  (+ passender `values.yaml`-Eintrag, z. B. `portalBaseUrl`) aufnehmen, `.env.example` um den
  Hinweis ergänzen, dass dieser Wert für Produktion zwingend auf die öffentliche URL gesetzt
  werden muss (analog zu `ELWASYS_PUBLIC_HOSTNAME`).
- **niedrig · `.env.example` (Repo-Wurzel) vs. `docs/kb/04-build-and-run.md`/`application.yml`** ·
  Die Wurzel-`.env.example` nennt als Backend-Beispiel `SPRING_DATASOURCE_URL`/
  `_USERNAME`/`_PASSWORD` (generische Spring-Relaxed-Binding-Namen), während
  `application.yml` und `deploy/compose/.env.example` durchgängig die eigenen Variablen
  `ELWASYS_DB_URL`/`ELWASYS_DB_USER`/`ELWASYS_DB_PASSWORD` verwenden. Beide Wege
  funktionieren technisch (Umgebungsvariablen haben in Spring Boot Vorrang vor
  `application.yml`), sind aber zwei unterschiedlich benannte Stellschrauben für dieselbe
  Einstellung, dokumentiert an zwei verschiedenen Stellen – potenziell verwirrend für
  jemanden, der beide Dateien liest. Die Wurzel-Datei weist zwar selbst darauf hin, nur ein
  „zentraler Platzhalter/Startpunkt" zu sein und verweist auf `deploy/compose/.env.example`
  als eigentliche Quelle – das entschärft es, macht die Doppelbenennung aber nicht
  verschwinden. **Empfehlung:** Wurzel-`.env.example` entweder auf die `ELWASYS_*`-Namen
  umstellen oder explizit vermerken, dass das nur ein alternativer (Spring-nativer)
  Override-Weg ist.
- **niedrig · `.dockerignore`** · Enthält einen eigenständigen Eintrag `doc/` (Singular)
  neben dem korrekten `docs/kb/`-Eintrag zwei Zeilen darüber. Im Repo existiert nur `docs/`
  (verifiziert: `find . -maxdepth 1 -iname doc` liefert nichts), der `doc/`-Eintrag matcht
  also nie etwas – wirkungslose Zeile, vermutlich ein Tippfehler-Rest. **Empfehlung:** Zeile
  entfernen (kein Schaden, aber unnötige Verwirrung beim nächsten Lesen).
- Ansonsten deckt `.gitignore` alle beim Bauen/Testen tatsächlich entstehenden Pfade ab, u. a.
  `target/`, Vaadin-generierte Frontend-Artefakte (`backend/src/main/frontend/generated/`,
  `backend/node_modules/`, generierte `package.json`/`package-lock.json`/`tsconfig.json`),
  `.terminal-ready`, `.client-uid`, `elwasys.properties`, `.env`/`.env.*` (mit
  `!.env.example`-Ausnahme) und `.claude/settings.local.json`. Kein Hinweis auf beim Testen
  erzeugte, aber nicht ignorierte Dateien gefunden.

### Kategorie 6 – Doppelte/widersprüchliche Konfiguration

- Siehe Kategorie 1 (`MANIFEST.MF` vs. Assembly-Plugin-`mainClass`) und Kategorie 5
  (Env-Var-Namensdopplung DB-Verbindung) – beide bereits oben aufgeführt, um Redundanz in
  diesem Report zu vermeiden.
- **Keine widersprüchlichen Versions-/Werte-Duplikate gefunden** bei den Stichproben, die
  typischerweise auseinanderlaufen: PostgreSQL-Version (`postgres:16` in
  `deploy/compose/docker-compose.yml` **und** `postgres:16-alpine` in
  `backend/src/test/java/.../TestPostgres.java` – konsistent), Java-Version (21 durchgängig
  in `pom.xml`, allen drei `setup-java`-Schritten in `ci.yml`, `maven-publish.yml`,
  `backend/Dockerfile`), Vaadin-Version (einzige Quelle `<vaadin.version>24.10.8</vaadin.version>`
  in `backend/pom.xml`), Zeitzone `Europe/Berlin` (identisch in `application.yml`-Kontext,
  `backend/Dockerfile`, `docker-compose.yml`, Helm-`values.yaml`/`configmap.yaml` – jeweils
  mit Verweis aufeinander begründet). Compose und Helm setzen für die übrigen, tatsächlich
  gespiegelten Einstellungen (Notifications AUS, Auth-Rehash AUS) identische Defaults ohne
  Diskrepanz.

## 3. Was geprüft wurde und sauber war

- **Alle 22 Shell-Skripte** (`.claude/hooks/`, `Client-Raspi/`, `backend/`, `deploy/cutover/`,
  `deploy/smoke/`, `deploy/terminal/`, `scripts/`) sind nachweislich aus Doku, CI oder
  anderen Skripten referenziert (je Skript per `git grep -l <Basisname>` gegengeprüft,
  0 Treffer bei keinem).
- Repo-Root-Dateien (`.dockerignore`, `.editorconfig`, `.env.example`, `.gitattributes`,
  `.gitignore`, `CNAME`, `LICENSE.md`) sind vollständig, passend referenziert
  (`docs/kb/00-overview.md` verweist korrekt auf `CNAME`/`LICENSE.md`) und ohne Duplikate.
- Verzeichnisstruktur folgt durchgängig dem Verzeichnis-Guide aus `AGENTS.md` Abschnitt 6 –
  keine Datei an einem Ort gefunden, der diesem widerspricht (Backend-Schichtung
  `domain`/`repository`/`service`/`api`/`ui`/`auth`/`ws`/`health`/`events`/`offline`/
  `notification`/`config`/`demo` konsistent unter `backend/src/main/java/...`; Terminal
  behält seine bestehende Paketstruktur inkl. der sechs Ex-`Common`-Klassen).
- ADR-Index (`docs/architecture/README.md`, 21 Einträge) und Spec-Index
  (`docs/specs/README.md`) stimmen 1:1 mit den tatsächlich vorhandenen Dateien überein.
- `.github/workflows/ci.yml` und `maven-publish.yml`: jeder Job/Step referenziert
  existierende Pfade/Skripte, keine toten Referenzen auf das entfernte Alt-Portal- oder
  `Common`-Modul.
- Font-/Bild-Assets in `Client-Raspi/res/` (`img/*.png`, `fontawesome-webfont.ttf`,
  `logback.xml`) sind trotz Ähnlichkeit zu Kopien im Java-Resource-Baum tatsächlich
  unterschiedlich genutzt (Klassenpfad-Root für FXML-`@/img/...`-Referenzen und den
  Logback-Autodiscovery-Default) und daher **kein** Duplikat-Fund – einzige Ausnahme ist die
  oben gemeldete `MANIFEST.MF`.
- Keine leeren, getrackten Verzeichnisse (Git trackt ohnehin keine leeren Verzeichnisse;
  auch keine `.gitkeep`-Leichen ohne Zweck gefunden).
- Keine Hinweise auf versehentlich committete Zugangsdaten, private Schlüssel oder
  Kunden-/Produktivdaten.
