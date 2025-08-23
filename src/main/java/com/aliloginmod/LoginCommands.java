package com.aliloginmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = AliLoginMod.MODID)
public class LoginCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginCommands.class);
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        // Comando /registrar <senha>
        dispatcher.register(Commands.literal("registrar")
                .then(Commands.argument("senha", StringArgumentType.string())
                        .executes(LoginCommands::executeRegister)));   
        
        // Comando /logar <senha>
        dispatcher.register(Commands.literal("logar")
                .then(Commands.argument("senha", StringArgumentType.string())
                        .executes(LoginCommands::executeLogin)));
        

        
        // Comando /forcelogin <jogador> (apenas console)
        dispatcher.register(Commands.literal("forcelogin")
                .then(Commands.argument("jogador", StringArgumentType.string())
                        .executes(LoginCommands::executeForceLogin)));
        
        // Comando /novasenha <senha_atual> <nova_senha>
        dispatcher.register(Commands.literal("novasenha")
                .then(Commands.argument("senha_atual", StringArgumentType.string())
                        .then(Commands.argument("nova_senha", StringArgumentType.string())
                                .executes(LoginCommands::executeChangePassword))));
        
        // Aliases em inglês
        // Comando /register <senha> (alias para /registrar)
        dispatcher.register(Commands.literal("register")
                .then(Commands.argument("senha", StringArgumentType.string())
                        .executes(LoginCommands::executeRegister)));
        
        // Comando /login <senha> (alias para /logar)
        dispatcher.register(Commands.literal("login")
                .then(Commands.argument("senha", StringArgumentType.string())
                        .executes(LoginCommands::executeLogin)));
        
        LOGGER.info("Comandos de login registrados com sucesso");
    }
    
    /**
     * Executa o comando /registrar
     */
    private static int executeRegister(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        // Verifica se o comando foi executado por um jogador
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cEste comando só pode ser usado por jogadores!"));
            return 0;
        }
        
        String password = StringArgumentType.getString(context, "senha");
        String username = player.getName().getString();
        
        // Validações básicas
        if (password.length() < 4) {
            player.sendSystemMessage(Component.literal("§cA senha deve ter pelo menos 4 caracteres!"));
            return 0;
        }
        
        if (password.length() > 32) {
            player.sendSystemMessage(Component.literal("§cA senha não pode ter mais de 32 caracteres!"));
            return 0;
        }
        
        // Verifica se o usuário já está registrado
        if (UserDataManager.isUserRegistered(username)) {
            player.sendSystemMessage(Component.literal("§cVocê já está registrado! Use /logar <senha> para fazer login."));
            return 0;
        }
        
        // Registra o usuário
        boolean success = UserDataManager.registerUser(username, password);
        
        if (success) {
             UserDataManager.loginPlayer(player);
             SessionManager.cancelLoginTimeout(player.getUUID());
             SessionManager.clearLoginAttempts(player.getUUID());
             MessageManager.stopLoginReminders(player.getUUID());
             
             // Teleporta para última posição se existir
             if (UserDataManager.hasPlayerPosition(username)) {
                 UserDataManager.teleportPlayerToLastPosition(player);
             }
             
             MessageManager.sendLoginSuccessMessage(player, true);
             LOGGER.info("Jogador {} se registrou com sucesso", username);
        } else {
            player.sendSystemMessage(Component.literal("§cErro ao registrar. Tente novamente."));
            LOGGER.error("Falha ao registrar jogador {}", username);
        }
        
        return success ? 1 : 0;
    }
    
    /**
     * Executa o comando /logar
     */
    private static int executeLogin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        // Verifica se o comando foi executado por um jogador
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cEste comando só pode ser usado por jogadores!"));
            return 0;
        }
        
        String password = StringArgumentType.getString(context, "senha");
        String username = player.getName().getString();
        
        // Verifica se o jogador já está logado
        if (UserDataManager.isPlayerLoggedIn(player)) {
            player.sendSystemMessage(Component.literal("§eVocê já está logado!"));
            return 0;
        }
        
        // Verifica se o usuário está registrado
        if (!UserDataManager.isUserRegistered(username)) {
            player.sendSystemMessage(Component.literal("§cVocê não está registrado! Use /registrar <senha> para se registrar."));
            return 0;
        }
        
        // Verifica se pode tentar fazer login
        if (!SessionManager.canAttemptLogin(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cVocê excedeu o limite de tentativas de login!"));
            return 0;
        }
        
        // Autentica o usuário
        boolean success = UserDataManager.authenticateUser(username, password);
        
        if (success) {
             UserDataManager.loginPlayer(player);
             SessionManager.cancelLoginTimeout(player.getUUID());
             SessionManager.clearLoginAttempts(player.getUUID());
             MessageManager.stopLoginReminders(player.getUUID());
             
             // Teleporta para última posição se existir
             if (UserDataManager.hasPlayerPosition(username)) {
                 UserDataManager.teleportPlayerToLastPosition(player);
             }
             
             MessageManager.sendLoginSuccessMessage(player, false);
             LOGGER.info("Jogador {} fez login com sucesso", username);
        } else {
             SessionManager.recordFailedLoginAttempt(player);
             
             int attempts = SessionManager.getLoginAttempts(player.getUUID());
             int maxAttempts = SessionManager.getMaxLoginAttempts();
             
             MessageManager.sendLoginErrorMessage(player, attempts, maxAttempts);
             LOGGER.warn("Tentativa de login com senha incorreta para jogador {} (tentativa {}/{})", 
                        username, attempts, maxAttempts);
        }
        
        return success ? 1 : 0;
    }
    

    
    /**
     * Executa o comando /forcelogin (apenas console)
     */
    private static int executeForceLogin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        // Verifica se o comando foi executado pelo console (não por um jogador)
        if (source.getEntity() instanceof ServerPlayer) {
            source.sendFailure(Component.literal("§cEste comando só pode ser usado pelo console do servidor!"));
            return 0;
        }
        
        String targetPlayerName = StringArgumentType.getString(context, "jogador");
        
        // Busca o jogador online
        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(targetPlayerName);
        
        if (targetPlayer == null) {
            source.sendFailure(Component.literal("§cJogador '" + targetPlayerName + "' não encontrado ou não está online!"));
            return 0;
        }
        
        // Verifica se o jogador está registrado
        if (!UserDataManager.isUserRegistered(targetPlayerName)) {
            source.sendFailure(Component.literal("§cJogador '" + targetPlayerName + "' não está registrado!"));
            return 0;
        }
        
        // Verifica se o jogador já está logado
        if (UserDataManager.isPlayerLoggedIn(targetPlayer)) {
            source.sendFailure(Component.literal("§eJogador '" + targetPlayerName + "' já está logado!"));
            return 0;
        }
        
        // Força o login do jogador
        UserDataManager.loginPlayer(targetPlayer);
        SessionManager.cancelLoginTimeout(targetPlayer.getUUID());
        SessionManager.clearLoginAttempts(targetPlayer.getUUID());
        MessageManager.stopLoginReminders(targetPlayer.getUUID());
        
        // Teleporta para última posição se existir
        if (UserDataManager.hasPlayerPosition(targetPlayerName)) {
            UserDataManager.teleportPlayerToLastPosition(targetPlayer);
        }
        
        // Mensagens de confirmação
        source.sendSuccess(() -> Component.literal("§aJogador '" + targetPlayerName + "' foi forçado a fazer login com sucesso!"), true);
        targetPlayer.sendSystemMessage(Component.literal("§aVocê foi forçado a fazer login pelo console do servidor!"));
        
        LOGGER.info("Console forçou login do jogador {}", targetPlayerName);
        return 1;
    }
    
    /**
     * Executa o comando /novasenha
     */
    private static int executeChangePassword(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        // Verifica se o comando foi executado por um jogador
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cEste comando só pode ser usado por jogadores!"));
            return 0;
        }
        
        String currentPassword = StringArgumentType.getString(context, "senha_atual");
        String newPassword = StringArgumentType.getString(context, "nova_senha");
        String username = player.getName().getString();
        
        // Verifica se o jogador está registrado
        if (!UserDataManager.isUserRegistered(username)) {
            player.sendSystemMessage(Component.literal("§cVocê não está registrado! Use /registrar <senha> para se registrar."));
            return 0;
        }
        
        // Verifica se o jogador está logado
        if (!UserDataManager.isPlayerLoggedIn(player)) {
            player.sendSystemMessage(Component.literal("§cVocê precisa estar logado para alterar sua senha! Use /logar <senha>."));
            return 0;
        }
        
        // Validações da nova senha
        if (newPassword.length() < 4) {
            player.sendSystemMessage(Component.literal("§cA nova senha deve ter pelo menos 4 caracteres!"));
            return 0;
        }
        
        if (newPassword.length() > 32) {
            player.sendSystemMessage(Component.literal("§cA nova senha não pode ter mais de 32 caracteres!"));
            return 0;
        }
        
        // Verifica se a nova senha é diferente da atual
        if (currentPassword.equals(newPassword)) {
            player.sendSystemMessage(Component.literal("§cA nova senha deve ser diferente da senha atual!"));
            return 0;
        }
        
        // Autentica a senha atual
        if (!UserDataManager.authenticateUser(username, currentPassword)) {
            player.sendSystemMessage(Component.literal("§cSenha atual incorreta!"));
            LOGGER.warn("Tentativa de alteração de senha com senha atual incorreta para jogador {}", username);
            return 0;
        }
        
        // Altera a senha
        boolean success = UserDataManager.changePassword(username, newPassword);
        
        if (success) {
            player.sendSystemMessage(Component.literal("§aSua senha foi alterada com sucesso!"));
            LOGGER.info("Jogador {} alterou sua senha com sucesso", username);
        } else {
            player.sendSystemMessage(Component.literal("§cErro ao alterar a senha. Tente novamente."));
            LOGGER.error("Falha ao alterar senha do jogador {}", username);
        }
        
        return success ? 1 : 0;
    }
}