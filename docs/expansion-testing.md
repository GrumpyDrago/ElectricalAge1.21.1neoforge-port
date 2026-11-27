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

### Breaker boxes, power bundle cables and supply cables

A consumer unit, as in a house: one supply in, eight circuits out, each with its own breaker, all in one cable.

| | 50 V | 200 V | 800 V |
| --- | --- | --- | --- |
| Breaker box | light, green strip | grey | dark, orange strip |
| Power bundle cable | green jacket | black jacket | orange jacket |
| Each line, like the cable of its voltage | 20 A, 1 kW | 10 A, 2 kW | 6.25 A, 5 kW |
| Breaker ratings | 2 to 20 A | 1 to 10 A | 1 to 6 A |
| Whole box, and its supply cable | 160 A, 8 kW | 80 A, 16 kW | 50 A, 40 kW |

- **Breaker box**: the supply on the front, a power bundle cable on the back. Feed it with the **supply cable** of its
  voltage, which carries what all eight lines can take; an ordinary cable carries only one line's worth. Each box
  burns out when fed more than 1.3 times its voltage, and so does a bundle.
- Open its screen to switch breakers on and off and to set their ratings (click to raise, shift-click to lower). A
  tripped breaker shows a red light; switch it on to reset it.
- Each breaker trips much like a real (type B) one, only faster: never up to 1.13 times its rating, within five
  seconds on any overload above that (about 3.4 s at twice its rating, 1.9 s at three times), and at once from five
  times its rating, on a short circuit.
- **Power bundle cable**: eight insulated lines in one cable, which join line for line from one bundle to the next. It
  does not connect to machines. Run a power cable out of it instead: the lines are **black** (unpainted cables),
  **red, orange, yellow, green, blue, purple and white**; paint a cable with a brush to pick its line. Cables of other
  colors do not join.
- **Fed by a breaker box**, whose breakers keep every line within its rating, a bundle does not heat up. **Used as a
  plain multi-core cable**, without a box, its eight lines share one jacket and its heat: it carries about six lines at
  their full current, or all eight at a lighter load, and burns up beyond that. Jade shows the lines in use and
  whether a breaker box feeds it.

### Lockout/tagout

Before working on a circuit, lock its switch open with a **Lockout Padlock**: use it on an electrical breaker, an
electrical switch, a protection relay, a battery management system, a transfer switch or a breaker box (which then
cuts all eight lines). It opens at once, and nobody can close it, by hand, by signal or by computer, until every lock
is off. Each player can only take their own lock off, by using a padlock on it again; Jade shows whose locks are on.
In creative mode, sneaking with a padlock cuts every lock off.

## The grid: transmission, substations and distribution

Power travels in three tiers, as on a real grid:

| Tier | Voltage | Carried by | Each line |
| --- | --- | --- | --- |
| Transmission | 51.2 kV | T2 transmission towers | 40 A, about 2 MW |
| Distribution | 12.8 kV | T1 utility poles | 20 A, about 250 kW |
| Local | 3.2 kV, then 800/200/50 V | the pole transformer, then cables and transformers | as the cables |

Lines are strung with high voltage cable as before, but carry far more than an 800 V cable: they are cooled by the
air. A pole or tower overheats above its current and burns out above 1.3 times its voltage, so a 51.2 kV line run into
utility poles burns them: step it down with a substation first.

**Existing worlds**: utility poles used to take 51.2 kV. Lines run above about 16.6 kV on utility poles now burn them;
use transmission towers and a distribution substation for those.

### Substations

Three blocks wide, deep and high. Link lines to them with high voltage cable, as with poles: click anywhere on them,
and each line goes to the side of its voltage by itself. Linking two different voltages directly is refused.

- **Step-Up Substation**: power station to grid. 3.2 kV supply cables at the front, at the ground, and 51.2 kV
  transmission lines on the left face. Up to 1 MW. A **3200V Supply Cable** carries about 120 kW; use several.
- **Distribution Substation**: 51.2 kV transmission lines on the left face, 12.8 kV distribution lines on the right
  face. Up to 2 MW.
- Each side has a **circuit breaker with a protection relay**: it trips on overcurrent (sooner the bigger, at once on
  a short circuit), closes again once by itself after five seconds to clear a passing fault, and then stays open
  until someone closes it in the substation's screen. Breaking a fault at these voltages makes a big arc flash.
- The **transformer** heats with its load (its oil is 50 °C above ambient at full load) and trips both breakers above
  75 °C, or on overvoltage, instead of exploding. Close them again once it has cooled.
- A **lockout padlock** opens both breakers and keeps them open. Computers can read it and open or close the
  breakers. The lamps on the cabinets show green while closed and red when tripped.

The Grid DC-DC Converter does the same as a distribution substation, without breakers; it now takes 51.2 kV too.

## Arc flash

When a breaker, switch or relay opens, the current keeps flowing for a moment as an arc between its contacts.

- Opening within its rating, it puts the arc out at once: a few sparks.
- Opening on a fault, over twice its rating and with more than 5 kW in the arc (breaking a short circuit), it
  **flashes**: a blast of light, molten metal and smoke that burns and sets fire to whoever is close, and blinds them
  for a moment when it is big. The higher the voltage and current, the bigger it is, up to 5 blocks across.
- So stand back from breakers on circuits that may be shorted, and switch big loads off before working on them.
- `gameplay.hazards.arcFlashEnabled` in the config turns it off.

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

- Like the other batteries, they wear only with use, slowly and more so at high current. A battery that sits unused
  keeps its life. The rest changes how fast use wears them:
- **Heat**: wear doubles for every 10 °C above 25 °C.
- **Lead-acid** sulphates when used nearly flat.
- **Lithium-ion** wears faster when used near full, is damaged by charging below freezing, and **runs away thermally** above
  80 °C. The cells then heat themselves faster and faster until they explode, so keep them cool.
- **LiFePO4** is also damaged by charging below freezing, but does not run away.
- The cold raises the internal resistance, so batteries give less power in a cold biome or in winter.
- Jade shows the chemistry and the cell temperature. Ambient temperature comes from the biome and the depth, so batteries
  deep underground run warmer.
- All batteries, these and the old ones, are made to work at full power in up to 35 °C ambient.

### Overheating: venting

No battery explodes any more. Overheated (or overcharged), it **vents**: it cuts itself off, glows red and smokes,
and loses a tenth of its life. It stays off until it is cooled with a **bucket of water** (use the bucket on it), or
broken and replaced. Before that, a battery near its limit turns reddish and smokes a little, as a warning.

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

Front: the + of the panels or turbine. Back: the + of the battery. **Right: the common negative**: wire the − of the
panels and of the battery here, as on a real charge controller. Eln batteries are not grounded, so without it no
current can flow. (It is grounded inside, so panels and batteries that are grounded elsewhere work too.) Left: a
signal of the power it delivers.

**Charge voltage**: 0, the default, is automatic. It finds how many 50 V batteries are in series, from 1 up, by their
voltage at rest, and charges each to 54 V (1.08 times), which fills every chemistry to over 90 % without overcharging
any. A bank that is nearly flat may be taken for one battery fewer and not charge; set the voltage by hand then, or
charge it a little first. Jade shows what it found.

Every tenth of a second it changes the load it puts on its input a little, and keeps going while the power rises
(perturb and observe). That holds solar panels at their maximum power point through clouds, and wind turbines at
their best tip speed. It converts the power to the battery's voltage at 97.5 % efficiency, up to 300 V in (two
monocrystalline panels in series) and 60 A out.

It stops at the charge voltage and at the current limit, both set in its screen.

## Computers (CC: Tweaked)

Every block of the mod is an `eln` peripheral. A block holds one machine, or up to six parts on its faces, named
`down`, `up`, `north`, `south`, `west` and `east`:

- `getParts()`: the parts, as `{face = name}`.
- `getInfo([face])`: what Jade shows, as text, for any part.
- `getValues([face])`: numbers and flags, for batteries, breaker boxes, breakers, switches, protection relays,
  battery management systems, transfer switches and MPPT controllers.
- `getActions([face])` and `call([face,] action, ...)`: switch breakers and breaker box lines, trip or close a
  relay, set a transfer switch's mode. Locked-out devices refuse to close.

```lua
local box = peripheral.find("eln")
for _, b in ipairs(box.getValues("down").breakers) do print(b.color, b.state, b.current) end
box.call("down", "setBreaker", 2, false)
```

## SCADA

A control room screen for a whole power system.

- **SCADA Monitor** (wall part): lists up to 16 devices with their readings. Devices in trouble (a trip, a vented
  battery, an overheated or overvoltage substation) show in red and blink its alarm lamp. Breakers, switches, relays
  and substations get open/close buttons; locked-out devices refuse to close.
- **SCADA Linker** (item): use it on devices to collect them, then on a monitor to add them. Linked devices have an
  **x** to remove them.
- **SCADA Transmitter** (wall part): reports every device within 2 blocks, wirelessly, to monitors on the same channel
  within 256 blocks. Both default to the channel `SCADA`.

## Guidebook

With GuideME installed, craft the **Electrical Age Guide** from a book and a copper ingot. It covers the basics and
all of these parts; hold the guide key over an item to open its page. Without GuideME the mod works as before.

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
