package org.enginehub.util.forge.dumper;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ItemRegistryDumper extends RegistryDumper<Item> {

    public ItemRegistryDumper(File file) {
        super(file);
    }

    @Override
    public IForgeRegistry<Item> getRegistry() {
        return ForgeRegistries.ITEMS;
    }

    @Override
    public Comparator<Map<String, Object>> getComparator() {
        return new MapComparator();
    }

    public List<Map<String, Object>> getProperties(Map.Entry<ResourceLocation, Item> entry) {
        List<Map<String, Object>> maps = new ArrayList<>();
        Item item = entry.getValue();
        if (item.getHasSubtypes() && item.getCreativeTab() != null) {
            NonNullList<ItemStack> itemStacks = NonNullList.create();
            item.getSubItems(item.getCreativeTab(), itemStacks);
            for (ItemStack stack : itemStacks) {
                maps.add(getPropertiesForItem(entry.getKey(), stack));
            }
        } else {
            maps.add(getPropertiesForItem(entry.getKey(), new ItemStack(item, 1)));
        }

        return maps;
    }

    private Map<String, Object> getPropertiesForItem(ResourceLocation resourceLocation, ItemStack itemStack) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", resourceLocation.toString());
        map.put("unlocalizedName", itemStack.getUnlocalizedName());
        map.put("localizedName", itemStack.getDisplayName());
        map.put("maxDamage", itemStack.getMaxDamage());
        map.put("maxStackSize", itemStack.getMaxStackSize());
        return map;
    }

    private static class MapComparator implements Comparator<Map<String, Object>> {
        @Override
        public int compare(Map<String, Object> a, Map<String, Object> b) {
            return ((String) a.get("id")).compareTo((String) b.get("id"));
        }
    }
}
