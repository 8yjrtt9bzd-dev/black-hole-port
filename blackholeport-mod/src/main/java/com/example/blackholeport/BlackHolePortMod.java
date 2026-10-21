package com.example.blackholeport;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

/** Forge 1.20.1 port of the Fabric mod "Black Hole" by vini2003 (MIT). */
@Mod(BlackHolePortMod.MODID)
public class BlackHolePortMod {
    public static final String MODID = "blackholeport";

    public BlackHolePortMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BlackHoleConfig.SPEC, "blackholeport.toml");
        BlackHoleNetwork.init();
    }
}
