package qouteall.imm_ptl.core.compat.mixin.voxy;

import it.unimi.dsi.fastutil.bytes.ByteArrayFIFOQueue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adapted from ImmersivePortalsMod-Updated: each model worker needs its own scratch. */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.model.MipGen", remap = false)
public class MixinVoxyMipGen {
    // Voxy 0.2.16-beta's initializer enforces MODEL_TEXTURE_SIZE <= 16.
    @Unique private static final ThreadLocal<short[]> ip$scratch = ThreadLocal.withInitial(() -> new short[256]);
    @Unique private static final ThreadLocal<ByteArrayFIFOQueue> ip$queue =
        ThreadLocal.withInitial(() -> new ByteArrayFIFOQueue(256));

    @Redirect(method = "solidify", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
        target = "Lme/cortex/voxy/client/core/model/MipGen;SCRATCH:[S"))
    private static short[] ip$scratch() {
        return ip$scratch.get();
    }

    @Redirect(method = "solidify", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
        target = "Lme/cortex/voxy/client/core/model/MipGen;QUEUE:Lit/unimi/dsi/fastutil/bytes/ByteArrayFIFOQueue;"))
    private static ByteArrayFIFOQueue ip$queue() {
        return ip$queue.get();
    }
}
