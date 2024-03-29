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
import su.plo.voice.api.server.event.audio.capture.ServerActivationRegisterEvent;
import su.plo.voice.api.server.event.audio.capture.ServerActivationUnregisterEvent;
import su.plo.voice.api.server.event.connection.UdpClientDisconnectedEvent;
import su.plo.voice.api.server.event.player.PlayerActivationDistanceUpdateEvent;
import su.plo.voice.api.server.player.VoicePlayer;
import su.plo.voice.api.server.player.VoiceServerPlayer;
import su.plo.voice.proto.data.audio.capture.VoiceActivation;
import su.plo.voice.proto.packets.tcp.serverbound.PlayerAudioEndPacket;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class WhisperActivation {

    private static final String ACTIVATION_NAME = "whisper";

    private final PlasmoVoiceServer voiceServer;

    private final WhisperAddon addon;

    private final Set<UUID> playerWhisperVisualized = Sets.newCopyOnWriteArraySet();

    private ProximityServerActivationHelper proximityHelper;

    public WhisperActivation(@NotNull PlasmoVoiceServer voiceServer,
                             @NotNull WhisperAddon addon) {
        this.voiceServer = voiceServer;
        this.addon = addon;
    }

    public void register() {
        unregister();

        WhisperConfig config = addon.getConfig();

        ServerActivation.Builder builder = voiceServer.getActivationManager().createBuilder(
                addon,
                ACTIVATION_NAME,
                "pv.activation.whisper",
                "plasmovoice:textures/icons/microphone_whisper.png",
                "pv.activation.whisper",
                config.activationWeight()
        );
        ServerActivation activation = builder
                .setProximity(true)
                .setTransitive(false)
                .setStereoSupported(false)
                .setPermissionDefault(PermissionDefault.TRUE)
                .setRequirements(new ServerActivation.Requirements() {

                    @Override
                    public boolean checkRequirements(@NotNull VoicePlayer player, @NotNull PlayerAudioPacket packet) {
                        return calculateWhisperDistance((VoiceServerPlayer) player) > 0;
                    }

                    @Override
                    public boolean checkRequirements(@NotNull VoicePlayer player, @NotNull PlayerAudioEndPacket packet) {
                        return calculateWhisperDistance((VoiceServerPlayer) player) > 0;
                    }
                })
                .build();

        ServerSourceLine sourceLine = voiceServer.getSourceLineManager().createBuilder(
                addon,
                ACTIVATION_NAME,
                "pv.activation.whisper",
                "plasmovoice:textures/icons/speaker_whisper.png",
                config.sourceLineWeight()
        ).build();

        activation.onPlayerActivationStart(this::onActivationStart);

        if (proximityHelper != null) voiceServer.getEventBus().unregister(addon, proximityHelper);

        this.proximityHelper = new ProximityServerActivationHelper(
                voiceServer,
                activation,
                sourceLine,
                new ProximityServerActivationHelper.DistanceSupplier() {

                    @Override
                    public short getDistance(@NotNull VoiceServerPlayer player, @NotNull PlayerAudioPacket packet) {
                        return calculateWhisperDistance(player);
                    }

                    @Override
                    public short getDistance(@NotNull VoiceServerPlayer player, @NotNull PlayerAudioEndPacket packet) {
                        return calculateWhisperDistance(player);
                    }
                }
        );
        voiceServer.getEventBus().register(addon, proximityHelper);
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onProximityRegister(@NotNull ServerActivationRegisterEvent event) {
        if (event.isCancelled()) return;

        ServerActivation activation = event.getActivation();
        if (!activation.getName().equals(VoiceActivation.PROXIMITY_NAME)) return;

        register();
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onProximityUnregister(@NotNull ServerActivationUnregisterEvent event) {
        if (event.isCancelled()) return;

        ServerActivation activation = event.getActivation();
        if (!activation.getName().equals(VoiceActivation.PROXIMITY_NAME)) return;

        unregister();
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

    private void unregister() {
        voiceServer.getActivationManager().unregister(ACTIVATION_NAME);
        voiceServer.getSourceLineManager().unregister(ACTIVATION_NAME);

        if (proximityHelper == null) return;

        voiceServer.getEventBus().unregister(addon, proximityHelper);
        this.proximityHelper = null;
    }

    private void onActivationStart(@NotNull VoicePlayer player) {
        if (!playerWhisperVisualized.contains(player.getInstance().getUUID())) {
            playerWhisperVisualized.add(player.getInstance().getUUID());
            player.visualizeDistance(
                    calculateWhisperDistance((VoiceServerPlayer) player),
                    addon.getConfig().visualizeDistanceHexColor()
            );
        }
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
                (int) ((proximityDistance / 100F) * addon.getConfig().proximityPercent()),
                1,
                proximityActivation.get().getMaxDistance()
        );
    }
}
