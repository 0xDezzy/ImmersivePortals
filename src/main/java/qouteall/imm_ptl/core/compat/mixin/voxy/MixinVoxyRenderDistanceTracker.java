package qouteall.imm_ptl.core.compat.mixin.voxy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.IPVoxyCompat;
import qouteall.imm_ptl.core.compat.voxy.PortalRenderDistanceTracker;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.util.List;
import java.util.function.LongConsumer;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.rendering.RenderDistanceTracker", remap = false)
public class MixinVoxyRenderDistanceTracker {
    @Unique private PortalRenderDistanceTracker<List<Portal>> ip$tracker;
    @Unique private int ip$processRate;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ip$init(int rate, int minY, int maxY, LongConsumer add, LongConsumer remove, CallbackInfo ci) {
        ip$tracker = new PortalRenderDistanceTracker<>(minY, maxY, add, remove, 10);
        ip$processRate = rate;
    }

    @Inject(method = "setRenderDistance", at = @At("HEAD"), cancellable = true)
    private void ip$radius(int radius, CallbackInfo ci) {
        ip$tracker.setRadius(radius);
        ci.cancel();
    }

    @Inject(method = "setCenterAndProcess", at = @At("HEAD"), cancellable = true)
    private void ip$process(double x, double z, CallbackInfoReturnable<Boolean> cir) {
        boolean inner = PortalRendering.isRendering();
        if (!inner) {
            Portal portal = IPVoxyCompat.consumeDistanceSwap();
            if (portal != null) {
                Portal reverse = PortalManipulation.findReversePortal(portal);
                ip$tracker.promote(List.of(portal), reverse == null ? null : List.of(reverse));
            }
        }
        cir.setReturnValue(ip$tracker.process(inner ? PortalRendering.getPortalPath() : null,
            x, z, ip$processRate, System.nanoTime()));
    }
}
