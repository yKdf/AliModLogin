package com.aliloginmod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;

import net.minecraftforge.event.entity.player.*;

import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.CommandEvent;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = AliLoginMod.MODID)
public class PlayerRestrictionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerRestrictionHandler.class);
    
    // Mapa para armazenar posições iniciais dos jogadores não logados
    private static final Map<UUID, Vec3> playerInitialPositions = new HashMap<>();
    
    // Mapa para armazenar o modo de jogo original dos jogadores
    private static final Map<UUID, GameType> playerOriginalGameMode = new HashMap<>();
    
    // Contador para reduzir frequência de teleporte
    private static final Map<UUID, Integer> teleportCooldown = new HashMap<>();
    
    /**
     * Monitora jogadores e coloca em modo espectador imóvel se não estiverem logados
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        
        UUID playerId = player.getUUID();
        
        if (!UserDataManager.isPlayerLoggedIn(player)) {
            // Se não está em modo espectador, salva o modo atual e muda para espectador
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                // Salva o modo de jogo original se ainda não foi salvo
                if (!playerOriginalGameMode.containsKey(playerId)) {
                    playerOriginalGameMode.put(playerId, player.gameMode.getGameModeForPlayer());
                }
                
                // Salva posição inicial se ainda não foi salva
                if (!playerInitialPositions.containsKey(playerId)) {
                    playerInitialPositions.put(playerId, player.position());
                }
                
                // Muda para modo espectador
                player.setGameMode(GameType.SPECTATOR);
                LOGGER.debug("Jogador {} colocado em modo espectador (não logado)", player.getName().getString());
            }
            
            // Mantém o jogador na posição inicial (imóvel)
            Vec3 initialPos = playerInitialPositions.get(playerId);
            if (initialPos != null) {
                Vec3 currentPos = player.position();
                double distance = currentPos.distanceTo(initialPos);
                if (distance > 0.1) {
                    // Teleporta de volta para posição inicial
                    player.teleportTo(player.serverLevel(), initialPos.x, initialPos.y, initialPos.z, player.getYRot(), player.getXRot());
                }
            }
            
            // Envia lembrete de login periodicamente
            int cooldown = teleportCooldown.getOrDefault(playerId, 0);
            if (cooldown <= 0) {
                sendLoginReminder(player);
                teleportCooldown.put(playerId, 100); // 5 segundos de cooldown para mensagens
            } else {
                teleportCooldown.put(playerId, cooldown - 1);
            }
        } else {
            // Jogador logado - restaura modo de jogo original e remove restrições
            GameType originalMode = playerOriginalGameMode.get(playerId);
            if (originalMode != null && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                player.setGameMode(originalMode);
                LOGGER.debug("Jogador {} modo de jogo restaurado para: {}", player.getName().getString(), originalMode);
            }
            
            // Remove da lista de restrições
            playerInitialPositions.remove(playerId);
            playerOriginalGameMode.remove(playerId);
            teleportCooldown.remove(playerId);
        }
    }
    
    /**
     * Bloqueia mudança de dimensão
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        if (!UserDataManager.isPlayerLoggedIn(player)) {
            event.setCanceled(true);
            sendLoginReminder(player);
        }
    }
    /**
     * Bloqueia uso de comandos (exceto os de login)
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerCommand(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        // Envia mensagem de boas-vindas e instruções
        if (!UserDataManager.isPlayerLoggedIn(player)) {
            MessageManager.sendWelcomeMessage(player);
            MessageManager.startLoginReminders(player);
        }
    }
    
    /**
     * Bloqueia comandos não relacionados ao login
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerCommand(CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)) return;
        
        String command = event.getParseResults().getReader().getString();
        
        // Permite comandos de login
        if (command.startsWith("logar") || command.startsWith("registrar") || command.startsWith("logout")) {
            return;
        }
        
        if (!UserDataManager.isPlayerLoggedIn(player)) {
            event.setCanceled(true);
            sendLoginReminder(player);
        }
    }
    
    /**
     * Impede que jogadores não logados usem comandos não relacionados ao login via chat
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getMessage().getString();
        
        // Permite comandos de login
        if (message.startsWith("/logar") || message.startsWith("/registrar") || message.startsWith("/logout")) {
            return;
        }
        
        if (!UserDataManager.isPlayerLoggedIn(player)) {
            if (message.startsWith("/")) {
                event.setCanceled(true);
                sendLoginReminder(player);
            } else {
                // Bloqueia chat normal também
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVocê precisa fazer login para usar o chat!"));
            }
        }
    }
    
    /**
     * Envia lembrete de login para o jogador
     */
    private static void sendLoginReminder(ServerPlayer player) {
        MessageManager.sendQuickLoginReminder(player);
    }
    
    /**
     * Teleporta jogador para spawn quando entra no servidor (se não estiver logado)
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        if (!UserDataManager.isPlayerLoggedIn(player)) {
            // Teleporta para o spawn do mundo
            var level = player.serverLevel();
            var spawnPos = level.getSharedSpawnPos();
            player.teleportTo(level, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
            
            LOGGER.info("Jogador {} teleportado para spawn (não logado)", player.getName().getString());
        }
    }
    
    /**
     * Limpa dados quando jogador sai do servidor
     */
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        UUID playerId = player.getUUID();
        playerInitialPositions.remove(playerId);
        teleportCooldown.remove(playerId);
        
        LOGGER.debug("Dados de restrição removidos para jogador {}", player.getName().getString());
    }
}