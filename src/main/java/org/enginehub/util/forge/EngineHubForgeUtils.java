package org.enginehub.util.forge;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import java.io.File;

@Mod(modid = EngineHubForgeUtils.MODID, name = "EngineHub Forge Utils", version = "%VERSION%", acceptableRemoteVersions = "*")
@SuppressWarnings("ALL")
public class EngineHubForgeUtils {
    public static final String MODID = "EngineHubForgeUtils";

    @EventHandler
    public void init(FMLPostInitializationEvent event) {

        try {
            if ("true".equalsIgnoreCase(System.getProperty("enginehub.dumpblocks"))) {
                (new BlockRegistryDumper(new File("blocks.json"))).run();
            }
        } catch (Exception e) {
            FMLLog.severe("Error running block registry dumper: " +  e);
            e.printStackTrace();
        }
    }

    @EventHandler
    public void serverLoad(FMLServerStartingEvent event) {
        event.registerServerCommand(new BeanShellCommand());
    }
}
