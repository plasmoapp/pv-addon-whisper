package su.plo.voice.whisper;

import com.google.inject.Inject;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import su.plo.config.provider.ConfigurationProvider;
import su.plo.config.provider.toml.TomlConfiguration;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.event.config.VoiceServerConfigReloadedEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Addon(id = "pv-addon-whisper", scope = AddonLoaderScope.SERVER, version = "1.0.0", authors = {"Apehum"})
public final class WhisperAddon implements AddonInitializer {

    private static final ConfigurationProvider toml = ConfigurationProvider.getProvider(TomlConfiguration.class);

    @Inject
    private PlasmoVoiceServer voiceServer;

    @Getter
    private WhisperConfig config;

    private WhisperActivation activation;

    @Override
    public void onAddonInitialize() {
        reloadConfig();
    }

    @EventSubscribe
    public void onConfigReloaded(@NotNull VoiceServerConfigReloadedEvent event) {
        reloadConfig();
    }

    private void reloadConfig() {
        try {
            File addonFolder = new File(voiceServer.getConfigsFolder(), "pv-addon-whisper");
            File configFile = new File(addonFolder, "config.toml");

            this.config = toml.load(WhisperConfig.class, configFile, false);
            addonFolder.mkdirs();
            toml.save(WhisperConfig.class, config, configFile);

            voiceServer.getLanguages().register(
                    "plasmo-voice-addons",
                    "server/whisper.toml",
                    this::getLanguageResource,
                    new File(addonFolder, "languages")
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config", e);
        }

        if (activation == null) {
            this.activation = new WhisperActivation(voiceServer, this);
            voiceServer.getEventBus().register(this, activation);
        }

        activation.register();
    }

    private InputStream getLanguageResource(@NotNull String resourcePath) throws IOException {
        return getClass().getClassLoader().getResourceAsStream(String.format("whisper/%s", resourcePath));
    }
}
