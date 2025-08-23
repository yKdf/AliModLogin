package com.aliloginmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserDataManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserDataManager.class);
    private static final String DATA_FILE = "aliloginmod_users.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // Armazena dados dos usuários registrados
    private static final Map<String, UserData> registeredUsers = new ConcurrentHashMap<>();
    
    // Armazena jogadores atualmente logados na sessão
    private static final Set<UUID> loggedInPlayers = ConcurrentHashMap.newKeySet();
    
    public static class UserData {
        public String username;
        public String passwordHash;
        public long registrationDate;
        public long lastLogin;
        public PlayerPosition lastPosition;
        
        public UserData(String username, String passwordHash) {
            this.username = username;
            this.passwordHash = passwordHash;
            this.registrationDate = System.currentTimeMillis();
            this.lastLogin = 0;
            this.lastPosition = null;
        }
    }
    
    public static class PlayerPosition {
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public String dimension;
        
        public PlayerPosition(double x, double y, double z, float yaw, float pitch, String dimension) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.dimension = dimension;
        }
    }
    
    /**
     * Carrega os dados dos usuários do arquivo JSON
     */
    public static void loadUserData() {
        File dataFile = new File(DATA_FILE);
        if (!dataFile.exists()) {
            LOGGER.info("Arquivo de dados de usuários não encontrado. Criando novo...");
            saveUserData();
            return;
        }
        
        try (FileReader reader = new FileReader(dataFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, UserData>>(){}.getType();
            Map<String, UserData> loadedData = GSON.fromJson(reader, type);
            
            if (loadedData != null) {
                registeredUsers.clear();
                registeredUsers.putAll(loadedData);
                LOGGER.info("Carregados {} usuários registrados", registeredUsers.size());
            }
        } catch (IOException e) {
            LOGGER.error("Erro ao carregar dados de usuários: ", e);
        }
    }
    
    /**
     * Salva os dados dos usuários no arquivo JSON
     */
    public static void saveUserData() {
        try (FileWriter writer = new FileWriter(DATA_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(registeredUsers, writer);
            LOGGER.debug("Dados de usuários salvos com sucesso");
        } catch (IOException e) {
            LOGGER.error("Erro ao salvar dados de usuários: ", e);
        }
    }
    
    /**
     * Registra um novo usuário
     */
    public static boolean registerUser(String username, String password) {
        if (registeredUsers.containsKey(username.toLowerCase())) {
            return false; // Usuário já existe
        }
        
        String passwordHash = hashPassword(password);
        UserData userData = new UserData(username, passwordHash);
        registeredUsers.put(username.toLowerCase(), userData);
        saveUserData();
        
        LOGGER.info("Usuário {} registrado com sucesso", username);
        return true;
    }
    
    /**
     * Autentica um usuário
     */
    public static boolean authenticateUser(String username, String password) {
        UserData userData = registeredUsers.get(username.toLowerCase());
        if (userData == null) {
            return false; // Usuário não existe
        }
        
        String passwordHash = hashPassword(password);
        boolean isValid = userData.passwordHash.equals(passwordHash);
        
        if (isValid) {
            userData.lastLogin = System.currentTimeMillis();
            saveUserData();
        }
        
        return isValid;
    }
    
    /**
     * Verifica se um usuário está registrado
     */
    public static boolean isUserRegistered(String username) {
        return registeredUsers.containsKey(username.toLowerCase());
    }
    
    /**
     * Marca um jogador como logado na sessão atual
     */
    public static void loginPlayer(ServerPlayer player) {
        loggedInPlayers.add(player.getUUID());
        LOGGER.info("Jogador {} fez login", player.getName().getString());
    }
    
    /**
     * Remove um jogador da sessão de login
     */
    public static void logoutPlayer(ServerPlayer player) {
        loggedInPlayers.remove(player.getUUID());
        LOGGER.info("Jogador {} fez logout", player.getName().getString());
    }
    
    /**
     * Verifica se um jogador está logado na sessão atual
     */
    public static boolean isPlayerLoggedIn(ServerPlayer player) {
        return loggedInPlayers.contains(player.getUUID());
    }
    
    /**
     * Limpa todos os jogadores logados (usado quando o servidor reinicia)
     */
    public static void clearLoggedInPlayers() {
        loggedInPlayers.clear();
        LOGGER.info("Lista de jogadores logados limpa");
    }
    
    /**
     * Gera hash SHA-256 da senha
     */
    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Erro ao gerar hash da senha: ", e);
            return password; // Fallback inseguro, mas funcional
        }
    }
    
    /**
     * Altera a senha de um usuário
     */
    public static boolean changePassword(String username, String newPassword) {
        UserData userData = registeredUsers.get(username.toLowerCase());
        if (userData == null) {
            return false; // Usuário não existe
        }
        
        String newPasswordHash = hashPassword(newPassword);
        userData.passwordHash = newPasswordHash;
        saveUserData();
        
        LOGGER.info("Senha do usuário {} alterada com sucesso", username);
        return true;
    }
    
    /**
     * Salva a posição atual do jogador
     */
    public static void savePlayerPosition(ServerPlayer player) {
        String username = player.getName().getString().toLowerCase();
        UserData userData = registeredUsers.get(username);
        
        if (userData != null) {
            String dimension = player.level().dimension().location().toString();
            PlayerPosition position = new PlayerPosition(
                player.getX(),
                player.getY(), 
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                dimension
            );
            
            userData.lastPosition = position;
            saveUserData();
            
            LOGGER.info("Posição salva para jogador {} em {}: {}, {}, {}", 
                       username, dimension, position.x, position.y, position.z);
        }
    }
    
    /**
     * Obtém a última posição salva do jogador
     */
    public static PlayerPosition getPlayerLastPosition(String username) {
        UserData userData = registeredUsers.get(username.toLowerCase());
        return userData != null ? userData.lastPosition : null;
    }
    
    /**
     * Verifica se o jogador tem uma posição salva
     */
    public static boolean hasPlayerPosition(String username) {
        UserData userData = registeredUsers.get(username.toLowerCase());
        return userData != null && userData.lastPosition != null;
    }
    
    /**
     * Teleporta o jogador para sua última posição salva
     */
    public static boolean teleportPlayerToLastPosition(ServerPlayer player) {
        String username = player.getName().getString();
        PlayerPosition lastPos = getPlayerLastPosition(username);
        
        if (lastPos == null) {
            LOGGER.info("Jogador {} não tem posição salva", username);
            return false;
        }
        
        try {
            // Obtém a dimensão de destino
            ResourceLocation dimensionLocation = new ResourceLocation(lastPos.dimension);
            ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionLocation);
            ServerLevel targetLevel = player.getServer().getLevel(dimensionKey);
            
            if (targetLevel == null) {
                LOGGER.warn("Dimensão {} não encontrada para jogador {}, teleportando para spawn", lastPos.dimension, username);
                return false;
            }
            
            // Teleporta o jogador
            player.teleportTo(targetLevel, lastPos.x, lastPos.y, lastPos.z, lastPos.yaw, lastPos.pitch);
            
            LOGGER.info("Jogador {} teleportado para última posição em {}: {}, {}, {}", 
                       username, lastPos.dimension, lastPos.x, lastPos.y, lastPos.z);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Erro ao teleportar jogador {} para última posição: ", username, e);
            return false;
        }
    }
    
    /**
     * Obtém estatísticas dos usuários
     */
    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRegisteredUsers", registeredUsers.size());
        stats.put("currentlyLoggedIn", loggedInPlayers.size());
        return stats;
    }
}