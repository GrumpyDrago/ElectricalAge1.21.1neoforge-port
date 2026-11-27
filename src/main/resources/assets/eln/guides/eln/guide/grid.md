---
navigation:
  title: The grid
  position: 5
item_ids:
  - eln:transmission_tower
  - eln:utility_pole
  - eln:distribution_substation
  - eln:step_up_substation
  - eln:3200v_supply_cable
---

# The grid

<Row>
  <ItemImage id="eln:step_up_substation" />
  <ItemImage id="eln:transmission_tower" />
  <ItemImage id="eln:distribution_substation" />
  <ItemImage id="eln:utility_pole" />
</Row>

For power over long distances, build a grid in three tiers, as in the real world:

| Tier | Voltage | Carried by | Each line |
| --- | --- | --- | --- |
| Transmission | 51.2 kV | T2 transmission towers | 40 A, about 2 MW |
| Distribution | 12.8 kV | T1 utility poles | 20 A, about 250 kW |
| Local | 3.2 kV | the pole transformer, then cables | as the cables |

Link poles, towers and substations with **high voltage cable**: use it anywhere on one, then anywhere on the other.
Where you click does not matter: a substation puts the line on the side of its voltage by itself. Two ends of different
voltages, such as a transmission tower and a utility pole, are refused: put a substation between them.

## From the power station: the step-up substation

Bring your power station's 3.2 kV to the front of a **step-up substation**, at the ground, with **3200V supply
cables** (about 120 kW each; use several for more). Link transmission towers to it: out comes 51.2 kV, up
to 1 MW.

## To the town: the distribution substation

Link the transmission line and utility poles to a **distribution substation**; each goes to the side of its voltage.
It gives 12.8 kV, up to 2 MW. A utility pole with a transformer then gives 3.2 kV for cables and transformers.

## Breakers and protection

Each side of a substation has a breaker with a protection relay. It trips on an overcurrent, closes again once by
itself after five seconds to clear a passing fault, and then stays open until you close it in the substation's
screen. The transformer trips both breakers if it overheats (it runs at 50 °C above ambient at full load, and trips at
75 °C) or sees too high a voltage; let it cool and close them again. Breaking a fault at these voltages makes a big arc
flash: keep your distance.
