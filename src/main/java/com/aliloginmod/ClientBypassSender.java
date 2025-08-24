package com.aliloginmod;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = AliLoginMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientBypassSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientBypassSender.class);
    
    static {
        LOGGER.info("ClientBypassSender carregado no lado do cliente!");
    }
    
    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        LOGGER.info("AliLoginMod: evento LoggingIn disparado");
        
        // Usar configuração do cliente em vez da configuração do servidor
        if (!ClientConfig.allowClientBypass) {
            LOGGER.info("AliLoginMod: allowClientBypass está desabilitado na configuração do cliente");
            return;
        }
        
        if (ClientConfig.sharedSecret.isEmpty()) {
            LOGGER.warn("AliLoginMod: sharedSecret está vazio na configuração do cliente");
            return;
        }
        
        try {
            long timestamp = System.currentTimeMillis();
            String playerName = Minecraft.getInstance().getUser().getName();
            String signature = generateSignature(playerName, timestamp, ClientConfig.sharedSecret);
            
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ClientBypassLoginMessage(timestamp, signature));
            LOGGER.info("AliLoginMod: handshake de auto-login enviado");
        } catch (Exception e) {
            LOGGER.error("AliLoginMod: erro ao enviar handshake de auto-login", e);
        }
    }
    
    // ← MÉTODO QUE ESTAVA FALTANDO
    private static String generateSignature(String playerName, long timestamp, String sharedSecret) {
        String data = playerName.toLowerCase(Locale.ROOT) + ":" + timestamp + ":" + sharedSecret;
        return sha256Hex(data);
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
            LOGGER.error("Erro SHA-256 no cliente: ", e);
            return "";
        }
    }
}