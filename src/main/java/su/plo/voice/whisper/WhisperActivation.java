package su.plo.voice.whisper;

import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import su.plo.lib.api.MathLib;
import su.plo.lib.api.server.permission.PermissionDefault;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.capture.ProximityServerActivationHelper;
import su.plo.voice.api.server.audio.capture.ServerActivation;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerPlayerSource;
import su.plo.voice.api.server.event.audio.capture.ServerActivationRegisterEvent;
import su.plo.voice.api.server.event.audio.capture.ServerActivationUnregisterEvent;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEndEvent;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.event.connection.UdpClientDisconnectedEvent;
import su.plo.voice.api.server.event.player.PlayerActivationDistanceUpdateEvent;
import su.plo.voice.api.server.player.VoiceServerPlayer;
import su.plo.voice.proto.data.audio.capture.VoiceActivation;
import su.plo.voice.proto.packets.tcp.serverbound.PlayerAudioEndPacket;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class WhisperActivation {

    private final PlasmoVoiceServer voiceServer;

    private final WhisperAddon addon;
    private final WhisperConfig config;

    private final Set<UUID> playerWhisperVisualized = Sets.newCopyOnWriteArraySet();

    private ProximityServerActivationHelper proximityHelper;

    public WhisperActivation(@NotNull PlasmoVoiceServer voiceServer,
                             @NotNull WhisperAddon addon) {
        this.voiceServer = voiceServer;
        this.addon = addon;
        this.config = addon.getConfig();
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onProximityRegister(@NotNull ServerActivationRegisterEvent event) {
        if (event.isCancelled()) return;

        ServerActivation activation = event.getActivation();
        if (!activation.getName().equals(VoiceActivation.PROXIMITY_NAME)) return;

        // register whisper
        ServerActivation.Builder builder = voiceServer.getActivationManager().createBuilder(
                addon,
                "whisper",
                "pv.activation.whisper",
                "plasmovoice:textures/icons/microphone_whisper.png",
                "pv.activation.whisper",
                config.activationWeight()
        );
        activation = builder
                .setProximity(true)
                .setTransitive(false)
                .setStereoSupported(false)
                .setPermissionDefault(PermissionDefault.TRUE)
                .build();

        ServerSourceLine sourceLine = voiceServer.getSourceLineManager().register(
                addon,
                "whisper",
                "pv.activation.whisper",
                "plasmovoice:textures/icons/speaker_whisper.png",
                config.sourceLineWeight(),
                false
        );

        this.proximityHelper = new ProximityServerActivationHelper(voiceServer, activation, sourceLine);
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onProximityUnregister(@NotNull ServerActivationUnregisterEvent event) {
        if (event.isCancelled()) return;

        ServerActivation activation = event.getActivation();
        if (!activation.getName().equals(VoiceActivation.PROXIMITY_NAME)) return;

        // unregister whisper
        voiceServer.getActivationManager().unregister(proximityHelper.getActivation());
        voiceServer.getSourceLineManager().unregister(proximityHelper.getSourceLine());
        this.proximityHelper = null;
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onProximityDistanceChanged(@NotNull PlayerActivationDistanceUpdateEvent event) {
        if (!event.getActivation().getId().equals(VoiceActivation.PROXIMITY_ID)) return;
        playerWhisperVisualized.remove(event.getPlayer().getInstance().getUUID());
    }

    @EventSubscribe
    public void onClientDisconnect(@NotNull UdpClientDisconnectedEvent event) {
        playerWhisperVisualized.remove(event.getConnection().getPlayer().getInstance().getUUID());
    }

    @EventSubscribe
    public void onPlayerSpeak(@NotNull PlayerSpeakEvent event) {
        if (proximityHelper == null) return;

        VoiceServerPlayer player = (VoiceServerPlayer) event.getPlayer();
        PlayerAudioPacket packet = event.getPacket();

        if (!proximityHelper.getActivation().checkPermissions(player)) return;

        ServerPlayerSource source = proximityHelper.getPlayerSource(player, packet.getActivationId(), packet.isStereo());
        if (source == null) return;

        short distance = calculateWhisperDistance(source.getPlayer());
        if (distance < 0) return;

        proximityHelper.sendAudioPacket(player, source, packet, distance);

        if (!playerWhisperVisualized.contains(player.getInstance().getUUID())) {
            playerWhisperVisualized.add(player.getInstance().getUUID());
            player.visualizeDistance(calculateWhisperDistance(player), config.visualizeDistanceHexColor());
        }
    }

    @EventSubscribe
    public void onPlayerSpeakEnd(@NotNull PlayerSpeakEndEvent event) {
        if (proximityHelper == null) return;

        VoiceServerPlayer player = (VoiceServerPlayer) event.getPlayer();
        PlayerAudioEndPacket packet = event.getPacket();

        if (!proximityHelper.getActivation().checkPermissions(player)) return;

        ServerPlayerSource source = proximityHelper.getPlayerSource(player, packet.getActivationId(), true);
        if (source == null) return;

        short distance = calculateWhisperDistance(source.getPlayer());
        if (distance < 0) return;

        proximityHelper.sendAudioEndPacket(source, packet, distance);
    }

    private short calculateWhisperDistance(@NotNull VoiceServerPlayer player) {
        Optional<ServerActivation> proximityActivation = voiceServer.getActivationManager()
                .getActivationById(VoiceActivation.PROXIMITY_ID);
        if (!proximityActivation.isPresent()) return -1;

        int proximityDistance = player.getActivationDistanceById(VoiceActivation.PROXIMITY_ID);
        if (proximityDistance < 0) {
            proximityDistance = proximityActivation.get().getDefaultDistance();
        }
        if (proximityDistance < 0) return -1;

        return (short) MathLib.clamp(
                (int) ((proximityDistance / 100F) * config.proximityPercent()),
                1,
                proximityActivation.get().getMaxDistance()
        );
    }
}
