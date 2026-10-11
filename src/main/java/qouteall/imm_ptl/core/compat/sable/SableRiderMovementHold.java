package qouteall.imm_ptl.core.compat.sable;

import java.util.UUID;

/** Bounded identity/coordinate-frame guard; contains no client or optional-mod classes. */
public final class SableRiderMovementHold {
    private final UUID playerId;
    private final UUID vehicleId;
    private final Object sourceDimension;
    private final Object destinationDimension;
    private final int maximumTicks;
    private int ticks;
    private boolean cancelled;

    public SableRiderMovementHold(UUID playerId, UUID vehicleId, Object sourceDimension,
        Object destinationDimension, int maximumTicks) {
        if (maximumTicks <= 0) throw new IllegalArgumentException("Positive movement hold lifetime required");
        this.playerId = playerId; this.vehicleId = vehicleId;
        this.sourceDimension = sourceDimension; this.destinationDimension = destinationDimension;
        this.maximumTicks = maximumTicks;
    }
    public boolean shouldHold(UUID currentPlayer, UUID currentVehicle, Object dimension) {
        return !expired() && vehicleId != null && playerId.equals(currentPlayer) && currentVehicle == null
            && (sourceDimension.equals(dimension) || destinationDimension.equals(dimension));
    }
    public void tick() { if (ticks < maximumTicks) ticks++; }
    public boolean expired() { return cancelled || ticks >= maximumTicks; }
    public void cancel() { cancelled = true; }
}
