# 26. Client-Raspi bleibt auf Java 17, weil es JavaFX 21 für 32-bit-ARM nicht mit GTK gibt

- **Status:** accepted
- **Datum:** 2026-09-20

## Kontext

Beim Feldtest der Produktivumschaltung (Homelab-Runbook `phase3-elwasys.md`, Phase 3) wurde
das Terminal `elwaClient2` (Raspberry Pi 3 Model B, Raspbian 9, armhf) von Java 8 auf die
in `deploy/terminal/upgrade-jre.sh` vorgesehene Java-21-Runtime gehoben. Der Client startete
nicht mehr und ließ das Terminal dunkel:

```
java.lang.UnsupportedOperationException: Minimum GTK version required is 3.8.0.
  System has 2.24.31.
    at javafx.graphics/com.sun.glass.ui.gtk.GtkApplication._initGTK(Native Method)
```

Die Meldung ist irreführend: **GTK 3.22.11 ist auf dem Gerät installiert.** Die Untersuchung
ergab eine andere Ursache.

**Befund 1 — die Liberica-arm32-Runtime hat gar keinen GTK-Backend.** In
`bellsoft-jre21…-linux-arm32-vfp-hflt-full` (Tarball wie `.deb`, beide geprüft) liegen nur
`libglass.so`, `libglass_monocle.so`, `libglass_monocle_x11.so`, `libglass_monocle_epd.so`
— **kein `libglassgtk3.so`**. Der mitgelieferte `libglass.so` referenziert ausschließlich
`libgtk-x11-2.0.so.0`, also GTK **2**, erzwingt zur Laufzeit aber GTK ≥ 3.8. Diese
Kombination kann auf keinem Betriebssystem aufgehen. In der aarch64-Variante derselben
Liberica 21 ist `libglassgtk3.so` enthalten und referenziert korrekt `libgtk-3.so.0`.

**Befund 2 — das ist kein Herstellerfehler, sondern Upstream-Politik.** OpenJFX 21
veröffentlicht für Linux-ARM diese Klassifizierer:

| Klassifizierer | Oberfläche |
|---|---|
| `linux-aarch64` | Desktop/GTK3 |
| `linux-aarch64-monocle` | Monocle |
| `linux-arm32-monocle` | **nur** Monocle |

Ein `linux-arm32` mit GTK gibt es nicht. **Für 32-bit-ARM existiert kein JavaFX 21 mit
Fensteroberfläche.** JavaFX 17 hatte die Versionsprüfung noch nicht und läuft deshalb bis
heute auf beiden Terminals.

Geprüft und verworfen wurde außerdem: `-Djdk.gtk.version=3` und das Weglassen von
`-Djavafx.platform` (beide ändern nichts, die Bibliothek ist GTK2), Debians `openjfx`
(steht in bullseye, bookworm **und** trixie unverändert bei 11.0.11) sowie Monocle-X11
(kommt weiter, scheitert an `Screen.initScreens`, und würde den bewährten X-/lightdm-Kiosk
gegen einen ungetesteten Framebuffer-Pfad tauschen).

## Entscheidung

**Das Modul `Client-Raspi` wird auf Java 17 gebaut**, das Backend bleibt auf Java 21.

Konkret:

- `maven-compiler-plugin` von **3.3 auf 3.13.0**, Konfiguration `<release>17</release>`
  statt `<source>/<target>`.
- JavaFX-Abhängigkeiten von **23.0.2 auf 17.0.20**.
- Die CI bleibt auf **JDK 21** — der Client-Job bootet über
  `run-cross-component-e2e.sh` den Spring-Context des Backends, und `release.yml` baut
  beide Module in einem Job. Ein Wechsel auf JDK 17 würde beides brechen.

## Warum `<release>` und nicht `<source>/<target>`

Das ist der Kern der Änderung, nicht Kosmetik. `source`/`target` beschränken **nur die
Bytecode-Version**; javac linkt weiter gegen die API des laufenden JDK. Auf der CI (JDK 21)
entstünden damit Klassen mit Class-File-Major 61, die trotzdem Java-21-Methoden aufrufen —
der Fehler fiele erst auf dem Terminal als `NoSuchMethodError` auf, also genau dort, wo er
am teuersten ist. `<release>` beschränkt Bytecode **und** API-Oberfläche. Das gepinnte
Plugin 3.3 von 2015 kennt `<release>` nicht (gibt es ab 3.6) — daher der Versionssprung.

Zur Absicherung prüft der Client-CI-Job nach dem Build, dass **alle** erzeugten Klassen
Class-File-Major 61 tragen. Ohne diesen Guard wäre ein versehentliches Zurückdrehen auf
`source/target` unsichtbar, bis ein Terminal ausfällt.

## Konsequenzen

**Positiv**

- Beide Terminals laufen unverändert auf ihrer vorhandenen, funktionierenden Java-17-Runtime;
  die Produktivumschaltung braucht keinen Runtime- und keinen Betriebssystemwechsel.
- Der Blocker ist an genau einer Stelle gelöst (Build), nicht an zwei Geräten.

**Negativ / bewusst akzeptiert**

- Der Client verliert Zugriff auf Java-18…21-APIs und bleibt auf JavaFX 17 (LTS,
  unterstützt bis 2026-09 als Liberica-Bestandteil). Das Modul nutzt heute kein einziges
  Sprachmerkmal jenseits von Java 17 — gegengeprüft: keine Virtual Threads, keine
  Record-Patterns, kein Pattern-Matching in `switch`, keine `SequencedCollection`.
- Backend (21) und Client (17) laufen auf verschiedenen Sprachständen. Gemeinsamer Code
  unter `org.kabieror.elwasys.common` wird im Client-Modul mitgebaut und ist damit
  faktisch auf Java 17 begrenzt.
- **Die eigentliche Ursache bleibt bestehen:** die Terminal-Hardware fährt ein 32-bit-OS.
  Der zukunftsfähige Weg ist ein **64-bit-Betriebssystem** — die Hardware (Cortex-A53)
  kann es, und die aarch64-Runtime hat den GTK3-Backend. Das ist ein eigenes Vorhaben,
  auch weil Raspbian 9 auf `elwaClient2` seit 2020 EOL ist und dessen apt-Quellen 404
  liefern. Es wird hier bewusst **nicht** mitentschieden, um die Produktivumschaltung nicht
  an einen Geräteumbau zu koppeln. Sobald beide Terminals 64-bit fahren, kann diese
  Entscheidung zurückgenommen werden.
- `deploy/terminal/upgrade-jre.sh` hebt Geräte weiterhin auf Java 21. Für armhf-Terminals
  ist das jetzt falsch; das ist als offener Punkt benannt (siehe PR).
