# GoLive-Testplan – manuelle Abnahme nach dem Cutover

Diese Checkliste ist der **manuelle Abnahmelauf für den Tag nach der Produktivumschaltung**.
Sie ergänzt das [Cutover-Runbook](CUTOVER-RUNBOOK.md) (das *die Umstellung selbst* führt) und
die automatisierten Suiten ([`docs/kb/08-test-plan.md`](../docs/kb/08-test-plan.md)) um das,
was nur **vor Ort, an echter Hardware, mit echten Karten und echtem Geld** prüfbar ist.

**Zuschnitt dieser Installation:** ein Backend/Portal, **zwei Terminals** (im Folgenden **T1**
und **T2**, je ein Standort). Alle Schritte sind darauf zugeschnitten – es gibt keine dritte
Charge und keinen weiteren Standort.

**Kein Rollback-Üben.** Dieser Plan probt bewusst **keinen** Rückweg (kein Restore, kein
Image-Rollback, kein Reverse-DDL). Der Rollback-Pfad steht im Runbook, Kapitel 4 – falls hier
etwas Schwerwiegendes auffällt, siehe [Abbruchkriterien](#abbruchkriterien) ganz unten.

---

## Wie diese Liste benutzt wird

- **Abhaken:** jede Zeile mit `- [ ]` ist ein Prüfpunkt. Erledigt → `- [x]`.
- **Ergebnis notieren:** unter jedem Prüfpunkt steht `Ergebnis:` – dort kurz „ok" oder die
  Abweichung eintragen. Abweichungen zusätzlich unten in der
  [Befundliste](#befundliste) sammeln, damit am Abend eine Liste statt einer Suche steht.
- **Reihenfolge:** die Blöcke bauen aufeinander auf (Testdaten aus Block 0, Benachrichtigungen
  erst nach dem Scharfschalten). Innerhalb eines Blocks ist die Reihenfolge frei.
- **⏱ Wartezeit:** Prüfpunkte mit diesem Zeichen brauchen eine Wartezeit. Steht daneben ein
  **▶ parallel**, ist die Wartezeit ausdrücklich dafür gedacht, in der Zwischenzeit den nächsten
  Prüfpunkt zu bearbeiten – die Wartezeiten sind so geschnitten, dass keine über ~10 Minuten
  geht.
- **⚠ Eingriff:** Prüfpunkte, die Produktivdaten oder den laufenden Betrieb anfassen. Bei
  jedem steht dabei, wie der Ausgangszustand wiederhergestellt wird.
- **○ optional:** kann bei Zeitmangel entfallen, ohne dass eine Kernfunktion ungeprüft bleibt.

**Legende der Rollen/Namen** (werden in Block 0 angelegt):

| Kürzel | Bedeutung |
|---|---|
| **T1**, **T2** | die beiden Terminals (jeweils ein eigener Standort) |
| **K1 … K5** | RFID-Testkarten (K5 = eine dem System *unbekannte* Karte) |
| `test-normal` | Testbenutzer, ausreichend Guthaben, Email + Pushover eingerichtet |
| `test-arm` | Testbenutzer mit absichtlich zu wenig Guthaben |
| `test-gesperrt` | Testbenutzer mit gesetztem Haken „Gesperrt" |
| `test-fremd` | Testbenutzer in einer Gruppe, die an T1 **nicht** zugelassen ist |

---

## Tagesplan (Vorschlag)

| Zeit | Block | Inhalt | Dauer |
|---|---|---|---|
| 08:30 | **0** | [Vorbereitung, Testdaten, Benachrichtigungen scharf](#block-0--vorbereitung) | 60 min |
| 09:30 | **A** | [Grundzustand nach dem Cutover](#block-a--grundzustand-nach-dem-cutover) | 30 min |
| 10:00 | **B** | [Portal – Administration](#block-b--portal--administration) | 60 min |
| 11:00 | **C** | [Terminal – Normalbetrieb](#block-c--terminal--normalbetrieb) | 60 min |
| 12:00 | **D** | [Terminal – Grenz- und Fehlerfälle](#block-d--terminal--grenz--und-fehlerfälle) | 60 min |
| 13:00 | – | Mittagspause (Backend/Terminals laufen weiter = Mini-Soak) | 30 min |
| 13:30 | **E** | [Zwei Terminals / Nebenläufigkeit](#block-e--zwei-terminals--nebenläufigkeit) | 30 min |
| 14:00 | **F** | [Ausfall- und Offline-Szenarien](#block-f--ausfall--und-offline-szenarien) | 90 min |
| 15:30 | **G** | [Fernwartung, Betrieb, Alarmierung](#block-g--fernwartung-betrieb-alarmierung) | 45 min |
| 16:15 | **H** | [Benachrichtigungen (E-Mail/Push)](#block-h--benachrichtigungen) | 30 min |
| 16:45 | **I** | [Benutzerportal (Nicht-Admin)](#block-i--benutzerportal-nicht-admin) | 25 min |
| 17:10 | **J** | [Abschluss und Aufräumen](#block-j--abschluss-und-aufräumen) | 45 min |

Wenn die Zeit knapp wird: Block **I** und alle `○`-Punkte sind die ersten Kandidaten zum
Streichen. Block **F** ist der wertvollste Teil des Tages – dort nicht kürzen. Block **J**
(Aufräumen) darf nicht entfallen, sonst bleiben Testdaten im Produktivbestand liegen.

---

## Block 0 — Vorbereitung

**Ziel:** Werkzeuge griffbereit, Testdaten angelegt, Ausgangswerte notiert. Ohne diesen Block
werden die späteren Blöcke zu Bastelei.

### 0.1 Werkzeuge und Zugänge

- [ ] **0.1** Portal im Browser offen, Admin-Login funktioniert (URL mit `https://`).
  Ergebnis: ______
- [ ] **0.2** Zweites Browserfenster **im Inkognito-Modus** offen – wird für die
  Live-Update-Tests (zwei gleichzeitige Sessions) und den Nicht-Admin-Login gebraucht.
  Ergebnis: ______
- [ ] **0.3** SSH-Zugang zum Backend-Host offen (`docker compose ps` bzw. `kubectl get pods`
  läuft durch).
  Ergebnis: ______
- [ ] **0.4** Zugang zu **beiden** Terminals: SSH **oder** Tastatur direkt am Pi. Für die
  Ausfalltests wird mindestens einmal die Stromversorgung von T1 gezogen – also physisch
  drankommen können.
  Ergebnis: ______
- [ ] **0.5** deCONZ/Phoscon-Oberfläche erreichbar (wird gebraucht, um eine Steckdose von Hand
  zu schalten und Messwerte abzulesen).
  Ergebnis: ______
- [ ] **0.6** Handy bereit: Mail-Postfach der Testadresse **und** Pushover-App installiert und
  angemeldet.
  Ergebnis: ______
- [ ] **0.7** Ein Notizmittel für Zeitmessungen (Reconnect-Dauer, Alarmlaufzeit,
  Terminal-Startdauer) – mehrere Prüfpunkte fragen konkrete Zeiten ab.
  Ergebnis: ______

### 0.2 Ausgangszustand festhalten

- [ ] **0.8** Versionen notieren – Backend-Image-Tag/Digest, Client-Jar-Version je Terminal
  (`/opt/elwasys/raspi-client.latest.jar`, Version steht im Terminal-Log beim Start bzw. in der
  Fernwartungs-Statusantwort), Chart-Version falls Helm.
  Backend: ______ · T1: ______ · T2: ______
- [ ] **0.9** Flyway-Historie einmal ansehen: im Backend-Log steht beim Start
  `BASELINE@1` gefolgt von `V2 … V12` mit `success`. Höchste angewandte Version notieren.
  Ergebnis: ______
- [ ] **0.10** Datenbestand grob notieren (für den Abgleich am Abend): Anzahl Benutzer,
  Geräte, Programme, Standorte laut Portal-Listen sowie die Guthabensumme einiger echter
  Nutzer als Stichprobe.
  Benutzer: ____ · Geräte: ____ · Programme: ____ · Standorte: ____
- [ ] **0.11** Ein **frisches Backup** liegt vom Zustand direkt nach dem Cutover vor
  (Runbook Kap. 5). Datum/Pfad notieren – heute wird viel geschrieben.
  Ergebnis: ______

### 0.3 Testdaten anlegen (Portal → Administration)

> Alles hier Angelegte trägt den Präfix `TEST-` bzw. `test-` und wird in **Block J** wieder
> entfernt. Bestehende Produktivdaten werden **nicht** verändert.

- [ ] **0.12** Benutzergruppe **`TEST-Gruppe`** anlegen (Rabatt: keiner). An **beiden**
  Standorten zulassen (Standorte → jeweiligen Standort bearbeiten → Benutzergruppen).
  Ergebnis: ______
- [ ] **0.13** Benutzergruppe **`TEST-Fremd`** anlegen (Rabatt: keiner) und ausdrücklich **nur
  am Standort von T2** zulassen, **nicht** an T1.
  Ergebnis: ______
- [ ] **0.14** ○ Benutzergruppe **`TEST-Rabatt`** anlegen, Rabatttyp **Faktor**, Wert `0.5`
  (50 %). An beiden Standorten zulassen. Nur nötig, wenn der Gruppenrabatt geprüft werden soll
  (D14).
  Ergebnis: ______
- [ ] **0.15** Benutzer **`test-normal`** anlegen: Name frei, Username `test-normal`, **Email =
  eine Adresse, die du selbst lesen kannst**, Kartennummer **K1**, Gruppe `TEST-Gruppe`,
  Haken „Sende dem Benutzer per Email ein neues Passwort" setzen.
  *Setzt einen erreichbaren SMTP-Server voraus (`spring.mail.*`); der Passwort-Reset hängt an
  seinem eigenen Schalter und ist per Default an – unabhängig von 0.27.*
  Ergebnis: ______
- [ ] **0.16** Benutzer **`test-arm`** anlegen: Karte **K2**, Gruppe `TEST-Gruppe`, keine Email
  nötig.
  Ergebnis: ______
- [ ] **0.17** Benutzer **`test-gesperrt`** anlegen: Karte **K3**, Gruppe `TEST-Gruppe`,
  Haken **„Gesperrt"** setzen. Email nur nötig, wenn I9 gefahren werden soll – dann eine
  **eigene** Adresse verwenden (z. B. eine Plus-Adresse `…+gesperrt@…`), nicht dieselbe wie
  `test-normal`: eine Reset-Mail geht an **alle** Treffer zu einer Adresse.
  Ergebnis: ______
- [ ] **0.18** Benutzer **`test-fremd`** anlegen: Karte **K4**, Gruppe **`TEST-Fremd`**.
  Ergebnis: ______
- [ ] **0.19** **K5** bereitlegen: eine RFID-Karte, deren Nummer bei **keinem** Benutzer
  eingetragen ist (z. B. eine fremde Zutrittskarte). Nicht ins Portal eintragen.
  Ergebnis: ______
- [ ] **0.20** Guthaben buchen (Benutzer → Zeilenaktion „Guthaben"): `test-normal` **20,00 €**,
  `test-arm` **0,50 €**, `test-gesperrt` **10,00 €**, `test-fremd` **10,00 €**.
  Ergebnis: ______

### 0.4 Kurz-Testprogramme anlegen

> **Das ist der Trick gegen lange Wartezeiten.** Die echten Programme laufen 30–90 Minuten;
> die Testprogramme unten haben eine Maximaldauer von wenigen Minuten. Alle Preis-, Auto-Ende-
> und Ablauf-Pfade lassen sich damit in Minuten statt Stunden durchspielen.
>
> Programme → „Neu". Anschließend an **ein** Testgerät je Terminal zuordnen (Geräte →
> Testgerät bearbeiten → Programme). Bestehende Zuordnungen dabei **nicht** entfernen.

- [ ] **0.21** **`TEST-Kurz-Fix`** – Typ **Statisch**, Preis **0,10 €**, Maximaldauer
  **5 Minuten**, Freie Zeit **0**, Auto-Ende **an**, Frühester Abbruch **1 Minute**,
  Benutzergruppen: `TEST-Gruppe` (+ `TEST-Rabatt`, falls angelegt).
  Ergebnis: ______
- [ ] **0.22** **`TEST-Kurz-Dyn`** – Typ **Dynamisch**, Grundgebühr **0,10 €**, Zeitpreis
  **0,05 €**, Abr.-Intervall **Minuten**, Maximaldauer **6 Minuten**, Freie Zeit **1 Minute**,
  Auto-Ende **an**, Frühester Abbruch **1 Minute**, Gruppen wie oben.
  Ergebnis: ______
- [ ] **0.23** **`TEST-Teuer`** – Typ **Statisch**, Preis **5,00 €**, Maximaldauer
  **5 Minuten**, Auto-Ende **an**, Gruppen wie oben. Dient nur dem Fall „Guthaben reicht nicht
  aus" (D4).
  Ergebnis: ______
- [ ] **0.24** **`TEST-Ohne-AutoEnde`** – wie `TEST-Kurz-Fix`, aber Auto-Ende **aus**,
  Maximaldauer **4 Minuten**. Prüft die Zwangsabschaltung bei Maximaldauer (C9).
  Ergebnis: ______
- [ ] **0.25** Die vier Programme sind an **je einem Testgerät an T1 und T2** zugeordnet und
  erscheinen dort in der Programmliste. Testgerät T1: ______ · Testgerät T2: ______
  Ergebnis: ______
- [ ] **0.26** Auto-Ende-Parameter des Testgeräts notieren (Geräte → bearbeiten → „Automatisches
  Ende"): Schwellwert (W) ______ · Wartezeit (s) ______. Default ist `0,5 W` / `20 s`.
  Ergebnis: ______

### 0.5 Benachrichtigungen früh scharfschalten

> Damit **jeder** Lauf des heutigen Tages eine Benachrichtigung erzeugt (und nicht erst die aus
> Block H), wird der Benachrichtigungsdienst hier scharfgeschaltet – das ist Schritt 3e des
> Runbooks. Ist er beim Cutover bereits eingeschaltet worden, sind 0.27/0.28 nur eine
> Bestätigung.

- [ ] **0.27** ⚠ `ELWASYS_NOTIFICATIONS_ENABLED=true` gesetzt, SMTP-Zugangsdaten **und**
  Absenderadresse (`ELWASYS_SMTP_SENDER_ADDRESS`) sowie `ELWASYS_PUSHOVER_API_TOKEN`
  konfiguriert, Backend neu ausgerollt. Prüfen, dass es danach wieder gesund hochkommt (A1).
  *Vorher sicherstellen, dass kein Alt-Terminal mit eigenem Versand mehr läuft – sonst
  Doppelversand.*
  Ergebnis: ______
- [ ] **0.28** Als **`test-normal`** im Portal anmelden (Passwort aus der Mail von 0.15) →
  Benutzer-Chip → **Einstellungen**: Email prüfen, **Email-Benachrichtigung anhaken**,
  **Pushover-Key** eintragen, speichern. Danach wieder als Admin anmelden.
  *Diese Einstellung – nicht der Haken auf der Terminal-Bestätigungsseite – entscheidet über
  den Versand.*
  Ergebnis: ______

---

## Block A — Grundzustand nach dem Cutover

**Ziel:** Bevor irgendetwas gebucht wird – lebt alles, und zwar aus den richtigen Gründen?

- [ ] **A1** **Prozess-Health:** `curl -sS https://<portal>/actuator/health/liveness` liefert
  `{"status":"UP"}`.
  Ergebnis: ______
- [ ] **A2** **Betriebs-Health:** `curl -sS -o /dev/null -w '%{http_code}\n'
  https://<portal>/actuator/health/operational` liefert **200**.
  *Ein `503` hier bedeutet: ein Standort ohne verbundenes Terminal, eine offene abgelaufene
  Ausführung oder ein unquittierter Offline-Vorfall – dann erst A5/G7/G9 klären.*
  Ergebnis: ______
- [ ] **A3** **Details nur angemeldet:** `/actuator/health` **unangemeldet** zeigt nur den
  Gesamtstatus, **keine** Standortnamen. (Im Browser im Inkognito-Fenster aufrufen.)
  Ergebnis: ______
- [ ] **A4** **TLS:** die Portal-URL ist `https://`, das Zertifikat ist gültig, ein Aufruf über
  `http://` landet nicht im Klartext beim Backend. Ablaufdatum des Zertifikats notieren: ______
  Ergebnis: ______
- [ ] **A5** **Beide Terminals verbunden:** Portal → Dashboard, je Standort steht in der
  Kopfzeile **„Verbunden"** mit einem „Verbunden seit"-Zeitpunkt.
  Ergebnis: ______
- [ ] **A6** **Terminals bedienbereit:** an T1 und T2 steht die Geräteauswahl auf dem Display
  (nicht „Fehlerzustand", nicht schwarz). Auf beiden Geräten:
  `ls -l /opt/elwasys/.terminal-ready` hat einen **frischen** Zeitstempel und
  `ls /opt/elwasys/.run-sh-pending-restart` findet **nichts**.
  Ergebnis: ______
- [ ] **A7** **Zeitzonen gleich:** auf beiden Pis `timedatectl` → `Europe/Berlin`, Uhrzeit auf
  die Minute genau; im Backend-Container `date` → dieselbe Zeit/Zone.
  *Läuft eine Uhr auseinander, rechnet der DYNAMIC-Preis über die Zeitumstellungsnacht falsch
  und jede Offline-Nachmeldung fällt in den Ersetzungszweig.*
  Ergebnis: ______
- [ ] **A8** **NTP aktiv** auf beiden Pis (`timedatectl` → `System clock synchronized: yes`).
  Ergebnis: ______
- [ ] **A9** **Portal-Basis-URL gesetzt:** `ELWASYS_PORTAL_BASE_URL` (Compose) bzw.
  `passwordReset.baseUrl` (Helm) zeigt auf die öffentliche `https://`-Portal-URL – nicht auf
  `localhost`. Wird in H4 an einer echten Reset-Mail gegengeprüft.
  Ergebnis: ______
- [ ] **A10** **Log sauber:** Backend-Log der letzten Stunde enthält keine `ERROR`-Zeilen
  (`docker compose logs --since 1h backend | grep -i error`).
  Ergebnis: ______
- [ ] **A11** **Rollout-Gate grün:** `deploy/smoke/post-deploy-smoke.sh` einmal mit den
  produktiven Zugangsdaten laufen lassen → PASS.
  Ergebnis: ______

---

## Block B — Portal – Administration

**Ziel:** Alles, was ein Admin im Alltag tut, einmal anfassen. Rein am Schreibtisch, ohne
Hardware – deshalb früh am Tag, solange die Maschinen noch nicht belegt sind.

### B.1 Navigation und Darstellung

- [ ] **B1** Alle sechs Menüpunkte öffnen sich und rendern: **Dashboard, Benutzer,
  Benutzergruppen, Programme, Geräte, Standorte**. Der aktive Punkt ist hervorgehoben, der
  Ansichtsname steht im Kopfbalken.
  Ergebnis: ______
- [ ] **B2** Der Benutzer-Chip oben rechts zeigt Initialen-Avatar und Namen; das Menü enthält
  **Einstellungen / Passwort ändern / Logout**.
  Ergebnis: ______
- [ ] **B3** **Dashboard:** beide Standorte sind zu sehen, je Standort alle Geräte als Karten
  mit „Frei"/„Besetzt".
  Ergebnis: ______
- [ ] **B4** **Freitext-Filter:** in der Benutzerliste einen Namensteil eingeben → nur Treffer
  bleiben; Feld leeren → vollständige Liste zurück. Dasselbe in Geräte- und Programmliste.
  Ergebnis: ______
- [ ] **B5** **Sortierung nach Rohwert:** Benutzerliste nach **Guthaben** sortieren. Erwartet
  ist eine Sortierung nach Betrag (10,00 € vor 9,00 €), nicht alphabetisch nach Anzeigetext.
  Ergebnis: ______

### B.2 Stammdaten anlegen, ändern, löschen

- [ ] **B6** **Benutzer bearbeiten:** bei `test-arm` die Email nachtragen, speichern, Dialog
  erneut öffnen → Wert steht. Ergebnis: ______
- [ ] **B7** **Sperren persistent:** bei `test-gesperrt` steht der Haken „Gesperrt" nach
  erneutem Öffnen weiterhin; in der Liste erscheint das Gesperrt-Symbol.
  Ergebnis: ______
- [ ] **B8** **Doppelte Kartennummer:** bei `test-arm` zusätzlich Karte **K1** eintragen
  (die von `test-normal`) → Speichern wird mit einer Fehlermeldung abgewiesen, nichts wird
  gespeichert. Danach den Wert wieder entfernen.
  Ergebnis: ______
- [ ] **B9** **Doppelter Username:** einen neuen Benutzer mit Username `TEST-NORMAL`
  (nur andere Groß-/Kleinschreibung) anlegen → wird abgewiesen.
  Ergebnis: ______
- [ ] **B10** **Pflichtfeldprüfung:** in irgendeinem Dialog ein Pflichtfeld leeren und speichern
  → Feld wird rot markiert, Dialog bleibt offen, **der Speichern-Knopf bleibt danach bedienbar**
  (nicht dauerhaft ausgegraut).
  Ergebnis: ______
- [ ] **B11** **Dialog schließen ohne Speichern:** Änderung eintragen, Dialog per **Kreuz**,
  per **ESC** und per **Klick daneben** schließen → in allen drei Fällen ist nichts gespeichert.
  Ergebnis: ______
- [ ] **B12** **Gerät bearbeiten:** beim Testgerät die Position ändern, speichern, Dialog erneut
  öffnen → Wert steht. Anschließend zurückstellen.
  Ergebnis: ______
- [ ] **B13** **Gerät deaktivieren:** Testgerät auf „Aktiviert = aus" setzen. Am Terminal T1
  erscheint es ausgegraut/nicht buchbar (wird in D9 geprüft). **Danach wieder aktivieren.**
  Ergebnis: ______
- [ ] **B14** **Löschwächter Programm:** versuchen, `TEST-Kurz-Fix` zu löschen, solange es einem
  Gerät zugeordnet ist → wird mit Meldung abgelehnt.
  Ergebnis: ______
- [ ] **B15** **Löschwächter Standort:** versuchen, einen Standort mit zugeordneten Geräten zu
  löschen → wird abgelehnt. **Nicht bestätigen, wenn wider Erwarten doch gelöscht würde.**
  Ergebnis: ______
- [ ] **B16** **Löschen mit Rückfrage:** eine leere Wegwerf-Benutzergruppe `TEST-Wegwerf`
  anlegen und wieder löschen → Rückfrage „Ja/Nein" erscheint, „Nein" lässt sie stehen, „Ja"
  entfernt sie. *(Nicht `TEST-Gruppe`/`TEST-Rabatt` nehmen – die werden noch gebraucht.)*
  Ergebnis: ______
- [ ] **B17** **Standort-Einstellungen:** Standort von T1 öffnen → Name vorbelegt,
  **Offline-Maximaldauer (Minuten)** sichtbar. Aktuellen Wert notieren: ______ min
  (wird in F5 kurzzeitig geändert und danach zurückgestellt).
  Ergebnis: ______

### B.3 Geld

- [ ] **B18** **Einzahlung:** `test-normal` 1,00 € gutschreiben. Guthaben in der Liste steigt
  sofort um 1,00 €, der Buchungstext ist vorbelegt.
  Ergebnis: ______
- [ ] **B19** **Betrag ≤ 0 abgewiesen:** Einzahlung über `0` bzw. `-1` → Fehlermeldung, keine
  Buchung.
  Ergebnis: ______
- [ ] **B20** **Auszahlung über Guthaben:** bei `test-arm` (0,50 €) 5,00 € auszahlen wollen →
  Fehlermeldung „Guthaben reicht nicht", keine Buchung.
  Ergebnis: ______
- [ ] **B21** **Doppelklick-Schutz:** beim Buchen bewusst zweimal schnell auf „Buchen" klicken →
  es entsteht **genau eine** Buchung. Danach ist der Knopf wieder bedienbar (Dialog erneut
  öffnen und eine weitere Buchung durchführen können).
  Ergebnis: ______
- [ ] **B22** **Buchungshistorie:** Zeilenaktion „Umsätze" bei `test-normal` → alle Buchungen,
  neueste zuerst, keine Bearbeiten-/Löschfunktion vorhanden.
  Ergebnis: ______
- [ ] **B23** **Auszahlung regulär:** `test-normal` 1,00 € auszahlen → Guthaben sinkt, ein
  eigener Buchungssatz entsteht (die Einzahlung aus B18 bleibt unverändert stehen).
  Ergebnis: ______

### B.4 Standort-Tokens

- [ ] **B24** Standorte → Zeilenaktion **„Terminal-Tokens verwalten"** beim Standort von T1 →
  die vorhandenen Tokens sind gelistet (das beim Cutover ausgestellte ist **aktiv**). Nirgends
  steht ein Token-Hash oder ein Klartext-Token aus der Vergangenheit.
  Ergebnis: ______
- [ ] **B25** **Neues Token erzeugen** → Klartext wird **genau einmal** angezeigt. Dialog
  schließen und erneut öffnen → der Klartext ist weg, die Zeile steht als aktives Token da.
  *(Dieses Token wird in B26 wieder widerrufen – nicht auf ein Terminal übertragen.)*
  Ergebnis: ______
- [ ] **B26** **Token widerrufen:** das eben erzeugte Token widerrufen → Status wechselt auf
  „widerrufen", die Zeile bleibt als Beleg stehen, **das produktive Token von T1 bleibt aktiv**.
  T1 arbeitet unverändert weiter (am Terminal einmal eine Karte auflegen).
  Ergebnis: ______

### B.5 Live-Updates zwischen Sessions

- [ ] **B27** Im Inkognito-Fenster ebenfalls als Admin anmelden und die **Benutzerliste**
  öffnen. Im ersten Fenster `test-normal` 1,00 € gutschreiben → im zweiten Fenster aktualisiert
  sich das Guthaben **ohne Neuladen**.
  Ergebnis: ______
- [ ] **B28** Im zweiten Fenster einen Filter auf die Benutzerliste setzen, im ersten Fenster
  erneut buchen → der **Filter bleibt gesetzt**, die Liste zeigt weiterhin nur die Treffer.
  Ergebnis: ______

---

## Block C — Terminal – Normalbetrieb

**Ziel:** der Weg, den jeder Bewohner täglich geht – an **T1**, danach die Kurzfassung an
**T2**.

> **Vorgehen bei den Waschgängen:** Maschine leer laufen lassen. Für das Auto-Ende gilt der
> Kniff aus C7: die Maschine nach ein bis zwei Minuten **am eigenen Schalter/Programmwähler
> ausschalten**. Der Strombezug fällt dadurch unter den Schwellwert, und das Auto-Ende greift
> nach der Wartezeit des Geräts (Default 20 s) – ohne einen vollen Waschgang abzuwarten.

- [ ] **C1** **Ruhezustand:** T1 zeigt die Geräteliste **des eigenen Standorts**. Geräte des
  anderen Standorts erscheinen **nicht**.
  Ergebnis: ______
- [ ] **C2** **Karten-Login:** Karte **K1** auflegen → `test-normal` ist angemeldet, Name und
  Guthaben stehen in der Kopfzeile.
  Ergebnis: ______
- [ ] **C3** **Geräteauswahl:** Testgerät antippen → Programmliste erscheint, `TEST-Kurz-Fix`,
  `TEST-Kurz-Dyn`, `TEST-Teuer`, `TEST-Ohne-AutoEnde` **und** die echten Programme des Geräts
  sind aufgeführt.
  Ergebnis: ______
- [ ] **C4** **Bestätigungsseite (FIXED):** `TEST-Kurz-Fix` wählen → angezeigt werden
  **Guthaben**, **Kosten (0,10 €)**, **verbleibendes Guthaben** und **„Späteste Abschaltung"**
  = jetzt + 5 Minuten.
  Ergebnis: ______
- [ ] **C5** **Bestätigungsseite (DYNAMIC):** zurück, `TEST-Kurz-Dyn` wählen → die Anzeige weist
  **Grundgebühr** und **Zeitpreis** getrennt aus; die genannten Kosten entsprechen dem
  Maximalpreis bei voller Laufzeit.
  Ergebnis: ______
- [ ] **C6** **Start:** `TEST-Kurz-Fix` bestätigen → die Steckdose schaltet **ein**, die Maschine
  läuft an, das Gerät erscheint in der Liste als **belegt** mit Restzeit.
  Ergebnis: ______
- [ ] **C7** ⏱ **Auto-Ende (ca. 2–3 min)** ▶ parallel: nach ~1,5 Minuten die Maschine am
  eigenen Schalter ausschalten. Erwartet: das Terminal erkennt den Leistungsabfall unter den
  Schwellwert und beendet die Ausführung nach
  `max(0, Frühester Abbruch − bisherige Laufzeit) + Wartezeit des Geräts` –
  hier also nach ~20 Sekunden. Die Steckdose schaltet **aus**, das Gerät ist wieder **frei**.
  *Nebenprobe: schaltet man die Maschine innerhalb dieser 20 Sekunden wieder ein, wird das
  geplante Ende verworfen und der Lauf geht weiter.*
  *Wartezeit nutzen für C11 am zweiten Terminal.*
  Ergebnis: ______
- [ ] **C8** **Abrechnung stimmt:** Portal → Benutzer → `test-normal` → „Umsätze": es gibt genau
  **eine** neue Buchung über **0,10 €**; das Guthaben ist um genau diesen Betrag gesunken.
  Ergebnis: ______
- [ ] **C9** ⏱ **Zwangsabschaltung bei Maximaldauer (4 min)** ▶ parallel: `TEST-Ohne-AutoEnde`
  starten (Auto-Ende aus) und die Maschine **weiterlaufen lassen**. Erwartet: nach genau
  4 Minuten schaltet das Terminal die Steckdose ohne Rücksicht auf den Maschinenzustand ab und
  rechnet ab.
  *Wartezeit nutzen für C12/C13.*
  Ergebnis: ______
- [ ] **C10** **Dynamische Abrechnung:** `TEST-Kurz-Dyn` starten, nach gut 3 Minuten wie in C7
  beenden. Erwartet: **Grundgebühr + Zeitpreis je voller Minute der Gesamtlaufzeit**;
  angefangene Minuten zählen nicht. Bei 3:20 Laufzeit also `0,10 € + 3 × 0,05 € = 0,25 €`.
  *Achtung, nicht offensichtlich: die „Freie Zeit" wird **nicht** von der abgerechneten Dauer
  abgezogen. Sie entscheidet nur, ob der Lauf ganz kostenlos ist (Laufzeit ≤ freie Zeit,
  siehe D15) – darüber hinaus zählt die volle Laufzeit.*
  Laufzeit: ______ · Erwarteter Betrag: ______ · Tatsächlich: ______
  Ergebnis: ______
- [ ] **C11** **Dasselbe an T2:** C2, C3, C6, C7 an **T2** wiederholen (mit `test-normal`,
  Karte K1). Erwartet: identisches Verhalten, Buchung landet beim selben Benutzer.
  Ergebnis: ______
- [ ] **C12** **Dashboard spiegelt den Lauf:** während eine Ausführung läuft, zeigt das
  Portal-Dashboard die Gerätekarte als **„Besetzt"** mit Programm, Benutzer und Restzeit samt
  Fortschrittsbalken – ohne Neuladen der Seite.
  Ergebnis: ______
- [ ] **C13** **Geräte-Historie:** im Dashboard beim Testgerät die Ausführungshistorie
  aufklappen → die eben gelaufenen Ausführungen stehen mit Datum, Benutzer, Dauer, Preis darin;
  die laufende ist hervorgehoben. Weiterblättern funktioniert.
  Ergebnis: ______
- [ ] **C14** **Abmelden:** am Terminal über den Benutzer-Knopf → **Abmelden** → zurück zur
  Geräteauswahl, kein Benutzer mehr angemeldet.
  Ergebnis: ______
- [ ] **C15** **Benutzerinfo am Terminal:** angemeldet den Benutzer-Knopf antippen → Name,
  Username, **Guthaben** und Email werden angezeigt.
  Ergebnis: ______

---

## Block D — Terminal – Grenz- und Fehlerfälle

**Ziel:** alles, was schiefgehen kann, wenn ein Mensch das Terminal bedient. Die meisten Punkte
dauern unter einer Minute.

### D.1 Login-Fehlerfälle

- [ ] **D1** **Unbekannte Karte:** K5 auflegen → Terminal meldet **„Karte unbekannt"**, es wird
  niemand angemeldet, der Zustand fällt nach kurzer Zeit zurück.
  Ergebnis: ______
- [ ] **D2** **Gesperrter Benutzer:** K3 (`test-gesperrt`) auflegen → **„Karte gesperrt"**,
  kein Login.
  Ergebnis: ______
- [ ] **D3** **Gruppe am Standort nicht zugelassen:** K4 (`test-fremd`) an **T1** auflegen →
  **„Unzulässig"**, kein Login. Dieselbe Karte an **T2** → Login klappt.
  Ergebnis: ______
- [ ] **D4** **Zu wenig Guthaben:** mit K2 (`test-arm`, 0,50 €) anmelden, Gerät wählen,
  `TEST-Teuer` (5,00 €) auswählen → Hinweis **„Guthaben reicht nicht aus"**, der Start-Knopf
  lässt sich nicht auslösen.
  Ergebnis: ______
- [ ] **D5** **Günstigeres Programm geht trotzdem:** mit demselben Benutzer `TEST-Kurz-Fix`
  (0,10 €) wählen → Start ist möglich. **Nicht starten** oder direkt wieder abbrechen.
  Ergebnis: ______
- [ ] **D6** ⏱ **Automatische Abmeldung (~1 min):** mit K1 anmelden und das Terminal
  unberührt lassen. Erwartet: nach der eingestellten `sessionTimeout` (Default **60 s**) meldet
  sich der Benutzer selbst ab.
  Tatsächliche Dauer: ______ s
  Ergebnis: ______
- [ ] **D7** ⏱ **Display-Abschaltung (~15 s):** Terminal unberührt lassen → nach
  `displayTimeout` (Default **10 s**) geht die Hintergrundbeleuchtung aus; eine Berührung
  (oder eine aufgelegte Karte) weckt sie wieder.
  Ergebnis: ______
- [ ] **D8** **Karte während einer laufenden Anmeldung:** angemeldet lassen und K2 auflegen →
  das Terminal verhält sich definiert (Wechsel oder Ignorieren) und hinterlässt keinen
  gemischten Zustand aus zwei Benutzern.
  Ergebnis: ______

### D.2 Geräte- und Buchungs-Grenzfälle

- [ ] **D9** **Deaktiviertes Gerät:** Testgerät im Portal deaktivieren (B13) → an T1 ist es in
  der Liste als deaktiviert erkennbar und **nicht buchbar**. Danach wieder aktivieren; die
  Kachel wird ohne Terminal-Neustart wieder normal.
  Ergebnis: ______
- [ ] **D10** **Belegtes Gerät:** während einer laufenden Ausführung mit einer **anderen** Karte
  (K2) dasselbe Gerät buchen wollen → das Gerät ist als belegt gekennzeichnet und lässt sich
  nicht ein zweites Mal starten.
  Ergebnis: ______
- [ ] **D11** **Abbruch mit Rückfrage:** eine Ausführung starten, das belegte Gerät antippen →
  Abbruchseite mit **„Abbruch bestätigen?"**. Erst **„Nein"** → Ausführung läuft weiter.
  Erneut, dann **„Ja"** → Steckdose aus, Gerät frei.
  Ergebnis: ______
- [ ] **D12** **Abbruch rechnet die tatsächliche Laufzeit ab:** nach D11 im Portal prüfen –
  bei `TEST-Kurz-Fix` (statisch) wird der volle Festpreis fällig; bei `TEST-Kurz-Dyn` nur die
  bis dahin gelaufenen vollen Minuten.
  Ergebnis: ______
- [ ] **D13** **„Tür freigeben":** nach einem beendeten Lauf beim Gerät die Schaltfläche
  **„Tür freigeben"** antippen → die Steckdose geht für ~30 Sekunden an und danach von selbst
  wieder aus; es entsteht **keine** Buchung und **kein** Guthabenabzug.
  Ergebnis: ______
- [ ] **D14** ○ **Gruppenrabatt:** `test-normal` vorübergehend in `TEST-Rabatt` (Faktor 0,5)
  setzen, `TEST-Kurz-Fix` buchen und wie in C7 beenden → abgerechnet werden **0,05 €**.
  ⚠ Danach zurück in `TEST-Gruppe` setzen.
  Ergebnis: ______
- [ ] **D15** **Freie Zeit greift:** `TEST-Kurz-Dyn` (freie Zeit 1 Minute) starten und
  **innerhalb der ersten Minute** abbrechen → Preis **0,00 €**, keine Guthabenänderung.
  Zusammen mit C10 ist damit beides belegt: unterhalb der freien Zeit kostenlos, darüber die
  volle Laufzeit.
  Ergebnis: ______
- [ ] **D16** ⚠ **Fremdeinschaltung wird zurückgenommen:** in Phoscon/deCONZ die Steckdose eines
  **freien** Geräts von Hand einschalten. Erwartet: das Terminal schaltet sie innerhalb von
  ~20 Sekunden von selbst wieder aus und protokolliert eine Warnung
  (`Device has been powered on but there is no execution running`).
  Ergebnis: ______
- [ ] **D17** ○ **Nicht registriertes Gerät:** ein Gerät ohne hinterlegte deCONZ-UUID erscheint
  am Terminal als „nicht registriert" und lässt sich nicht buchen. *(Nur prüfen, wenn ein
  Ersatz-Zwischenstecker vorhanden ist – kein Produktivgerät dafür entkoppeln.)*
  Ergebnis: ______
- [ ] **D18** **Bedienung unter Last:** während ein Programm läuft, mehrfach zwischen
  Geräteliste, Benutzerinfo und Gerätedetails hin- und herwechseln → keine hängende Anzeige,
  keine doppelten Einträge, die Restzeit läuft korrekt weiter.
  Ergebnis: ______
- [ ] **D19** **Schnelles Doppeltippen:** auf der Bestätigungsseite zweimal schnell „Starten"
  antippen → es entsteht **genau eine** Ausführung und **eine** Buchung.
  Ergebnis: ______

---

## Block E — Zwei Terminals / Nebenläufigkeit

**Ziel:** die beiden Terminals stören einander nicht – und dieselbe Person kann an beiden
gleichzeitig aktiv sein.

- [ ] **E1** **Standort-Trennung:** T1 zeigt ausschließlich Geräte seines Standorts, T2
  ausschließlich seine. Kein Gerät taucht auf beiden auf.
  Ergebnis: ______
- [ ] **E2** **Gleichzeitige Buchung, ein Benutzer:** mit K1 an T1 starten und **sofort** danach
  mit K1 an T2 auf einem anderen Gerät starten. Erwartet: beide Läufe starten, das Guthaben
  wird **zweimal** korrekt belastet, kein negativer Saldo, keine Fehlermeldung.
  Guthaben vorher: ______ · nachher: ______ (erwartet: −0,20 €)
  Ergebnis: ______
- [ ] **E3** **Guthaben-Wettlauf:** `test-arm` per Auszahlung auf genau **0,10 €** bringen.
  Dann mit K2 an T1
  **und** an T2 nahezu gleichzeitig je `TEST-Kurz-Fix` (0,10 €) starten wollen. Erwartet:
  **genau eine** Buchung geht durch, die andere wird mit „Guthaben reicht nicht aus" abgelehnt –
  das Guthaben wird **nicht** negativ.
  Ergebnis: ______
- [ ] **E4** **Dashboard zeigt beide:** im Portal sind währenddessen beide Standorte mit je
  einem belegten Gerät zu sehen, jeweils mit dem richtigen Benutzer.
  Ergebnis: ______
- [ ] **E5** **Zwei Admin-Sessions:** in beiden Browserfenstern gleichzeitig am Portal
  arbeiten (einer im Dashboard, einer in den Benutzern) → keine gegenseitigen Aussetzer,
  Änderungen erscheinen im jeweils anderen Fenster.
  Ergebnis: ______
- [ ] **E6** **Nur T1 anfassen, T2 unbeeindruckt:** an T1 einen Abbruch durchführen → T2 zeigt
  weiterhin seinen eigenen, unveränderten Zustand.
  Ergebnis: ______

---

## Block F — Ausfall- und Offline-Szenarien

**Ziel:** der wichtigste Block des Tages. Hier wird bewiesen, dass kein Geld verloren geht,
wenn Netz, Backend oder Strom wegbrechen.

> **Vor jedem Punkt kurz notieren, was erwartet wird** – erst dann den Ausfall auslösen. Das
> macht den Unterschied zwischen „hat funktioniert" und „ich glaube, es hat funktioniert".
>
> **Wo man nachsieht:** Das Terminal zeigt den Offline-Betrieb **nicht** in der Oberfläche an
> (das ist Absicht – der Bedienfluss bleibt gleich). Nachweisbar ist er über die Dateien im
> Arbeitsverzeichnis des Terminals:
> `/opt/elwasys/offline-snapshot.json` · `/opt/elwasys/offline-journal.jsonl` ·
> `/opt/elwasys/offline-journal.jsonl.deadletter` sowie über das Log unter `/opt/elwasys/log/`.

### F.1 Backend-Ausfall während eines laufenden Programms

- [ ] **F1** **Vorbereitung:** an T1 mit K1 `TEST-Kurz-Fix` starten. Danach **das Backend
  stoppen** (`docker compose stop backend` bzw. `kubectl scale --replicas=0`).
  Ergebnis: ______
- [ ] **F2** ⏱ **Lauf endet trotzdem lokal (~2–3 min)** ▶ parallel: die Maschine wie in C7
  ausschalten. Erwartet: das Terminal beendet die Ausführung **lokal**, schaltet die Steckdose
  ab und schreibt einen Eintrag ins Journal. Prüfen:
  `wc -l /opt/elwasys/offline-journal.jsonl` → mindestens eine Zeile.
  *Wartezeit nutzen für F3.*
  Ergebnis: ______
- [ ] **F3** **Neue Buchung offline:** während das Backend noch aus ist, mit K1 an T1 eine
  **neue** Ausführung starten. Erwartet: das Terminal akzeptiert sie gegen den gespeicherten
  Snapshot, die Steckdose schaltet ein, der Bedienfluss ist unverändert. Das Journal wächst um
  einen START-Eintrag.
  Ergebnis: ______
- [ ] **F4** ⏱ **Nachmeldung nach Rückkehr (~1–2 min):** das Backend wieder starten. Erwartet:
  innerhalb von ~20 Sekunden (Abgleichintervall) meldet das Terminal die Journaleinträge nach.
  Prüfen:
  - `/opt/elwasys/offline-journal.jsonl` ist **leer** bzw. verschwunden,
  - im Portal stehen **beide** Ausführungen mit ihren **echten Start-/Endzeiten** (nicht der
    Rückkehrzeit),
  - das Guthaben von `test-normal` ist um genau die Summe der beiden Läufe gesunken –
    **nicht doppelt**.
  Guthaben vorher: ______ · nachher: ______ · erwartet: ______
  Ergebnis: ______
- [ ] **F5** ⚠ ⏱ **Abgelaufener Snapshot (~5 min)** – der Fall „Backend zu lange weg":
  1. Portal → Standorte → Standort von T1 → **Offline-Maximaldauer auf 2 Minuten** setzen.
  2. Am Terminal ~1 Minute warten, damit der Snapshot frisch gezogen wird.
  3. Backend stoppen und **mehr als 2 Minuten** warten.
  4. An T1 eine neue Buchung versuchen.

  Erwartet: die Buchung wird **abgelehnt**; das Terminal zeigt seinen Fehlerzustand mit
  Wiederholen-Möglichkeit statt still eine nicht gedeckte Buchung anzunehmen.
  ⚠ **Danach:** Backend starten und die Offline-Maximaldauer auf den in B17 notierten Wert
  zurücksetzen.
  Ergebnis: ______

### F.2 Netz- und Infrastrukturausfälle

- [ ] **F6** ⏱ **Netzwerk am Terminal trennen (~2 min):** an T2 das Netzwerkkabel ziehen (bzw.
  WLAN deaktivieren), während **kein** Programm läuft. Erwartet:
  - das Terminal bleibt bedienbar, Kartenlogin funktioniert weiter (gegen den Snapshot),
  - im Portal-Dashboard wechselt der Standort von T2 nach spätestens ~2 Minuten auf
    **„Nicht verbunden"**,
  - `/actuator/health/operational` liefert **503**.

  Zeit bis „Nicht verbunden": ______
  Ergebnis: ______
- [ ] **F7** ⏱ **Wiederverbinden (~1–5 min):** Netz wieder anstecken. Erwartet: das Terminal
  verbindet sich selbstständig neu (Wartezeit wächst schrittweise bis max. 5 Minuten), das
  Dashboard zeigt wieder **„Verbunden"**, `/operational` steht wieder auf **200**.
  Zeit bis „Verbunden": ______
  Ergebnis: ______
- [ ] **F8** ⚠ ⏱ **Datenbank stoppen (~3 min):** `docker compose stop postgres` (nur bei
  mitgelieferter DB; bei externer Bestands-DB **überspringen**). Erwartet: `/actuator/health`
  wird ungesund, das Portal meldet Fehler statt abzustürzen, die Terminals fallen in den
  Offline-Pfad. Danach DB wieder starten → alles kehrt von selbst zurück, ohne Neustart des
  Backends.
  Ergebnis: ______
- [ ] **F9** ⚠ **deCONZ/Zigbee-Gateway weg:** deCONZ stoppen (bzw. den ConBee-Stick ziehen).
  Erwartet: eine Buchung schlägt mit einer verständlichen Meldung („Gerät nicht erreichbar")
  fehl, das Terminal bleibt bedienbar und stürzt nicht ab. Danach deCONZ wieder starten →
  das Terminal verbindet sich selbst neu und Buchungen funktionieren wieder **ohne**
  Terminal-Neustart.
  Ergebnis: ______

### F.3 Terminal-Ausfälle

- [ ] **F10** **Geplanter Neustart (im Betrieb):** T1 über das Portal neu starten lassen
  (Dashboard → Kopfzeile des Standorts → **„Neustart"**). Erwartet: das Portal **quittiert den
  Empfang** des Befehls, das Terminal reinitialisiert sich und kommt in die Geräteauswahl
  zurück. *Das ist ein Neustart innerhalb des Prozesses, kein Betriebssystem-Neustart – die
  WebSocket-Verbindung soll ihn ausdrücklich überleben: „Verbunden seit" bleibt danach
  **unverändert**.*
  Ergebnis: ______
- [ ] **F11** ⚠ ⏱ **Stromausfall mitten im Programm (~6 min)** – der wichtigste Ausfalltest:
  1. An T1 mit K1 **`TEST-Kurz-Fix`** starten (Maximaldauer 5 min).
  2. Nach ~1 Minute **dem Terminal den Strom ziehen** (nicht der Maschine!).
  3. Erwartet und **sofort prüfen**: die Steckdose der Maschine bleibt **eingeschaltet**, die
     Maschine läuft unbeaufsichtigt weiter. *Das ist das dokumentierte Betriebsrisiko – hier
     wird bestätigt, dass es eintritt und dass man es bemerkt.*
  4. Terminal **ausgeschaltet lassen**, bis die Maximaldauer (5 min ab Start) verstrichen ist.
  5. Portal prüfen: `/actuator/health/operational` steht auf **503**, in der Benutzerliste
     trägt `test-normal` ein **Warndreieck**.
  6. Warndreieck anklicken → Dialog **„Abgelaufene Ausführungen"** listet den Lauf.
  7. **„Abrechnen"** → die Ausführung wird abgerechnet, das Warndreieck verschwindet,
     `/operational` geht auf **200** zurück.
  8. Terminal wieder einschalten. Erwartet: es startet, erreicht die Geräteauswahl und zeigt
     das Gerät als **frei** (die Ausführung ist ja abgerechnet). Die Steckdose der Maschine
     wird spätestens beim nächsten Geräte-Scan (~20 s) **abgeschaltet**.

  Ergebnis: ______
- [ ] **F12** ○ **Löschen statt Abrechnen:** F11 sinngemäß wiederholen und im Dialog
  stattdessen **„Löschen"** wählen → Rückfrage erscheint, danach ist die Ausführung ohne
  Buchung verschwunden.
  Ergebnis: ______
- [ ] **F13** **Stromausfall ohne laufendes Programm:** T1 einfach aus- und wieder einschalten.
  Erwartet: sauberer Start in die Geräteauswahl, keine Fehlermeldung, Verbindung zum Backend
  innerhalb weniger Sekunden.
  Startdauer bis bedienbar: ______
  Ergebnis: ______
- [ ] **F14** ⏱ **Wiederaufnahme nach Neustart (~5 min):** an T1 mit K1 `TEST-Kurz-Fix`
  (5 min) starten, nach ~1 Minute dem Terminal den Strom ziehen und **sofort wieder
  einschalten**. Erwartet: nach dem Hochfahren übernimmt das Terminal die laufende Ausführung
  wieder als laufend – das Gerät bleibt **belegt**, die Restzeit rechnet ab dem
  **ursprünglichen** Start weiter, und der Lauf wird regulär beendet und **einmal**
  abgerechnet.
  *Bekannte Einschränkung, hier bewusst nicht getestet: startet das Terminal neu, während
  gleichzeitig das Backend nicht erreichbar ist, kann es einen laufenden Lauf nicht
  wiederaufnehmen (die Belegungsdaten kommen nur vom Backend).*
  Ergebnis: ______
- [ ] **F15** ○ **Doppelbuchungsschutz beim Nachmelden:** die Nachmeldung aus F4 lief bereits
  erfolgreich. Zusätzlich prüfen, dass es zu jedem Lauf im Portal **genau eine** Buchung gibt –
  Benutzer → `test-normal` → „Umsätze" durchzählen und mit der Zahl der heute gefahrenen Läufe
  vergleichen.
  Läufe heute: ______ · Buchungen: ______
  Ergebnis: ______

### F.4 Offline-Vorfälle (Dead-Letter)

- [ ] **F16** **Regelzustand:** Portal → **Offline-Vorfälle** → die Liste offener Vorfälle ist
  **leer**, der Umschalter auf „quittierte" funktioniert, das Dashboard zeigt keinen Hinweis.
  Ergebnis: ______
- [ ] **F17** ○ ⚠ ⏱ **Vorfall erzwingen (~8 min)** *(braucht einen freien
  Ersatz-Zwischenstecker – kein Produktivgerät dafür opfern)*. Der Trick: nicht das Backend
  abschalten, sondern **nur das Terminal vom Netz nehmen** – dann bleibt das Portal die ganze
  Zeit bedienbar.
  1. Im Portal ein **Wegwerf-Testgerät** `TEST-Wegwerfgerät` am Standort von T1 anlegen, dem
     Ersatzstecker (deCONZ-UUID) zuordnen und `TEST-Kurz-Fix` daran hängen.
  2. **T1 vom Netz trennen** (Kabel ziehen). Kurz warten, bis der Snapshot das neue Gerät
     kennt – notfalls vor dem Trennen eine Minute warten.
  3. An T1 mit K1 auf dem Wegwerf-Testgerät **offline buchen und beenden** (wie F3/F2).
  4. Im Portal das **Wegwerf-Testgerät löschen** (es hat aus Backend-Sicht keine laufende
     Ausführung, das Löschen geht also durch).
  5. **T1 wieder ans Netz.**

  Erwartet: die Nachmeldung scheitert fachlich („Gerät unbekannt"), der Eintrag landet in
  `/opt/elwasys/offline-journal.jsonl.deadletter` statt den Replay dauerhaft zu blockieren,
  und im Portal erscheint unter **Offline-Vorfälle** ein offener Vorfall mit Art, Benutzer und
  Betrag; `/actuator/health/operational` steht auf **503**.
  Anschließend den Vorfall **quittieren** → er verschwindet aus der offenen Liste, bleibt unter
  „quittierte" als Beleg stehen, `/operational` geht auf **200**.
  Ergebnis: ______

---

## Block G — Fernwartung, Betrieb, Alarmierung

**Ziel:** kann man das System aus der Ferne beobachten und bedienen – und erreicht ein Alarm
tatsächlich einen Menschen?

- [ ] **G1** **Verbindungsanzeige:** Dashboard zeigt für beide Standorte „Verbunden" mit
  plausiblem „Verbunden seit".
  Ergebnis: ______
- [ ] **G2** **Log anzeigen (T1):** Dashboard → Standort T1 → **„Log anzeigen"** → das Log
  erscheint innerhalb weniger Sekunden. Erwartet: es sind die **letzten** Zeilen (max. 1000
  bzw. 128 KiB); ist gekürzt worden, sagt das die erste Zeile.
  *Wichtig: direkt danach G3 ausführen – bis Juli 2026 riss genau dieser Abruf die Verbindung ab.*
  Ergebnis: ______
- [ ] **G3** **Verbindung überlebt den Log-Abruf:** unmittelbar nach G2 die Dashboard-Seite neu
  laden → der Standort steht weiterhin auf **„Verbunden"**, „Verbunden seit" ist **unverändert**
  (kein Reconnect).
  Ergebnis: ______
- [ ] **G4** **Log anzeigen (T2):** dasselbe für T2.
  Ergebnis: ______
- [ ] **G5** **Neustart aus dem Portal (T2):** wie F10, diesmal für **T2** – und diesmal mit
  Blick auf die Quittung: das Portal meldet, dass der Befehl **angekommen** ist (nicht nur,
  dass er abgeschickt wurde).
  Ergebnis: ______
- [ ] **G6** **Fernwartung eines getrennten Terminals:** während T2 vom Netz ist (F6),
  „Log anzeigen" versuchen → es kommt **sofort** eine verständliche Fehlermeldung
  („nicht verbunden"), kein Hängen, kein Absturz.
  Ergebnis: ______
- [ ] **G7** **Alarm-Probe Stufe 1 (lokales Skript):** Backend stoppen und einen Lauf des
  Alarmskripts erzwingen (`sudo systemctl start elwasys-health-alert.service`). Erwartet: eine
  **Pushover-/Mail-Meldung kommt auf dem Handy an**. Backend wieder starten und erneut auslösen
  → die **„behoben"-Meldung** folgt.
  Zeit bis Alarm: ______ · Zeit bis Entwarnung: ______
  Ergebnis: ______
- [ ] **G8** **Alarm-Probe Stufe 2 (externer Monitor):** den externen Uptime-Monitor auslösen –
  je nach Variante den Endpoint blockieren oder den lokalen Timer stoppen (Dead-Man's-Switch)
  und warten, bis der externe Dienst alarmiert. Erwartet: **Alarm auf dem Handy**, ohne dass
  der lokale Poller beteiligt ist.
  Zeit bis Alarm: ______
  Ergebnis: ______
  ⚠ Danach Timer/Monitor wieder in den Normalzustand bringen und den Ruhezustand bestätigen.
- [ ] **G9** **Systemd-Timer läuft:** `systemctl list-timers elwasys-health-alert.timer` zeigt
  einen künftigen Lauf (bzw. der Cron-Eintrag existiert).
  Ergebnis: ______
- [ ] **G10** **Backup-Cron aktiv:** der Backup-Job ist eingerichtet
  (`/etc/cron.d/elwasys-db-backup` o. ä.) und im Backup-Verzeichnis liegt mindestens ein
  aktueller Dump. Offsite-Spiegelung geprüft.
  Letztes Backup: ______
  Ergebnis: ______
- [ ] **G11** **Log-Rotation greift:** auf beiden Terminals sind
  `/opt/elwasys/log/stdout` und `/opt/elwasys/log/errout` deutlich unter der Kappungsgrenze
  (Default 5 MiB); die Anwendungslogs rotieren täglich. Am Backend-Host sind die
  Container-Logs auf `max-size` begrenzt.
  Ergebnis: ______
- [ ] **G12** ○ **Watchdog:** `/opt/elwasys/log/auto-update-watchdog.log` existiert auf beiden
  Terminals und zeigt regelmäßige, fehlerfreie Läufe; der Cron-Eintrag zeigt auf
  `/opt/elwasys/bin/auto-update-watchdog.sh`.
  Ergebnis: ______
- [ ] **G13** **Betriebsskripte vollständig:** auf beiden Terminals liegen alle vier Dateien in
  `/opt/elwasys/bin/`: `upgrade-jre.sh`, `update.sh`, `auto-update-watchdog.sh`,
  `run-sh.lib.sh`.
  Ergebnis: ______
- [ ] **G14** ○ **Swagger/API-Dokumentation:** `https://<portal>/swagger-ui.html` ist
  **nur angemeldet** erreichbar und listet die `/api/v1/**`-Endpunkte.
  Ergebnis: ______

---

## Block H — Benachrichtigungen

**Ziel:** Mail und Push kommen an – genau einmal.

> **Voraussetzung:** 0.27 (Dienst scharfgeschaltet) und 0.28 (Empfängerregeln bei
> `test-normal` gesetzt) sind erledigt. Dann haben bereits die Läufe aus Block C und F
> Benachrichtigungen erzeugt – H2/H3 sind die gezielte Gegenprobe.

- [ ] **H1** **Bestandsaufnahme:** im Postfach und in der Pushover-App nachzählen, wie viele
  Meldungen der heutige Tag bisher erzeugt hat, und mit der Zahl der abgeschlossenen Läufe
  von `test-normal` vergleichen. Erwartet: **je Lauf genau eine** Mail und **eine** Push.
  Läufe: ______ · Mails: ______ · Pushes: ______
  Ergebnis: ______
- [ ] **H2** **Mail bei regulärem Ende:** an T1 mit K1 `TEST-Kurz-Fix` starten und wie in C7
  beenden. Erwartet: **genau eine** Mail mit Gerätename und Preis; Absender ist die
  konfigurierte Adresse. **Den Haken „Bei Fertigstellung Email an …" auf der
  Bestätigungsseite des Terminals dabei bewusst nicht setzen** – die Mail muss trotzdem
  kommen, denn maßgeblich ist allein die Portal-Einstellung aus 0.28.
  Zeit bis Zustellung: ______ · Spam-Ordner geprüft: ☐
  Ergebnis: ______
- [ ] **H3** **Push bei regulärem Ende:** dieselbe Ausführung löst **genau eine**
  Pushover-Nachricht auf dem Handy aus.
  Ergebnis: ______
- [ ] **H3b** **Abschalten wirkt:** in den Einstellungen von `test-normal` die
  Email-Benachrichtigung **aushaken**, einen weiteren Lauf fahren → es kommt **keine** Mail,
  die Push kommt weiterhin (der Pushover-Versand hängt allein am hinterlegten Key). Danach
  wieder anhaken.
  Ergebnis: ______
- [ ] **H4** **Mail bei Abbruch:** eine Ausführung starten und am Terminal abbrechen (D11) →
  eine Abbruch-Mail/-Push kommt an, mit dem tatsächlich abgerechneten Betrag.
  Ergebnis: ______
- [ ] **H5** **Keine Doppelversände nach Offline-Nachmeldung:** F4 hat Läufe nachgemeldet.
  Prüfen, dass zu diesen Läufen **keine zweite** Mail/Push eingetroffen ist.
  *Erwartungsgemäß kommt für einen offline beendeten Lauf **eine** Meldung, sobald die
  Nachmeldung durchläuft – es sei denn, das Ereignis ist zu diesem Zeitpunkt bereits älter als
  die Offline-Maximaldauer des Standorts, dann wird die Benachrichtigung bewusst unterdrückt.
  Beides ist richtig; falsch wäre nur eine doppelte Meldung.*
  Ergebnis: ______
- [ ] **H6** **Passwort vergessen (Selbstbedienung):** auf der Login-Seite **„Passwort
  vergessen?"** → Email von `test-normal` eingeben → Mail kommt an. Der Link zeigt auf die
  **öffentliche https-Portal-URL** (nicht `localhost`) und führt auf die Seite zum Setzen eines
  neuen Passworts. Neues Passwort setzen → Login damit funktioniert.
  Ergebnis: ______
- [ ] **H7** **Reset-Link ist einmalig:** denselben Link ein zweites Mal aufrufen → wird
  abgewiesen.
  Ergebnis: ______
- [ ] **H8** **Unbekannte Adresse verrät nichts:** „Passwort vergessen?" mit einer im System
  **nicht** vorhandenen Adresse → dieselbe neutrale Meldung wie bei einer bekannten Adresse,
  keine Auskunft darüber, ob die Adresse existiert. Es kommt keine Mail.
  Ergebnis: ______
- [ ] **H9** **Admin setzt Passwort per Mail:** Benutzer → `test-arm` bearbeiten → Haken
  „Sende dem Benutzer per Email ein neues Passwort" → Mail mit neuem Passwort kommt an,
  Login damit funktioniert.
  Ergebnis: ______

---

## Block I — Benutzerportal (Nicht-Admin)

**Ziel:** was ein normaler Bewohner im Portal sieht – und was er ausdrücklich **nicht** sieht.

- [ ] **I1** **Login als `test-normal`** (Inkognito-Fenster) → landet auf der
  Benutzer-Übersicht, **nicht** im Admin-Bereich.
  Ergebnis: ______
- [ ] **I2** **Kein Admin-Menü:** Dashboard/Benutzer/Geräte/Programme/Standorte tauchen in der
  Navigation **nicht** auf.
  Ergebnis: ______
- [ ] **I3** **Direkter URL-Zugriff abgewiesen:** eine Admin-Route direkt in die Adresszeile
  eingeben → Zugriff wird verweigert (keine Admin-Inhalte, kein Serverfehler).
  Ergebnis: ______
- [ ] **I4** **Eigene Daten:** Guthaben und letzte Einzahlung stimmen mit dem überein, was der
  Admin sieht; die eigene Buchungshistorie ist vollständig und zeigt die heutigen Läufe.
  Ergebnis: ______
- [ ] **I5** **Nur eigene Daten:** in der Historie tauchen keine fremden Benutzer auf.
  Ergebnis: ______
- [ ] **I6** **Eigenes Passwort ändern:** Benutzer-Chip → „Passwort ändern" → mit falschem
  aktuellem Passwort abgewiesen; mit richtigem geändert; Logout und Login mit dem neuen
  Passwort funktioniert.
  Ergebnis: ______
- [ ] **I7** **Zu kurzes Passwort:** ein Passwort mit weniger als 8 Zeichen wird abgewiesen.
  Ergebnis: ______
- [ ] **I8** **Einstellungen persistent:** Email-Benachrichtigung aus- und wieder einschalten →
  Wert bleibt nach dem Neuladen erhalten.
  Ergebnis: ______
- [ ] **I9** ○ **Gesperrter Benutzer kommt nicht rein:** `test-gesperrt` ein Passwort geben
  (H9-Weg, braucht die Email aus 0.17) und damit anmelden wollen → Login wird abgewiesen,
  obwohl das Passwort stimmt.
  Ergebnis: ______
- [ ] **I10** **Falsches Passwort:** dreimal falsch anmelden → Meldung „Login fehlgeschlagen",
  keine Auskunft darüber, ob der Benutzername existiert.
  Ergebnis: ______
- [ ] **I11** ○ **Brute-Force-Sperre:** fünfmal hintereinander falsch anmelden → weitere
  Versuche werden zeitweise gesperrt, **mit derselben neutralen Meldung**. Nach dem
  Sperrfenster (Default 15 min) geht es wieder – oder mit dem richtigen Passwort in einem
  anderen Browser gegenprüfen, dass andere Benutzer nicht betroffen sind.
  Ergebnis: ______
- [ ] **I12** **Logout:** führt zurück auf die Login-Seite; der Zurück-Knopf des Browsers
  bringt keine angemeldete Ansicht zurück.
  Ergebnis: ______

---

## Block J — Abschluss und Aufräumen

**Ziel:** Testdaten weg, Zustand sauber, Ergebnis dokumentiert.

### J.1 Aufräumen

- [ ] **J1** ⚠ Alle **Test-Programme** von den Geräten lösen und löschen: `TEST-Kurz-Fix`,
  `TEST-Kurz-Dyn`, `TEST-Teuer`, `TEST-Ohne-AutoEnde`.
  Ergebnis: ______
- [ ] **J2** ⚠ Alle **Test-Benutzer** löschen: `test-normal`, `test-arm`, `test-gesperrt`,
  `test-fremd`. *(Die Buchungen bleiben als Beleg erhalten – das ist so gewollt.)*
  Ergebnis: ______
- [ ] **J3** ⚠ **Test-Benutzergruppen** löschen: `TEST-Gruppe`, `TEST-Fremd`, `TEST-Rabatt`,
  `TEST-Wegwerf` (falls B16 sie stehen ließ). Vorher sicherstellen, dass kein echter Benutzer
  darin gelandet ist – beim Löschen einer Gruppe werden ihre Mitglieder einer anderen Gruppe
  zugeordnet.
  Ergebnis: ______
- [ ] **J4** Falls F17 gefahren wurde: `TEST-Wegwerfgerät` ist in Schritt 4 bereits gelöscht
  worden – in der Geräteliste gegenprüfen. Der zugehörige Offline-Vorfall bleibt bewusst als
  quittierter Beleg stehen (kein Purge).
  Ergebnis: ______
- [ ] **J5** **Zurückgestellte Werte prüfen:** Offline-Maximaldauer des Standorts von T1 steht
  wieder auf ______ min (B17); Testgerät ist **aktiviert**; kein Benutzer hängt mehr in einer
  Testgruppe; Alarm-Timer und externer Monitor sind im Normalzustand.
  Ergebnis: ______
- [ ] **J6** **Testkarten** aus dem Verkehr ziehen bzw. dokumentieren, wem sie gehören.
  Ergebnis: ______

### J.2 Endzustand bestätigen

- [ ] **J7** **Keine offenen Baustellen:** Portal → keine abgelaufenen Ausführungen (kein
  Warndreieck), **Offline-Vorfälle** leer bzw. alle quittiert.
  Ergebnis: ______
- [ ] **J8** **Health grün:** `/actuator/health/liveness` **UP**,
  `/actuator/health/operational` **200**, beide Standorte „Verbunden".
  Ergebnis: ______
- [ ] **J9** **Journale leer:** auf beiden Terminals sind `offline-journal.jsonl` und
  `offline-journal.jsonl.deadletter` leer oder nicht vorhanden.
  Ergebnis: ______
- [ ] **J10** **Datenabgleich gegen 0.10:** Anzahl Benutzer/Geräte/Programme/Standorte
  entspricht wieder dem Ausgangsstand (plus/minus die bewusst behaltenen Änderungen); die in
  0.10 notierten Stichproben-Guthaben echter Nutzer sind **unverändert**.
  Ergebnis: ______
- [ ] **J11** **Mini-Soak auswerten:** Backend läuft seit dem Cutover ohne Neustart (außer den
  bewusst ausgelösten); Speicherverbrauch unauffällig
  (`docker stats --no-stream`); Terminal-Logs ohne wiederkehrende Fehlerschleifen; Zahl der
  WebSocket-Reconnects plausibel (nur die selbst ausgelösten).
  Ergebnis: ______
- [ ] **J12** **Log-Durchsicht:** Backend-Log des ganzen Tages auf `ERROR`/`WARN` durchsehen und
  jede Zeile einem Testpunkt zuordnen können. Unerklärte Einträge in die Befundliste.
  Ergebnis: ______
- [ ] **J13** **Frisches Backup** vom Endzustand ziehen und wegsichern.
  Ergebnis: ______
- [ ] **J14** **Release einfrieren:** Backend-Image-Digest, Client-Jar-Version + SHA-256 je
  Terminal und Chart-Version festhalten – damit dieser geprüfte Stand eindeutig ist.
  Ergebnis: ______
- [ ] **J15** **Nutzerinformation:** Aushang/Nachricht „Waschküche wieder normal nutzbar",
  Support-Kontakt und ein Zweizeiler „Terminal reagiert nicht → …" sind draußen.
  Ergebnis: ______
- [ ] **J16** **Befundliste durchgehen:** jeder Befund unten hat eine Einstufung (blockierend /
  zu beheben / notiert) und einen nächsten Schritt.
  Ergebnis: ______

---

## Befundliste

Alles, was von der Erwartung abweicht – auch Kleinigkeiten. Am Abend ist das die Arbeitsliste.

| # | Prüfpunkt | Beobachtung | Schwere (blockierend / hoch / mittel / niedrig) | Nächster Schritt |
|---|---|---|---|---|
| 1 |  |  |  |  |
| 2 |  |  |  |  |
| 3 |  |  |  |  |
| 4 |  |  |  |  |
| 5 |  |  |  |  |
| 6 |  |  |  |  |
| 7 |  |  |  |  |
| 8 |  |  |  |  |
| 9 |  |  |  |  |
| 10 |  |  |  |  |

---

## Abbruchkriterien

Dieser Plan probt keinen Rollback. Er benennt nur, wann das Durchtesten **aufhört** und
stattdessen das Runbook (Kapitel 4, Rollback-Entscheidungsbaum) gilt:

- **Geld stimmt nicht.** Eine Buchung fehlt, ist doppelt oder hat den falschen Betrag – und es
  ist kein Bedienfehler (F4, F15, E2, E3, C8).
- **Guthaben wird negativ**, ohne dass eine Offline-Nachmeldung die Ursache ist.
- **Eine Steckdose lässt sich nicht mehr abschalten** oder das Terminal schaltet eine fremde
  Maschine ein.
- **Das Backend kommt nach einem der Ausfalltests nicht von selbst zurück** (F4, F7, F8).
- **Kartenlogin oder Buchung funktioniert an einem Terminal grundsätzlich nicht mehr.**

Alles andere – Anzeigefehler, Formulierungen, fehlende Bequemlichkeiten – gehört in die
Befundliste und nicht in eine Abbruchentscheidung.

---

## Verweise

- Umstellung selbst: [`CUTOVER-RUNBOOK.md`](CUTOVER-RUNBOOK.md)
- Rollout-Gate: [`smoke/README.md`](smoke/README.md)
- Alarmierung: [`monitoring/README.md`](monitoring/README.md)
- Backup/Restore: [`backup/README.md`](backup/README.md)
- Terminal-Betrieb: [`terminal/README.md`](terminal/README.md)
- Automatisierte Testabdeckung: [`docs/kb/08-test-plan.md`](../docs/kb/08-test-plan.md)
- Generalprobe-Punkte vor dem Feldeinsatz: [`docs/specs/0001-finale-review.md`](../docs/specs/0001-finale-review.md)
