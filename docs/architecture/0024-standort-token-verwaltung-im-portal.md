# 24. Standort-Token-Verwaltung im Admin-Portal (revidiert ADR 0018, #43)

- **Status:** accepted
- **Datum:** 2026-07-28

## Kontext

[ADR 0008](0008-api-auth-standort-token-und-admin-session.md) stellte ein Portal-Admin-UI
für die Standort-Token-Verwaltung in Aussicht. [ADR 0018](0018-ap4-auth-security-entscheidungen.md)
(Pre-Launch-Review AP4, Issue #43) nahm diese Zusage am 2026-07-22 wieder zurück: der
Auftraggeber wählte damals die **minimale Variante** („Restrisiko dokumentieren, kein
Code-Ausbau"), die Verwaltung blieb bei der CLI (`TerminalTokenCliRunner`, Profil
`token-cli`, bzw. `deploy/cutover/02-issue-terminal-tokens.sh`). Begründung waren
Aufwand/Nutzen vor dem Launch und die geringe Eintrittswahrscheinlichkeit eines Token-Leaks.

Beim Aufbau eines **weiteren Terminals** hat sich diese Entscheidung als in der Praxis
untragbar erwiesen: der CLI-Weg verlangt Shell-Zugriff auf den Backend-Host bzw. den
Container, Kenntnis des Profil-Aufrufs und der DB-Zugangsdaten — für einen Vorgang, der zum
normalen Betrieb gehört (neues Gerät in Betrieb nehmen, defektes Gerät tauschen, Token nach
Verdacht rotieren). Genau die im Runbook geforderte Rotations-/Widerrufs-Disziplin
(verpflichtendes `revoked_at` beim Gerätetausch) wird dadurch unwahrscheinlicher, nicht
wahrscheinlicher. ADR 0018 hatte das Admin-UI ausdrücklich als „mögliche spätere
Ausbaustufe, falls sich der Betrieb anders entscheidet" offengehalten.

## Entscheidung

Vom Auftraggeber am **2026-07-28** festgelegt: Die Streichung des Admin-UI aus ADR 0018
(#43) wird **revidiert**. Die Standort-Token-Verwaltung kommt ins Admin-Portal.

- **Umfang:** Tokens eines Standorts auflisten (Label, erstellt, zuletzt benutzt, Status),
  ein neues Token erzeugen und ein bestehendes widerrufen.
- **Das Klartext-Token wird genau einmal angezeigt** — unmittelbar nach der Erzeugung, mit
  ausdrücklichem Hinweis, dass es nicht erneut abrufbar ist. Das Speicher- und Hash-Modell
  aus ADR 0008 bleibt dabei **unverändert**: in der Datenbank steht weiterhin nur der
  SHA-256-Hash, das Klartext-Token existiert nur im Rückgabewert von
  `TerminalTokenService#createToken` und wird nicht geloggt.
- **Zugriff nur für Rolle `ADMIN`** — ein Standort-Token ist ein Terminal-Credential.
- **Widerruf statt Löschung:** wie bisher wird `revoked_at` gesetzt, Zeilen bleiben zur
  Nachvollziehbarkeit erhalten.
- **Die CLI bleibt bestehen** (`TerminalTokenCliRunner`, `deploy/cutover/02-issue-terminal-tokens.sh`).
  Sie ist der Weg für die Erstinbetriebnahme/den Cutover, bevor ein Admin-Login existiert,
  und für automatisierte Abläufe. Portal-UI und CLI nutzen denselben
  `TerminalTokenService` — es gibt keine zweite Token-Erzeugungslogik.

**Bewusst weiterhin nicht** umgesetzt (die übrigen in ADR 0018 verworfenen Ausbaustufen
bleiben verworfen): kein additives `expires_at`, keine Reduktion des
Snapshot-Kartendaten-Umfangs, keine Einschränkung der Guthabenabfrage auf Standort-Nutzer.
Diese ADR ändert **nur** die Verwaltbarkeit, nicht das Token-Design.

## Konsequenzen

- **ADR 0018 ist in Punkt #43 teilweise überholt**, ebenso die Korrektur-Notiz in ADR 0008.
  Beide bleiben als Historie stehen und verweisen auf diese ADR; die übrigen Festlegungen
  aus ADR 0018 (#24 Reset-Enumeration, #44 Passwort-Mindestlänge) sind unberührt.
- **Das Restrisiko aus #43 sinkt, verschwindet aber nicht.** Der Blast-Radius eines
  geleakten Tokens (standortübergreifende Guthaben-/Karten-Enumeration) ist unverändert —
  was sich ändert, ist die Wahrscheinlichkeit, dass Rotation und Widerruf tatsächlich
  stattfinden. Die Restrisiko-Tabelle in
  [`../kb/05-migration-plan.md`](../kb/05-migration-plan.md) wird entsprechend nachgezogen.
- **Neue Angriffsfläche:** eine übernommene Admin-Session kann jetzt Terminal-Credentials
  erzeugen. Das ist gegenüber dem Bestand keine echte Erweiterung — eine Admin-Session kann
  ohnehin Standorte, Geräte, Preise und Guthaben ändern — wird hier aber ausdrücklich
  benannt, damit es nicht unbemerkt bleibt.
- Nutzer-sichtbare Änderung: **neue Funktion** im Admin-Portal. Der Grundsatz „Verhalten
  bewahren" ([ADR 0013](0013-verhalten-bewahren-strangler-und-e2e.md)) bezieht sich auf die
  Portierung des Alt-Portals; hier entsteht bewusst Neues, vom Auftraggeber beauftragt.

Herkunft: Auftraggeber-Entscheidung 2026-07-28 (Anlass: Inbetriebnahme eines weiteren
Terminals); revidiert ADR 0018 in Punkt #43 und stellt die ursprüngliche Absicht aus
ADR 0008 wieder her.
