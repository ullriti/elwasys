# 19. Pre-Launch AP6: Vaadin-Lizenz – Restrisiko bewusst akzeptiert

- **Status:** accepted
- **Datum:** 2026-07-23

> **Namens-Hinweis:** „AP6" meint hier das Arbeitspaket **AP6 der Pre-Launch-Review
> (Epic #66)** – *Deployment, Betrieb & Cutover*. Es ist **nicht** eine gleichnamige
> Migrations-Phase aus [`../kb/05-migration-plan.md`](../kb/05-migration-plan.md).

## Kontext

Das Admin-/Benutzer-Portal ist ein eingebettetes **Vaadin Flow** (ADR 0003). Die
eingesetzte Version ist **Vaadin 24.10.8** (`backend/pom.xml`), die inzwischen als
**„Extended Maintenance"** gilt – also über die freie Community-Support-Periode hinaus
grundsätzlich kostenpflichtig (Subscription). Beim Servlet-Start protokolliert die
Anwendung eine `MissingLicenseKeyException`; im Produktionsmodus (`-Pproduction`)
**beantwortet der Server Anfragen dennoch** (anders als der fatale Dev-Modus-Abbruch),
solange die UI **kein anwendungsspezifisches Frontend-Bundle** braucht (kein
kompiliertes `@Theme`/`@CssImport`/`@JsModule`; das Portal-Styling wird deshalb bewusst
zur Laufzeit als Inline-CSS injiziert, siehe Änderungslog „Portal-Design" in
[`../kb/05-migration-plan.md`](../kb/05-migration-plan.md)).

Die Risikotabelle in [`../kb/05-migration-plan.md`](../kb/05-migration-plan.md) hielt
schon fest, dass der **technische** Show-Stopper für Tests/Deployment entschärft ist,
aber „weiterhin ungeklärt [ist], ob das rechtlich unbedenklich ist". Issue #33 (AP6)
markiert das als **Auftraggeber-Entscheidung (🧩)**: Für einen Launch ist der
Lizenz-/Subscription-Status kein reines Sandbox-Detail mehr, sondern ein offenes
Rechtsrisiko. Ein reiner Code-Fix ist ohne diese Entscheidung nicht möglich.

Zur Wahl standen: (1) eine Vaadin-Extended-Maintenance-**Subscription** abschließen und
einen Lizenzschlüssel hinterlegen, (2) auf eine **kostenlos wartbare Vaadin-Linie**
wechseln (Downgrade/Migration, kann Portal-Verhalten/Build berühren), oder (3) das
**Restrisiko dokumentieren und bewusst akzeptieren** (Launch mit der aktuellen Version).

## Entscheidung

Vom Auftraggeber am **2026-07-23** festgelegt: **Option (3) – Restrisiko bewusst
akzeptieren.**

- Der Launch erfolgt mit **Vaadin 24.10.8** im Produktionsmodus; die beim Start geloggte
  `MissingLicenseKeyException` wird als **bewusst akzeptiertes Restrisiko** geführt.
- **Kein** Abschluss einer Subscription und **kein** Versionswechsel vor dem Launch.
- Die technische Rahmenbedingung „**kein kompiliertes Theme / kein `@CssImport` /
  `@JsModule`**" bleibt bindend – jede solche Ergänzung würde einen lizenzpflichtigen
  `vaadin:build-frontend`-Online-Check auslösen und den lizenzfreien `-Pproduction`-Build
  brechen (real reproduziert). Portal-Styling deshalb weiterhin ausschließlich per
  Laufzeit-Inline-CSS.

## Konsequenzen

- **Rechtliches Restrisiko bleibt bestehen** und wird in der Restrisiko-Tabelle in
  [`../kb/05-migration-plan.md`](../kb/05-migration-plan.md) („Restrisiken Betrieb &
  Deployment") als bewusst akzeptiert geführt. Sollte der Betrieb später eine saubere
  Lizenzlage verlangen, bleiben Subscription (Option 1) oder ein Wechsel auf eine freie
  Vaadin-Linie (Option 2) als spätere Ausbaustufen offen – letzterer als eigenes
  Arbeitspaket, weil er Build und ggf. Portal-Verhalten berührt.
- **Bindende technische Leitplanke:** Der Build bleibt fragil gegenüber Frontend-Bundling.
  Ein künftiges `@CssImport`/`@Theme` bricht den lizenzfreien Produktionsbuild – dieser
  Punkt ist als Konvention im Portal-Kontext zu beachten (Laufzeit-Inline-CSS statt
  kompiliertem Theme).
- **Kein Code-Fix in AP6** zu #33: Die Umsetzung von AP6 fasst das Portal-Frontend nicht
  an; diese ADR ist der Abschluss von #33.

Herkunft: Pre-Launch-Review AP6 (Epic #66, Issue #33), Auftraggeber-Festlegung 2026-07-23.
