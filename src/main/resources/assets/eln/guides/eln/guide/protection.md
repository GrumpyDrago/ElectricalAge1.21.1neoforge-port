---
navigation:
  title: Protection
  position: 3
item_ids:
  - eln:overcurrent_protection_relay
  - eln:battery_management_system
  - eln:automatic_transfer_switch
---

# Protection

<Row>
  <ItemImage id="eln:overcurrent_protection_relay" />
  <ItemImage id="eln:battery_management_system" />
  <ItemImage id="eln:automatic_transfer_switch" />
</Row>

These three take a cable in their slot, which rates their contacts, like the electrical breaker.

## Overcurrent Protection Relay

Opens on an overcurrent: the bigger it is, the sooner (standard, very or extremely inverse curves, or a fixed time),
and at once on a short circuit. It can close again by itself up to three times, to clear passing faults, and then
locks out until reset. Its left pin tells its state; a signal on its right pin trips it.

## Battery Management System

Goes between a battery (front) and its load and charger (back). It cuts the load before the battery runs flat (a
charger can still charge it), stops charging when it is full (the load can still draw), and opens on overcurrent.

## Automatic Transfer Switch

Feeds a load (front) from the mains (back), and from a backup (left) when the mains fail, never joining the two.
With a battery, a charger and a battery management system it makes a UPS.

## Arc flash

When a switch opens, the current jumps the gap as an arc for a moment. Within its rating that is a spark. Breaking a
fault (over twice the rating, more than 5 kW) makes an **arc flash**: light, molten metal and smoke that burn and blind
whoever stands close, up to five blocks away at high voltage. Stand back from breakers on circuits that may be shorted.
