# Expansion parts (testing)

These parts are on the `expansion-testing` branch and in the `2.0.0-testing` builds. They aim at a more realistic
power system: protection that behaves like real relays, batteries with real chemistries, generators that need their
load matched, and instruments that show what the simulation is doing.

They are new items with new ids, so worlds keep working, but they are still being tested: balance and recipes may
change. Back up worlds you care about.

## Protection and switching

All three are wall or floor parts. Like the electrical breaker, they take a cable in their slot, which sets the
resistance of their contacts and the current they are made for.

### Overcurrent Protection Relay

A breaker driven by a protection relay, as used on feeders and power lines.

- **Inverse-time curves** (IEC 60255): standard, very and extremely inverse, or a definite time. The more the current is
  above the pickup, the sooner it trips; twice the pickup on the standard curve with a time multiplier of 0.1 trips in
  about a second, ten times in 0.3 seconds. Below the pickup it never trips, and the timer runs back as an induction disc
  would.
- **Instantaneous trip** at a set multiple of the pickup (8 by default), for short circuits.
- **Auto-reclose**: after a trip it can close again by itself up to three times, after the reclose delay. If the fault
  is still there it trips again, and after the last try it locks out until someone resets it. Thirty seconds without a
  trip and the count starts over, so passing faults (a branch in the line) clear themselves.
- Pins: front and back are the line. **Left** gives a signal: 1 closed, 0.5 waiting to reclose, 0 open or locked out.
  **Right** takes a signal: high trips it and keeps it open, for interlocks and remote control.
- Pickup 0 means "the current the cable is made for".
- The LEDs show closed (green), waiting to reclose (blinking amber) and locked out (red, with the orange flag in the
  window).

### Battery Management System

Goes between a battery (front) and whatever it powers and charges it (back).

- **Low-voltage cutoff**: below 90 % of the battery voltage it cuts the load off, before the battery is flat. A charger
  on the load side can still push current in, and once the battery is back to its nominal voltage the load is
  reconnected.
- **High-voltage cutoff**: above 108 % it stops charging, but a load can still draw from the battery.
- **Overcurrent**: above the current limit it opens, and tries again after five seconds.
- The left pin gives a rough state of charge, worked out from the voltage. The five blue segments show it too.

### Automatic Transfer Switch

Feeds a load (front) from the mains (back), and from a backup source (left) when the mains fail.

- The mains have to be out of range (15 % by default) for the transfer delay (0.5 s) before it switches, so short dips
  are ridden through.
- It returns to the mains once they have been good for the return delay (10 s), or at once if the backup fails.
- It opens one contact before closing the other, so the two sources are never connected together.
- The modes are automatic, mains only, backup only and off. The right pin gives 1 while on the backup.

**A UPS**: mains → battery charger → battery → *backup* side of the transfer switch, and mains → *mains* side. Add a
battery management system between the battery and the switch to keep the battery from running flat.

## Batteries

Four batteries of real chemistries. They work like the other batteries, with these differences:

| | Lead-acid | Nickel-cadmium | Lithium-ion | LiFePO4 |
| --- | --- | --- | --- | --- |
| Voltage curve | sloping | very flat | sloping | flattest |
| Power | 1.5× | 2× | 2× | 3× |
| Energy | 1.8× | 1.3× | 3× | 3× |
| Efficiency | 90 % | 93 % | 97 % | 97 % |
| Life | 1.5× | 4× | 2× | 6× |
| Explodes above | 60 °C | 70 °C | 130 °C | 90 °C |
| Internal resistance at −20 °C | 3× | 1.5× | 4× | 5× |

Multiples are of the cost oriented battery.

- **Heat wears every battery out**: ageing doubles for every 10 °C above 25 °C.
- **Lead-acid** sulphates when left nearly flat.
- **Lithium-ion** wears faster when kept full, is damaged by charging below freezing, and **runs away thermally** above
  80 °C. The cells then heat themselves faster and faster until they explode, so keep them cool.
- **LiFePO4** is also damaged by charging below freezing, but does not run away.
- The cold raises the internal resistance, so batteries give less power in a cold biome or in winter.
- Jade shows the chemistry and the cell temperature. Ambient temperature comes from the biome and the depth, so batteries
  deep underground run warmer.

## Generation

### Monocrystalline Solar Panel

A 2x3 panel that follows the current–voltage curve of a real silicon panel, instead of giving its power at any voltage.
It gives its full power only near 80 % of its open-circuit voltage (118 V). Connected straight to a 50 V battery it
works on the flat part of its curve and gives about half its rating. Use an MPPT charge controller to get the rest.

### Permanent-Magnet Wind Turbine

Simulates its rotor:

- The power of the wind grows with the cube of its speed. The blades turn it into torque by a power coefficient that
  peaks at 0.48 (below the Betz limit of 0.59) when the blade tips move 8 times faster than the wind.
- The generator's voltage rises with the rotor speed, and the current it delivers brakes the rotor. A load that takes
  too much stalls it; one that takes too little lets it run away from its best speed.
- Above the rated speed the blades pitch to shed power. Above 25 m/s they feather and the rotor brakes.
- Rated at 1 kW at 10 m/s (scaled by the wind turbine power factor in the settings). Jade shows wind, rotor speed and
  blade pitch.

It charges a 50 V battery directly once its voltage gets above the battery's, but an MPPT charge controller lets the
rotor run at its best speed.

### MPPT Charge Controller

Front: panels or turbine. Back: battery. Left: a signal of the power it delivers.

Every tenth of a second it changes the load it puts on its input a little, and keeps going while the power rises
(perturb and observe). That holds solar panels at their maximum power point through clouds, and wind turbines at
their best tip speed. It converts the power to the battery's voltage at 97.5 % efficiency, up to 200 V in and 60 A out.

It stops at the charge voltage (52.5 V by default, about 1.05 times a 50 V battery's voltage) and at the current
limit. Both can be set in its screen.

## Instruments

### Oscilloscope

Plots two voltages over time on its screen: channel 1 (yellow) on the front, channel 2 (blue) on the back. Probe power
or signal cables. For a current, probe the output of a current sensor.

- Times across the screen from 6 seconds to 5 minutes. It samples at the rate of the simulation (20 times a second by
  default), so it shows how things change over seconds: a relay tripping, a battery sagging, a turbine spinning up.
- The vertical scale follows the signal (1, 2, 5, 10, ...) and is shown in its screen. **Hold** freezes the picture.

### Thermal Camera

Hold it to see how hot the parts within 24 blocks are, cables included. They are tinted from dark blue (ambient)
through red and yellow to white (hottest), and the hottest is outlined. The scale on the right runs from the ambient
temperature to at least 40 °C above it. Hot spots show long before anything burns.

## Models

The models and textures are made by `script/expansion_models.py`, in plain Python. They can be replaced by Blender
exports with the same part names; see the script for the parts each one needs.
