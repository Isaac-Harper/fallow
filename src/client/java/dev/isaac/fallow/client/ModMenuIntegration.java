package dev.isaac.fallow.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Registers the {@link ConfigScreen} with Mod Menu via the {@code modmenu} entrypoint. Loaded
 * only when Mod Menu is installed, so the mod has no hard dependency on it.
 */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
