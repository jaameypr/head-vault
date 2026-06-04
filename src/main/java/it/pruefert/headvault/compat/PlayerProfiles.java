package it.pruefert.headvault.compat;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Resolves a Minecraft username to a textured {@link GameProfile}, off the server thread. The
 * profile-resolution API is version-sensitive (26.1 routes it through
 * {@link MinecraftServer#services()} → {@link ProfileResolver}), so it lives in {@code compat}.
 */
public final class PlayerProfiles {

    private static final ExecutorService LOOKUP = Executors.newSingleThreadExecutor(daemon());

    private PlayerProfiles() {
    }

    /**
     * Resolve {@code name} to a profile; the callback runs back on the server thread with the
     * resolved profile, or empty if the name is unknown/invalid.
     */
    public static void resolveAsync(MinecraftServer server, String name, Consumer<Optional<GameProfile>> callback) {
        LOOKUP.execute(() -> {
            Optional<GameProfile> result = Optional.empty();
            try {
                ProfileResolver resolver = server.services().profileResolver();
                GameProfile profile = ResolvableProfile.createUnresolved(name).resolveProfile(resolver).join();
                if (profile != null && profile.id() != null) {
                    result = Optional.of(profile);
                }
            } catch (Exception ignored) {
                // unknown name / lookup failure -> empty
            }
            Optional<GameProfile> resolved = result;
            server.execute(() -> callback.accept(resolved));
        });
    }

    private static java.util.concurrent.ThreadFactory daemon() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "HeadVault-profile-lookup-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
