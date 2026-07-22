# 7. Passwort-Hashing auf Argon2id mit transparenter Re-Hash-Migration

- **Status:** accepted
- **Datum:** 2026-07-20

## Kontext

Bestandspasswörter liegen als unsichere SHA1-Hex-Hashes in `users.password`
(`VARCHAR(50)`). Der Alt-Code verifiziert per String-Vergleich
(`this.password.equals(Utilities.sha1(password))`) und schreibt SHA1 direkt in dieselbe
Spalte. Solange das Alt-Portal parallel läuft, muss sich ein Nutzer dort weiter einloggen
können (harte Rahmenbedingung: Nutzer dürfen sich nicht umstellen müssen).

## Entscheidung

Passwörter werden auf **Argon2id** (Spring Security,
`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`) umgestellt. Der
Migrationspfad – Format-Erkennung, SHA1-Verifikation, transaktionaler Re-Hash beim ersten
erfolgreichen Login – ist vollständig implementiert und getestet, steht aber hinter dem
Konfig-Flag **`elwasys.auth.rehash-on-login` (Default AUS)**. Aktiviert wird das Flag erst
beim Portal-Cutover, wenn das Alt-Portal abgeschaltet wird, da ein re-gehashter Wert vom
SHA1-String-Vergleich des Alt-Codes nicht mehr verstanden würde. Jedes Neusetzen eines
Passworts über das neue Backend erzeugt bereits Argon2id.

## Konsequenzen

- Sichere Hashes ohne Zwangs-Reset für die Nutzer.
- Bewusst akzeptiert: wer sein Passwort über das neue Portal ändert/zurücksetzt, kann sich
  danach nicht mehr am Alt-Portal anmelden – vor der Endnutzer-Freigabe zu kommunizieren.
- Die Spalte wurde additiv auf `VARCHAR(255)` verbreitet (Argon2id-Strings ~97 Zeichen).

Herkunft: docs/kb/05-migration-plan.md, Abschnitt Entscheidungen (AP3, Auth; AP4).
