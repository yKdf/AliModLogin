package com.aliloginmod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.api.distmarker.Dist;

@Mod.EventBusSubscriber(modid = AliLoginMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    
    public static final ForgeConfigSpec.BooleanValue ALLOW_CLIENT_BYPASS = BUILDER
            .comment("Ativar auto-login quando conectar ao servidor (requer mod no servidor)")
            .define("allowClientBypass", false);
    
    public static final ForgeConfigSpec.ConfigValue<String> SHARED_SECRET = BUILDER
            .comment("Chave secreta para auto-login (deve ser igual à do servidor)")
            .define("sharedSecret", "");
    
    public static final ForgeConfigSpec SPEC = BUILDER.build();
    
    public static boolean allowClientBypass;
    public static String sharedSecret;
    
    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        allowClientBypass = ALLOW_CLIENT_BYPASS.get();
        sharedSecret = SHARED_SECRET.get();
    }
}