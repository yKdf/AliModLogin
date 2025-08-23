package com.aliloginmod;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;


@Mod(AliLoginMod.MODID)
public class AliLoginMod {
    public static final String MODID = "aliloginmod";
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public AliLoginMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        modEventBus.addListener(this::commonSetup);
        
        MinecraftForge.EVENT_BUS.register(this);
        

    MinecraftForge.EVENT_BUS.register(PlayerRestrictionHandler.class);
    
    MinecraftForge.EVENT_BUS.register(SessionManager.class);
        
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        UserDataManager.loadUserData();
        
        LOGGER.info("AliLoginMod inicializado com sucesso!");
    }
    
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
        
        LOGGER.info("Servidor iniciado com sucesso!");
    }

}