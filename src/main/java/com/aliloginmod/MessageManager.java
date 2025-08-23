package com.aliloginmod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MessageManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageManager.class);
    
    // Executor para mensagens agendadas
    private static final ScheduledExecutorService messageScheduler = Executors.newScheduledThreadPool(1);
    
    // Mapa de tarefas de mensagens por jogador
    private static final Map<UUID, ScheduledFuture<?>> messageTasks = new HashMap<>();
    
    // Intervalos de tempo para lembretes (em segundos)
    private static final int[] REMINDER_INTERVALS = {30, 60, 120, 180, 240};
    
    /**
     * Inicia mensagens periódicas para um jogador não logado
     */
    public static void startLoginReminders(ServerPlayer player) {
        UUID playerId = player.getUUID();
        String username = player.getName().getString();
        
        // Cancela mensagens anteriores se existirem
        stopLoginReminders(playerId);
        
        // Agenda mensagens em intervalos específicos
        for (int interval : REMINDER_INTERVALS) {
            messageScheduler.schedule(() -> {
                try {
                    // Verifica se o jogador ainda está online e não logado
                    if (player.isRemoved() || UserDataManager.isPlayerLoggedIn(player)) {
                        return;
                    }
                    
                    sendLoginReminder(player, interval);
                    
                } catch (Exception e) {
                    LOGGER.error("Erro ao enviar lembrete para {}", username, e);
                }
            }, interval, TimeUnit.SECONDS);
        }
        
        LOGGER.debug("Lembretes de login iniciados para {}", username);
    }
    
    /**
     * Para mensagens periódicas para um jogador
     */
    public static void stopLoginReminders(UUID playerId) {
        ScheduledFuture<?> task = messageTasks.remove(playerId);
        if (task != null && !task.isDone()) {
            task.cancel(true);
            LOGGER.debug("Lembretes de login cancelados para jogador {}", playerId);
        }
    }
    
    /**
     * Envia lembrete de login personalizado baseado no tempo
     */
    private static void sendLoginReminder(ServerPlayer player, int secondsElapsed) {
        String username = player.getName().getString();
        boolean isRegistered = UserDataManager.isUserRegistered(username);
        
        // Calcula tempo restante (assumindo 5 minutos de timeout)
        int remainingTime = 300 - secondsElapsed;
        
        if (remainingTime <= 0) {
            return; // Timeout já deve ter ocorrido
        }
        
        // Mensagens baseadas no tempo decorrido
        switch (secondsElapsed) {
            case 30:
                player.sendSystemMessage(Component.literal("§e⏰ Lembrete: Você ainda não fez login!"));
                if (isRegistered) {
                    player.sendSystemMessage(Component.literal("§7Use: §a/logar <sua_senha>"));
                } else {
                    player.sendSystemMessage(Component.literal("§7Use: §a/registrar <nova_senha>"));
                }
                break;
                
            case 60:
                player.sendSystemMessage(Component.literal("§6⚠ Você tem " + formatTime(remainingTime) + " para fazer login!"));
                sendLoginInstructions(player, isRegistered);
                break;
                
            case 120:
                player.sendSystemMessage(Component.literal("§c⚠ ATENÇÃO: Apenas " + formatTime(remainingTime) + " restantes!"));
                if (isRegistered) {
                    player.sendSystemMessage(Component.literal("§7Esqueceu a senha? Contate um administrador."));
                }
                break;
                
            case 180:
                player.sendSystemMessage(Component.literal("§c🚨 URGENTE: " + formatTime(remainingTime) + " para login!"));
                player.sendSystemMessage(Component.literal("§cVocê será desconectado em breve!"));
                break;
                
            case 240:
                player.sendSystemMessage(Component.literal("§4🚨 ÚLTIMO AVISO: " + formatTime(remainingTime) + " restantes!"));
                player.sendSystemMessage(Component.literal("§4Faça login AGORA ou será desconectado!"));
                sendLoginInstructions(player, isRegistered);
                break;
        }
    }
    
    /**
     * Envia instruções de login
     */
    private static void sendLoginInstructions(ServerPlayer player, boolean isRegistered) {
        if (isRegistered) {
            player.sendSystemMessage(Component.literal("§7Digite: §a/logar <sua_senha>"));
        } else {
            player.sendSystemMessage(Component.literal("§7Digite: §a/registrar <nova_senha>"));
            player.sendSystemMessage(Component.literal("§7Escolha uma senha segura!"));
        }
    }
    
    /**
     * Envia mensagem de boas-vindas completa
     */
    public static void sendWelcomeMessage(ServerPlayer player) {
        String username = player.getName().getString();
        boolean isRegistered = UserDataManager.isUserRegistered(username);
        
        // Cabeçalho
        player.sendSystemMessage(Component.literal("§6§l=================================="));
        player.sendSystemMessage(Component.literal("§6§l        AliLoginMod v1.0"));
        player.sendSystemMessage(Component.literal("§6§l=================================="));
        player.sendSystemMessage(Component.literal(""));
        
        if (isRegistered) {
            player.sendSystemMessage(Component.literal("§eBem-vindo de volta, §a" + username + "§e!"));
            player.sendSystemMessage(Component.literal("§7Faça login para continuar:"));
            player.sendSystemMessage(Component.literal("§a/logar <sua_senha>"));
        } else {
            player.sendSystemMessage(Component.literal("§eBem-vindo ao servidor, §a" + username + "§e!"));
            player.sendSystemMessage(Component.literal("§7Você precisa se registrar primeiro:"));
            player.sendSystemMessage(Component.literal("§a/registrar <nova_senha>"));
            player.sendSystemMessage(Component.literal("§7§oEscolha uma senha que você lembre!"));
        }
        
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§c⚠ IMPORTANTE:"));
        player.sendSystemMessage(Component.literal("§7• Você não pode se mover até fazer login"));
        player.sendSystemMessage(Component.literal("§7• Você não pode interagir com o mundo"));
        player.sendSystemMessage(Component.literal("§7• Você tem §c5 minutos§7 para fazer login"));
        player.sendSystemMessage(Component.literal("§7• Máximo de §c5 tentativas§7 de login"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§6§l=================================="));
    }
    
    /**
     * Envia mensagem de sucesso no login/registro
     */
    public static void sendLoginSuccessMessage(ServerPlayer player, boolean isNewRegistration) {
        String username = player.getName().getString();
        
        player.sendSystemMessage(Component.literal("§a§l✓ " + (isNewRegistration ? "REGISTRO" : "LOGIN") + " REALIZADO COM SUCESSO!"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§eBem-vindo" + (isNewRegistration ? "" : " de volta") + ", §a" + username + "§e!"));
        player.sendSystemMessage(Component.literal("§7Agora você pode:"));
        player.sendSystemMessage(Component.literal("§7• Se mover livremente"));
        player.sendSystemMessage(Component.literal("§7• Interagir com o mundo"));
        player.sendSystemMessage(Component.literal("§7• Usar todos os comandos"));
        player.sendSystemMessage(Component.literal("§7• Conversar no chat"));
        
        if (isNewRegistration) {
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§6💡 Dica: Lembre-se de sua senha para próximos logins!"));
        }
        
    }
    
    /**
     * Envia mensagem de erro de login
     */
    public static void sendLoginErrorMessage(ServerPlayer player, int attempts, int maxAttempts) {
        player.sendSystemMessage(Component.literal("§c✗ Senha incorreta!"));
        player.sendSystemMessage(Component.literal("§7Tentativas: §c" + attempts + "§7/§c" + maxAttempts));
        
        int remaining = maxAttempts - attempts;
        if (remaining <= 2) {
            player.sendSystemMessage(Component.literal("§c⚠ Cuidado! Apenas " + remaining + " tentativa(s) restante(s)!"));
        }
        
        if (remaining > 0) {
            player.sendSystemMessage(Component.literal("§7Tente novamente: §a/logar <senha>"));
        }
    }
    
    /**
     * Envia mensagem de logout
     */
    public static void sendLogoutMessage(ServerPlayer player) {
        String username = player.getName().getString();
        
        player.sendSystemMessage(Component.literal("§7§l✓ Logout realizado com sucesso!"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§7Até logo, §e" + username + "§7!"));
        player.sendSystemMessage(Component.literal("§7Para voltar a jogar, faça login novamente:"));
        player.sendSystemMessage(Component.literal("§a/logar <sua_senha>"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§c⚠ Você não pode se mover até fazer login novamente."));
    }
    
    /**
     * Envia lembrete rápido de login
     */
    public static void sendQuickLoginReminder(ServerPlayer player) {
        String username = player.getName().getString();
        
        if (UserDataManager.isUserRegistered(username)) {
            player.sendSystemMessage(Component.literal("§cVocê precisa fazer login! §7Use: §a/logar <senha>"));
        } else {
            player.sendSystemMessage(Component.literal("§cVocê precisa se registrar! §7Use: §a/registrar <senha>"));
        }
    }
    
    /**
     * Formata tempo em minutos e segundos
     */
    private static String formatTime(int seconds) {
        if (seconds >= 60) {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            return minutes + "m" + (remainingSeconds > 0 ? remainingSeconds + "s" : "");
        } else {
            return seconds + "s";
        }
    }
    
    /**
     * Para o scheduler de mensagens
     */
    public static void shutdown() {
        // Cancela todas as tarefas
        messageTasks.values().forEach(task -> task.cancel(true));
        messageTasks.clear();
        
        // Para o scheduler
        messageScheduler.shutdown();
        try {
            if (!messageScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                messageScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            messageScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        LOGGER.info("MessageManager desligado");
    }
}