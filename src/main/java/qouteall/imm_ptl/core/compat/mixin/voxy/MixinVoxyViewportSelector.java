package qouteall.imm_ptl.core.compat.mixin.voxy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.IPVoxyCompat;
import qouteall.imm_ptl.core.compat.voxy.PortalViewportCache;
import qouteall.imm_ptl.core.compat.voxy.VoxyReflection;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.util.List;
import java.util.function.Supplier;

/** Separate temporal/HiZ state for each portal camera; preserve Voxy's VR/shadow views. */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.rendering.ViewportSelector", remap = false)
public class MixinVoxyViewportSelector {
    @Unique private Object ip$originalViewport;
    @Unique private PortalViewportCache<List<Portal>, Object> ip$viewports;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ip$init(Supplier<?> creator, CallbackInfo ci) {
        ip$originalViewport = VoxyReflection.defaultViewport(this);
        ip$viewports = new PortalViewportCache<>(ip$originalViewport, creator::get, VoxyReflection::delete, 10);
    }

    @Inject(method = "getViewport", at = @At("RETURN"), cancellable = true)
    private void ip$select(CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() != ip$originalViewport) return;
        if (PortalRendering.isRendering()) {
            cir.setReturnValue(ip$viewports.portal(PortalRendering.getPortalPath()));
            return;
        }
        Portal portal = IPVoxyCompat.consumeViewportSwap();
        if (portal != null) {
            Portal reverse = PortalManipulation.findReversePortal(portal);
            ip$viewports.promote(List.of(portal), reverse == null ? null : List.of(reverse));
        }
        cir.setReturnValue(ip$viewports.outer());
    }

    @Inject(method = "free", at = @At("HEAD"))
    private void ip$free(CallbackInfo ci) {
        ip$viewports.close();
    }
}
