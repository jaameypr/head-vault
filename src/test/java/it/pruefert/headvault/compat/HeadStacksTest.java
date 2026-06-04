package it.pruefert.headvault.compat;

import net.minecraft.world.item.component.ResolvableProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression test for the immutable-property-map bug: the 2-arg {@code GameProfile} constructor
 * yields an unmodifiable property map, so putting the texture threw
 * {@code UnsupportedOperationException} when a head was rendered. Exercises the exact construction
 * path with no Minecraft registries required.
 */
class HeadStacksTest {

    private static final String VALUE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZhM2JiYTJiN2EyYjRmYTQ2OTQ1YjE0NzE3NzdhYmU0NTk5Njk1NTQ1MjI5ZTc4MjI1OWFlZDQxZDYifX19";

    @Test
    void texturedProfileIsBuiltAndCarriesTexture() {
        ResolvableProfile profile = HeadStacks.texturedProfile(VALUE);
        assertNotNull(profile, "profile must be created");
        assertFalse(profile.partialProfile().properties().get("textures").isEmpty(),
                "the textures property must be present (mutable map populated, not the immutable default)");
    }

    @Test
    void sameTextureYieldsSameOwnerSoHeadsStack() {
        // Deterministic owner UUID -> identical PROFILE component -> stacks up to 64.
        assertEquals(HeadStacks.texturedProfile(VALUE).partialProfile().id(),
                HeadStacks.texturedProfile(VALUE).partialProfile().id());
        // A different texture must NOT collide (keeps distinct heads unstackable).
        assertNotEquals(HeadStacks.texturedProfile(VALUE).partialProfile().id(),
                HeadStacks.texturedProfile("ZGlmZmVyZW50").partialProfile().id());
    }
}
