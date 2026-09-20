# 2026-09-20 — Client auf Java 17: JavaFX 21 gibt es für 32-bit-ARM nicht mit GTK

**Ziel:** Den beim Feldtest der Produktivumschaltung gefundenen Startfehler beheben, mit dem
das Terminal nach dem Wechsel auf Java 21 dunkel blieb.

## Ausgangslage

Beim Feldtest (Homelab-Runbook `phase3-elwasys.md`, Phase 3) wurde `elwaClient2`
(Raspberry Pi 3 Model B, Raspbian 9, armhf) nach dem Verfahren aus
`deploy/terminal/README.md` auf Java 21 gehoben und mit `raspi-client-1.0.0.jar` gestartet.
Der Client brach beim Start ab:

```
java.lang.UnsupportedOperationException: Minimum GTK version required is 3.8.0.
  System has 2.24.31.
```

Das Gerät wurde innerhalb weniger Minuten auf den Alt-Stand zurückgerollt (Java 8, altes Jar,
alte Konfiguration) und lief danach wieder produktiv.

## Untersuchung

Die Fehlermeldung führte zunächst in die Irre — **GTK 3.22.11 war installiert**. Vier Schritte
bis zur Ursache:

1. `-Djdk.gtk.version=3` und das Weglassen von `-Djavafx.platform`: unverändert derselbe Fehler.
2. Monocle-X11 als Alternative: kommt weiter, scheitert aber an `Screen.initScreens`.
3. Blick in die Runtime: `bellsoft-jre21…-linux-arm32-vfp-hflt-full` enthält **kein**
   `libglassgtk3.so`, nur die Monocle-Backends. Der mitgelieferte `libglass.so` referenziert
   ausschließlich `libgtk-x11-2.0.so.0` (GTK **2**), erzwingt zur Laufzeit aber GTK ≥ 3.8 —
   das kann auf keinem OS aufgehen. Für `.deb` und Tarball identisch; die **aarch64**-Variante
   derselben Liberica 21 hat `libglassgtk3.so` und referenziert korrekt `libgtk-3.so.0`.
4. Gegenprobe upstream: OpenJFX 21 veröffentlicht für Linux-ARM nur `linux-aarch64`,
   `linux-aarch64-monocle` und `linux-arm32-monocle`. **Ein `linux-arm32` mit GTK existiert
   nicht.** Kein Hersteller-Bug, sondern Upstream-Politik. JavaFX 17 hat die Versionsprüfung
   noch nicht und läuft deshalb bis heute auf beiden Terminals.

Ebenfalls geprüft und verworfen: Debians `openjfx` steht in bullseye, bookworm **und** trixie
unverändert bei 11.0.11.

## Erledigt

- `Client-Raspi/pom.xml`: JavaFX **23.0.2 → 17.0.20**; `maven-compiler-plugin`
  **3.3 → 3.13.0** mit `<release>17</release>` statt `<source>/<target>`.
- `.github/workflows/ci.yml`: neuer Schritt im `client`-Job, der prüft, dass **alle**
  erzeugten Client-Klassen Class-File-Major 61 tragen.
- [ADR 0026](../architecture/0026-client-auf-java-17-wegen-javafx-auf-32-bit-arm.md) und
  CHANGELOG geschrieben.

## Entscheidungen

- **`<release>` statt `<source>/<target>` — das ist der Kern, nicht Kosmetik.**
  `source`/`target` beschränken nur die Bytecode-Version; javac linkt weiter gegen die API des
  laufenden JDK. Da die CI auf JDK 21 baut, entstünden Klassen mit Major 61, die trotzdem
  Java-21-Methoden aufrufen — der Fehler fiele erst auf dem Terminal als `NoSuchMethodError`
  auf. Das gepinnte Plugin 3.3 (2015) kennt `<release>` nicht, daher der Versionssprung.
- **Die CI bleibt auf JDK 21.** Der `client`-Job bootet über `run-cross-component-e2e.sh` den
  Spring-Context des Backends, und `release.yml` baut beide Module in einem Job — ein Wechsel
  auf JDK 17 würde beides brechen. Die Korrektheit sichert `<release>`, nicht die JDK-Wahl.
- **Der CI-Guard ist Teil der Lösung, nicht Beiwerk.** Ohne ihn wäre ein versehentliches
  Zurückdrehen auf `source/target` unsichtbar, bis ein Terminal ausfällt.
- **Der 64-bit-Umstieg wird hier bewusst nicht mitentschieden.** Er ist der eigentliche
  Zukunftsweg (die Hardware kann es, die aarch64-Runtime hat GTK3), aber er ist ein
  Geräteprojekt inklusive deCONZ-/FHEM-Migration und soll die Produktivumschaltung nicht
  aufhalten.

## Offen

- `deploy/terminal/upgrade-jre.sh` hebt Geräte weiterhin auf Java 21. Für armhf-Terminals ist
  das nach diesem Befund falsch und muss angepasst werden (eigenes Issue).
- Der Feldtest ist zu wiederholen — diesmal ohne Runtime-Wechsel, auf dem vorhandenen Java 17.
- 64-bit-Betriebssystem für beide Terminals als eigenes Vorhaben; auf `elwaClient2` zusätzlich
  dringlich, weil Raspbian 9 seit 2020 EOL ist und dessen apt-Quellen 404 liefern.
