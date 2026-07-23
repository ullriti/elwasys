# 2026-07-23 — KB-Ist-Zustand-Refactor (ADR 0020)

**Ziel:** Die KB-Artikel lesbar machen. Sie waren zu einem Palimpsest geworden — entfernte
Artefakte mit durchgestrichenen Schema-/Code-Dumps reproduziert, „entfernt in Phase X"/„neu seit
Phase Y"/„seit Phase Z"-Marker quer durch den Fließtext, per-Arbeitspaket-Tags. Man las die
Entstehungsgeschichte statt des Ist-Zustands. Auslöser: das durchgestrichene Tabellen-Wirrwarr in
`02-data-model.md` (`reservations`/`foreign_authkeys`).

## Vorgehen
Auftraggeber-Entscheidung eingeholt (dünner Pointer-Footer + Pilot-zuerst). Konvention als
[ADR 0020](../architecture/0020-kb-ist-zustand-und-historie-footer.md) festgeschrieben, `kb/README`
„Regeln" ergänzt. Pilot `02-data-model.md` gebaut und vom Auftraggeber abgenommen (Form). Dann die
restlichen Artikel parallel umgebaut (je Datei ein Spezialist, disjunkt), mit Auflage „nur
Historie/Markup entfernen, kein Ist-Fakt verlieren"; anschließend Residuen-Grep + blockierendes
`code-reviewer`-Gate.

## Konvention (ADR 0020)
- **Body = Ist-Zustand, Präsens.** Verboten: Durchstreichen, „neu/entfernt/seit Phase X"-Marker,
  reproduzierte entfernte Artefakte, per-AP-Tags.
- **`## Historie`-Footer je Artikel:** datierte Bullets, je Bullet ein Pointer auf
  Worklog/ADR/`05`-Änderungslog. Verlinkt, dupliziert nicht.
- Ausnahmen: `05-migration-plan.md` (bewusst historisch/Roadmap), `README.md`-„Aktueller Stand".

## Erledigt
- **ADR 0020** angelegt + im ADR-Index verlinkt; `kb/README` „Regeln" um die Konvention ergänzt.
- **`02-data-model.md`** (Pilot): durchgestrichene Alt-Tabellen/-Rollen raus, seitenlange
  Migrations-Herleitung durch kompakte `V1–V11`-Referenztabelle ersetzt, Historie-Footer.
- **`00-overview.md`**: „Ausgangslage vor der Modernisierung" + „seit Phase X"-Einsprengsel raus,
  Reservierungs-Feature in den Footer, Komponenten-Bild (nur Backend an der DB) im Body.
- **`01-architecture.md`**: durchgestrichene `~~Common~~`/`~~Portal~~`-Modulzeilen + Alt-TCP-/
  Alt-Portal-Abschnitte raus, Modul-Tabelle nur 2 Module, Tech-Stack ohne Herkunftsklammern.
- **`03-modules.md`**: 1738 → 1044 Zeilen, nach Ist-Sicht umstrukturiert (Client-Raspi / Backend /
  Historie), alle Phase-/AP-/`(#NN)`-Tags raus, Alt-Portal-/Common-Auflösung-/HTTP-Client-Saga nur
  noch als Footer-Pointer; alle Ist-Fakten (Endpunkte, Klassen, Mechanik) code-verifiziert erhalten.
- **`04-build-and-run.md`**: Phase-Tags raus, Health-Pfade als Ist-Zustand, Compose-Härtung
  präsentisch; Build-/Test-/Cutover-Kommandos erhalten.
- **`06-ui-tests.md` / `08-test-plan.md`**: datierte Lauf-Count-Blöcke (inkl. AP7-Nachtrag) in den
  Footer; Body = aktuelle Testinfrastruktur + Ist-Inventar (C1–C16, P1–P26); Selektor-Strategie
  und Ausführungskommandos erhalten; Zahlen als Inventar, keine erfundenen Grün-Läufe.
- **`07-cloud-init.md`**: datierte „Verifizierter Zustand (2026-07-19)"-Tabelle in den Footer,
  Body = aktueller SessionStart-Hook-/Cloud-Init-Stand.
- Verifikation: Residuen-Grep über alle Bodies sauber (kein `~~`/„seit Phase"/„Pre-Launch AP");
  jeder Artikel hat `## Historie`-Footer; blockierendes `code-reviewer`-Gate gelaufen.

## Offen / nächster Schritt
- Optional später: `scripts/check-ai-docs.sh` um eine Body-Prüfung erweitern (verbotene Marker
  `~~`/„seit Phase"), damit die Konvention automatisch gehalten wird (in ADR 0020 als Option notiert).

## Referenzen
- [ADR 0020](../architecture/0020-kb-ist-zustand-und-historie-footer.md), [kb/README](../kb/README.md)
- Branch: `claude/ap7-issue-66-deep-dive-ew0ynk`
