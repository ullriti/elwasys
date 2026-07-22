-- Cutover: Review potenziell obsoleter "locations"-Zeilen (Phase 6 AP1, siehe
-- kb/05-migration-plan.md "Produktivumschaltung").
--
-- READ-ONLY: dies ist eine reine SELECT-Abfrage, kein Automatismus. Die spaltenweise
-- Alt-Registrierungs-Bereinigung (locations.client_uid/client_ip/client_port/client_last_seen)
-- erledigt bereits Flyway V9__drop_obsolete_location_client_columns.sql automatisch beim
-- ersten Backend-Start gegen die Bestands-DB - dieses Skript geht es NICHT um Spalten,
-- sondern um ganze Standort-ZEILEN, die im Lauf der Zeit angelegt, aber nie produktiv benutzt
-- wurden (z.B. Test-/Seed-Standorte) und vor dem Cutover manuell bereinigt werden könnten.
--
-- Ausführen z.B. mit:
--   psql -h <host> -p <port> -U elwaportal -d elwasys -f deploy/cutover/04-review-obsolete-locations.sql
-- (Verbindungsdaten wie bei den anderen deploy/cutover/*.sh Skripten, siehe README.md.)
--
-- Ein Standort gilt hier als "obsolet-verdächtig", wenn er WEDER Geräte NOCH ein aktives
-- Standort-Token hat - d.h. es hängt aktuell kein Terminal und kein Gerät an ihm. Das seit
-- database-init.sql/V1 mitgelieferte Seed "Default" wird zusätzlich markiert, weil es
-- typischerweise nie umbenannt/mit echten Daten befüllt wurde, wenn eine Installation von
-- Anfang an eigene Standortnamen verwendet hat.
SELECT
    l.id,
    l.name,
    (SELECT COUNT(*) FROM devices d WHERE d.location_id = l.id) AS device_count,
    (SELECT COUNT(*) FROM terminal_tokens t WHERE t.location_id = l.id AND t.revoked_at IS NULL)
        AS active_token_count,
    (l.name = 'Default') AS is_default_seed,
    (
        (SELECT COUNT(*) FROM devices d WHERE d.location_id = l.id) = 0
        AND (SELECT COUNT(*) FROM terminal_tokens t
             WHERE t.location_id = l.id AND t.revoked_at IS NULL) = 0
    ) AS obsolete_candidate
FROM locations l
ORDER BY obsolete_candidate DESC, l.id;

-- ----------------------------------------------------------------------------------------
-- Löschen ist bewusst NICHT Teil dieser Abfrage - nichts wird hier automatisch entfernt.
-- Falls nach manueller Prüfung der Ausgabe oben tatsächlich ein Standort gelöscht werden
-- soll, das folgende Template als eigene, bewusste Aktion ausführen (id aus der Abfrage oben
-- einsetzen; vorher unbedingt ein DB-Backup ziehen, siehe README.md). Läuft in einer
-- Transaktion, damit man sich das Ergebnis vor dem COMMIT ansehen kann:
--
-- BEGIN;
-- -- locations_valid_user_groups.location_id hat "ON DELETE CASCADE" (siehe database-init.sql/
-- -- V1__baseline_schema_0_4_0.sql), räumt sich also von selbst mit auf. devices.location_id
-- -- hat dagegen KEINE Kaskade (NOT NULL, Default 1, ohne ON DELETE) - ein Standort mit noch
-- -- vorhandenen Geräten (device_count > 0 oben) lässt sich daher ohnehin nicht einfach
-- -- löschen (Fremdschlüsselverletzung), was für "obsolete_candidate"-Zeilen per Definition
-- -- (device_count = 0) kein Thema ist:
-- -- DELETE FROM locations WHERE id = <id>;
-- -- Ergebnis prüfen, dann:
-- -- COMMIT;   -- oder ROLLBACK; falls doch nicht gewünscht
