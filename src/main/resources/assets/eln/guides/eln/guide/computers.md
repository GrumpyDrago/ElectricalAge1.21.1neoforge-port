---
navigation:
  title: Computers
  position: 8
---

# Computers

With CC: Tweaked, every block of the mod is an `eln` peripheral. A block holds one machine, or up to six parts on
its faces, named `down`, `up`, `north`, `south`, `west` and `east`.

| Method | Gives |
| --- | --- |
| `getParts()` | the parts of the block, as `{face = name}` |
| `getInfo([face])` | what Jade shows, as text, for any part |
| `getValues([face])` | numbers and flags of batteries, breakers, breaker boxes, relays, substations and more |
| `getActions([face])` | what `call` can do with the part |
| `call([face,] action, ...)` | switches a breaker, a breaker box line, a relay, a substation breaker |

A locked-out device refuses to close, whatever the computer says.

```lua
local box = peripheral.find("eln")
for _, b in ipairs(box.getValues("down").breakers) do
  print(b.color, b.state, b.current)
end
box.call("down", "setBreaker", 2, false)
```
