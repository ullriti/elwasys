-- DB-Härtung (Phase 5 AP2, siehe docs/kb/05-migration-plan.md): entfernt das in
-- V1__baseline_schema_0_4_0.sql (Alt-Weg: Common/resources/database-init.sql) fest
-- eingebettete Default-Admin-Passwort (Klartext "admin", als SHA1-Hash gespeichert).
--
-- Die WHERE-Klausel prüft zusätzlich zu "username = 'admin'" explizit den unveränderten
-- SHA1-Hash von "admin" ('d033e22ae348aeb5660fc2140aec35850c4da997') - Verhalten-bewahren-
-- Gebot: ein Betreiber, der das Admin-Passwort bereits (über das Alt-Portal oder das neue
-- Portal) geändert hat, ist von dieser Migration NICHT betroffen, sein Passwort bleibt exakt
-- wie es ist. Nur eine Installation, die noch das Ausliefer-Default-Passwort trägt, verliert
-- es hier (password wird NULL - PasswordVerificationService#verify liefert für einen NULL-
-- Hash immer "kein Treffer", ein Login mit "admin"/"admin" ist damit nicht mehr möglich).
--
-- Für eine FRISCHE Installation (V1 legt den admin-Benutzer mit genau diesem Default-Hash an,
-- V7 läuft direkt im Anschluss) bedeutet das: der admin-Benutzer hat ab sofort KEIN bekanntes
-- Passwort mehr. Es wird über das neue Admin-CLI gesetzt (siehe
-- org.kabieror.elwasys.backend.auth.AdminPasswordCliRunner, Profil "admin-cli",
-- docs/kb/04-build-and-run.md für das vollständige Kommando) - bewusste, vom Auftraggeber
-- bestätigte Verhaltensänderung NUR für Neuinstallationen (siehe docs/kb/05-migration-plan.md
-- "Entscheidungen").
UPDATE users
SET password = NULL
WHERE username = 'admin'
  AND password = 'd033e22ae348aeb5660fc2140aec35850c4da997';
