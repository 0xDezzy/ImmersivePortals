package qouteall.imm_ptl.core.compat.voxy;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Inspect the original Fabric jar without loading its Minecraft 1.21.11 classes. */
class VoxyTargetContractTest {
    private static final String RENDERING = "me/cortex/voxy/client/core/rendering/";
    private static final String MIP_GEN = "me/cortex/voxy/client/core/model/MipGen";

    @Test
    void pinnedArtifactAndEverySelectorHookMatchTheTranslatedNonMinecraftAbi() throws Exception {
        try (var jar = openJar()) {
            try (var input = jar.getInputStream(jar.getEntry("fabric.mod.json"))) {
                var metadata = JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
                assertEquals("0.2.16-beta", metadata.get("version").getAsString());
            }
            var selector = read(jar, RENDERING + "ViewportSelector");
            method(selector, "<init>", "(Ljava/util/function/Supplier;)V");
            method(selector, "getViewport", "()L" + RENDERING + "Viewport;");
            method(selector, "free", "()V");
            assertTrue(selector.fields.stream().anyMatch(field -> field.name.equals("defaultViewport")
                && field.desc.equals("L" + RENDERING + "Viewport;")));
            method(read(jar, RENDERING + "Viewport"), "delete", "()V");
            var tracker = read(jar, RENDERING + "RenderDistanceTracker");
            method(tracker, "<init>", "(IIILjava/util/function/LongConsumer;Ljava/util/function/LongConsumer;)V");
            method(tracker, "setRenderDistance", "(I)V");
            method(tracker, "setCenterAndProcess", "(DD)Z");
        }
    }

    @Test
    void bothMandatoryMipRedirectsMatchEveryScratchAndQueueRead() throws Exception {
        try (var jar = openJar()) {
            var mip = read(jar, MIP_GEN);
            var solidify = mip.methods.stream().filter(m -> m.name.equals("solidify") && m.desc.equals("(JB)V"))
                .findFirst().orElseThrow();
            int scratchReads = 0;
            int queueReads = 0;
            for (var instruction : solidify.instructions) {
                if (instruction instanceof FieldInsnNode field && field.owner.equals(MIP_GEN)) {
                    assertEquals(Opcodes.GETSTATIC, field.getOpcode());
                    if (field.name.equals("SCRATCH")) {
                        assertEquals("[S", field.desc);
                        scratchReads++;
                    } else if (field.name.equals("QUEUE")) {
                        assertEquals("Lit/unimi/dsi/fastutil/bytes/ByteArrayFIFOQueue;", field.desc);
                        queueReads++;
                    } else fail("Unexpected shared scratch field " + field.name);
                }
            }
            assertTrue(scratchReads > 0);
            assertTrue(queueReads > 0);
        }
    }

    @Test
    void modelWorkersDoNotShareBuffersButReuseTheirOwnScratch() throws Exception {
        Class<?> mixin = Class.forName("qouteall.imm_ptl.core.compat.mixin.voxy.MixinVoxyMipGen");
        var scratch = mixin.getDeclaredMethod("ip$scratch");
        var queue = mixin.getDeclaredMethod("ip$queue");
        scratch.setAccessible(true);
        queue.setAccessible(true);
        Object mainScratch = scratch.invoke(null);
        Object mainQueue = queue.invoke(null);
        assertSame(mainScratch, scratch.invoke(null));
        assertSame(mainQueue, queue.invoke(null));
        try (var executor = Executors.newSingleThreadExecutor()) {
            Object[] worker = executor.submit(() -> new Object[]{scratch.invoke(null), queue.invoke(null),
                scratch.invoke(null), queue.invoke(null)}).get();
            assertNotSame(mainScratch, worker[0]);
            assertNotSame(mainQueue, worker[1]);
            assertSame(worker[0], worker[2]);
            assertSame(worker[1], worker[3]);
        }
        assertEquals(256, ((short[]) mainScratch).length);
    }

    @Test
    void optionalMixinsAreClientOnlyPseudoAndNeverRemapped() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/imm_ptl_compat.mixins.json")) {
            assertNotNull(input);
            var config = JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();
            assertFalse(config.getAsJsonArray("mixins").asList().stream()
                .anyMatch(entry -> entry.getAsString().startsWith("voxy.")));
            for (String name : new String[]{"MixinVoxyMipGen", "MixinVoxyRenderDistanceTracker", "MixinVoxyViewportSelector"}) {
                assertTrue(config.getAsJsonArray("client").asList().stream()
                    .anyMatch(entry -> entry.getAsString().equals("voxy." + name)));
                try (var bytes = getClass().getResourceAsStream("/qouteall/imm_ptl/core/compat/mixin/voxy/" + name + ".class")) {
                    var mixin = new ClassNode();
                    new ClassReader(bytes).accept(mixin, 0);
                    assertTrue(mixin.invisibleAnnotations.stream().anyMatch(a ->
                        a.desc.equals("Lorg/spongepowered/asm/mixin/Pseudo;")));
                    var annotation = mixin.invisibleAnnotations.stream().filter(a ->
                        a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
                    assertEquals(false, annotation.values.get(annotation.values.indexOf("remap") + 1));
                }
            }
        }
    }

    private static ZipFile openJar() throws Exception {
        return new ZipFile(System.getProperty("ip.voxyJar"));
    }

    private static ClassNode read(ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry(name + ".class");
        assertNotNull(entry, "Missing Voxy ABI class " + name);
        try (var input = jar.getInputStream(entry)) {
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static void method(ClassNode type, String name, String descriptor) {
        assertTrue(type.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)),
            "Missing Voxy ABI method " + type.name + "." + name + descriptor);
    }
}
