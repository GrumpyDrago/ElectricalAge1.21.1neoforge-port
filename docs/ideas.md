# Ideas for the expansion

Ideas for what to build next on the `expansion-testing` branch, after what is in 2.0.0-testing.5. Effort: **S** a few
days, **M** a week or two, **L** several weeks. ★ marks the ones that would make the most difference for players.

Already built: protection relays, BMS, transfer switch, battery chemistries and variants, venting, MPPT, real solar
and wind, oscilloscope, thermal camera, arc flash, breaker boxes and bundles, lockout, CC: Tweaked, grid tiers,
substations, SCADA, the guidebook. Ruled out: ground faults and earthing.

## 1. The grid

- ★ **Pole-mounted recloser and fuse cutouts (S).** Protection out on the lines, so a fault on one street does not trip
  the whole substation. Cutouts blow and hang open, visibly; a hook stick puts them back.
- ★ **Grid frequency (M).** Generation and load must balance: too much load and the frequency sags, turbines slow, and
  under-frequency relays shed load. Governors bring it back. Makes running a grid a game of its own.
- **On-load tap changer (S).** Substations that keep their output voltage up as the load grows, in steps you can hear.
- **Voltage drop along long lines (S).** Towns at the end of a long line get a low voltage; capacitor banks and tap
  changers fix it.
- **Underground cables (M).** Buried high voltage cable between substations: no poles, costlier, and hard to repair.
- **Lightning and storms (M).** Strikes on lines and poles, surge arresters to catch them, and reclosers to ride through.
- **HVDC link (L).** Converter stations at both ends of a very long line, with no frequency to match.
- **Islanding and black start (M).** Parts of the grid running on their own, and starting it again from nothing with a
  diesel set.
- **Grid map (S).** The pole map as an in-game screen or a map item.

## 2. Generation

- ★ **Diesel and gas generator sets (M).** A container genset with a governor and voltage regulator: frequency sags
  under a load step and recovers; fuel from other mods.
- ★ **Hydro (M).** A dam or run-of-river turbine whose power depends on the height of the water and the flow.
- **Solar farm parts (S).** String combiner boxes, panel dust (rain cleans it), panels that age over years.
- **Wind farm details (S).** Turbines that turn into the wind (yaw) and shade each other's wind downstream.
- **Geothermal and tidal (M).** Heat from lava deep down; turbines that follow the tide.
- **Nuclear reactor (L).** A multiblock with control rods, coolant, decay heat and a meltdown to avoid. A big project.
- **Biogas and fuel cells (M).** Digesters fed with crops and manure; hydrogen fuel cells for clean backup power.
- **Generator synchronising panel (M).** Matching speed and phase before closing a generator onto the grid.

## 3. Storage

- ★ **Battery container (M).** A multiblock battery bank with its BMS, inverter and cooling built in, for grid storage.
- **Supercapacitors (S).** Huge power, little energy, no wear; for smoothing and short bursts.
- **Pumped hydro (M).** Pump water up when there is power to spare, run it down through a turbine when not.
- **Flywheel storage (S).** The existing flywheel made into a proper store, with its losses.
- **Thermal storage (M).** Molten salt for solar heat, released at night.
- **Battery cooling (S).** Batteries next to water, or with a fan, run cooler; liquid-cooled battery racks.

## 4. Power electronics

- ★ **Inverters (M).** Off-grid and grid-tie inverters, so solar and batteries can feed the grid. Needs a frequency or AC
  model, at least a simple one.
- **Motor drives (M).** Variable frequency drives and soft starters for big motors, which otherwise draw six times
  their current to start.
- **Power factor (M).** Motors and lamps that load the grid more than their power; capacitor banks to correct it.
- **Rectifiers and DC grids (S).** For electrolysis and electric railways.

## 5. Motors and loads

- **Motor behaviour (M).** Starting current, torque against speed, stalling and overheating, with motor protection.
- **Electric heating and cooling (M).** Heaters, heat pumps and air conditioning on the room temperature system.
- **Electric vehicle and minecart charging (S).** Charging stations for electric minecarts and vehicles of other mods.
- **Machines that draw more while working (S).** And slow down when the supply sags.

## 6. Safety

- ★ **Electrocution and protective gear (S).** Touching live parts hurts, more at higher voltage and in the wet; rubber
  gloves and boots protect, and an arc-rated suit cuts arc flash burns.
- **Cable fires (S).** Badly overloaded cables can set fire to wood and wool nearby, with a config switch.
- **Key interlocks (S).** A key that only comes out of one switch when it is open, and is needed to open a door or
  close another switch, as in real switchgear.
- **Warning signs and barriers (S).** High voltage signs, fences that hum, and fenced substations mobs stay out of.
- **Cable and insulation ageing (M).** Old, hot cables wear and fail; an insulation tester finds them first.

## 7. Control and monitoring

- ★ **SCADA trends and alarm log (M).** Graphs of voltages and power over hours, and a list of alarms to acknowledge.
- ★ **Video wall (M).** A multiblock SCADA screen drawing a live one-line diagram of the grid in the world.
- **Programmable logic controller (L).** Ladder logic in a screen, with signal inputs and outputs.
- **Remote control of generators and tap changers from SCADA (S).**
- **Load dispatch (M).** Rank generators by cost and run the cheapest first, automatically.
- **CC: Tweaked events (S).** Computers notified of trips and alarms instead of having to poll.

## 8. Instruments

- **Clamp meter (S).** Current through a cable, without breaking it.
- **Insulation tester (S).** Finds worn cables before they fail.
- **Panel meters (S).** Analogue voltmeters and ammeters for walls and control panels, with moving needles.
- **Power quality analyser (M).** Frequency, power factor and dips, with the oscilloscope's screen.

## 9. Building and playing

- ★ **Quest line (M).** An FTB Quests chapter or an advancement tree from the first battery to running a grid.
- ★ **Example builds in the guide (M).** GuideME scenes showing working setups in 3D: a UPS, a solar charge station,
  a substation.
- **Placement preview (S).** A hologram of where a substation or tower will go, and whether it fits.
- **Cable spools and a wire cutter (S).** Cable length that costs, and cutting a grid line without breaking the pole.
- **Cable routing tool (M).** Place a run of cable along a path in one go.
- **Recipe viewer categories (S).** JEI or EMI pages for the mod's own machines (macerator, compressor, and so on).

## 10. Looks and sound

- **Load hum (S).** Transformers and substations louder with their load.
- **Corona and sparks (S).** A faint glow on overloaded high voltage lines at night, sparks from failing insulators.
- **Animated switchgear (S).** Breaker handles that swing, and breaker box doors.
- **Better models (M).** Blender-made replacements for the script-made models, part names kept.
- **Old items' icons (S).** A pass over the icons of the original items.

## 11. Other mods

- ★ **Create (M).** Create's rotation to and from the mod's shafts, both ways; and overhead lines for electric Create
  trains.
- **Mekanism (M).** Hydrogen from electrolysis, and heat to and from its machines.
- **Immersive Engineering (M).** Their wires and transformers joined to the mod's grid.
- **Applied Energistics 2 and Refined Storage (S).** Power their networks from the grid, with a proper load.
- **Serene Seasons (S).** Seasons in the ambient temperature and the sun's height.
- **KubeJS (S).** Recipes and events for pack makers.
- **FE exchange limits (S).** A configurable rate and limit for the energy exporter.

## 12. Under the hood

- ★ **Multiplayer and dedicated server testing (S).** Nothing of the expansion has been tried there yet.
- **Big grid performance (M).** Profiling and speeding up worlds with thousands of parts.
- **Data-driven batteries and cables (M).** New ones from data packs, without code.
- **An API for addon mods (M).** Building on the existing electrical API.
- **Translations (S).** Most new text is only in English.
