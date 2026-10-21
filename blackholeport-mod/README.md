# Black Hole (Forge 1.20.1 port)

A Forge 1.20.1 port of the Fabric mod **Black Hole** by vini2003 (MIT licence, see
`LICENSE-blackhole-fabric.txt`). The behaviour, defaults and command names follow the original; the code is a
rewrite for Forge. Everyone who joins needs the mod installed (it draws the sphere and the black screen).

## What it does
- One black hole per dimension. `/blackhole spawn` creates it at your feet, size 1, and it wakes up after a
  100-tick (5 s) countdown.
- **Grows** over time: size is multiplied by `1 + growSpeed` every tick, up to `limit` (64). The default doubles it
  about every 15 minutes.
- **Follows** the nearest survival player, very slowly by default (`followSpeed`).
- **Pulls** players and entities toward it (strength `pullSpeed ^ (0.6 * distance / size)`) and **kills** anything
  inside it. Creative and spectator players are ignored.
- **Destroys terrain** two ways: a clearing sphere (everything closer than 1.5 x size) and surface peeling (each
  tick, in each nearby chunk, one random column within 2.5 x size has its top block removed, 1-in-sqrt(size)).
  Flying block debris is drawn toward the hole.
- The screen goes **black** while the camera is inside it.

## Commands (op level 2)
`/blackhole spawn`, `kill`, `followSpeed <n>`, `growSpeed <n>`, `pullSpeed <n>` (n = whole-number multiplier of the
default, 1 = default), `damage <true|false>`, `pause`, `resume`.

## Differences from the original
- The original clears its whole sphere in one tick; here at most `blocksClearedPerTick` (100) blocks per tick, so big
  holes do not freeze a server. Raise it to clear faster.
- Bedrock and other unbreakable blocks are never removed.
- Settings are in `config/blackholeport.toml`. The speed/damage/pause commands change the live values only.

Status: written but never compiled or run before the first GitHub build.
