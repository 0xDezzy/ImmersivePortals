package qouteall.imm_ptl.core;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ModMetadataTest {
    @Test void releaseJarIncludesConsistentHumanReadableMetadata() throws Exception {
        try (var jar = new JarFile(Path.of(System.getProperty("ip.modJar")).toFile())) {
            Config metadata = new TomlParser().parse(new StringReader(read(jar, "META-INF/neoforge.mods.toml")));
            List<Config> mods = metadata.get("mods");
            assertEquals(Set.of("immersive_portals_core", "q_misc_util", "imm_ptl"),
                mods.stream().map(mod -> mod.<String>get("modId")).collect(Collectors.toSet()),
                "display fixes must preserve the existing loader identities");

            String name = mods.getFirst().get("displayName");
            String version = mods.getFirst().get("version");
            String description = mods.getFirst().get("description");
            assertEquals("Immersive Portals - CE", name);
            assertFalse(description.isBlank());
            assertFalse(read(jar, "META-INF/neoforge.mods.toml").contains("${"));
            assertNotNull(metadata.get("issueTrackerURL"));
            for (Config mod : mods) {
                assertEquals(name, mod.get("displayName"));
                assertEquals(version, mod.get("version"));
                assertEquals(description, mod.get("description"));
                assertEquals("icon.png", mod.get("logoFile"));
                assertFalse(mod.<String>get("authors").isBlank());
            }
            assertNotNull(jar.getJarEntry("icon.png"));

            var manifest = jar.getManifest().getMainAttributes();
            assertEquals(name, manifest.getValue("Implementation-Title"));
            assertEquals(version, manifest.getValue("Implementation-Version"));
            assertEquals(mods.getFirst().get("authors"), manifest.getValue("Implementation-Vendor"));

            var launcher = JsonParser.parseString(read(jar, "mcmod.info")).getAsJsonArray().get(0).getAsJsonObject();
            assertEquals("immersive_portals_core", launcher.get("modid").getAsString());
            assertEquals(name, launcher.get("name").getAsString());
            assertEquals(version, launcher.get("version").getAsString());
            assertEquals(description, launcher.get("description").getAsString());
            assertEquals("icon.png", launcher.get("logoFile").getAsString());
            List<String> authors = new ArrayList<>();
            launcher.getAsJsonArray("authorList").forEach(entry -> authors.add(entry.getAsString()));
            assertFalse(authors.isEmpty(), "mcmod.info must list authors for legacy launchers");
            for (String author : authors) {
                assertFalse(author.toLowerCase().startsWith("and "),
                    "authorList must contain names, not the trailing credits clause: " + author);
            }

            var pack = JsonParser.parseString(read(jar, "pack.mcmeta")).getAsJsonObject().getAsJsonObject("pack");
            assertEquals(name + " resources", pack.getAsJsonObject("description").get("text").getAsString());
            assertNull(jar.getJarEntry("META-INF/mods.toml"), "this remains a native NeoForge mod");
            assertNoPreemptingLauncherMetadata(jar);
        }
    }

    /**
     * Launchers such as PrismLauncher, MultiMC and ATLauncher scan the archive once in entry
     * order and stop at the first file they recognise. An empty {@code fabric.mod.json}
     * therefore makes them treat this NeoForge mod as a nameless Fabric mod and fall back to
     * the jar file name instead of reading {@code META-INF/neoforge.mods.toml}.
     *
     * @see <a href="https://github.com/PrismLauncher/PrismLauncher/blob/develop/launcher/minecraft/mod/tasks/LocalModParseTask.cpp">LocalModParseTask</a>
     */
    @Test void jarHasNoLauncherMetadataThatPreemptsNeoForgeMetadata() throws Exception {
        try (var jar = new JarFile(Path.of(System.getProperty("ip.modJar")).toFile())) {
            assertNoPreemptingLauncherMetadata(jar);
        }
    }

    private static void assertNoPreemptingLauncherMetadata(JarFile jar) {
        // Any of these makes a launcher stop before it reaches our NeoForge metadata.
        for (String entry : List.of("fabric.mod.json", "quilt.mod.json", "forgeversion.properties",
            "litemod.json", "META-INF/nil/mappings.json")) {
            assertNull(jar.getJarEntry(entry),
                "launcher metadata must not preempt META-INF/neoforge.mods.toml: " + entry);
        }
        assertTrue(jar.stream().noneMatch(entry -> entry.getName().endsWith(".nilmod.css")),
            "launcher metadata must not preempt META-INF/neoforge.mods.toml: .nilmod.css");
    }

    private static String read(JarFile jar, String path) throws Exception {
        var entry = jar.getJarEntry(path);
        assertNotNull(entry, "Missing packaged metadata: " + path);
        try (var input = jar.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
