# ID Mappings

This document records the top-level registration group mappings used by the main item families.

For `sharedItem`, `sixNodeItem`, and `transparentNodeItem`, the final damage/identifier is:

```text
(group_id << 6) | sub_id
```

Since 1.21.1 every entry is registered as an item of its own, and the identifier is only used inside the mod: in saved nodes, in packets, and to look descriptors up.

The tables below are keyed by hexadecimal `group_id`. The first column and first row are hexadecimal lookups so a save/debug value can be located quickly.

`--` means the group is currently unused in that registry.

## Shared Item Groups

| Hex | `0` | `1` | `2` | `3` | `4` | `5` | `6` | `7` | `8` | `9` | `A` | `B` | `C` | `D` | `E` | `F` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `0x0_` | `--` | Heating elements | `--` | Regulators | Lamps / bulbs | Protection items | Combustion chamber | Ferromagnetic cores | Ingots | Dusts | Electrical motors | Solar tracker | `--` | `--` | Meters | Electrical drills |
| `0x1_` | Ore scanner | Mining pipe | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x2_` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x3_` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x4_` | Tree resin / rubber | Raw cable materials | `--` | `--` | `--` | Arc materials | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x5_` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x6_` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | Brushes | Misc items | Electrical tools | Portable items | Fuel burner items | `--` | `--` | Basic items | Electric minecart items / wire tools |
| `0x7_` | `--` | `--` | `--` | `--` | `--` | `--` | Wire machine items | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |

## Six-Node Groups

| Hex | `0` | `1` | `2` | `3` | `4` | `5` | `6` | `7` | `8` | `9` | `A` | `B` | `C` | `D` | `E` | `F` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `0x0_` | `--` | `--` | Ground | Electrical source | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x1_` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x2_` | Electrical cable | Current cable | Utility cables A | Utility cables B | Utility cables C | Utility cables D | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x3_` | Thermal cable | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x4_` | Lamp socket | Lamp supply | Battery charger | Power socket | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x5_` | Switchgear | MPPT charge controller | Oscilloscope | Power bundle cable | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | Wireless signal | Data logger | Electrical relay | Electrical gate source |
| `0x6_` | Passive components | Switches | Electrical manager | `--` | Electrical sensors | Thermal sensors | VU meters | Alarms | Environmental sensors | `--` | `--` | `--` | Electrical redstone | Electrical gates | `--` | `--` |
| `0x7_` | `--` | `--` | `--` | `--` | Tree resin collector | Six-node misc | Logic gates | `--` | `--` | `--` | `--` | `--` | Analog chips | Portable NaN | Current relays | `--` |

Notes:

- `0x22` through `0x25` are consumed by the utility cable allocator.
- `0x7E` is current relays.
- `0x7F` is the conduit/dev slot.

## Transparent-Node Groups

| Hex | `0` | `1` | `2` | `3` | `4` | `5` | `6` | `7` | `8` | `9` | `A` | `B` | `C` | `D` | `E` | `F` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `0x0_` | `--` | Power components | Transformers | Heat furnace | Turbines | `--` | `--` | Electrical antenna | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x1_` | Batteries | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x2_` | `--` | `--` | Electrical furnace | Macerator | Arc furnace | Compressor | Magnetizer | Plate machine | `--` | `--` | `--` | Egg incubator | Auto miner | `--` | `--` | `--` |
| `0x3_` | Solar panel | Wind turbine | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x4_` | Thermal dissipators | Transparent misc | Turret | Fuel generator | Floodlight | Festive devices | Fabrication machines | Railroad | Wire processing machines | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x5_` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x6_` | Large rheostat (special registration) | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` | `--` |
| `0x7_` | `--` | `--` | `--` | `--` | `--` | Nixie tube (special registration) | `--` | `--` | `--` | `--` | `--` | Grid devices | `--` | `--` | `--` | `--` |

Notes:

- `0x60` (`96`) is used by `registerLargeRheostat()`.
- `0x75` (`117`) is used by `registerNixieTube()`.
- `0x7B` (`123`) is used by grid devices.

## Added on the expansion-testing branch

New sub ids only; no existing id moved.

| Registry | Group | Sub id | Item |
| --- | --- | --- | --- |
| `sharedItem` | `0x0E` meters | 3 | Thermal Camera |
| `sharedItem` | `0x0E` meters | 4 | Lockout Padlock |
| `sharedItem` | `0x0E` meters | 5 | SCADA Linker |
| `sixNodeItem` | `0x50` switchgear | 0 | Overcurrent Protection Relay |
| `sixNodeItem` | `0x50` switchgear | 1 | Battery Management System |
| `sixNodeItem` | `0x50` switchgear | 2 | Automatic Transfer Switch |
| `sixNodeItem` | `0x50` switchgear | 3 | 200V Breaker Box |
| `sixNodeItem` | `0x50` switchgear | 4 | 50V Breaker Box |
| `sixNodeItem` | `0x50` switchgear | 5 | 800V Breaker Box |
| `sixNodeItem` | `0x51` | 0 | MPPT Charge Controller |
| `sixNodeItem` | `0x52` | 0 | Oscilloscope |
| `sixNodeItem` | `0x53` | 0 | 200V Power Bundle Cable |
| `sixNodeItem` | `0x53` | 1 | 800V Power Bundle Cable |
| `sixNodeItem` | `0x53` | 2 | 50V Power Bundle Cable |
| `sixNodeItem` | `0x53` | 3 | 50V Supply Cable |
| `sixNodeItem` | `0x53` | 4 | 200V Supply Cable |
| `sixNodeItem` | `0x53` | 5 | 800V Supply Cable |
| `sixNodeItem` | `0x53` | 6 | 3200V Supply Cable |
| `sixNodeItem` | `0x54` | 0 | SCADA Monitor |
| `sixNodeItem` | `0x54` | 1 | SCADA Transmitter |
| `transparentNodeItem` | `0x7B` grid devices | 9 | Distribution Substation |
| `transparentNodeItem` | `0x7B` grid devices | 10 | Step-Up Substation |
| `transparentNodeItem` | `0x10` batteries | 8 | Lead-Acid Battery |
| `transparentNodeItem` | `0x10` batteries | 9 | Nickel-Cadmium Battery |
| `transparentNodeItem` | `0x10` batteries | 10 | Lithium-Ion Battery |
| `transparentNodeItem` | `0x10` batteries | 11 | LiFePO4 Battery |
| `transparentNodeItem` | `0x30` solar panels | 8 | Monocrystalline Solar Panel |
| `transparentNodeItem` | `0x31` wind turbines | 4 | Permanent-Magnet Wind Turbine |
