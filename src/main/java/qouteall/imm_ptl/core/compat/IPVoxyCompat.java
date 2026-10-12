package qouteall.imm_ptl.core.compat;

import de.nick1st.imm_ptl.events.ClientCleanupEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import qouteall.imm_ptl.core.compat.voxy.VoxyCompatibility;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;

/** Client-only entrypoint for the optional Roxy-translated Fabric Voxy installation. */
public final class IPVoxyCompat {
    private static boolean present;
    private static Portal viewportSwap;
    private static Portal distanceSwap;

    private IPVoxyCompat() {}

    public static void init() {
        ModList mods = ModList.get();
        String version = mods.getModContainerById("voxy")
            .map(mod -> mod.getModInfo().getVersion().toString()).orElse(null);
        present = VoxyCompatibility.enabled(mods.isLoaded("roxy"), mods.isLoaded("voxy"), version);
        if (present) Helper.log("Immersive Portals: enabling Roxy / Voxy 0.2.16-beta compatibility");
        NeoForge.EVENT_BUS.addListener(ClientCleanupEvent.class, event -> clearPendingSwaps());
    }

    public static boolean isPresent() {
        return present;
    }

    public static void requestViewportSwap(Portal portal) {
        // Cross-dimension renderers have separate state and must never exchange viewports.
        clearPendingSwaps();
        if (present && portal.level().dimension().equals(portal.getDestDim())) {
            viewportSwap = portal;
            distanceSwap = portal;
        }
    }

    public static Portal consumeViewportSwap() {
        Portal result = viewportSwap;
        viewportSwap = null;
        return result;
    }

    public static Portal consumeDistanceSwap() {
        Portal result = distanceSwap;
        distanceSwap = null;
        return result;
    }

    private static void clearPendingSwaps() {
        viewportSwap = null;
        distanceSwap = null;
    }
}
