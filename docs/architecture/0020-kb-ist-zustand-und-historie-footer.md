# 20. KB-Artikel: Ist-Zustand im Body, Historie als Pointer-Footer

- **Status:** accepted
- **Datum:** 2026-07-23

## Kontext

Die KB-Artikel (`docs/kb/00`–`08`) sollen laut [`../kb/README.md`](../kb/README.md) den
*aktuellen Sollzustand* beschreiben („die KB sagt, wie es ist; das Worklog, wie es dazu kam").
In der Praxis waren sie zu einem Palimpsest geworden: entfernte Artefakte wurden mit
durchgestrichenen Schema-/Spaltendumps reproduziert (z. B. die Alt-Tabellen
`reservations`/`foreign_authkeys` in `02-data-model.md`), „entfernt in Phase X"/„neu seit
Phase Y"/„seit Phase Z"-Marker zogen sich quer durch den Fließtext, dazu per-Arbeitspaket-Tags.
Der Ist-Zustand war dadurch schwer lesbar; dieselbe Historie wurde teils mehrfach gepflegt
(Artikel + Worklog + Änderungslog), was Drift begünstigte.

## Entscheidung

Vom Auftraggeber am 2026-07-23 festgelegt:

1. **Body = Ist-Zustand, Präsens.** KB-Artikel beschreiben im Fließtext ausschließlich, wie das
   System *jetzt* ist. **Verboten im Body:** Durchstreichen (`~~…~~`), „entfernt/neu/geändert in
   Phase X / AP Y"-Marker, „seit Phase/AP …"-Einsprengsel, das Nacherzählen von Zwischenständen.
2. **Entfernte/abgelöste Artefakte werden nicht reproduziert.** Kein Schema-Dump, kein
   durchgestrichener Block für Weggefallenes. Wo das frühere Bestehen fürs Verständnis nötig ist,
   genügt ein Satz — der Rest gehört in den Footer.
3. **Jeder Artikel endet mit `## Historie`:** datierte Bullets (neueste zuerst), je Bullet **ein
   Pointer** auf die Quelle der Wahrheit (Worklog-Eintrag, ADR, `05`-Änderungslog). Format:
   `YYYY-MM-DD — <knappe Ist-Aussage der Änderung> (<Quelle(n)>)`. Der Footer **verlinkt,
   dupliziert nicht**.
4. **Single-Source der Historie bleibt:** [Worklog](../worklog/README.md) (Chronologie), ADRs
   (Entscheidungen), [`../kb/05-migration-plan.md`](../kb/05-migration-plan.md) Änderungslog
   (feinkörnig). `05-migration-plan.md` ist bewusst das historische/Roadmap-Dokument und von
   dieser Ist-Zustands-Regel **ausgenommen**; der `README.md`-„Aktueller Stand"-Snapshot ist
   ebenfalls ein Sonderfall (er ist per Definition der Ist-Snapshot inkl. „Nächster Schritt").

Eine kompakte Referenzliste (z. B. die Flyway-Migrationen `V1–Vn` in `02-data-model.md`) darf im
Body bleiben, solange sie **terse und faktisch** ist (eine Zeile je Eintrag, kein
Begründungs-Essay, keine Phase-Tags) — sie beschreibt, wie das Ist-Schema aufgebaut ist. Die
*Herleitung/Abwägung* dazu gehört in `05`/Worklog, verlinkt aus dem Footer.

## Konsequenzen

- KB-Artikel werden lesbar: man liest den Ist-Zustand, ohne die Entstehungsgeschichte mitzulesen.
- Historie ist einfach-gesourct; die Footer sind billig zu pflegen (ein Pointer je Änderung).
- Einmaliger Umbau-Aufwand für den Bestand — Pilot `02`/`03`, danach `00`/`01`/`04`/`06`/`07`/`08`.
- Die in Pre-Launch AP7 (#66) eingefügten „Pre-Launch AP<n> (Epic #66)"-Inline-Tags wandern beim
  Umbau aus dem Body in die Historie-Footer.
- Optional später: `scripts/check-ai-docs.sh` könnte den Body auf verbotene Marker (`~~`,
  „seit Phase") prüfen.

Herkunft: Auftraggeber-Festlegung 2026-07-23 (nach der Pre-Launch-Review, Epic #66).
