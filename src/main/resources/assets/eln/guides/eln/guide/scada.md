---
navigation:
  title: SCADA
  position: 9
item_ids:
  - eln:scada_monitor
  - eln:scada_transmitter
  - eln:scada_linker
---

# SCADA

<Row>
  <ItemImage id="eln:scada_monitor" />
  <ItemImage id="eln:scada_transmitter" />
  <ItemImage id="eln:scada_linker" />
</Row>

SCADA (supervisory control and data acquisition) is the control room of a power system: one screen that shows what
every part of it is doing, raises the alarm when something trips, and switches breakers from afar.

## The monitor

Put a **SCADA monitor** on a wall and open it. It lists up to 16 devices, each with its readings. A device in trouble,
a tripped breaker, a vented battery, an overheated substation, shows in red, and the monitor's red lamp blinks.
Breakers, switches, relays and substations get buttons to open and close them (green: closed, red: open). A
locked-out device refuses to close, from here as from anywhere.

## Getting devices onto it

Two ways, which can be mixed:

- **SCADA linker**: use it on devices to collect them, then on the monitor to put them on it. The **x** next to a
  linked device takes it off again.
- **SCADA transmitter**: a small wireless sensor. It reports every device within two blocks of it on its channel;
  every monitor on the same channel, within 256 blocks, shows them. Put one by each substation, battery bank or
  breaker box, and they appear on the monitor by themselves.

Both start on the channel `SCADA`; set another in their screens to keep sites apart.
