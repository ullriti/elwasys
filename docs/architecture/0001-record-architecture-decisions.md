# 1. Architektur-Entscheidungen festhalten (ADRs)

- **Status:** accepted
- **Datum:** 2026-07-22

## Kontext

Architektur-Entscheidungen gehen sonst in Chats und Commit-Messages verloren. Neue
Team-Mitglieder – und KI-Agenten – rekonstruieren die Absicht dann aus dem Code, was
fehleranfällig ist. Die frühen elwasys-Modernisierungsentscheidungen wurden bislang im
Modernisierungsplan [`../kb/05-migration-plan.md`](../kb/05-migration-plan.md) unter
„Entscheidungen (Auftraggeber)" gesammelt; wir überführen die tragenden davon in ADRs
und halten künftige Entscheidungen direkt hier fest.

## Entscheidung

Wir dokumentieren jede nennenswerte Architektur-Entscheidung als **ADR** (Architecture
Decision Record) in `docs/architecture/`, fortlaufend nummeriert, im Format dieser Datei.

## Konsequenzen

- Entscheidungen sind nachvollziehbar und versioniert.
- Agenten lesen den „Warum"-Kontext, statt zu raten.
- Kleiner Aufwand je Entscheidung.

---

## Vorlage für neue ADRs

```markdown
# N. <Titel der Entscheidung>

- **Status:** proposed | accepted | superseded by ADR-M
- **Datum:** YYYY-MM-DD

## Kontext
<Welches Problem, welche Randbedingungen?>

## Entscheidung
<Was wird entschieden?>

## Konsequenzen
<Positive und negative Effekte, Trade-offs.>
```
