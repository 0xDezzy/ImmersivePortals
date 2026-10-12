package qouteall.imm_ptl.core.compat.mixin.fabric;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Compare Roxy's networking implementation to the old ABI exercised by concurrency tests. */
class RoxyNetworkingContractTest {
    @Test
    void registrationAndDispatchBytecodeIsUnchangedExceptTheNativeMapConstructor() throws Exception {
        try (var jar = new ZipFile(System.getProperty("ip.roxyNetworkingJar"))) {
            for (String name : new String[]{
                "net/fabricmc/fabric/impl/networking/PayloadTypeRegistryImpl",
                "org/sinytra/fabric/networking_api/NeoNetworkRegistrar",
                "org/sinytra/fabric/networking_api/NeoNetworkRegistrar$NeoPayloadHandler"
            }) {
                ClassNode oldType;
                try (var input = getClass().getResourceAsStream("/" + name + ".class")) {
                    assertNotNull(input);
                    oldType = read(input.readAllBytes());
                }
                ClassNode newType;
                try (var input = jar.getInputStream(jar.getEntry(name + ".class"))) {
                    newType = read(input.readAllBytes());
                }
                assertEquals(oldType.fields.stream().map(f -> f.access + ":" + f.name + f.desc).toList(),
                    newType.fields.stream().map(f -> f.access + ":" + f.name + f.desc).toList(), name);
                assertEquals(oldType.methods.size(), newType.methods.size(), name);
                for (MethodNode oldMethod : oldType.methods) {
                    MethodNode newMethod = newType.methods.stream()
                        .filter(m -> m.name.equals(oldMethod.name) && m.desc.equals(oldMethod.desc))
                        .findFirst().orElseThrow();
                    if (name.endsWith("/NeoNetworkRegistrar") && oldMethod.name.equals("<init>")) {
                        // Upstream made this one map concurrent; every other instruction is identical.
                        int replacements = 0;
                        for (var instruction : oldMethod.instructions) {
                            if (instruction instanceof TypeInsnNode type && type.desc.equals("java/util/HashMap")) {
                                type.desc = "java/util/concurrent/ConcurrentHashMap";
                                replacements++;
                            } else if (instruction instanceof MethodInsnNode call && call.owner.equals("java/util/HashMap")) {
                                assertEquals("<init>", call.name);
                                call.owner = "java/util/concurrent/ConcurrentHashMap";
                                replacements++;
                            }
                        }
                        assertEquals(2, replacements);
                    }
                    assertArrayEquals(bytecode(oldMethod), bytecode(newMethod), name + "." + oldMethod.name);
                }
            }
        }
    }

    private static ClassNode read(byte[] bytes) {
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static byte[] bytecode(MethodNode method) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "Contract", null, "java/lang/Object", null);
        method.accept(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }
}
