package qouteall.imm_ptl.core.compat.voxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoxyCompatibilityTest {
    @Test
    void integrationRequiresBothOptionalModsAndThePinnedVersion() {
        assertFalse(VoxyCompatibility.enabled(false, false, null));
        assertFalse(VoxyCompatibility.enabled(true, false, null));
        assertFalse(VoxyCompatibility.enabled(false, true, "0.2.16-beta"));
        assertTrue(VoxyCompatibility.enabled(true, true, "0.2.16-beta"));
        assertThrows(IllegalStateException.class, () -> VoxyCompatibility.enabled(true, true, "0.2.20-beta"));
        assertThrows(IllegalStateException.class, () -> VoxyCompatibility.enabled(true, true, null));
    }

    @Test
    void fallbackPreservesShadersAndExplicitDebugDisabledOrCompatibilityModes() {
        assertTrue(VoxyCompatibility.needsFramebuffer(true, false, true));
        assertFalse(VoxyCompatibility.needsFramebuffer(false, false, true));
        assertFalse(VoxyCompatibility.needsFramebuffer(true, true, true));
        assertFalse(VoxyCompatibility.needsFramebuffer(true, false, false));
    }
}
