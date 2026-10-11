# Stationary miniature base prototype (issue #24, milestone 1)

This is an **operator-only, command-based prototype** for viewing and entering an
existing region at miniature exterior size. It is not the complete tiny-airship
feature. There is no craftable anchor item, automatic building relocation, or
Sable scale-changing handoff in this milestone. Issue #24 remains open.

## Workflow

Select two inclusive block corners in the current dimension. Include the building,
its floor and enough interior air for its occupants. Choose a unique name and a
scale divisor from 2 to 32 (fractional values such as 2.5 work). Each backing axis
is 1–64 blocks, and each exterior axis must be at least 0.25 blocks. Load all
backing and placement chunks first. Place the miniature into empty air, at least
one block away from any managed backing/miniature region.

- `/portal miniature create home 0 64 0 7 71 7 2.5 32 64 32`
- `/portal miniature list`
- `/portal miniature pickup home`
- `/portal miniature place home 48 64 32`
- `/portal miniature restore home`

`position` is the miniature's bottom center. `place` uses the command's current
dimension, so `/execute in <dimension> run portal miniature place ...` also works.
Commands require a player with permission level 2, even if ordinary portal
commands are enabled for creative players. Only the creator can mutate a view.
Names are server-wide; the initial cap is 32 registered views.

## Backing blocks, occupants and lifecycle

The selected building **stays at its original world coordinates**. The view wraps
that existing region with six bidirectional portal faces. This is the explicit
existing-region exception documented by `PortalManipulation.createScaledBoxView`;
it is not a copy or a compact structure NBT snapshot. Inventories, ticking block
entities and machines are the original instances. Existing chunk/simulation rules
continue to apply. Miniature appearance does not make the simulation cheaper.

Portal crossing keeps an entity's existing scale on both entry and exit. It does
not apply the portal multiplier to living entities. Choose an exterior opening
large enough for normal player crossing; the quarter-block lower limit is a
view/interaction limit, not a promise that a normal player fits through it. Walk
through any face with sufficient clearance; blocks inside remain interactable
through the ordinary cross-portal interaction system. This prototype does not
resize passengers or provide a separate reach/camera scaling engine.

Pickup removes only the portal view and retains its owned backing reference.
Restoration removes the view and registry entry, revealing the same ordinary
full-size region. Neither operation deletes, moves or recreates blocks or entities.
Occupants remain at the original backing coordinates and can leave through the
now ordinary boundary. Offline players likewise reconnect at their original
backing coordinates; no interior dimension is deleted. Owner disconnect does not
remove the view. Chunk unloading does not remove the backing reference. There is
no physical anchor to break or piston-move in this prototype; use the commands.

The authoritative registry is saved in overworld SavedData. Portal faces are
rebuildable derived data with deterministic IDs for each placement revision.
Reconciliation removes stale/duplicate faces and repairs missing ones after a
restart or an interrupted save, without copying backing contents. If a dimension
is missing, its reference is retained and its view stays unavailable. Unsupported
registry versions and unreadable existing files quarantine the registry: commands
and reconciliation stop, and the original data is not overwritten. Minecraft
otherwise catches saved-data load failures and may call the empty constructor;
the quarantine explicitly covers that fallback. Ordinary
Minecraft save durability still applies; this does not provide crash-atomic
saving of unrelated inventories or bypass backups.

## Verification and limitations

JUnit covers fractional/minimum sizes, invalid input, ownership, placement and
NBT round-trips. A real GameTest checks all twelve faces, idempotent recovery,
native disk reload (including unknown-version quarantine), all 32 views retaining
their destination loaders, and preservation of the identical chest inventory through
pickup/replacement/restoration. The diagnostic graphical matrix adds a managed
1:2.5 view of real blocks, including shader/resource reload phases. Existing
real-pack lanes remain regression coverage; they do not yet establish miniature
visual compatibility for arbitrary shaderpacks. Runtime results must be read from
the exact PR's CI; source tests alone are not a runtime certification.

Not yet verified/supported: automated backing allocation/relocation, breakable
anchor items, all modded machines, automatic occupant resizing, very small player
entry, or any moving miniature Sable assembly. Do not treat this prototype as
completion of all issue #24 acceptance items.

## Moving structures: integration boundary

CE currently pins Sable 2.0.5 and companion 1.6.0. The optional moving-structure
milestone must establish one owner for pose persistence, TCP/UDP serialization,
physics/collider updates and connected-body scaling. Simply lifting CE's
`!portal.hasScaling()` filter is unsafe and is deliberately not done here.

Relevant source references:

- [Issue #24 and staged acceptance](https://github.com/UpperMoon0/ImmersivePortals/issues/24)
- [Existing-region portal helper](https://github.com/UpperMoon0/ImmersivePortals/blob/b63f321f9e42bd8641f4b05d1d4417177a6b992d/src/main/java/qouteall/imm_ptl/core/portal/PortalManipulation.java)
- [CE Sable handoff guard](https://github.com/UpperMoon0/ImmersivePortals/blob/b63f321f9e42bd8641f4b05d1d4417177a6b992d/src/main/java/qouteall/imm_ptl/core/compat/sable/SableDimensionStackCompat.java)
- [Sable Scale candidate, inspected commit 2cb9b88](https://github.com/falling-colud/sable-scale/tree/2cb9b8859ac7c7ac1bd22bf45fa79c48fe0ddff1)

Sable Scale is a candidate, not a verified compatible dependency in this PR.

Native persistence reference: [Minecraft 1.21.1 DimensionDataStorage](https://github.com/UpperMoon0/MC-Modding-Src/blob/2474fa0c367e1393f805dc6157dae207626e9387/1.21.1/net/minecraft/world/level/storage/DimensionDataStorage.java).
