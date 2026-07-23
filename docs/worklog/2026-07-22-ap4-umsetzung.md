# 2026-07-22 — AP4 (Auth & Security): Umsetzung

**Ziel:** Das Pre-Launch-Arbeitspaket **AP4 „Auth & Security"** (Epic #66) umsetzen – die drei
in [ADR 0018](../architecture/0018-ap4-auth-security-entscheidungen.md) geklärten
🧩-Entscheidungen **plus** die nicht-entscheidungsbedürftigen Härtungen. Ein PR gegen `master`,
Branch `claude/ap4-fragen-klaren-kq6qvv`. Eng gekoppelte Auth-Naht → bewusst in **einem**
Backend-Kontext (delegiert an den `backend`-Spezialisten), nicht über parallele Agenten
(Kollision auf geteilten Dateien `PasswordResetService`/`UserService`/`UserRepository`/
`ElwasysAuthenticationProvider`).

## Erledigt (Backend)

**MAJOR**
- **#21 Regex-Injection Kartenlogin:** `UserRepository.findByCardId` regex-frei
  (`:cardId = ANY(string_to_array(card_ids, E'\n'))`), `CardLoginRequest.cardId` mit
  `@Pattern("[0-9A-Fa-f]{1,50}")` (Dezimal-Karten ⊂ Hex, sichere Obermenge); derselbe
  regex-freie Pfad in `assertCardIdsAreFree`. `.*` wird an der API-Grenze mit 400 abgewiesen.
- **#23 Case-insensitiver Username-Konflikt:** `existsByUsernameIgnoreCaseAndDeletedFalse` +
  `assertUsernameIsFree`-Guard in `UserService.create/update` (eigene Id ausgenommen),
  `DuplicateUsernameException`, Anzeige im `UserFormDialog`. **Keine** `LOWER(username)`-
  Migration (Altbestand kann Kollisionen enthalten → Flyway würde scheitern); Restrisiko der
  TOCTOU-Race bewusst akzeptiert und dokumentiert.
- **#25 Brute-Force-Limit Login:** neue wiederverwendbare `RateLimiter`-Komponente (In-Memory,
  fixed window, injizierbarer `Clock`; kein bucket4j) + `ClockConfig`. `ElwasysAuthentication\
  Provider` sperrt nach N Fehlversuchen (Default 5/15 min, `AuthProperties`) **vor** der
  Passwortprüfung, Reset bei Erfolg, Zähler nur für existierende Nutzer.
- **#26 Fernwartung Standort-Validierung:** `completeIfPending(senderLocationId, message)` prüft
  gegen `pendingRequestLocations`, bevor das `CompletableFuture` erfüllt wird.

**MINOR / 🧩**
- **#24 Reset-Enumeration + Rate-Limit (ADR 0018):** `requestReset` still bei unbekannter
  Adresse, `UserNotFoundForEmailException` raus, Versand-Cooldown über dieselbe `RateLimiter`;
  `PasswordForgotDialog` immer neutrale Meldung.
- **#44 Passwort-Mindestlänge ≥ 8 (ADR 0018):** zentral in `PasswordService.setNewPassword`
  (`PasswordTooShortException`); `ResetPasswordView`/`ChangePasswordDialog` spiegeln Meldung.
- **#42** deconz-uuid `@NotBlank @Size(max=64)` + `@Valid` → 400. **#45** `last_used_at` nur
  alle 5 min geschrieben (`Clock`-basiert). **#46** Alphabet-Tippfehler korrigiert (Klartext-
  Admin-Mail bewusst bewahrt, Restrisiko dokumentiert). **#47** `findByEmailIgnoreCase…` →
  `List`, `requestReset` schreibt alle Treffer an. **#48** Session-Invalidierung bewusst als
  Restrisiko dokumentiert statt riskanter `SessionRegistry`-Änderung. **#43** nur Doku
  (Restrisiko + Runbook).

## Review-Gate (`code-reviewer`) – Findings behoben
- **MAJOR (Enumeration-Orakel):** Die Login-Sperre warf eine eigene `LockedException`/Meldung,
  die – da der Zähler nur für existierende Konten läuft – reale Konten markiert hätte. **Fix:**
  Sperre wirft jetzt dieselbe generische `BadCredentialsException` wie ein Fehlversuch (Test
  `lockoutIsIndistinguishableFromAWrongPasswordFailure` sichert das ab).
- **MINOR:** Reset-Cooldown wird erst **nach** erfolgreichem Versand markiert (fehlgeschlagener
  SMTP blockiert keinen legitimen Retry mehr). Javadoc `CardLoginRequest` korrigiert
  (Dezimal-Karten, Hex als Obermenge). TOCTOU-Race (#23) und Timing-Orakel (#24/#25) als
  akzeptierte Restrisiken in der KB festgehalten.

## Entscheidungen / Restrisiken
- Login-Sperre neutral (keine Enumeration über die Meldung); verbleibendes **Timing**-Orakel
  (Login/Reset) bewusst akzeptiert. #23 ohne DB-Migration (Altbestand). #46 Klartext-Admin-Mail
  bewahrt. #48 Session-Invalidierung nicht implementiert. Alle in
  [`docs/kb/05-migration-plan.md`](../kb/05-migration-plan.md) „Restrisiken Auth & Security"
  und ADR 0018.

## Tests
- Backend-Suite grün: **243 Tests, 0 Fehler** (`backend/run-backend-tests.sh`, lokales
  PostgreSQL). Neu/erweitert u. a.: `ElwasysAuthenticationProviderBruteForceTest` (Lockout +
  Anti-Enumeration, deterministisch via `MutableClock`),
  `TerminalMaintenanceServiceLocationScopeTest` (#26), `UserRepositoryCardIdTest` +
  `CardLoginControllerTest` (#21), `UserServiceTest` (#23), `PasswordResetServiceTest`
  (#24/#47), `PasswordServiceTest` (#44), `TerminalTokenServiceTest` (#45),
  `DeviceControllerTest` (#42).

## Offen / nächster Schritt
- Restliche Pre-Launch-Arbeitspakete AP5 (Portal-Performance/CRUD/Datenmodell) und AP6
  (Deployment/Betrieb/Cutover, inkl. Vaadin-Lizenz-🧩) je als eigener PR; **AP7 (KB)** zuletzt.

## Referenzen
- Epic: https://github.com/ullriti/elwasys/issues/66 ; Issues #21/#23/#24/#25/#26/#42/#44/#45/#46/#47/#48
- ADR: [0018](../architecture/0018-ap4-auth-security-entscheidungen.md) (aktualisiert),
  [0008](../architecture/0008-api-auth-standort-token-und-admin-session.md) (korrigiert)
- Branch: `claude/ap4-fragen-klaren-kq6qvv`
