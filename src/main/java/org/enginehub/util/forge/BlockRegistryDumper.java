package org.enginehub.util.forge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3i;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.registry.FMLControlledNamespacedRegistry;
import net.minecraftforge.fml.common.registry.GameData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@SuppressWarnings({"rawtypes", "unchecked"})
public class BlockRegistryDumper {

    private File file;
    private Gson gson;

    public BlockRegistryDumper(File file) {
        this.file = file;
        GsonBuilder builder = new GsonBuilder().setPrettyPrinting();
        builder.registerTypeAdapter(Vec3i.class, new Vec3iAdapter());
        this.gson = builder.create();
    }

    public void run() throws Exception {
        List<Map<String, Object>> list = new LinkedList<Map<String, Object>>();

        FMLControlledNamespacedRegistry<Block> registry = GameData.getBlockRegistry();
        Map map = (Map) getField(registry, registry.getClass().getSuperclass().getSuperclass(), "field_148758_b", "field_148758_b");
        if (map == null) {
            throw new Exception("Couldn't find map field from registry.");
        }
        for (Entry e : (Iterable<Entry>) map.entrySet()) {
            list.add(getProperties(e));
        }

        Collections.sort(list, new MapComparator());
        String out = gson.toJson(list);
        this.write(out);
        FMLLog.info("Wrote file: %s", file.getAbsolutePath());
    }

    private Map<String, Object> getProperties(Entry e) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        Block b = (Block) e.getKey();
        map.put("legacyId", Block.getIdFromBlock(b));
        map.put("id", e.getValue().toString());
        map.put("unlocalizedName", b.getUnlocalizedName());
        map.put("localizedName", b.getLocalizedName());
        map.put("states", getStates(b));
        map.put("material", getMaterial(b));
        return map;
    }

    private Map<String, Map> getStates(Block b) {
        Map<String, Map> map = new LinkedHashMap<String, Map>();
        BlockState bs = b.getBlockState();
        Collection<IProperty> props = bs.getProperties();
        for (IProperty prop : props) {
            map.put(prop.getName(), dataValues(b, prop));
        }

        return map;
    }

    private Map<String, Object> dataValues(Block b, IProperty prop) {
        //BlockState bs = b.getBlockState();
        IBlockState base = b.getStateFromMeta(0);

        Map<String, Object> dataMap = new LinkedHashMap<String, Object>();
        Map<String, Object> valueMap = new LinkedHashMap<String, Object>();
        int maxData = -1;
        for (Comparable val : (Iterable<Comparable>) prop.getAllowedValues()) {
            Map<String, Object> stateMap = new LinkedHashMap<String, Object>();
            int dv = b.getMetaFromState(base.withProperty(prop, val));
            stateMap.put("data", dv);

            // TODO devise fix for state data which relies on other states
            // i.e. some bits have different meanings depending on other bits
            // also datamask is mostly legacy and also subject to the above issue
            if (dv > maxData) maxData = dv;

            if (prop instanceof PropertyDirection) {
                Vec3i vec = EnumFacing.byName(val.toString()).getDirectionVec();
                stateMap.put("direction", vec);
            }
            valueMap.put(prop.getName(val), stateMap);
        }
        // this should work mostly? might be up to worldedit's data tests to check everything
        if (maxData != -1) dataMap.put("dataMask", maxData > 12 ? 15 : (maxData > 8 ? 12 : (maxData > 4 ? 7 : (maxData > 0 ? 3 : 0))));
        dataMap.put("values", valueMap);
        return dataMap;
    }

    private Map<String, Object> getMaterial(Block b) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("powerSource", b.canProvidePower());
        map.put("lightOpacity", b.getLightOpacity());
        map.put("lightValue", b.getLightValue());
        map.put("usingNeighborLight", b.getUseNeighborBrightness());
        map.put("hardness", getField(b, Block.class, "blockHardness", "field_149782_v"));
        map.put("resistance", getField(b, Block.class, "blockResistance", "field_149781_w"));
        map.put("ticksRandomly", b.getTickRandomly());
        map.put("fullCube", b.isFullCube());
        map.put("slipperiness", b.slipperiness);
        map.put("renderedAsNormalBlock", b.isFullBlock());
        //map.put("solidFullCube", b.isSolidFullCube());
        Material m = b.getMaterial();
        map.put("liquid", m.isLiquid());
        map.put("solid", m.isSolid());
        map.put("movementBlocker", m.blocksMovement());
        //map.put("blocksLight", m.blocksLight());
        map.put("burnable", m.getCanBurn());
        map.put("opaque", m.isOpaque());
        map.put("replacedDuringPlacement", m.isReplaceable());
        map.put("toolRequired", !m.isToolNotRequired());
        map.put("fragileWhenPushed", m.getMaterialMobility() == 1);
        map.put("unpushable", m.getMaterialMobility() == 2);
        map.put("adventureModeExempt", getField(m, Material.class, "isAdventureModeExempt", "field_85159_M"));
        //map.put("mapColor", rgb(m.getMaterialMapColor().colorValue));

        try {
            map.put("ambientOcclusionLightValue", b.getAmbientOcclusionLightValue());
        } catch (NoSuchMethodError ignored) {
            map.put("ambientOcclusionLightValue", b.isSolidFullCube() ? 0.2F : 1.0F);
        }
        map.put("grassBlocking", false); // idk what this property was originally supposed to be...grass uses a combination of light values to check growth
        return map;
    }

    private Object getField(Object obj, Class<?> clazz, String name, String obfName) {
        try {
            Field f;
            try {
                f = clazz.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                f = clazz.getDeclaredField(obfName);
            }
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(obj);
        } catch (IllegalAccessException ignored) {
        } catch (NoSuchFieldException ignored) {
        }
        return null;
    }

    private String rgb(int i) {
        int r = (i >> 16) & 0xFF;
        int g = (i >>  8) & 0xFF;
        int b = i & 0xFF;
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private void write(String s) {
        try {
            FileOutputStream str = new FileOutputStream(file);
            str.write(s.getBytes());
        } catch (IOException e) {
            FMLLog.severe("Error writing registry dump: %e", e);
        }
    }


    public static class Vec3iAdapter extends TypeAdapter<Vec3i> {
        @Override
        public Vec3i read(final JsonReader in) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public void write(final JsonWriter out, final Vec3i vec) throws IOException {
            out.beginArray();
            out.value(vec.getX());
            out.value(vec.getY());
            out.value(vec.getZ());
            out.endArray();
        }
    }

    private static class MapComparator implements Comparator<Map<String, Object>> {
        @Override
        public int compare(Map<String, Object> a, Map<String, Object> b) {
            return ((Integer) a.get("legacyId")).compareTo((Integer) b.get("legacyId"));
        }
    }
}