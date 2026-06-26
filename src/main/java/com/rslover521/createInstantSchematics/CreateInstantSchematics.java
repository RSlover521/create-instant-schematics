package com.rslover521.createInstantSchematics;

import com.mojang.logging.LogUtils;
import com.rslover521.createInstantSchematics.core.ModCommands;
import com.rslover521.createInstantSchematics.core.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CreateInstantSchematics.MODID)
public class CreateInstantSchematics {
    public static final String MODID = "create_instant_schematics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateInstantSchematics(FMLJavaModLoadingContext context) {
        ModNetwork.register();
        MinecraftForge.EVENT_BUS.register(new ModCommands());
    }
}
