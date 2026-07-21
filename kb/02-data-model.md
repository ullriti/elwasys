# 02 – Datenmodell (PostgreSQL)

Quelle: `Common/resources/database-init.sql` (DB-Version `0.4.0`).
Upgrades (historisch, seit Phase 2 AP1 nicht mehr fortgeschrieben – siehe „Flyway-Baseline“
unten): `Common/resources/database-upgrade/upgrade_0.3.1_0.3.2.sql`, `upgrade_0.4.0.sql`.

## ER-Überblick

```
user_groups ─┬─< users >──┬─< credit_accounting
             │            │
             ├─< locations_valid_user_groups >── locations ──< devices
             ├─< devices_valid_user_groups >── devices
             └─< programs_valid_user_groups >── programs
                                                  │
devices ──< device_program_rel >── programs      │
executions >── devices, programs, users ─────────┘
```
(`reservations` – App-Relikt der mobilen App, in Phase 5 AP4 entfernt, siehe unten.)

## Tabellen

### config
Schlüssel/Wert-Konfiguration.
- `key` (unique), `value`
- Seeds: `db.version=0.4.0`, ~~`authkey.prefix` (2 Zufallszeichen), `reservation.duration=900`
  (Sek.)~~ *(entfernt in Phase 5 AP4, `V10__drop_app_remnants.sql` – App-Relikte, siehe
  „App-Reste“ unten)*

### user_groups
Benutzergruppen mit Rabattregel.
- `id`, `name`
- `discount_type` ENUM `DISCOUNT_TYPE` = `NONE` | `FIX` | `FACTOR`
- `discount_value` (double)
- Seed: `Default`

### users
- `id`, `name`, `username` (unique), `email`, `card_ids` (Text, mehrere RFID-IDs)
- `blocked`, `password` – ursprünglich `VARCHAR(50)` für den SHA1-Hash (40 hex); seit
  Phase 2 AP3 per Flyway-Migration `V2` auf `VARCHAR(255)` erweitert (trägt jetzt sowohl
  SHA1- als auch Argon2id-kodierte Hashes, siehe „Flyway-Baseline“ unten und
  kb/05-migration-plan.md), `is_admin`
- `email_notification`, `push_notification`, `pushover_user_key`
- `password_reset_key`, `password_reset_timeout` – seit Phase 3 AP4 aktiv vom Backend
  genutzt (`UserEntity#passwordResetKey`/`#passwordResetTimeout`, Service
  `PasswordResetService`): fachlicher Nachfolger des Alt-`PasswordForgotWindow`/
  `ResetPasswordWindow`-Flows, bewusst dieselben Bestandsspalten wiederverwendet statt einer
  neuen Migration/Tabelle (additiv im Sinne der Rahmenbedingung – Alt-Code liest/schreibt
  dieselben Spalten weiterhin unverändert, kein Konflikt bei Parallelbetrieb, siehe
  kb/05-migration-plan.md, „Entscheidungen“)
- `deleted`, `last_login`, `group_id` → user_groups (Default 1)
- ~~App-Anbindung: `app_id`, `access_key`, `auth_key` (Trigger `user_authkey_trigger`
  generiert `auth_key` beim INSERT über `generate_user_authkey()`)~~ *(entfernt in Phase 5
  AP4, `V10__drop_app_remnants.sql`: Trigger + beide Funktionen
  `user_authkey_trigger_function()`/`generate_user_authkey()` sowie die drei Spalten – die
  mobile App (`elwaapi`) ist laut Auftraggeber nicht mehr relevant, siehe
  kb/05-migration-plan.md „Entscheidungen“)*
- Seed: `admin` / Passwort-Hash `d033e22ae348aeb5660fc2140aec35850c4da997`
  (= SHA1 von „admin“), `is_admin=TRUE`

### locations
Ein Standort = ein Client-Terminal.
- `id`, `name` (unique)
- `client_uid`, `client_ip`, `client_port`, `client_last_seen` – **entfernt in Phase 5 AP3**
  (2026-07-21, `V9__drop_obsolete_location_client_columns.sql`): dienten der Alt-IP-
  Registrierung für die Fernwartung (`LocationManager`, siehe kb/01-architecture.md
  „Maintenance-Protokoll (Common)“) – seit Phase 4 AP5 läuft die Fernwartung über eine vom
  Terminal ausgehende WebSocket-Verbindung, `TerminalConnectionRegistry` im Backend hält die
  Erreichbarkeit rein in-memory (siehe kb/03-modules.md). Die Spalten waren seitdem ungenutzt
  und wurden per V9 gedroppt.
- Seed: `Default`
- `offline_max_duration_minutes` (INT, NOT NULL, Default 60) – **neu seit Phase 4 AP6**
  (2026-07-21): `offline.max-duration` dieses Standorts (Auftraggeber-Vorgabe, siehe
  kb/05-migration-plan.md „Festlegungen zu den Offline-Detailfragen“) – wie lange ein
  Terminal ohne Backend-Verbindung eigenständig neue Buchungen annimmt, bevor es sie ablehnt
  (Fehlerbild wie C15); im Portal-Standorte-Dialog editierbar
  (`LocationFormDialog`/`AdminLocationsView`), an das Terminal über `SnapshotDto
  #offlineMaxDurationMinutes()` ausgeliefert. Additive Migration
  `V5__add_offline_max_duration_to_locations.sql`.

### locations_valid_user_groups
n:m Standort ↔ erlaubte Benutzergruppen. Seed: Default-Location ↔ Default-Group.

### devices
Ein Gerät (Waschmaschine/Trockner).
- `id`, `name`, `position`, `location_id` → locations
- fhem: `fhem_name`, `fhem_switch_name`, `fhem_power_name`
- deCONZ: `deconz_uuid`
- Ende-Erkennung: `auto_end_power_threshold` (REAL, Default 0.5 W; hieß bis V7
  `auto_end_power_threashold` – Tippfehler in Phase 5 AP3 per V8 korrigiert),
  `auto_end_wait_time` (INT, Default 20 s)
- `enabled`

### devices_valid_user_groups
n:m Gerät ↔ erlaubte Benutzergruppen.

### programs
Waschprogramm / Tarif.
- `id`, `name`
- `type` ENUM `PROGRAM_TYPE` = `FIXED` | `DYNAMIC`
- `max_duration`, `free_duration` (Sek.)
- `flagfall` (Grundgebühr), `rate` (Preis pro Zeiteinheit), `time_unit`
  ENUM `TIME_UNIT_TYPE` = `SECONDS` | `MINUTES` | `HOURS`
- `auto_end`, `earliest_auto_end` (Sek.), `enabled`

### programs_valid_user_groups
n:m Programm ↔ erlaubte Benutzergruppen.

### device_program_rel
n:m Gerät ↔ verfügbare Programme.

### executions
Eine Programm-Ausführung (Waschvorgang).
- `id`, `device_id` → devices, `program_id` → programs, `user_id` → users
  (alle `ON DELETE SET DEFAULT`, Default -1)
- `start`, `stop`, `finished`

### credit_accounting
Guthaben-Buchungen.
- `id`, `user_id` → users, `execution_id` → executions (nullable)
- `amount` (numeric, +Aufladung / −Verbrauch), `date`, `description`

### ~~foreign_authkeys~~ *(entfernt in Phase 5 AP4)*
~~Verzeichnis für Föderation mit anderen Servern.~~
~~- `prefix` (Auth-Key-Prefix), `server_address`~~
*(`V10__drop_app_remnants.sql` – App-Relikt, mobile App laut Auftraggeber nicht mehr
relevant, siehe kb/05-migration-plan.md „Entscheidungen“)*

### ~~reservations~~ *(entfernt in Phase 5 AP4)*
~~Gerätereservierungen.~~
~~- `id`, `user_id` → users, `device_id` → devices, `start_time`~~
~~- Unique-Constraint (`user_id`, `device_id`)~~
*(`V10__drop_app_remnants.sql` – App-Relikt, mobile App laut Auftraggeber nicht mehr
relevant)*

### terminal_tokens *(neu, seit Phase 2 AP4)*
Standort-Tokens für die Terminal-REST-API/den WebSocket-Endpunkt (additive Migration
`V3__create_terminal_tokens.sql`, siehe „Flyway-Migrationen“ unten und
kb/05-migration-plan.md). Vom Alt-Code unbenutzt/unbekannt.
- `id`, `location_id` → locations (`ON DELETE CASCADE`)
- `token_hash` (VARCHAR(64), unique) – SHA-256-Hex-Hash des Klartext-Tokens; das Klartext-
  Token selbst wird NIE gespeichert (nur einmalig beim Erzeugen angezeigt)
- `label` (optional, rein informativ, z.B. Terminal-Hostname)
- `created_at`, `revoked_at` (NULL = aktiv), `last_used_at` (vom Auth-Filter aktualisiert)
- Mehrere aktive Tokens pro Standort zulässig (Rotation ohne Ausfallfenster: neues Token
  anlegen, Terminal umstellen, dann altes per `revoked_at` widerrufen)

### terminal_idempotency_keys *(neu, seit Phase 4 AP3)*
Dedupliziert terminal-gemeldete Execution-Ereignisse (Start/Ende/Abbruch/Reset über
`/api/v1/executions/**`, siehe kb/03-modules.md „Idempotenz + Replay" und
kb/05-migration-plan.md). Additive Migration `V4__create_terminal_idempotency_keys.sql`. Vom
Alt-Code unbenutzt/unbekannt.
- `id`, `location_id` → locations (`ON DELETE CASCADE`, rein informativ)
- `idempotency_key` (VARCHAR(64), unique) – die vom Terminal erzeugte UUID; ein Schlüssel
  identifiziert GENAU EIN fachliches Ereignis, unabhängig vom HTTP-Pfad
- `operation` (VARCHAR(50)) – z. B. `execution-start`/`-finish`/`-abort`/`-reset`, rein
  informativ/für Diagnose
- `response_status`, `response_body` (TEXT) – die beim ERSTEN, erfolgreichen Aufruf tatsächlich
  gelieferte Antwort; ein Replay liefert sie unverändert erneut aus, ohne die fachliche Aktion
  erneut auszuführen
- `created_at`
- Nur ERFOLGREICHE Aufrufe werden abgelegt (siehe `IdempotencyService`-Javadoc) – ein
  fehlgeschlagener Erstversuch „friert" nicht dauerhaft ein, ein erneuter Versuch mit demselben
  Schlüssel führt die Aktion normal erneut aus

## DB-Rollen & Rechte

**Stand seit Phase 5 AP2 (`V6__harden_db_roles.sql`, 2026-07-21):** `elwaportal` ist der
EINZIGE Anwendungs-DB-User. `elwaclient1`/`elwaapi` + die Gruppe `elwaclients` (mitsamt ihrer
Default-Passwörter) sind entfernt – das Terminal spricht seit Phase 4 AP4/AP5 nur noch über
die Backend-REST-API/den Standort-Token mit dem Backend (kein Direkt-DB-Zugriff mehr), und die
mobile App (`elwaapi`) ist laut Auftraggeber nicht mehr relevant (siehe „Entscheidungen“ in
kb/05-migration-plan.md). Die alte Rollenbeschreibung bleibt hier als historischer Kontext
dokumentiert:

- ~~**Gruppe `elwaclients`**, User `elwaclient1` (PW `elwaclient1`): SELECT auf alles;
  INSERT/UPDATE auf `executions`; UPDATE auf `locations`, `devices`; INSERT auf
  `credit_accounting`. (Terminal durfte nur Nötiges schreiben.)~~ *(entfernt, V6)*
- **User `elwaportal`**: volles SELECT/INSERT/UPDATE/DELETE, aber `REVOKE UPDATE, DELETE`
  auf `credit_accounting` (Buchungen sind unveränderlich). Unverändert seit V1.
- ~~**User `elwaapi`** (PW `api1234`): SELECT auf alles; UPDATE auf `users`; INSERT/DELETE auf
  `reservations` (für die mobile App).~~ *(DB-User entfernt in V6; die Tabelle `reservations`
  selbst wurde zusätzlich in Phase 5 AP4 per `V10` gedroppt.)*

**Cluster-weite Rollen im geteilten Test-Cluster**: `V6` läuft idempotent und fängt den Fall
ab, dass eine Rolle in DIESER Datenbank noch Rechte besitzt, aber in einer ANDEREN Datenbank
desselben Clusters (z. B. die von den Client-Raspi-Testharnesses über `database-init.sql`
geseedete `elwasys`-Test-DB, solange diese parallel existiert) noch referenziert wird – in
diesem Fall wird `DROP ROLE`/`DROP GROUP` für diesen Lauf übersprungen (`RAISE NOTICE`), die
Rechte in der aktuellen DB sind trotzdem entfernt. In einer echten Produktivumgebung mit
genau einer `elwasys`-Datenbank tritt das nicht auf, siehe Kommentarkopf der Migration.

## Migrations-relevante Beobachtungen

- Passwörter als **SHA1** gespeichert → bei Modernisierung sicherheitskritisch
  (Wechsel auf bcrypt/argon2 einplanen, Migrationspfad nötig).
- Klartext-/schwache Default-Passwörter (`elwaclient1`, `api1234`) → härten.
- Tippfehler in Spaltenname `auto_end_power_threashold` (statt *threshold*) – in Phase 5 AP3
  per `V8__rename_auto_end_power_threshold.sql` auf `auto_end_power_threshold` korrigiert.

## Flyway-Baseline (seit Phase 2 AP1, 2026-07-20)

Neues Backend-Modul (`backend/`, siehe kb/03-modules.md) übernimmt die Schemapflege künftig
über **Flyway** statt der handgepflegten SQL-Skripte. Details/Entscheidungen siehe
kb/05-migration-plan.md (Änderungslog, Phase 2 AP1); hier die für das Datenmodell relevante
Zusammenfassung:

- **Baseline-Migration**: `backend/src/main/resources/db/migration/V1__baseline_schema_0_4_0.sql`.
  Inhaltlich eine 1:1-Übernahme von `Common/resources/database-init.sql` (ohne die
  psql-only `CREATE DATABASE`/`\connect`-Zeilen, die für eine bereits per JDBC-URL gewählte
  Ziel-DB nicht gebraucht werden). **Nicht** separat aus den beiden
  `database-upgrade/*.sql`-Skripten rekonstruiert: `database-init.sql` enthält bereits deren
  Endzustand (0.4.0), ein frischer Lauf beider Wege ergibt also per Konstruktion dasselbe
  Schema – siehe Verifikation unten. Einzige technische Anpassung: die Rollenanlage
  (`CREATE GROUP`/`CREATE USER` für `elwaclients`/`elwaclient1`/`elwaportal`/`elwaapi`) ist in
  einen `DO`-Block mit Existenzprüfung (`pg_roles`) gefasst, weil PostgreSQL-Rollen
  Cluster-weit sind (nicht pro Datenbank) und ein zweiter Lauf gegen denselben Cluster sonst
  mit „role already exists“ fehlschlagen würde. Der Spaltentypo
  `auto_end_power_threashold` blieb hier bewusst erhalten (eingefrorene 0.4.0-Baseline);
  Phase 5 AP3 hat ihn per `V8` auf dem Migrationspfad vorwärts korrigiert (siehe
  Änderungslog).
- **Zwei Betriebsszenarien**, beide mit `backend/verify-schema-baseline.sh` verifiziert:
  1. **Frische, leere DB**: Flyway führt `V1` normal aus → schema-äquivalent zu einer frischen
     `database-init.sql`-DB (verifiziert per `pg_dump --schema-only`-Diff; einzige Abweichung
     ist Flywayss eigene Buchführungstabelle `flyway_schema_history` + deren PK/Index, die
     bewusst nicht Teil des Anwendungsschemas ist).
  2. **Bestehende Alt-Weg-DB** (über `database-init.sql` + ggf. `database-upgrade/*.sql`
     angelegt, mit Daten): `spring.flyway.baseline-on-migrate=true` (siehe
     `backend/src/main/resources/application.yml`) markiert sie beim ersten Start als bereits
     auf `baselineVersion=1` migriert, ohne `V1` erneut auszuführen oder Daten zu verändern.
     Verifiziert: Backend startet sauber (Health-Endpoint UP), `flyway_schema_history` enthält
     genau eine `BASELINE`-Zeile bei Version 1, Bestandsdaten (z. B. der `admin`-Nutzer)
     bleiben unverändert.
- **`config.db.version` stillgelegt**: Untersuchung von Common/Client-Raspi/Portal (Grep über
  alle drei Module) zeigt, dass **kein** Java-Code (`DataManager`, `ConfigurationManager` o.
  ä.) den Wert von `config.db.version` je liest – es gab nie einen automatischen
  Upgrade-Mechanismus im Code; die `database-upgrade/*.sql`-Skripte wurden offenbar manuell
  vom Betreiber per `psql -f` ausgeführt, `db.version` diente rein als von Hand gepflegte
  Notiz in den SQL-Skripten selbst. Verhalten bewahren heißt hier: der Seed-Wert
  `db.version = '0.4.0'` bleibt in der Flyway-Baseline erhalten (für den Fall, dass ihn
  doch irgendein Alt-Code oder externes Werkzeug liest), wird aber **ab sofort nicht mehr
  fortgeschrieben** – zukünftige Schemaänderungen laufen ausschließlich über weitere
  Flyway-Migrationen (`V2__...`, `V3__...`, …). `Common/resources/database-upgrade/*.sql`
  wird nicht mehr gepflegt; die vorhandenen Dateien (`upgrade_0.3.1_0.3.2.sql`,
  `upgrade_0.4.0.sql`) bleiben unverändert als historisches Artefakt im Repo (Bestands-DBs,
  die noch über sie hochgezogen werden, landen ohnehin beim Endstand 0.4.0, den die Baseline
  abbildet).
- **`V2__widen_users_password_column.sql`** (Phase 2 AP3, 2026-07-20): `ALTER TABLE users
  ALTER COLUMN password TYPE VARCHAR(255)` (war `VARCHAR(50)`). Befund: Argon2id-kodierte
  Passwort-Hashes (neues Format, siehe kb/03-modules.md „Auth“) sind mit Spring Securitys
  empfohlenen Parametern empirisch gemessen konstant 97 Zeichen lang – mehr als das
  Doppelte der bisherigen, exakt auf 40-Zeichen-SHA1-Hex zugeschnittenen Spaltenbreite.
  Additiv/abwärtskompatibel: der Alt-Code prüft die Spaltenlänge nirgends selbst, ein
  SHA1-Hash passt weiterhin klaglos in die breitere Spalte – Parallelbetrieb bleibt
  unangetastet. Wird bei jedem Flyway-Lauf gegen eine Bestands-DB automatisch nach dem
  `baselineOnMigrate`-Baseline-Schritt mit angewendet. Details/Abwägung siehe
  kb/05-migration-plan.md („Entscheidungen“, AP3).
- **Rollen/Grants**: Die DB-Rollen `elwaclient1`/`elwaportal`/`elwaapi` (siehe „DB-Rollen &
  Rechte“ oben) wurden von der Baseline (V1) zunächst unverändert mit angelegt/gegrantet – das
  Backend selbst nutzte sie in AP1 noch nicht (es hatte noch keine fachlichen Endpunkte). Die
  Ablösung durch einen einzelnen technischen Backend-User (`elwaportal`) ist seit Phase 5 AP2
  (`V6`) umgesetzt, siehe „DB-Rollen & Rechte“ oben.
- **`V3__create_terminal_tokens.sql`** (Phase 2 AP4, 2026-07-20): neue Tabelle
  `terminal_tokens` (siehe „Tabellen“ oben) für die Standort-Token-Auth der Terminal-API.
  Rein additiv (neue Tabelle, keine Änderung an Bestandstabellen) – der Alt-Code bekommt
  davon nichts mit. Details/Entscheidungen (Hash statt Klartext, Rotation über mehrere
  aktive Tokens) siehe kb/05-migration-plan.md.
- **`V4__create_terminal_idempotency_keys.sql`** (Phase 4 AP3, 2026-07-21): neue Tabelle
  `terminal_idempotency_keys` (siehe „Tabellen“ oben) für die Deduplizierung terminal-gemeldeter
  Execution-Ereignisse. Rein additiv (neue Tabelle, keine Änderung an Bestandstabellen) – der
  Alt-Code bekommt davon nichts mit. Details siehe kb/03-modules.md „Idempotenz + Replay“ und
  kb/05-migration-plan.md.
- **`V5__add_offline_max_duration_to_locations.sql`** (Phase 4 AP6, 2026-07-21): neue Spalte
  `locations.offline_max_duration_minutes` (siehe „Tabellen“ oben, `locations`). Rein additiv
  (`NOT NULL`-Spalte mit `DEFAULT 60`, keine Bestandsspalte geändert) – der Alt-Code bekommt
  davon nichts mit. Details siehe kb/03-modules.md „Offline-Robustheit (AP6)“ und
  kb/05-migration-plan.md.
- **`V6__harden_db_roles.sql`** (Phase 5 AP2, 2026-07-21): entfernt `elwaclient1`/`elwaapi` +
  Gruppe `elwaclients` (idempotent, cluster-weite Rollen-Problematik abgefangen, siehe „DB-
  Rollen & Rechte“ oben). `elwaportal` unverändert.
- **`V7__remove_default_admin_password.sql`** (Phase 5 AP2, 2026-07-21): setzt
  `users.password` für den Seed-`admin`-Benutzer auf `NULL`, aber NUR wenn er noch den
  unveränderten Default-SHA1-Hash von „admin“ trägt (Verhalten-bewahren für Bestände, die das
  Passwort bereits geändert haben). Frische Installationen haben danach kein bekanntes
  Admin-Passwort mehr – es wird über das neue Admin-CLI gesetzt (`AdminPasswordCliRunner`,
  Profil `admin-cli`, Kommando siehe kb/04-build-and-run.md). Details/Entscheidung siehe
  kb/05-migration-plan.md.

## JPA-Entities (seit Phase 2 AP2, 2026-07-20)

Details zu den Entities/Repositories/Services siehe kb/03-modules.md (Abschnitt Backend)
und kb/05-migration-plan.md (Änderungslog, AP2). Für das Datenmodell relevante Erkenntnisse:

- **Postgres-native Enums** (`DISCOUNT_TYPE`, `PROGRAM_TYPE`, `TIME_UNIT_TYPE`) lassen sich
  nicht per einfachem `@Enumerated(STRING)` gegen eine Postgres-ENUM-Spalte binden (Fehler
  „column is of type … but expression is of type character varying“) – die Entities nutzen
  dafür Hibernates `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`.
- ~~**`auth_key`-Trigger bleibt wirksam**: die Spalte ist zwar bewusst nicht in `UserEntity`
  gemappt (siehe Rahmenbedingungen zu den App-Relikt-Spalten), der DB-Trigger
  `user_authkey_trigger` (`BEFORE INSERT ON users`) befüllt sie bei jedem per JPA
  ausgeführten INSERT trotzdem automatisch – verifiziert (keine NOT-NULL-/Constraint-
  Verletzung beim Anlegen eines `UserEntity` über den Backend-Testlauf).~~ *(Trigger +
  Spalte in Phase 5 AP4 per `V10` entfernt; `UserEntity`-INSERTs laufen seither ohne
  Trigger-Nebenwirkung.)*
- **`credit_accounting.date`**: die Spalte hat einen `CURRENT_TIMESTAMP`-DB-Default, den
  der Alt-Code nie explizit überschreibt. `CreditAccountingEntryEntity` setzt den Wert
  stattdessen bewusst per Anwendungs-Uhr – siehe kb/05-migration-plan.md (Änderungslog,
  AP2) für die Begründung.
- **Alle fachlich genutzten Assoziationen sind `FetchType.EAGER`** (kein `LAZY`), um den
  Alt-`DataManager` nachzubilden, der beim Laden eines Objekts immer sofort alle
  referenzierten Objekte mitlädt – siehe kb/05-migration-plan.md (Änderungslog, AP2).
