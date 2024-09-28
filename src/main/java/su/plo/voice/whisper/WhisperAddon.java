package su.plo.voice.whisper;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import su.plo.config.provider.ConfigurationProvider;
import su.plo.config.provider.toml.TomlConfiguration;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.event.config.VoiceServerConfigReloadedEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@Addon(id = "pv-addon-whisper", scope = AddonLoaderScope.SERVER, version = BuildConstants.VERSION, authors = {"Apehum"})
public final class WhisperAddon implements AddonInitializer {

    private static final ConfigurationProvider toml = ConfigurationProvider.getProvider(TomlConfiguration.class);

    @InjectPlasmoVoice
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
            File addonFolder = new File(voiceServer.getMinecraftServer().getConfigsFolder(), "pv-addon-whisper");
            File configFile = new File(addonFolder, "config.toml");

            this.config = toml.load(WhisperConfig.class, configFile, false);
            addonFolder.mkdirs();
            toml.save(WhisperConfig.class, config, configFile);

            voiceServer.getLanguages().register(
                    URI.create("https://github.com/plasmoapp/plasmo-voice-crowdin/archive/refs/heads/addons.zip").toURL(),
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
