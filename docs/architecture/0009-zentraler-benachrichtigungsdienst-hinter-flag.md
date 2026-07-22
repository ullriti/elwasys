# 9. Zentraler Benachrichtigungsdienst hinter Konfig-Flag

- **Status:** accepted
- **Datum:** 2026-07-20

## Kontext

Heute verschickt jedes Terminal selbst E-Mails (SMTP) und Pushover-Nachrichten und braucht
dafür SMTP-Zugangsdaten. Im Parallelbetrieb (Phase 2–4) versendet der Alt-Client
(`ExecutionFinisher`) für jedes Programmende weiterhin selbst – ein zusätzlicher Versand
durch das Backend für dasselbe Ereignis wäre ein Doppelversand.

## Entscheidung

Benachrichtigungen werden **zentral im Backend** verschickt (`NotificationService`), auf
Basis von `spring-boot-starter-mail`/`JavaMailSender` (statt commons-email) und einem
selbst geschriebenen Pushover-Client (`java.net.http`, statt der unmaintainten
Alt-Bibliothek). Der Versand steht hinter dem Flag **`elwasys.notifications.enabled`
(Default AUS)**; scharfgeschaltet wird er in Phase 4, wenn zugleich der Alt-Versand
abgeschaltet wird. Scope ist bewusst **SMTP + Pushover**; der elwaApp/Ionic-Kanal
(gehört zur abzubauenden mobilen App) wird nicht portiert. Interaktive
Passwort-Reset-Mails hängen an einem eigenen Schalter
(`elwasys.password-reset.enabled`, Default AN), da sie kein automatisch doppelt
ausgelöstes Ereignis sind.

## Konsequenzen

- Terminals brauchen keine SMTP-Zugangsdaten mehr.
- Kein Doppelversand im Parallelbetrieb (Flag verhindert es).
- Der Pushover-API-Token ist konfigurierbar statt im Code hartkodiert.
- Fallstrick dokumentiert: `users.push_notification` ist NICHT das Pushover-Opt-in
  (das ergibt sich aus `pushover_user_key`).

Herkunft: docs/kb/05-migration-plan.md, Abschnitt Entscheidungen (AP5; AP4).
