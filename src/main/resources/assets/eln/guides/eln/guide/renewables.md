---
navigation:
  title: Solar and wind
  position: 6
item_ids:
  - eln:monocrystalline_solar_panel
  - eln:permanent_magnet_wind_turbine
  - eln:mppt_charge_controller
---

# Solar and wind

<Row>
  <ItemImage id="eln:monocrystalline_solar_panel" />
  <ItemImage id="eln:mppt_charge_controller" />
</Row>

A real solar panel gives its full power only at one voltage, about 80 % of its open-circuit voltage; a wind turbine
only at one rotor speed. Connected straight to a battery, they work wherever the battery puts them, often at half
their power.

## MPPT Charge Controller

It finds that best point by itself (maximum power point tracking) and charges the battery from it.

- **Front**: the + of the panels or turbine. **Back**: the + of the battery.
- **Right**: the common negative. Wire the − of the panels and of the batteries here: batteries are not grounded,
  so without it no current flows.
- Leave the charge voltage at 0: it finds how many 50 V batteries are in series and charges each to 54 V.
- It takes up to 300 V in, so two monocrystalline panels can go in series.

## The wind turbine

The permanent-magnet wind turbine simulates its rotor: the power of the wind grows with the cube of its speed, the
blades pitch to limit power above the rated wind, and feather in a storm. An MPPT controller keeps it at its best speed.
