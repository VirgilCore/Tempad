package me.codexadrian.tempad.common.utils.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class PlatformUtilsImpl {
    public static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
