package org.enginehub.util.forge.dumper;

import com.google.common.collect.Lists;
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
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@SuppressWarnings({"unchecked"})
public class BlockRegistryDumper extends RegistryDumper<Block> {

    public BlockRegistryDumper(File file) {
        super(file);
    }

    @Override
    public void registerAdapters(GsonBuilder builder) {
        super.registerAdapters(builder);

        builder.registerTypeAdapter(Vec3i.class, new Vec3iAdapter());
        builder.registerTypeAdapter(Vec3d.class, new Vec3dAdapter());
    }

    @Override
    public IForgeRegistry<Block> getRegistry() {
        return ForgeRegistries.BLOCKS;
    }

    @Override
    public Comparator<Map<String, Object>> getComparator() {
        return new MapComparator();
    }

    @Override
    public List<Map<String, Object>> getProperties(Entry<ResourceLocation, Block> e) {
        Map<String, Object> map = new LinkedHashMap<>();
        Block b = e.getValue();
        map.put("legacyId", Block.getIdFromBlock(b));
        map.put("id", e.getKey().toString());
        map.put("unlocalizedName", b.getUnlocalizedName());
        map.put("localizedName", b.getLocalizedName());
        map.put("states", getStates(b));
        map.put("material", getMaterial(b));
        return Lists.newArrayList(map);
    }

    private Map<String, Map> getStates(Block b) {
        Map<String, Map> map = new LinkedHashMap<>();
        BlockStateContainer bs = b.getBlockState();
        Collection<IProperty<?>> props = bs.getProperties();
        for (IProperty prop : props) {
            map.put(prop.getName(), dataValues(b, prop));
        }

        return map;
    }

    private final Vec3d[] rotations = {
            new Vec3d(0, 0, -1),
            new Vec3d(0.5, 0, -1),
            new Vec3d(1, 0, -1),
            new Vec3d(1, 0, -0.5),
            new Vec3d(1, 0, 0),
            new Vec3d(1, 0, 0.5),
            new Vec3d(1, 0, 1),
            new Vec3d(0.5, 0, 1),
            new Vec3d(0, 0, 1),
            new Vec3d(-0.5, 0, 1),
            new Vec3d(-1, 0, 1),
            new Vec3d(-1, 0, 0.5),
            new Vec3d(-1, 0, 0),
            new Vec3d(-1, 0, -0.5),
            new Vec3d(-1, 0, -1),
            new Vec3d(-0.5, 0, -1)
    };


    private Vec3i addDirection(Object orig, Vec3i addend) {
        if (orig instanceof Vec3i) {
            Vec3i ov = ((Vec3i) orig);
            return new Vec3i(addend.getX() + ov.getX(), addend.getY() + ov.getY(), addend.getZ() + ov.getZ());
        }
        return addend;
    }

    private Map<String, Object> dataValues(Block b, IProperty prop) {
        //BlockState bs = b.getBlockState();
        IBlockState base = b.getStateFromMeta(0);

        Map<String, Object> dataMap = new LinkedHashMap<>();
        Map<String, Object> valueMap = new LinkedHashMap<>();
        List<Integer> dvs = new ArrayList<>();
        for (Comparable val : (Iterable<Comparable>) prop.getAllowedValues()) {
            Map<String, Object> stateMap = new LinkedHashMap<>();
            int dv = b.getMetaFromState(base.withProperty(prop, val));
            stateMap.put("data", dv);

            Map<String, Object> addAfter = null;
            String addAfterName = null;

            dvs.add(dv);

            if (prop instanceof PropertyDirection) {
                Vec3i vec = EnumFacing.byName(val.toString()).getDirectionVec();
                stateMap.put("direction", addDirection(stateMap.get("direction"), vec));
            } else if (prop.getName().equals("half")) {
                if (prop.getName(val).equals("top")) {
                    stateMap.put("direction", addDirection(stateMap.get("direction"), new Vec3i(0, 1, 0)));
                } else if (prop.getName(val).equals("bottom")) {
                    stateMap.put("direction", addDirection(stateMap.get("direction"), new Vec3i(0, -1, 0)));
                }
            } else if (prop.getName().equals("axis")) {
                switch (prop.getName(val)) {
                    case "x":
                        stateMap.put("direction", new Vec3i(1, 0, 0));
                        addAfter = new LinkedHashMap<>();
                        addAfter.put("data", dv);
                        addAfter.put("direction", new Vec3i(-1, 0, 0));
                        addAfterName = "-x";
                        break;
                    case "y":
                        stateMap.put("direction", new Vec3i(0, 1, 0));
                        addAfter = new LinkedHashMap<>();
                        addAfter.put("data", dv);
                        addAfter.put("direction", new Vec3i(0, -1, 0));
                        addAfterName = "-y";
                        break;
                    case "z":
                        stateMap.put("direction", new Vec3i(0, 0, 1));
                        addAfter = new LinkedHashMap<>();
                        addAfter.put("data", dv);
                        addAfter.put("direction", new Vec3i(0, 0, -1));
                        addAfterName = "-z";
                        break;
                }
            } else if (prop.getName().equals("rotation")) {
                stateMap.put("direction", rotations[Integer.valueOf(prop.getName(val))]);
            } else if (prop.getName().equals("facing")) { // usually already instanceof PropertyDirection, unless it's a lever
                switch (prop.getName(val)) {
                    case "south":
                        stateMap.put("direction", new Vec3i(0, 0, 1));
                        break;
                    case "north":
                        stateMap.put("direction", new Vec3i(0, 0, -1));
                        break;
                    case "west":
                        stateMap.put("direction", new Vec3i(-1, 0, 0));
                        break;
                    case "east":
                        stateMap.put("direction", new Vec3i(1, 0, 0));
                        break;
                }
                /*
                // TODO fix these levers. they disappear right now
                // excluding them just means they won't get rotated
                } else if (prop.getName(val).equals("up_x")) {
                    stateMap.put("direction", new Vec3i(1, 1, 0));
                    addAfter = new LinkedHashMap<String, Object>();
                    addAfter.put("data", dv);
                    addAfter.put("direction", new Vec3i(-1, 1, 0));
                    addAfterName = "up_-x";
                } else if (prop.getName(val).equals("up_z")) {
                    stateMap.put("direction", new Vec3i(0, 1, 1));
                    addAfter = new LinkedHashMap<String, Object>();
                    addAfter.put("data", dv);
                    addAfter.put("direction", new Vec3i(0, 1, -1));
                    addAfterName = "up_-z";
                } else if (prop.getName(val).equals("down_x")) {
                    stateMap.put("direction", new Vec3i(1, -1, 0));
                    addAfter = new LinkedHashMap<String, Object>();
                    addAfter.put("data", dv);
                    addAfter.put("direction", new Vec3i(-1, -1, 0));
                    addAfterName = "down_-x";
                } else if (prop.getName(val).equals("down_z")) {
                    stateMap.put("direction", new Vec3i(0, -1, 1));
                    addAfter = new LinkedHashMap<String, Object>();
                    addAfter.put("data", dv);
                    addAfter.put("direction", new Vec3i(0, -1, -1));
                    addAfterName = "down_-z";
                }*/
            }
            valueMap.put(prop.getName(val), stateMap);
            if (addAfter != null) {
                valueMap.put(addAfterName, addAfter);
            }
        }

        // attempt to calc mask
        int dataMask = 0;
        for (int dv : dvs) {
            dataMask |= dv;
        }
        dataMap.put("dataMask", dataMask);

        dataMap.put("values", valueMap);
        return dataMap;
    }

    private Map<String, Object> getMaterial(Block b) {
        IBlockState bs = b.getDefaultState();
        Map<String, Object> map = new LinkedHashMap<>();
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

    public static class Vec3dAdapter extends TypeAdapter<Vec3d> {
        @Override
        public Vec3d read(final JsonReader in) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public void write(final JsonWriter out, final Vec3d vec) throws IOException {
            out.beginArray();
            out.value(vec.x);
            out.value(vec.y);
            out.value(vec.z);
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