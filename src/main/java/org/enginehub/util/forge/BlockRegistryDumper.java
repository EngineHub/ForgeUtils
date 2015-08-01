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
        List<Map<String, Object>> list = new LinkedList<>();

        FMLControlledNamespacedRegistry<Block> registry = GameData.getBlockRegistry();
        Map map = null;
        try {
            Field fd = registry.getClass().getSuperclass().getSuperclass().getDeclaredField("field_148758_b");
            fd.setAccessible(true);
            map = (Map) fd.get(registry);
        } catch (IllegalAccessException ignored) {
        } catch (NoSuchFieldException e) {
            FMLLog.severe("Error accessing registry map:" + e);
        }
        if (map == null) {
            throw new Exception("Couldn't find map field from registry.");
        }
        for (Entry e : (Iterable<Entry>) map.entrySet()) {
            list.add(getProperties(e));
        }

        Collections.sort(list, (Map<String, Object> a, Map<String, Object> b)
                -> ((Integer) a.get("legacyId")).compareTo((Integer) b.get("legacyId")));
        String out = gson.toJson(list);
        this.write(out);
        FMLLog.info("Wrote file: %s", file.getAbsolutePath());
    }

    private Map<String, Object> getProperties(Entry e) {
        Map<String, Object> map = new LinkedHashMap<>();
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
        Map<String, Map> map = new LinkedHashMap<>();
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

        Map<String, Object> dataMap = new LinkedHashMap<>();
        int dataMask = -1;
        for (Comparable val : (Iterable<Comparable>) prop.getAllowedValues()) {
            Map<String, Object> valueMap = new LinkedHashMap<>();
            int dv = b.getMetaFromState(base.withProperty(prop, val));
            valueMap.put("data", dv);

            // TODO devise fix for state data which relies on other states
            // i.e. some bits have different meanings depending on other bits
            // also datamask is mostly legacy and also subject to the above issue
            if (dv > dataMask) dataMask = dv;

            if (prop instanceof PropertyDirection) {
                Vec3i vec = EnumFacing.byName(val.toString()).getDirectionVec();
                valueMap.put("direction", vec);
            }
            dataMap.put(prop.getName(val), valueMap);
        }
        if (dataMask != -1) dataMap.put("dataMask", dataMask);
        return dataMap;
    }

    private Map<String, Object> getMaterial(Block b) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("powerProvider", b.canProvidePower());
        map.put("lightOpacity", b.getLightOpacity());
        map.put("lightValue", b.getLightValue());
        map.put("usesNeighborLight", b.getUseNeighborBrightness());
        map.put("hardness", getField(b, Block.class, "hardness"));
        map.put("resistance", getField(b, Block.class, "resistance"));
        map.put("randomTicks", b.getTickRandomly());
        map.put("fullCube", b.isFullCube());
        map.put("fullBlock", b.isFullBlock());
        map.put("fullCube", b.isFullCube());
        Material m = b.getMaterial();
        map.put("liquid", m.isLiquid());
        map.put("solid", m.isSolid());
        map.put("blocksMovement", m.blocksMovement());
        map.put("blocksLight", m.blocksLight());
        map.put("flammable", m.getCanBurn());
        map.put("opaque", m.isOpaque());
        map.put("replaceable", m.isReplaceable());
        map.put("noToolRequired", m.isToolNotRequired());
        map.put("mobility", m.getMaterialMobility());
        map.put("adventureModeExempt", getField(m, Material.class, "isAdventureModExempt"));
        map.put("mapColor", rgb(m.getMaterialMapColor().colorValue));

        return map;
    }

    private Object getField(Object obj, Class<?> clazz, String name) {
        try {
            Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (IllegalAccessException | NoSuchFieldException ignored) {
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
            // warning not guaranteed to work
            Vec3i vec;
            int x, y, z;
            String s = in.nextString();
            s = s.substring(1, s.length() - 1);
            String[] sA = s.split(",");
            x = Integer.parseInt(sA[0]);
            y = Integer.parseInt(sA[1]);
            z = Integer.parseInt(sA[2]);
            vec = new Vec3i(x, y, z);
            return vec;
        }
        @Override
        public void write(final JsonWriter out, final Vec3i vec) throws IOException {
            out.value("[" + vec.getX() + "," + vec.getY() + "," + vec.getZ() + "]");
        }
    }

}