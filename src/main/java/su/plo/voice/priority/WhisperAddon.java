package su.plo.voice.priority;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import su.plo.config.provider.ConfigurationProvider;
import su.plo.config.provider.toml.TomlConfiguration;
import su.plo.lib.api.MathLib;
import su.plo.lib.api.server.permission.PermissionDefault;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.capture.ServerActivation;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerPlayerSource;
import su.plo.voice.api.server.event.VoiceServerConfigLoadedEvent;
import su.plo.voice.api.server.event.VoiceServerInitializeEvent;
import su.plo.voice.api.server.event.audio.capture.ServerActivationRegisterEvent;
import su.plo.voice.api.server.event.audio.capture.ServerActivationUnregisterEvent;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEndEvent;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.event.connection.UdpDisconnectEvent;
import su.plo.voice.api.server.event.player.PlayerActivationDistanceUpdateEvent;
import su.plo.voice.api.server.player.VoicePlayer;
import su.plo.voice.proto.data.audio.capture.VoiceActivation;
import su.plo.voice.proto.data.audio.line.VoiceSourceLine;
import su.plo.voice.proto.packets.tcp.clientbound.SourceAudioEndPacket;
import su.plo.voice.proto.packets.tcp.serverbound.PlayerAudioEndPacket;
import su.plo.voice.proto.packets.udp.clientbound.SourceAudioPacket;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Addon(id = "whisper", scope = Addon.Scope.SERVER, version = "1.0.0", authors = {"Apehum"})
public final class WhisperAddon {

    private static final ConfigurationProvider toml = ConfigurationProvider.getProvider(TomlConfiguration.class);

    private static final String ACTIVATION_NAME = "whisper";
    private static final UUID ACTIVATION_ID = VoiceActivation.generateId(ACTIVATION_NAME);
    private static final UUID SOURCE_LINE_ID = VoiceSourceLine.generateId(ACTIVATION_NAME);

    private final Set<UUID> playerWhisperVisualized = Sets.newCopyOnWriteArraySet();

    private PlasmoVoiceServer voiceServer;
    private WhisperConfig config;

    @EventSubscribe
    public void onInitialize(@NotNull VoiceServerInitializeEvent event) {
        this.voiceServer = event.getServer();
    }

    @EventSubscribe
    public void onConfigLoaded(@NotNull VoiceServerConfigLoadedEvent event) {
        try {
            File addonFolder = new File(voiceServer.getConfigFolder(), "addons");
            File configFile = new File(addonFolder, "whisper.toml");

            this.config = toml.load(WhisperConfig.class, configFile, false);
            toml.save(WhisperConfig.class, config, configFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config", e);
        }
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onActivationRegister(@NotNull ServerActivationRegisterEvent event) {
        if (event.isCancelled()) return;

        ServerActivation activation = event.getActivation();
        if (!activation.getName().equals(ACTIVATION_NAME)) return;

        voiceServer.getMinecraftServer()
                .getPermissionsManager()
                .register("voice.activation." + activation.getName(), PermissionDefault.TRUE);
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onActivationUnregister(@NotNull ServerActivationUnregisterEvent event) {
        if (event.isCancelled()) return;

        ServerActivation activation = event.getActivation();
        if (!activation.getName().equals(ACTIVATION_NAME)) return;

        voiceServer.getMinecraftServer()
                .getPermissionsManager()
                .unregister("voice.activation." + activation.getName());
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onProximityRegister(@NotNull ServerActivationRegisterEvent event) {
        if (event.isCancelled()) return;

        ServerActivation activation = event.getActivation();
        if (!activation.getName().equals(VoiceActivation.PROXIMITY_NAME)) return;

        // register whisper
        voiceServer.getActivationManager().register(
                this,
                ACTIVATION_NAME,
                "activation.plasmovoice.whisper",
                "plasmovoice:textures/icons/microphone_whisper.png",
                ImmutableList.of(),
                0,
                false,
                false,
                config.getActivationWeight()
        );

        voiceServer.getSourceLineManager().register(
                this,
                ACTIVATION_NAME,
                "activation.plasmovoice.whisper",
                "plasmovoice:textures/icons/speaker_whisper.png",
                config.getSourceLineWeight()
        );
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onProximityUnregister(@NotNull ServerActivationUnregisterEvent event) {
        if (event.isCancelled()) return;

        ServerActivation activation = event.getActivation();
        if (!activation.getName().equals(VoiceActivation.PROXIMITY_NAME)) return;

        // unregister whisper
        voiceServer.getActivationManager().unregister(ACTIVATION_NAME);
        voiceServer.getSourceLineManager().unregister(ACTIVATION_NAME);
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onProximityDistanceChanged(@NotNull PlayerActivationDistanceUpdateEvent event) {
        if (!event.getActivation().getId().equals(VoiceActivation.PROXIMITY_ID)) return;
        playerWhisperVisualized.remove(event.getPlayer().getInstance().getUUID());
    }

    @EventSubscribe
    public void onClientDisconnect(@NotNull UdpDisconnectEvent event) {
        playerWhisperVisualized.remove(event.getConnection().getPlayer().getInstance().getUUID());
    }

    @EventSubscribe
    public void onPlayerSpeak(@NotNull PlayerSpeakEvent event) {
        VoicePlayer player = event.getPlayer();
        PlayerAudioPacket packet = event.getPacket();

        if (!player.getInstance().hasPermission("voice.activation." + ACTIVATION_NAME)) return;

        getPlayerSource(player, packet.getActivationId(), packet.isStereo()).ifPresent((source) -> {
            short distance = calculateWhisperDistance(source.getPlayer());
            if (distance < 0) return;

            SourceAudioPacket sourcePacket = new SourceAudioPacket(
                    packet.getSequenceNumber(),
                    (byte) source.getState(),
                    packet.getData(),
                    source.getId(),
                    distance
            );
            source.sendAudioPacket(sourcePacket, distance);

            if (!playerWhisperVisualized.contains(player.getInstance().getUUID())) {
                playerWhisperVisualized.add(player.getInstance().getUUID());
                player.visualizeDistance(calculateWhisperDistance(player), config.getVisualizeDistanceHexColor());
            }
        });
    }

    @EventSubscribe
    public void onPlayerSpeakEnd(@NotNull PlayerSpeakEndEvent event) {
        VoicePlayer player = event.getPlayer();
        PlayerAudioEndPacket packet = event.getPacket();

        if (!player.getInstance().hasPermission("voice.activation." + ACTIVATION_NAME)) return;

        getPlayerSource(player, packet.getActivationId(), true).ifPresent((source) -> {
            short distance = calculateWhisperDistance(source.getPlayer());
            if (distance < 0) return;

            SourceAudioEndPacket sourcePacket = new SourceAudioEndPacket(source.getId(), packet.getSequenceNumber());
            source.sendPacket(sourcePacket, distance);
        });
    }

    private short calculateWhisperDistance(@NotNull VoicePlayer player) {
        Optional<ServerActivation> proximityActivation = voiceServer.getActivationManager()
                .getActivationById(VoiceActivation.PROXIMITY_ID);
        if (!proximityActivation.isPresent()) return -1;

        int proximityDistance = player.getActivationDistanceById(VoiceActivation.PROXIMITY_ID);
        if (proximityDistance < 0) {
            proximityDistance = proximityActivation.get().getDefaultDistance();
        }
        if (proximityDistance < 0) return -1;

        return (short) MathLib.clamp(
                (int) ((proximityDistance / 100F) * config.getProximityPercent()),
                1,
                proximityActivation.get().getMaxDistance()
        );
    }

    private Optional<ServerPlayerSource> getPlayerSource(@NotNull VoicePlayer player,
                                                         @NotNull UUID activationId,
                                                         boolean isStereo) {
        if (!activationId.equals(ACTIVATION_ID)) return Optional.empty();

        Optional<ServerActivation> activation = voiceServer.getActivationManager()
                .getActivationById(activationId);
        if (!activation.isPresent()) return Optional.empty();

        Optional<ServerSourceLine> sourceLine = voiceServer.getSourceLineManager()
                .getLineById(SOURCE_LINE_ID);
        if (!sourceLine.isPresent()) return Optional.empty();

        isStereo = isStereo && activation.get().isStereoSupported();
        ServerPlayerSource source = voiceServer.getSourceManager().createPlayerSource(
                voiceServer,
                player,
                sourceLine.get(),
                "opus",
                isStereo
        );
        source.setLine(sourceLine.get());
        source.setStereo(isStereo);

        return Optional.of(source);
    }
}
