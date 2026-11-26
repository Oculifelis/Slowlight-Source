package dev.slowlight;

import dev.slowlight.block.GloamWeathering;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import dev.slowlight.block.StrippableLogBlock;
import java.util.function.Supplier;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Weathered Gloamwood: planks, stairs and slabs left out under the sky go Faded, then Weathered, then Silvered, as
 * copper goes Exposed, Weathered, Oxidized, and so do every plank pattern and the fence, gate, door, trapdoor, button
 * and pressure plate, the logs, wood and signs. Each step also comes Waxed, which stops it where it is. Signs have no
 * waxed blocks: vanilla's honeycomb already waxes a sign, and that also stops it ageing. The steps and the waxing live in
 * data/neoforge/data_maps/block/{oxidizables,waxables}.json, written by tools/make_crafts.py and tools/make_weathering.py.
 */
public final class SlowlightWeathering {
    /** The steps after plain Gloamwood, with the colour each shows on a map. */
    public static final List<String> PHASES = List.of("faded", "weathered", "silvered");
    private static final List<WeatherState> AGES = List.of(WeatherState.EXPOSED, WeatherState.WEATHERED, WeatherState.OXIDIZED);
    private static final List<MapColor> COLOURS = List.of(MapColor.COLOR_LIGHT_BLUE, MapColor.CLAY, MapColor.WOOL);

    public static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = new ArrayList<>();
    private static final List<DeferredBlock<Block>> FLAMMABLE = new ArrayList<>();
    private static final List<DeferredBlock<Block>> LOGS = new ArrayList<>();
    /** Each step's wood type, for its sign textures. */
    public static final List<WoodType> WOODS = new ArrayList<>();
    /** The stepped standing and wall signs, then the hanging ones, for the sign block entities. */
    public static final List<DeferredBlock<Block>> SIGNS = new ArrayList<>();
    public static final List<DeferredBlock<Block>> HANGING_SIGNS = new ArrayList<>();

    static {
        for (int i = 0; i < PHASES.size(); i++) {
            WeatherState age = AGES.get(i);
            BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).mapColor(COLOURS.get(i));
            String p = PHASES.get(i) + "_gloamwood";
            DeferredBlock<Block> planks = add(SlowlightBlocks.BLOCKS.register(p + "_planks", () -> new GloamWeathering.Planks(age, props)));
            add(SlowlightBlocks.BLOCKS.register(p + "_stairs", () -> new GloamWeathering.Stairs(age, planks.get().defaultBlockState(), props)));
            add(SlowlightBlocks.BLOCKS.register(p + "_slab", () -> new GloamWeathering.Slab(age, props)));
        }
        // Waxed: the same four looks, held. Plain Gloamwood waxed too, for builders who want it never to change.
        for (int i = 0; i <= PHASES.size(); i++) {
            String p = "waxed_" + (i == 0 ? "" : PHASES.get(i - 1) + "_") + "gloamwood";
            BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                    .mapColor(i == 0 ? MapColor.TERRACOTTA_LIGHT_BLUE : COLOURS.get(i - 1));
            DeferredBlock<Block> planks = add(SlowlightBlocks.BLOCKS.register(p + "_planks", () -> new Block(props)));
            add(SlowlightBlocks.BLOCKS.register(p + "_stairs", () -> new StairBlock(planks.get().defaultBlockState(), props)));
            add(SlowlightBlocks.BLOCKS.register(p + "_slab", () -> new SlabBlock(props)));
        }
    }

    static {
        for (int i = 0; i <= PHASES.size(); i++) {
            for (boolean waxed : new boolean[] {false, true}) {
                if (i == 0 && !waxed) {
                    continue; // plain Gloamwood's own are in SlowlightBlocks and SlowlightDecor
                }
                WeatherState age = i == 0 ? WeatherState.UNAFFECTED : AGES.get(i - 1);
                MapColor colour = i == 0 ? MapColor.TERRACOTTA_LIGHT_BLUE : COLOURS.get(i - 1);
                String p = (waxed ? "waxed_" : "") + (i == 0 ? "" : PHASES.get(i - 1) + "_") + "gloamwood";
                family(p, age, colour, waxed);
            }
        }
    }

    // Logs and wood. An axe strips a log to the same step's stripped log, waxed or not.
    static {
        for (int i = 0; i <= PHASES.size(); i++) {
            for (boolean waxed : new boolean[] {false, true}) {
                if (i == 0 && !waxed) {
                    continue; // plain Gloamwood's are in SlowlightBlocks
                }
                WeatherState age = i == 0 ? WeatherState.UNAFFECTED : AGES.get(i - 1);
                MapColor colour = i == 0 ? MapColor.TERRACOTTA_LIGHT_BLUE : COLOURS.get(i - 1);
                String w = waxed ? "waxed_" : "";
                String p = i == 0 ? "" : PHASES.get(i - 1) + "_";
                DeferredBlock<Block> strippedLog = log(w + p + "stripped_gloamwood_log", age, waxed, null,
                        BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_OAK_LOG).mapColor(colour));
                DeferredBlock<Block> strippedWood = log(w + p + "stripped_gloamwood_wood", age, waxed, null,
                        BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_OAK_WOOD).mapColor(colour));
                log(w + p + "gloamwood_log", age, waxed, strippedLog, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG).mapColor(colour));
                log(w + p + "gloamwood_wood", age, waxed, strippedWood, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_WOOD).mapColor(colour));
            }
        }
    }

    // Signs, one set per step, each step its own wood type so it gets its own sign textures.
    static {
        for (int i = 0; i < PHASES.size(); i++) {
            WeatherState age = AGES.get(i);
            MapColor colour = COLOURS.get(i);
            String p = PHASES.get(i) + "_gloamwood";
            WoodType wood = WoodType.register(new WoodType(Slowlight.MOD_ID + ":" + p, Gloamwood.SET));
            WOODS.add(wood);
            DeferredBlock<Block> sign = SlowlightBlocks.BLOCKS.register(p + "_sign", () -> new GloamWeathering.Sign(age, wood,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SIGN).mapColor(colour)));
            DeferredBlock<Block> wallSign = SlowlightBlocks.BLOCKS.register(p + "_wall_sign", () -> new GloamWeathering.WallSign(age,
                    wood, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_WALL_SIGN).mapColor(colour).dropsLike(sign.get())));
            DeferredBlock<Block> hanging = SlowlightBlocks.BLOCKS.register(p + "_hanging_sign", () -> new GloamWeathering.HangingSign(age,
                    wood, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_HANGING_SIGN).mapColor(colour)));
            DeferredBlock<Block> wallHanging = SlowlightBlocks.BLOCKS.register(p + "_wall_hanging_sign",
                    () -> new GloamWeathering.WallHangingSign(age, wood, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_WALL_HANGING_SIGN)
                            .mapColor(colour).dropsLike(hanging.get())));
            SIGNS.add(sign);
            SIGNS.add(wallSign);
            HANGING_SIGNS.add(hanging);
            HANGING_SIGNS.add(wallHanging);
            BLOCK_ITEMS.add(SlowlightItems.ITEMS.<BlockItem>register(p + "_sign",
                    () -> new SignItem(new Item.Properties().stacksTo(16), sign.get(), wallSign.get())));
            BLOCK_ITEMS.add(SlowlightItems.ITEMS.<BlockItem>register(p + "_hanging_sign",
                    () -> new HangingSignItem(hanging.get(), wallHanging.get(), new Item.Properties().stacksTo(16))));
        }
    }

    /** A log, wood or stripped one; waxed ones are plain logs that still strip. */
    private static DeferredBlock<Block> log(String id, WeatherState age, boolean waxed, Supplier<? extends Block> stripped,
                                            BlockBehaviour.Properties props) {
        DeferredBlock<Block> block = SlowlightBlocks.BLOCKS.register(id, () -> waxed
                ? (stripped == null ? new RotatedPillarBlock(props) : new StrippableLogBlock(stripped, props))
                : new GloamWeathering.Log(age, stripped, props));
        item(block);
        LOGS.add(block);
        return block;
    }

    /** One step of the rest of the set: the plank patterns, then the fence, gate, door, trapdoor, button and plate. */
    private static void family(String p, WeatherState age, MapColor colour, boolean waxed) {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).mapColor(colour);
        for (String v : SlowlightDecor.PLANK_VARIANTS) {
            String n = p + "_planks_" + v;
            DeferredBlock<Block> planks = add(SlowlightBlocks.BLOCKS.register(n,
                    () -> waxed ? new Block(props) : new GloamWeathering.Planks(age, props)));
            add(SlowlightBlocks.BLOCKS.register(n + "_stairs", () -> waxed
                    ? new StairBlock(planks.get().defaultBlockState(), props)
                    : new GloamWeathering.Stairs(age, planks.get().defaultBlockState(), props)));
            add(SlowlightBlocks.BLOCKS.register(n + "_slab", () -> waxed ? new SlabBlock(props) : new GloamWeathering.Slab(age, props)));
        }
        BlockBehaviour.Properties fence = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE).mapColor(colour);
        add(SlowlightBlocks.BLOCKS.register(p + "_fence", () -> waxed ? new FenceBlock(fence) : new GloamWeathering.Fence(age, fence)));
        BlockBehaviour.Properties gate = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE_GATE).mapColor(colour);
        add(SlowlightBlocks.BLOCKS.register(p + "_fence_gate",
                () -> waxed ? new FenceGateBlock(Gloamwood.WOOD, gate) : new GloamWeathering.Gate(age, Gloamwood.WOOD, gate)));
        // Doors, trapdoors, buttons and plates do not burn, as oak's do not.
        BlockBehaviour.Properties door = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_DOOR).mapColor(colour);
        door(SlowlightBlocks.BLOCKS.register(p + "_door",
                () -> waxed ? new DoorBlock(Gloamwood.SET, door) : new GloamWeathering.Door(age, Gloamwood.SET, door)));
        BlockBehaviour.Properties trapdoor = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_TRAPDOOR).mapColor(colour);
        item(SlowlightBlocks.BLOCKS.register(p + "_trapdoor",
                () -> waxed ? new TrapDoorBlock(Gloamwood.SET, trapdoor) : new GloamWeathering.TrapDoor(age, Gloamwood.SET, trapdoor)));
        // Held 1 tick, as the plain Gloamwood button is.
        BlockBehaviour.Properties button = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_BUTTON);
        item(SlowlightBlocks.BLOCKS.register(p + "_button",
                () -> waxed ? new ButtonBlock(Gloamwood.SET, 1, button) : new GloamWeathering.Button(age, Gloamwood.SET, 1, button)));
        BlockBehaviour.Properties plate = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PRESSURE_PLATE).mapColor(colour);
        item(SlowlightBlocks.BLOCKS.register(p + "_pressure_plate",
                () -> waxed ? new PressurePlateBlock(Gloamwood.SET, plate) : new GloamWeathering.Plate(age, Gloamwood.SET, plate)));
    }

    private static DeferredBlock<Block> add(DeferredBlock<Block> block) {
        item(block);
        FLAMMABLE.add(block);
        return block;
    }

    private static DeferredBlock<Block> item(DeferredBlock<Block> block) {
        BLOCK_ITEMS.add(SlowlightItems.ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    /** A door's item places both halves, as oak's does. */
    private static void door(DeferredBlock<Block> block) {
        BLOCK_ITEMS.add(SlowlightItems.ITEMS.<BlockItem>register(block.getId().getPath(),
                () -> new DoubleHighBlockItem(block.get(), new Item.Properties())));
    }

    /** Burns like the planks they were. Called from Gloamwood.setup. */
    static void setFlammable(FireBlock fire) {
        FLAMMABLE.forEach(b -> fire.setFlammable(b.get(), 5, 20));
        LOGS.forEach(b -> fire.setFlammable(b.get(), 5, 5));
    }

    static void fillTab(CreativeModeTab.Output output) {
        BLOCK_ITEMS.forEach(i -> output.accept(i.get()));
    }

    /** Loads the class, so everything above is registered before the registries close. */
    static void init() {
    }

    private SlowlightWeathering() {
    }
}
