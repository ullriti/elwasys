# Specs

Spezifikationen beschreiben, **was** gebaut werden soll – *vor* der Implementierung. Sie
ergänzen die ADRs sauber:

- **Spec** (`docs/specs/`) = *Was* soll das Feature tun? Anforderungen, Abnahmekriterien.
- **ADR** (`docs/architecture/`) = *Warum* wurde eine Architektur-/Technikentscheidung so
  getroffen?

## Ablauf

1. Neue Spec aus [`0000-template.md`](0000-template.md) anlegen, fortlaufend nummeriert:
   `0001-<kebab-titel>.md`.
2. Abstimmen/beschließen, `Status` auf `Accepted` setzen.
3. Implementieren; die Abnahmekriterien sind der Maßstab (Tests referenzieren sie).
4. Nach Abschluss `Status` auf `Implemented` setzen; unterwegs getroffene
   Architektur­entscheidungen als ADR festhalten.

> Hinweis: Die Modernisierung (Phasen 0–6) wurde vorab im Modernisierungsplan
> [`../kb/05-migration-plan.md`](../kb/05-migration-plan.md) als Roadmap mit Arbeitspaketen
> spezifiziert – diese historischen Arbeitspakete sind dort dokumentiert und wurden nicht
> nachträglich als Einzel-Specs zurückportiert. Neue Features ab jetzt hier spezifizieren.

## Index

| Nr. | Titel | Status |
|-----|-------|--------|
| — | *(noch keine Specs)* | — |
