package org.enginehub.util.forge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.minecraft.block.Block;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.registry.FMLControlledNamespacedRegistry;
import net.minecraftforge.fml.common.registry.GameData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
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
        Map map = (Map) getField(registry, registry.getClass().getSuperclass().getSuperclass(), "inverseObjectRegistry", "field_148758_b");
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
        BlockStateContainer bs = b.getBlockState();
        Collection<IProperty<?>> props = bs.getProperties();
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
        List<Integer> dvs = new ArrayList<Integer>();
        for (Comparable val : (Iterable<Comparable>) prop.getAllowedValues()) {
            Map<String, Object> stateMap = new LinkedHashMap<String, Object>();
            int dv = b.getMetaFromState(base.withProperty(prop, val));
            stateMap.put("data", dv);

            dvs.add(dv);

            if (prop instanceof PropertyDirection) {
                Vec3i vec = EnumFacing.byName(val.toString()).getDirectionVec();
                stateMap.put("direction", vec);
            }
            valueMap.put(prop.getName(val), stateMap);
        }

        // attempt to calc mask
        int dataMask = -1;
        if (dvs.size() == 2) {
            // binary states
            if (dvs.contains(12)) {
                dataMask = 12;
            } if (dvs.contains(8)) {
                dataMask = 8;
            } else if (dvs.contains(4)) {
                dataMask = 4;
            } else if (dvs.contains(1)) {
                dataMask = 1;
            }
        } else if (dvs.size() == 16) {
            // full range - colors, rotation, etc
            dataMask = 15;
        } else if (prop.getName().equals("facing") || prop.getName().equals("shape")) {
            // most directions go 0-x, but some are in the middle, so mask is the highest set of bits
            if (dvs.size() == 4) {
                if (dvs.contains(4) || dvs.contains(5) || dvs.contains(6) || dvs.contains(7)) {
                    dataMask = 7;
                } else {
                    dataMask = 3;
                }
            } else {
                if (dvs.contains(8) || dvs.contains(9) || dvs.contains(10) || dvs.contains(11)) {
                    dataMask = 12;
                } else {
                    dataMask = 7;
                }
            }
        } else {
            // almost all "variant", but catch all for safety
            int max = -1;
            for (int dv : dvs) {
                if (dv > max) max = dv;
            }
            // usually....
            dataMask = (max > 12 ? 15 : (max > 8 ? 12 : (max == 8 ? 8 :
                    (max > 4 ? 7 : (max == 4 ? 4 : (max > 1 ? 3 : max > 0 ? 1 : -1))))));
        }
        if (dataMask != -1) dataMap.put("dataMask", dataMask);

        dataMap.put("values", valueMap);
        return dataMap;
    }

    private Map<String, Object> getMaterial(Block b) {
        IBlockState bs = b.getDefaultState();
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("powerSource", b.canProvidePower(bs));
        map.put("lightOpacity", b.getLightOpacity(bs));
        map.put("lightValue", b.getLightValue(bs));
        map.put("usingNeighborLight", b.getUseNeighborBrightness(bs));
        map.put("hardness", getField(b, Block.class, "blockHardness", "field_149782_v"));
        map.put("resistance", getField(b, Block.class, "blockResistance", "field_149781_w"));
        map.put("ticksRandomly", b.getTickRandomly());
        map.put("fullCube", b.isFullCube(bs));
        map.put("slipperiness", b.slipperiness);
        map.put("renderedAsNormalBlock", b.isFullBlock(bs));
        //map.put("solidFullCube", b.isSolidFullCube());
        Material m = b.getMaterial(bs);
        map.put("liquid", m.isLiquid());
        map.put("solid", m.isSolid());
        map.put("movementBlocker", m.blocksMovement());
        //map.put("blocksLight", m.blocksLight());
        map.put("burnable", m.getCanBurn());
        map.put("opaque", m.isOpaque());
        map.put("replacedDuringPlacement", m.isReplaceable());
        map.put("toolRequired", !m.isToolNotRequired());
        map.put("fragileWhenPushed", m.getMobilityFlag() == EnumPushReaction.DESTROY);
        map.put("unpushable", m.getMobilityFlag() == EnumPushReaction.BLOCK);
        map.put("adventureModeExempt", getField(m, Material.class, "isAdventureModeExempt", "field_85159_M"));
        //map.put("mapColor", rgb(m.getMaterialMapColor().colorValue));

        try {
            map.put("ambientOcclusionLightValue", b.getAmbientOcclusionLightValue(bs));
        } catch (NoSuchMethodError ignored) {
            map.put("ambientOcclusionLightValue", b.isBlockNormalCube(bs) ? 0.2F : 1.0F);
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