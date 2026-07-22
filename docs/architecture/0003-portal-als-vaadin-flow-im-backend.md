# 3. Admin-Portal als eingebettete Vaadin-Flow-UI im Backend

- **Status:** accepted
- **Datum:** 2026-07-20

## Kontext

Das Alt-Portal ist ein separates Vaadin-7-/GWT-WAR (2016), das per Jetty-Plugin
deployt und nicht mehr wartbar ist. Es wird komplett neu gebaut. Serverseitig ist Java
gesetzt; ein zusätzlicher, separat zu pflegender Frontend-Stack ist unerwünscht. Laut
Auftraggeber loggen sich im Wesentlichen nur Verwalter/Admins ins Portal ein, normale
Nutzer verwenden ausschließlich die Terminals.

## Entscheidung

Das Portal wird als **Vaadin Flow 24** neu gebaut und **direkt in das Spring-Boot-Backend
eingebettet** (ein Deployment statt WAR+Jetty). Vaadin Flow bleibt reines Java, ist ideal
für CRUD-Admin-UIs und bringt Push eingebaut mit. Die gewohnte **Struktur** der Ansichten/
Arbeitsabläufe bleibt wiedererkennbar (auch Admins sollen sich nicht umstellen müssen),
UX-Verbesserungen sind aber ausdrücklich erwünscht. Fokus auf den Admin-Ansichten; der
Nutzer-Selbstbedienungsbereich hat niedrigere Parity-Priorität.

## Konsequenzen

- Nur ein Deployment und ein Technologie-Stack (Java) für Backend + Portal.
- Feature-Parität zum Alt-Portal ist Abnahmekriterium (portierte Playwright-Suite).
- Konzeptuelle Nähe zum Bestand erleichtert die Parität.
- Das Alt-Portal-Modul (`Portal/`) wird nach dem Cutover entfernt.

Herkunft: docs/kb/05-migration-plan.md, Abschnitt Entscheidungen (Auftraggeber).
