# elwasys Betriebs-Alerting (verdrahtet)

Der **mitgelieferte Alarmkanal** (Issue #83/H4). Er schließt die in der finalen Review (Track
R5) benannte Lücke: das Backend liefert unter `/actuator/health/operational` sauber HTTP 503
bei betrieblichen Fehlerbildern, aber **nichts pollte es** – ein stiller 503 erreichte niemanden.
Dieses Verzeichnis liefert den Poller und die Zustellung mit, statt sie nur zu „empfehlen".

## Was wird überwacht

`elwasys-health-alert.sh` pollt periodisch und alarmiert per **Pushover und/oder Mail**:

1. **Betriebs-Health** – `/actuator/health/operational` ≠ HTTP 200 **oder nicht erreichbar**.
   Deckt ab: Backend down, DB down (Endpoint nicht erreichbar zählt als Alarm), Terminal-WS
   getrennt, offene abgelaufene Execution.
2. **Zertifikats-Ablauf** (#89) – TLS-Zert des öffentlichen Endpoints läuft in < N Tagen ab
   (wichtig für den Selbstverwaltungs-Pfad ohne Auto-Erneuerung).
3. **Plattenplatz** (#89) – eine überwachte Partition überschreitet den Füllstand-Schwellwert.

**Anti-Spam:** Alarm bei Wechsel in den Fehlerzustand und bei der Erholung; ein anhaltender
Fehler wird nur alle `ELWASYS_ALERT_RENOTIFY_SECONDS` (Default 6 h) wiederholt.

## Einrichtung (Pflicht vor dem Feldeinsatz)

> Das Einrichten des Alarmkanals ist ein **Pflicht-Gate** vor dem Feldeinsatz
> (siehe `deploy/CUTOVER-RUNBOOK.md`, Kap. 7b). Ein System ohne verdrahteten Alarm gilt als
> nicht feldbereit.

1. **Skript ablegen** (Docker-Host bzw. der Host, der das Backend betreibt):
   ```bash
   sudo install -D -m 0755 deploy/monitoring/elwasys-health-alert.sh \
        /opt/elwasys/monitoring/elwasys-health-alert.sh
   ```
2. **Konfiguration** anlegen (enthält den Pushover-Token → Rechte 600, nicht einchecken):
   ```bash
   sudo install -D -m 0600 deploy/monitoring/elwasys-health-alert.env.example \
        /etc/elwasys/elwasys-health-alert.env
   sudoedit /etc/elwasys/elwasys-health-alert.env   # mindestens EINEN Kanal (Pushover/Mail) setzen
   ```
3. **Timer aktivieren** (systemd-Variante, empfohlen):
   ```bash
   sudo install -m 0644 deploy/monitoring/elwasys-health-alert.service /etc/systemd/system/
   sudo install -m 0644 deploy/monitoring/elwasys-health-alert.timer   /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now elwasys-health-alert.timer
   systemctl list-timers elwasys-health-alert.timer   # nächster Lauf sichtbar?
   ```

### Alternative: Cron statt systemd

```cron
# /etc/cron.d/elwasys-health-alert – alle 5 Minuten, Env aus der .env laden
*/5 * * * * root set -a; . /etc/elwasys/elwasys-health-alert.env; /opt/elwasys/monitoring/elwasys-health-alert.sh >> /var/log/elwasys-health-alert.log 2>&1
```
(`ELWASYS_ALERT_STATE_DIR` muss für den Cron-User beschreibbar sein.)

## Zweite Stufe: externer Uptime-Monitor (ebenfalls PFLICHT)

Das lokale Poll-Skript hat eine bauartbedingte blinde Stelle: **fällt der ganze Host aus**
(Strom, Netz, Plattentod), läuft auch der Poller nicht mehr – und es kommt kein Alarm, gerade
im schwersten Fehlerfall. Deshalb ist zusätzlich ein **externer Monitor Pflicht**, der von
außen prüft:

- Dienst: z. B. [healthchecks.io](https://healthchecks.io) (Dead-Man's-Switch, kostenloser Tarif)
  oder eine eigene [Uptime-Kuma](https://github.com/louislam/uptime-kuma)-Instanz.
- **Variante 1 (Endpoint-Check, bevorzugt, wenn das Portal öffentlich erreichbar ist):** den
  Monitor auf `https://<portal-host>/actuator/health/operational` zeigen lassen –
  Erwartung HTTP `200`, Alarm bei `503`/Timeout.
- **Variante 2 (Dead-Man's-Switch, wenn nichts von außen erreichbar ist):** das lokale Skript
  pingt nach jedem **erfolgreichen** Lauf eine healthchecks.io-URL; bleibt der Ping aus (Host
  tot), alarmiert der externe Dienst. Dafür im Cron/Timer-Wrapper ergänzen:
  ```bash
  /opt/elwasys/monitoring/elwasys-health-alert.sh && curl -fsS -m 10 https://hc-ping.com/<uuid> >/dev/null
  ```

**Beide Stufen gehören eingerichtet:** das lokale Skript meldet die *betrieblichen* Fehlerbilder
(Terminal weg, abgelaufene Execution, Zertifikat, Platte) mit Details; der externe Monitor deckt
den *Totalausfall* ab. Die Alarm-Probe unten muss für **beide** Wege einmal durchlaufen.

## Alarm-Probe (Generalprobe) – für BEIDE Stufen

Vor dem Feldeinsatz **einmal bewusst einen Fehler provozieren** und prüfen, dass der Alarm
tatsächlich bei einem Menschen ankommt (Spec 0001, Generalprobe-Punkt „Alarm-Probe"):

```bash
# Stufe 1 (lokales Skript): Backend kurz stoppen und einen Lauf erzwingen:
sudo systemctl start elwasys-health-alert.service   # bei gestopptem Backend -> Alarm muss kommen
# danach Backend wieder starten -> die "behoben"-Meldung muss folgen.

# Stufe 2 (externer Monitor): den Timer stoppen (Dead-Man's-Switch) bzw. den Endpoint
# blockieren und warten, bis der externe Dienst alarmiert:
sudo systemctl stop elwasys-health-alert.timer      # Ping bleibt aus -> externer Alarm
sudo systemctl start elwasys-health-alert.timer     # danach wieder aktivieren (!)
```

Beide Proben mit Datum und „Alarm kam an bei <wem/wie>" im Runbook festhalten.

## Test

`elwasys-health-alert-selftest.sh` prüft die volle Alarm-/Anti-Spam-/Recovery-Logik **offline**
(kein Netz/Backend/Pushover) über Fixture-Hooks und läuft in der CI. Lokal:

```bash
bash deploy/monitoring/elwasys-health-alert-selftest.sh
```
