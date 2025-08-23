package com.aliloginmod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Mod.EventBusSubscriber(modid = AliLoginMod.MODID)
public class SessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);
    
    // Tempo limite para login em segundos (5 minutos)
    private static final int LOGIN_TIMEOUT_SECONDS = 300;
    
    // Executor para tarefas agendadas
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // Mapa de jogadores e suas tarefas de timeout
    private static final Map<UUID, ScheduledFuture<?>> loginTimeouts = new HashMap<>();
    
    // Mapa de tentativas de login por jogador
    private static final Map<UUID, Integer> loginAttempts = new HashMap<>();
    
    // Máximo de tentativas de login
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    
    /**
     * Evento quando jogador entra no servidor
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        String username = player.getName().getString();
        UUID playerId = player.getUUID();
        
        LOGGER.info("Jogador {} ({}) entrou no servidor", username, playerId);
        
        // Remove tentativas anteriores se existirem
        loginAttempts.remove(playerId);
        
        // Se o jogador não estiver logado, inicia o timeout
        if (!UserDataManager.isPlayerLoggedIn(player)) {
            startLoginTimeout(player);
        } else {
            LOGGER.info("Jogador {} já estava logado", username);
        }
    }
    
    /**
     * Evento quando jogador sai do servidor
     */
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        String username = player.getName().getString();
        UUID playerId = player.getUUID();
        
        LOGGER.info("Jogador {} ({}) saiu do servidor", username, playerId);
        
        // Salva a posição do jogador se ele estiver logado
        if (UserDataManager.isPlayerLoggedIn(player)) {
            UserDataManager.savePlayerPosition(player);
        }
        
        // Remove o jogador da lista de logados
        UserDataManager.logoutPlayer(player);
        
        // Cancela timeout se existir
        cancelLoginTimeout(playerId);
        
        // Remove tentativas de login
        loginAttempts.remove(playerId);
        
        // Para lembretes de mensagens
        MessageManager.stopLoginReminders(playerId);
        
        // Salva os dados
        UserDataManager.saveUserData();
    }
    
    /**
     * Evento quando servidor está parando
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Servidor parando - salvando dados e limpando sessões");
        
        // Cancela todos os timeouts
        loginTimeouts.values().forEach(future -> future.cancel(true));
        loginTimeouts.clear();
        
        // Limpa tentativas de login
        loginAttempts.clear();
        
        // Salva dados de usuários
        UserDataManager.saveUserData();
        
        // Para o MessageManager
        MessageManager.shutdown();
        
        // Para o scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Inicia o timeout de login para um jogador
     */
    public static void startLoginTimeout(ServerPlayer player) {
        UUID playerId = player.getUUID();
        String username = player.getName().getString();
        
        // Cancela timeout anterior se existir
        cancelLoginTimeout(playerId);
        
        // Cria nova tarefa de timeout
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            try {
                // Verifica se o jogador ainda está online e não logado
                if (player.isRemoved() || UserDataManager.isPlayerLoggedIn(player)) {
                    return;
                }
                
                LOGGER.info("Timeout de login para jogador {}", username);
                
                // Kick do jogador por timeout
                player.connection.disconnect(Component.literal(
                    "§cTempo limite para login esgotado!\n" +
                    "§7Você tinha " + LOGIN_TIMEOUT_SECONDS + " segundos para fazer login.\n" +
                    "§7Reconecte e faça login mais rapidamente."
                ));
                
            } catch (Exception e) {
                LOGGER.error("Erro ao processar timeout de login para {}", username, e);
            } finally {
                // Remove da lista de timeouts
                loginTimeouts.remove(playerId);
            }
        }, LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        // Armazena a tarefa
        loginTimeouts.put(playerId, timeoutTask);
        
        LOGGER.info("Timeout de login iniciado para {} ({} segundos)", username, LOGIN_TIMEOUT_SECONDS);
    }
    
    /**
     * Cancela o timeout de login para um jogador
     */
    public static void cancelLoginTimeout(UUID playerId) {
        ScheduledFuture<?> timeoutTask = loginTimeouts.remove(playerId);
        if (timeoutTask != null && !timeoutTask.isDone()) {
            timeoutTask.cancel(true);
            LOGGER.debug("Timeout de login cancelado para jogador {}", playerId);
        }
    }
    
    /**
     * Registra uma tentativa de login falhada
     */
    public static void recordFailedLoginAttempt(ServerPlayer player) {
        UUID playerId = player.getUUID();
        int attempts = loginAttempts.getOrDefault(playerId, 0) + 1;
        loginAttempts.put(playerId, attempts);
        
        LOGGER.info("Tentativa de login falhada para {} (tentativa {}/{})", 
                   player.getName().getString(), attempts, MAX_LOGIN_ATTEMPTS);
        
        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            LOGGER.warn("Jogador {} excedeu o limite de tentativas de login", player.getName().getString());
            
            player.connection.disconnect(Component.literal(
                "§cMuitas tentativas de login falhadas!\n" +
                "§7Você excedeu o limite de " + MAX_LOGIN_ATTEMPTS + " tentativas.\n" +
                "§7Aguarde alguns minutos antes de tentar novamente."
            ));
        }
    }
    
    /**
     * Limpa tentativas de login para um jogador (após login bem-sucedido)
     */
    public static void clearLoginAttempts(UUID playerId) {
        loginAttempts.remove(playerId);
    }
    
    /**
     * Verifica se um jogador pode tentar fazer login
     */
    public static boolean canAttemptLogin(UUID playerId) {
        return loginAttempts.getOrDefault(playerId, 0) < MAX_LOGIN_ATTEMPTS;
    }
    
    // Método removido - funcionalidade movida para MessageManager
    
    /**
     * Obtém o número de tentativas de login para um jogador
     */
    public static int getLoginAttempts(UUID playerId) {
        return loginAttempts.getOrDefault(playerId, 0);
    }
    
    /**
     * Obtém o número máximo de tentativas permitidas
     */
    public static int getMaxLoginAttempts() {
        return MAX_LOGIN_ATTEMPTS;
    }
}