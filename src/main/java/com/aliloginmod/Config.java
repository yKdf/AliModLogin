package com.aliloginmod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// Configuração do AliLoginMod
@Mod.EventBusSubscriber(modid = AliLoginMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    
    private static final ForgeConfigSpec.IntValue LOGIN_TIMEOUT = BUILDER
            .comment("Tempo limite em segundos para fazer login (padrão: 300 = 5 minutos)")
            .defineInRange("loginTimeout", 300, 30, 1800);
    
    private static final ForgeConfigSpec.IntValue MAX_LOGIN_ATTEMPTS = BUILDER
            .comment("Número máximo de tentativas de login antes do kick (padrão: 5)")
            .defineInRange("maxLoginAttempts", 5, 1, 20);
    
    private static final ForgeConfigSpec.BooleanValue ALLOW_CLIENT_BYPASS = BUILDER
            .comment("Permite auto-login se o cliente tiver o mod e enviar handshake válido (padrão: false)")
            .define("allowClientBypass", false); // ← Está desabilitado!

    private static final ForgeConfigSpec.ConfigValue<String> SHARED_SECRET = BUILDER
            .comment("Chave secreta compartilhada cliente/servidor para assinar o handshake (mantenha em segredo)")
            .define("sharedSecret", ""); // ← Está vazio!
    
    static final ForgeConfigSpec SPEC = BUILDER.build();
    
    public static int loginTimeout;
    public static int maxLoginAttempts;
    public static boolean allowClientBypass;
    public static String sharedSecret;
    
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        loginTimeout = LOGIN_TIMEOUT.get();
        maxLoginAttempts = MAX_LOGIN_ATTEMPTS.get();
        allowClientBypass = ALLOW_CLIENT_BYPASS.get();
        sharedSecret = SHARED_SECRET.get();
    }
}