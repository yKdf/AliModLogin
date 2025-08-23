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
    
    static final ForgeConfigSpec SPEC = BUILDER.build();
    
    public static int loginTimeout;
    public static int maxLoginAttempts;
    
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        loginTimeout = LOGIN_TIMEOUT.get();
        maxLoginAttempts = MAX_LOGIN_ATTEMPTS.get();
    }
}