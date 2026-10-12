package qouteall.imm_ptl.core.compat.voxy;

/** Shared loading-time/runtime policy; no optional mod classes are linked here. */
public final class VoxyCompatibility {
    private VoxyCompatibility() {}

    public static boolean enabled(boolean roxyLoaded, boolean voxyLoaded, String version) {
        if (!roxyLoaded || !voxyLoaded) return false;
        if (!"0.2.16-beta".equals(version)) {
            throw new IllegalStateException("Immersive Portals: Roxy compatibility requires Voxy 0.2.16-beta; found "
                + version + ". Install the pinned Minecraft 1.21.11 Fabric Voxy jar through Roxy.");
        }
        return true;
    }

    public static boolean needsFramebuffer(boolean enabled, boolean shaders, boolean normalMode) {
        return enabled && !shaders && normalMode;
    }
}
