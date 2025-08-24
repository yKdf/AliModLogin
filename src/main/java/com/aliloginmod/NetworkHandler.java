package com.aliloginmod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

public class NetworkHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkHandler.class);
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(AliLoginMod.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(s -> true)
            .serverAcceptedVersions(s -> true) // ← Também aceita qualquer servidor
            .simpleChannel();

    private static int packetId = 0;

    public static void register() {
        CHANNEL.messageBuilder(ClientBypassLoginMessage.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientBypassLoginMessage::encode)
                .decoder(ClientBypassLoginMessage::decode)
                .consumerMainThread(ClientBypassLoginMessage::handle)
                .add();
        LOGGER.info("Canal de rede do AliLoginMod registrado.");
    }

    // Pacote enviado pelo cliente para solicitar auto-login
    public static class ClientBypassLoginMessage {
        public long timestamp;
        public String signature;

        public ClientBypassLoginMessage() {}

        public ClientBypassLoginMessage(long timestamp, String signature) {
            this.timestamp = timestamp;
            this.signature = signature;
        }

        public static void encode(ClientBypassLoginMessage msg, FriendlyByteBuf buf) {
            buf.writeLong(msg.timestamp);
            buf.writeUtf(msg.signature == null ? "" : msg.signature);
        }

        public static ClientBypassLoginMessage decode(FriendlyByteBuf buf) {
            ClientBypassLoginMessage msg = new ClientBypassLoginMessage();
            msg.timestamp = buf.readLong();
            msg.signature = buf.readUtf(32767);
            return msg;
        }

        public static void handle(ClientBypassLoginMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            if (ctx.getDirection() != NetworkDirection.PLAY_TO_SERVER) {
                ctx.setPacketHandled(true);
                return;
            }

            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) {
                    return;
                }

                // Log para diagnosticar recebimento
                LOGGER.info("AliLoginMod: handshake recebido do cliente {} (ts={})", player.getGameProfile().getName(), msg.timestamp);

                try {
                    if (!Config.allowClientBypass) {
                        LOGGER.debug("Bypass desativado por configuração. Ignorando handshake de {}", player.getGameProfile().getName());
                        return;
                    }
                    if (Config.sharedSecret == null || Config.sharedSecret.isBlank()) {
                        LOGGER.warn("sharedSecret não definido. Auto-login via cliente bloqueado por segurança.");
                        return;
                    }

                    long now = System.currentTimeMillis();
                    if (Math.abs(now - msg.timestamp) > Duration.ofSeconds(60).toMillis()) {
                        LOGGER.warn("Handshake expirado para {} (timestamp inválido).", player.getGameProfile().getName());
                        return;
                    }

                    String username = player.getGameProfile().getName();
                    String expectedSig = sha256Hex(username.toLowerCase(Locale.ROOT) + ":" + msg.timestamp + ":" + Config.sharedSecret);

                    if (!Objects.equals(expectedSig, msg.signature)) {
                        LOGGER.warn("Assinatura inválida no handshake de {}. Bypass negado.", username);
                        return;
                    }

                    if (UserDataManager.isPlayerLoggedIn(player)) {
                        LOGGER.debug("Jogador {} já está logado. Ignorando bypass.", username);
                        return;
                    }

                    if (!UserDataManager.isUserRegistered(username)) {
                        player.sendSystemMessage(Component.literal("§cVocê não está registrado! Use /registrar <senha>."));
                        return;
                    }

                    UserDataManager.loginPlayer(player);
                    SessionManager.cancelLoginTimeout(player.getUUID());
                    SessionManager.clearLoginAttempts(player.getUUID());
                    MessageManager.stopLoginReminders(player.getUUID());

                    if (UserDataManager.hasPlayerPosition(username)) {
                        UserDataManager.teleportPlayerToLastPosition(player);
                    }

                    MessageManager.sendLoginSuccessMessage(player, false);
                    LOGGER.info("Auto-login por handshake realizado com sucesso para {}", username);

                } catch (Exception e) {
                    LOGGER.error("Erro ao processar handshake de auto-login: ", e);
                }
            });

            ctx.setPacketHandled(true);
        }

        private static String sha256Hex(String input) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) {
                    String h = Integer.toHexString(0xff & b);
                    if (h.length() == 1) hex.append('0');
                    hex.append(h);
                }
                return hex.toString();
            } catch (Exception e) {
                LOGGER.error("Erro ao gerar hash SHA-256: ", e);
                return "";
            }
        }
    }
}