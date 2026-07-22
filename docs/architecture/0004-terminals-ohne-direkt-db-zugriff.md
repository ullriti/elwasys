# 4. Terminals ohne Direkt-DB-Zugriff (REST + WebSocket + Standort-Token)

- **Status:** accepted
- **Datum:** 2026-07-20

## Kontext

Die Raspi-Terminals greifen heute direkt per JDBC auf die DB zu und tragen dafür
DB-Zugangsdaten. Das ist ein Sicherheits- und Wartungsproblem. Die Terminals bleiben
Raspberry Pis mit Touch-Display, der Bedienfluss darf sich nicht ändern; die
Hardware-Nähe (deCONZ/fhem, Leistungsmessung, Ende-Erkennung, RFID) muss lokal bleiben.

## Entscheidung

Das Terminal spricht ausschließlich die **REST-API** des Backends (plus einen
WebSocket-Kanal) statt SQL. Es authentifiziert sich mit einem **Standort-Token**
(`Authorization: Bearer <token>`), lädt Geräte/Programme/Nutzerdaten über REST und meldet
Ereignisse (Start, Ende mit Leistungswerten, Abbruch). Der Standort-Scope wird API-seitig
als `404` (unbekanntes Objekt) durchgesetzt, nicht als `403`, um keine Existenz fremder
Objekte zu verraten; Guthaben-Abfragen sind bewusst standortunabhängig. HTTP im Terminal
läuft über `java.net.http` (JDK). Statt DB-Credentials konfiguriert `setup.sh` künftig
Backend-URL + Terminal-Token.

## Konsequenzen

- Keine DB-Zugangsdaten mehr auf den Terminals; Rechte werden zentral durchgesetzt.
- Hardware-nahe Abläufe bleiben lokal, das Terminal bleibt bei Netz-Schluckauf
  bedienfähig (siehe ADR 10).
- `Common.DataManager` stirbt mit dem letzten Direkt-DB-Zugriff.
- Standard-`Bearer`-Header funktioniert unverändert auch für den WS-Handshake.

Herkunft: docs/kb/05-migration-plan.md, Abschnitte Zielarchitektur und Entscheidungen.
