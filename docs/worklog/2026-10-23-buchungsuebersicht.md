# 2026-10-23 — Portal: Buchungsübersicht mit Filter nach Ein-/Auszahlungen

**Ziel:** Eine Admin-Seite, die alle Buchungen anzeigt und nach Ein- und Auszahlungen filtern lässt
(bisher gab es Buchungen nur je Benutzer im Dialog „Umsätze ansehen“).

## Umsetzung (direkt bearbeitet, kein Delegieren)

Reine Backend-/Portal-Änderung ohne Wire-Naht zum Terminal; ein Kontext war ausreichend.

- `BookingType` (INPAYMENT/PAYOUT/EXECUTION): die Art ergibt sich wie im Alt-Code aus Vorzeichen
  und Ausführungsbezug – **keine Migration**, kein neues Schema.
- `CreditService#findBookings(type, from, to)`: Filter in der Datenbank per `Specification`
  (`CreditAccountingEntryRepository` erbt dafür `JpaSpecificationExecutor`), Benutzer per
  `fetch`, Reihenfolge `date DESC, id DESC` (#88). `CreditAccountingEntryEntity#getExecutionId`
  liest die Id der LAZY-Ausführung ohne Nachladen.
- `AdminBookingsView` (`/admin/bookings`, Menüpunkt „Buchungen“): Art-Auswahl, Von/Bis, Freitext
  (`ListFilterField`), Summenzeile, sortierbare Spalten, Beträge grün/rot.

## Tests

- `CreditServiceBookingsFilterTest` (Art, Zeitraum inkl. Randtage, Kombination, Art-Ableitung),
  `RouteAccessAnnotationsTest` um die neue View erweitert, E2E P34 (`bookings.spec.ts`).
- Gesamte Backend-JUnit-Suite grün (348). Playwright-E2E: siehe PR-Beschreibung.
- Sichtprüfung per Demo-Modus (`backend/run-demo.sh`), Screenshots im Chat.

## Offen / Hinweise

- Die Liste lädt alle Treffer ohne Paging; bei sehr großem Bestand ist der Zeitraumfilter das
  Mittel der Wahl. Paging wäre ein Folgeschritt, falls nötig.
