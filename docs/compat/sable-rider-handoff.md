# Detached rider prediction during Sable handoff

Main CI [38159514148](https://github.com/UpperMoon0/ImmersivePortals/actions/runs/38159514148), UDP job 114529327652, exposed a client prediction defect during the ordered seat-detach/dimension/Ack packet window. The captured source body stayed at y=-62.87 while the detached local player fell to y=-80.77 after 20 client ticks and y=-96.51 after 30. The destination body was already staged at y=133.1; no dimension transition had been applied. The existing 32-block spatial-continuity assertion correctly failed. TCP passed in that run. This was not an installation or runner-startup failure.

The compatibility layer now suspends only LocalPlayer.aiStep (local input/movement prediction) for the captured detached rider, in the correlated source/destination dimensions. The client and network continue ticking; authoritative packets remain free to apply position and dimension changes. It does not overwrite position, velocity, gravity attributes, flight flags, or unrelated players. Mounted players and unrelated coordinate frames are not held.

The hold is bounded by the existing 100-tick rider-sync constant and is released on attachment, rejection, cancellation, client cleanup/disconnect, or expiry. The existing post-Ack seat timer and fallback camera transform remain in place. The E2E 120-tick correlated-detach budget, 32-block distance limit, overlap budget, and three-transition assertions are unchanged.

## Regression

Before requesting the first real crossing, the dedicated client uses its actual captured seat and dimension-stack portal to reproduce a pending handoff locally. The server keeps the source body stationary until its normal source-ready marker. The client detaches, executes 40 ordinary client ticks without an Ack, and requires its world position to remain within 0.01 blocks. It then clears the probe, verifies the hold has ended, remounts the original seat, and runs every existing real handoff/gravity/dismount/remote-observer check. No sleeps or extended budgets are used. Both transport lanes require the new authoritative client-movement-hold marker.

JUnit separately checks identity/frame scope, the 100-tick expiry, immediate cancellation, and a delayed-Ack prediction model. Native runtime verification is required before merging; local Python or standalone Java compilation is not a substitute.

This patch does not add scaled Sable structures or lift the scaling-portal traversal guard. Issue #24's moving-miniature milestone remains unfinished.
