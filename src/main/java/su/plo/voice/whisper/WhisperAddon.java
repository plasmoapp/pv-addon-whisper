package su.plo.voice.whisper;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import su.plo.config.provider.ConfigurationProvider;
import su.plo.config.provider.toml.TomlConfiguration;
import su.plo.voice.api.addon.AddonScope;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.event.config.VoiceServerConfigLoadedEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Addon(id = "whisper", scope = AddonScope.SERVER, version = "1.0.0", authors = {"Apehum"})
public final class WhisperAddon {

    private static final ConfigurationProvider toml = ConfigurationProvider.getProvider(TomlConfiguration.class);

    @Getter
    private WhisperConfig config;

    @EventSubscribe
    public void onConfigLoaded(@NotNull VoiceServerConfigLoadedEvent event) {
        PlasmoVoiceServer voiceServer = event.getServer();

        try {
            File addonFolder = new File(voiceServer.getConfigFolder(), "addons/whisper");
            File configFile = new File(addonFolder, "whisper.toml");

            this.config = toml.load(WhisperConfig.class, configFile, false);
            addonFolder.mkdirs();
            toml.save(WhisperConfig.class, config, configFile);

            voiceServer.getLanguages().register(
                    this::getLanguageResource,
                    new File(addonFolder, "languages")
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config", e);
        }

        WhisperActivation activation = new WhisperActivation(voiceServer, this);
        voiceServer.getEventBus().register(this, activation);
    }

    private InputStream getLanguageResource(@NotNull String resourcePath) throws IOException {
        return getClass().getClassLoader().getResourceAsStream(String.format("whisper/%s", resourcePath));
    }
}
