package org.enginehub.util.forge.dumper;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.enginehub.util.forge.ForgeUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * This creates a legacy ID/Meta -> New format json file.
 *
 * For running in 1.12.2 or prior ONLY.
 */
public class LegacyDumper {

    private File file;
    private Gson gson;

    public LegacyDumper(File file) {
        this.file = file;
        GsonBuilder builder = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting();
        registerAdapters(builder);
        this.gson = builder.create();
    }

    public void registerAdapters(GsonBuilder builder) {

    }

    private double normaliseForOrdering(String string) {
        // Eww but it works
        String[] split = string.split(":", 2);
        while (split[1].length() < 6) {
            split[1] = "0" + split[1];
        }
        return Double.parseDouble(split[0] + "." + split[1]);
    }

    private String convertItem(String itemId, int data) {
        itemId = itemId.replace("minecraft:", "");
        Item item = Item.getByNameOrId(itemId);
        Block block = Block.getBlockFromItem(item);
        Map<String, String> properties = new HashMap<>();
        try {
            properties = stringifyProperties(block.getStateFromMeta(item.getMetadata(data)).getProperties());
        } catch (Exception e) {}
        switch (itemId) {
            case "bed":
                properties.put("color", String.valueOf(EnumDyeColor.byMetadata(data)));
                break;
            case "dye":
                properties.put("color", String.valueOf(EnumDyeColor.byDyeDamage(data)));
                break;
            case "banner":
                properties.put("color", String.valueOf(EnumDyeColor.byDyeDamage(data & 15)));
                break;
        }
        if (itemId.startsWith("record_")) {
            itemId = itemId.replace("record_", "music_disk_");
        }
        itemId = convertBlockAndProperties(itemId, properties);
        return itemId.split("\\[")[0];
    }

    private String convertBlockAndProperties(String blockId, Map<String, String> propertyMap) {
        blockId = blockId.replace("minecraft:", "");
        if (blockId.contains(":")) {
            System.out.println("Found non-minecraft block '" + blockId + "'. Skipping");
            return blockId + normaliseProperties(propertyMap);
        }

        // Convert block IDs.
        switch (blockId) {
            case "grass":
                blockId = "grass_block";
                break;
            case "silver_glazed_terracotta":
                blockId = "light_gray_glazed_terracotta";
                break;
            case "silver_shulker_box":
                blockId = "light_gray_shulker_box";
                break;
        }

        // Convert properties
        Iterator<Map.Entry<String, String>> iterator = propertyMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> property = iterator.next();
            entireProperty:
            switch (property.getKey()) {
                case "legacy-data":
                    iterator.remove();
                    break;
                case "color":
                    String color = property.getValue();
                    if (color.equals("lightBlue")) {
                        color = "light_blue";
                    } else if (color.equals("silver")) {
                        color = "light_gray";
                    }
                    blockId = color + "_" + blockId;
                    iterator.remove();
                    break;
                case "variant":
                case "type":
                    switch (blockId) {
                        case "stone":
                            blockId = property.getValue().replace("smooth", "polished");
                            break;
                        case "dirt":
                            blockId = property.getValue();
                            break;
                        case "planks":
                        case "sapling":
                        case "leaves":
                        case "leaves2":
                            blockId = property.getValue() + "_" + blockId.replace("2", "");
                            break;
                        default:
                            // No conversion case yet
                            break entireProperty;
                    }
                    iterator.remove();
                    break;
            }
        }

        if (propertyMap.containsKey("variant") || propertyMap.containsKey("type") || propertyMap.containsKey("contents")) {
            System.out.println("Still to go: " + blockId + normaliseProperties(propertyMap));
        }

        return "minecraft:" + blockId + normaliseProperties(propertyMap);
    }

    private String normaliseProperties(Map<String, String> propertyMap) {
        if (propertyMap.isEmpty()) {
            return "";
        }
        return "[" + propertyMap.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(",")) + "]";
    }

    private Map<String, String> stringifyProperties(ImmutableMap<IProperty<?>, Comparable<?>> propertyMap) {
        return propertyMap.entrySet().stream().collect(
                Collectors.toMap(k -> k.getKey().getName(), v -> v.getValue().toString()));
    }

    public void run() {
        Map<String, String> blockMap = new TreeMap<>(Comparator.comparingDouble(this::normaliseForOrdering));

        for (Map.Entry<ResourceLocation, Block> entry: ForgeRegistries.BLOCKS.getEntries()) {
            Block block = entry.getValue();
            for (IBlockState state: block.getBlockState().getValidStates()) {
                Map<String, String> properties = stringifyProperties(state.getProperties());
                try {
                    blockMap.put(Block.getIdFromBlock(block) + ":" + block.getMetaFromState(state),
                            convertBlockAndProperties(entry.getKey().toString(), properties));
                } catch (Exception e) {
                    System.out.println("Skipping " + normaliseProperties(properties) + " for " + block.getLocalizedName());
                }
            }
        }

        Map<String, String> itemMap = new TreeMap<>(Comparator.comparingDouble(this::normaliseForOrdering));

        for (Map.Entry<ResourceLocation, Item> entry: ForgeRegistries.ITEMS.getEntries()) {
            Item item = entry.getValue();
            if (item.getHasSubtypes() && item.getCreativeTab() != null) {
                NonNullList<ItemStack> itemStacks = NonNullList.create();
                item.getSubItems(item.getCreativeTab(), itemStacks);
                for (ItemStack stack : itemStacks) {
                    itemMap.put(Item.getIdFromItem(item) + ":" + stack.getMetadata(), convertItem(entry.getKey().toString(), stack.getMetadata()));
                }
            } else {
                itemMap.put(Item.getIdFromItem(item) + ":0", convertItem(entry.getKey().toString(), 0));
            }
        }

        Map<String, Map<String, String>> map = new LinkedHashMap<>();

        map.put("blocks", blockMap);
        map.put("items", itemMap);

        String out = gson.toJson(map);
        this.write(out);
        ForgeUtils.instance.modLogger.info("Wrote file: " + file.getAbsolutePath());
    }

    private void write(String s) {
        try(FileOutputStream str = new FileOutputStream(file)) {
            str.write(s.getBytes());
        } catch (IOException e) {
            ForgeUtils.instance.modLogger.error("Error writing legacy dump: %e", e);
        }
    }
}
