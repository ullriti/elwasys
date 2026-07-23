# 18. Pre-Launch AP4 (Auth & Security): Reset-Enumeration, Passwort-Mindestlänge, Standort-Token-Restrisiko

- **Status:** accepted
- **Datum:** 2026-07-22

> **Namens-Hinweis:** „AP4" meint hier das Arbeitspaket **AP4 der Pre-Launch-Review
> (Epic #66)** – *Auth & Security*. Es ist **nicht** die gleichnamige Migrations-Phase
> „Phase 4 AP4" (Client-Cutover) aus `docs/kb/05-migration-plan.md`.

## Kontext

Die Pre-Launch-Review (Epic #66) bündelt die Auth-/Security-Befunde in AP4. Drei davon
sind als **Auftraggeber-Entscheidungen (🧩)** markiert, weil sie das nutzer-sichtbare
Verhalten gegenüber dem Alt-Portal ändern bzw. eine Scope-Frage aufwerfen. Sie wurden vor
der Umsetzung geklärt (diese ADR friert die Antworten ein); die übrigen AP4-Befunde
(#21, #23, #25, #26, #42, #45, #46, #47, #48) sind reine Härtungen ohne Entscheidungsbedarf
und werden in der AP4-Umsetzung mitbehoben.

1. **#24 – Passwort-Reset verrät Kontenexistenz + kein Rate-Limit:** Der öffentlich
   (`@AnonymousAllowed`) erreichbare Reset meldet über
   `UserNotFoundForEmailException`/Dialog-Text unterscheidbar, ob eine Adresse existiert
   (1:1-Portierung, aber im neuen öffentlich erreichbaren Portal ein Informationsabfluss),
   und drosselt nichts – jeder Request erzeugt Token **und Mailversand** (Mail-Bombing).
2. **#44 – keine serverseitige Passwort-Mindestlänge:** Alle Passwort-Pfade
   (`PasswordService.setNewPassword`/`changeOwnPassword`, Reset-Link, Ändern-Dialog)
   akzeptieren jedes nicht-leere Passwort; über den Reset-Link ist ein 1-Zeichen-Passwort
   setzbar. Regel gäbe es nur als `maxLength 50`.
3. **#43 – Standort-Token: Blast-Radius, kein Ablauf, kein Verwaltungs-UI:** Ein geleaktes
   Token (Klartext in `elwasys.properties` auf der SD-Karte) erlaubt standortübergreifende
   Guthaben-/Karten-Enumeration (`UserController`, `SnapshotController`); Tokens haben
   keinen Ablauf, Rotation/Widerruf nur per CLI. ADR 0008 stellte ein Portal-Admin-UI in
   Aussicht, das nie kam. Das Token-Design selbst (256-bit-Zufall, Hash-Speicherung,
   Mehrfach-Token-Rotation) ist gut.

## Entscheidung

Vom Auftraggeber am **2026-07-22** festgelegt (siehe auch
[`../kb/05-migration-plan.md`](../kb/05-migration-plan.md), Abschnitt „Entscheidungen"):

- **#24 Reset-Enumeration → neutralisieren (bewusste Verhaltensänderung):** Der öffentliche
  `requestReset` beendet bei unbekannter Adresse **still** (kein Wurf, kein Versand); der
  Dialog zeigt **immer** dieselbe neutrale Meldung („Falls ein Konto zu dieser Adresse
  existiert, wurde eine Email versandt."). `UserNotFoundForEmailException` verschwindet aus
  dem öffentlichen Pfad (der Admin-Reset arbeitet ohnehin auf einer bereits aufgelösten
  `UserEntity`). Dies ist eine **bewusste Abweichung vom Alt-Portal**, hier freigegeben.
  Unabhängig davon (keine Entscheidung nötig, aber Teil desselben Fixes) kommt ein
  serverseitiges **Rate-Limit** in `requestReset` (pro E-Mail/IP frühestens alle N Minuten
  ein Versand; In-Memory genügt bei einer Instanz) – gemeinsames Konzept mit dem
  Brute-Force-Login-Limit (#25).
- **#44 Passwort-Mindestlänge → ≥ 8 Zeichen serverseitig erzwingen:** Zentral in
  `PasswordService.setNewPassword`/`changeOwnPassword` (fachliche Exception), damit UI-,
  Reset- und Ändern-Pfad gleichermaßen geschützt sind; beide Ansichten spiegeln die Meldung.
  Bewusste **Verschärfung** gegenüber dem Alt-Portal (dort vermutlich ohne Regel), hier
  freigegeben – analog zur bereits dokumentierten Blocked-Login-Verschärfung.
- **#43 Standort-Token → minimale Variante: Restrisiko dokumentieren, kein Code-Ausbau:**
  Der Auftraggeber wählt die **Doku-/Runbook-Stufe**. Das heißt:
  - Restrisiko (standortübergreifende Guthaben-/Karten-Enumeration bei Token-Leak) explizit
    in `docs/kb/` festhalten und **bewusst akzeptieren**.
  - **ADR 0008 korrigieren**: das in Aussicht gestellte Admin-UI wird **nicht** gebaut;
    Verwaltung bleibt bei der CLI (`TerminalTokenCliRunner` /
    `deploy/cutover/02-issue-terminal-tokens.sh`).
  - Rotations-/Widerrufs-Prozedur ins Runbook aufnehmen, mit **verpflichtendem `revoked_at`
    beim Gerätetausch**.
  - **Bewusst nicht** umgesetzt (verworfene Ausbaustufen): kein additives `expires_at`, kein
    Portal-Admin-UI, keine Reduktion des Snapshot-Kartendaten-Umfangs, keine Einschränkung
    der Guthabenabfrage auf Standort-Nutzer. Gründe: geringe Eintrittswahrscheinlichkeit
    (Token liegt hinter physischem SD-Karten-Zugriff), Verhaltensbewahrung des Terminals
    (Snapshot/Guthaben speisen die Offline-Fähigkeit) und Aufwand/Nutzen vor dem Launch.

## Konsequenzen

- AP4 kann jetzt ohne offene Auftraggeber-Frage umgesetzt werden. Die Umsetzung der drei
  Entscheidungen (plus der nicht-🧩-Härtungen) erfolgt in der AP4-Fix-Umsetzung mit den
  zugehörigen Regressionstests (`PasswordResetServiceTest`, `PasswordServiceTest`).
- **#24/#44 ändern nutzer-sichtbares Verhalten** (neutrale Reset-Meldung; abgelehnte zu
  kurze Passwörter). Das ist durch diese Auftraggeber-Freigabe gedeckt und wird im
  Änderungslog von `05-migration-plan.md` als bewusste Abweichung geführt.
- **#43 bleibt ein akzeptiertes Restrisiko.** Es gibt vor dem Launch keinen technischen
  Riegel gegen einen Token-Leak-Missbrauch außer der physischen Token-Haltung und der
  Rotation/Widerruf-Disziplin. `expires_at` und ein Admin-UI bleiben mögliche spätere
  Ausbaustufen, falls sich der Betrieb anders entscheidet.
- **Konsistenz mit dem Brute-Force-Limit (#25):** Damit die Neutralisierung aus #24 nicht an
  anderer Stelle unterlaufen wird, wirft auch die Login-Sperre (#25) bewusst dieselbe
  generische `BadCredentialsException` wie ein normaler Fehlversuch – andernfalls wäre ein
  „gesperrt"-Signal (der Zähler wird nur für existierende Konten geführt) ein Enumeration-
  Orakel (Code-Review-Befund AP4). Die verbleibenden **Timing**-Orakel (Login wie Reset:
  unbekanntes Konto kehrt ohne Argon2/Versand schneller zurück) sind bewusst akzeptiert und in
  der Restrisiko-Tabelle in [`../kb/05-migration-plan.md`](../kb/05-migration-plan.md)
  („Restrisiken Auth & Security") festgehalten.

Herkunft: Pre-Launch-Review AP4 (Epic #66, Issues #24, #43, #44), Auftraggeber-Festlegung
2026-07-22; korrigiert die Admin-UI-Aussage aus ADR 0008.
