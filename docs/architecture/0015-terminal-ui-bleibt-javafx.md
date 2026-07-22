# 15. Terminal-UI bleibt JavaFX (aktualisiert auf Java 21)

- **Status:** accepted
- **Datum:** 2026-07-20

## Kontext

Das Terminal-UI ist eine JavaFX-/FXML-Anwendung (State-Machine, Views) in zwei Größen
(800×480 und 320×240). Die Nutzer dürfen sich nicht umstellen müssen; ein Wechsel des
UI-Stacks würde Bedienfluss und Layout gefährden. Als Alternative wurde ein
"Chromium-Kiosk + Web-UI" erwogen.

## Entscheidung

Das Terminal-UI **bleibt JavaFX** (aktuelles JavaFX auf Java 21); UI/FXML und Bedienfluss
bleiben unverändert, sodass Nutzer nichts merken und die TestFX-Suite gültig bleibt. Die
kleine 320×240-Variante (`ui/small`) **bleibt** – sie ist laut Auftraggeber noch im
Einsatz – und wird mitmodernisiert und mindestens per Smoke-Test abgedeckt. Beide
Geräte-Gateways (deCONZ und fhem) bleiben unterstützt und werden mit je eigenem Simulator
getestet. Die Alternative "Chromium-Kiosk + Web-UI" wurde verworfen (neuer Stack, Touch-/
Offline-Verhalten riskanter, kein Nutzer-Mehrwert).

## Konsequenzen

- Bedienfluss und Layout bleiben identisch; bestehende UI-Tests bleiben der Maßstab.
- Nur eine JavaFX-/Java-Aktualisierung statt eines UI-Neubaus.
- Beide Display-Größen und beide Gateways bleiben im Test abgedeckt.

Herkunft: docs/kb/05-migration-plan.md, Abschnitte Technologie-Entscheidungen und
Entscheidungen (Auftraggeber).
