package it.pruefert.headvault.compat;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Builds player-head and icon item stacks. This is the <b>#1 version-churn point</b>: the player
 * profile component changes shape across Minecraft versions. On 26.1, {@link ResolvableProfile} is
 * abstract and is created via {@link ResolvableProfile#createResolved(GameProfile)} (the old public
 * constructor is gone). Keeping this here means a future port edits one method, not the GUI.
 */
public final class HeadStacks {

    private HeadStacks() {
    }

    /** A player head textured from a minecraft-heads base64 {@code value}, with name + lore. */
    public static ItemStack playerHead(String textureValue, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        applyTexture(stack, textureValue);
        applyDisplay(stack, name, lore);
        return stack;
    }

    /** A player head from a resolved/unresolved {@link GameProfile} (used by the player-name search). */
    public static ItemStack playerHead(GameProfile profile, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        boolean hasTextures = !profile.properties().get("textures").isEmpty();
        // Embed textures if we have them; otherwise resolve by UUID (the client fetches the skin).
        stack.set(DataComponents.PROFILE, hasTextures
                ? ResolvableProfile.createResolved(profile)
                : ResolvableProfile.createUnresolved(profile.id()));
        applyDisplay(stack, name, lore);
        return stack;
    }

    /** A generic icon stack (category buttons, nav). Falls back to paper if the id is unknown. */
    public static ItemStack icon(String itemId, Component name, List<Component> lore) {
        Item item = McItems.resolve(itemId).orElse(Items.PAPER);
        ItemStack stack = new ItemStack(item);
        applyDisplay(stack, name, lore);
        return stack;
    }

    private static void applyTexture(ItemStack stack, String textureValue) {
        stack.set(DataComponents.PROFILE, texturedProfile(textureValue));
    }

    /**
     * Build a resolved profile carrying a custom skin texture. The owner UUID is derived
     * <i>deterministically</i> from the texture so two heads with the same texture produce an
     * identical PROFILE component and therefore stack (up to the vanilla player-head max of 64);
     * a random UUID would make every head unique and unstackable. The 2-arg {@code GameProfile}
     * constructor yields an <i>immutable</i> property map, so we build a mutable one and use the
     * 3-arg constructor. Package-visible for unit testing (no registries needed).
     */
    static ResolvableProfile texturedProfile(String textureValue) {
        Multimap<String, Property> properties = LinkedHashMultimap.create();
        properties.put("textures", new Property("textures", textureValue));
        UUID owner = UUID.nameUUIDFromBytes(textureValue.getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(owner, "HeadVault", new PropertyMap(properties));
        return ResolvableProfile.createResolved(profile);
    }

    private static void applyDisplay(ItemStack stack, Component name, List<Component> lore) {
        if (name != null) {
            stack.set(DataComponents.CUSTOM_NAME, name);
        }
        if (lore != null && !lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
    }
}
