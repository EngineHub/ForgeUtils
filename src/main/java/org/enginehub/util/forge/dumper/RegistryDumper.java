package org.enginehub.util.forge.dumper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.enginehub.util.forge.ForgeUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

abstract class RegistryDumper<V extends IForgeRegistryEntry<V>> {

    private File file;
    private Gson gson;

    public RegistryDumper(File file) {
        this.file = file;
        GsonBuilder builder = new GsonBuilder().setPrettyPrinting();
        registerAdapters(builder);
        this.gson = builder.create();
    }

    public void registerAdapters(GsonBuilder builder) {

    }

    public void run() {
        List<Map<String, Object>> list = new LinkedList<>();

        IForgeRegistry<V> registry = getRegistry();
        registry.getEntries().stream().map(this::getProperties).forEach(list::addAll);

        list.sort(getComparator());
        String out = gson.toJson(list);
        this.write(out);
        ForgeUtils.instance.modLogger.info("Wrote file: " + file.getAbsolutePath());
    }

    private void write(String s) {
        try(FileOutputStream str = new FileOutputStream(file)) {
            str.write(s.getBytes());
        } catch (IOException e) {
            ForgeUtils.instance.modLogger.error("Error writing registry dump: %e", e);
        }
    }

    private String rgb(int i) {
        int r = (i >> 16) & 0xFF;
        int g = (i >>  8) & 0xFF;
        int b = i & 0xFF;
        return String.format("#%02x%02x%02x", r, g, b);
    }

    public abstract List<Map<String, Object>> getProperties(Map.Entry<ResourceLocation, V> entry);

    public abstract IForgeRegistry<V> getRegistry();

    public abstract Comparator<Map<String, Object>> getComparator();
}
