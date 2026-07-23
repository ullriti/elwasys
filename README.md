# elwasys #

![Screenshot](Client-Raspi/docs/screenshot-startscreen-md.png)

![Screenshot](Client-Raspi/docs/screenshot-confirmation-md.png)

elwasys manages and bills shared washing machines in communal laundry rooms. It consists of
Raspberry-Pi touch terminals at the washing machines and a central Spring-Boot backend (REST
API + WebSocket for the terminals, plus a built-in Vaadin Flow admin portal UI) backed by a
shared PostgreSQL database. See [docs/kb/00-overview.md](docs/kb/00-overview.md) and
[docs/kb/01-architecture.md](docs/kb/01-architecture.md) for the full architecture.

## Components

- **`Client-Raspi/`** – the terminal application (JavaFX), developed to run on a RaspberryPi
  with the official 7" touch screen. It controls wireless sockets that are plugged in front of
  the managed washing machines, talking to the backend exclusively via its REST API and an
  outgoing WebSocket connection (no direct database access) - see
  [docs/kb/03-modules.md](docs/kb/03-modules.md). It also contains the six former `Common`
  utility classes (enum type, formatting/config helpers); the standalone `Common` module was
  dissolved, so the root reactor now has just two modules.
- **`backend/`** – the central Spring Boot application: the REST API/WebSocket that the
  terminals use, the admin portal UI (Vaadin Flow, built into the backend - there is no
  separate portal module), and the notification service. Owns the PostgreSQL schema via
  Flyway migrations.

It uses the [ConBee2](https://phoscon.de/de/conbee2) stick to communicate with Zigbee smart plugs (e.g. [LIDL SilverCrest](https://www.lidl.de/p/silvercrest-3er-set-steckdosen-zwischenstecker-zigbee-smart-home-mit-energiezaehler/p800003184)).
The wireless sockets are switched on if a user has enough credit and are switched off again when the washer is done.

## Features

- Use RFID Cards to identify users at the terminal
- Pre-Paid washing
- Detect end of program by measuring power consumption (=> vendor independent)
- Email- and push-notification when the laundry is ready
- Fixed price or time-based billing
- Fine-grained permissions
  - Special prices per user group
  - Deny access to a washing machine for a certain user group

## Setup

On a fresh Raspberry Pi, run the following command:

```bash
bash <(curl -s https://raw.githubusercontent.com/ullriti/elwasys/master/Client-Raspi/setup.sh)
```