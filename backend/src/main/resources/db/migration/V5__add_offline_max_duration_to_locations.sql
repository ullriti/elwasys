-- Phase 4 AP6 (siehe kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal"
-- und "Festlegungen zu den Offline-Detailfragen"): pro Standort konfigurierbare maximale
-- Offline-Dauer ("offline.max-duration"). Rein additiv (neue, NOT NULL-Spalte mit Default,
-- keine bestehende Spalte wird angefasst) - der Alt-Code bekommt von dieser Spalte nichts
-- mit.
--
-- Auftraggeber-Vorgabe (siehe kb/05-migration-plan.md, "Entscheidungen (Auftraggeber)"
-- 2026-07-21): Default 60 Minuten, muss über das Portal (Standorte-Dialog) konfigurierbar
-- sein. Nach Ablauf dieser Zeitspanne akzeptiert ein Terminal ohne Backend-Verbindung keine
-- neuen Buchungen mehr (Fehlerbild wie C15); laufende Ausführungen werden weiterhin lokal zu
-- Ende geführt und nach Wiederverbindung nachgemeldet, unabhängig vom Zeitfenster.
ALTER TABLE locations
    ADD COLUMN offline_max_duration_minutes INTEGER NOT NULL DEFAULT 60;
