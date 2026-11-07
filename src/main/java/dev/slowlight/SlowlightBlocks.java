package dev.slowlight;

import dev.slowlight.block.GloamWeathering;
import dev.slowlight.block.DuskbloomCropBlock;
import dev.slowlight.block.EmberKilnBlock;
import dev.slowlight.block.KeepsakeUrnBlock;
import dev.slowlight.block.SandglassBlock;
import dev.slowlight.block.SundialBlock;
import dev.slowlight.block.GloamwoodSaplingBlock;
import dev.slowlight.block.LanternfruitBlock;
import dev.slowlight.block.StrippableLogBlock;
import dev.slowlight.block.LongKilnBlock;
import dev.slowlight.block.PatientKilnBlock;
import dev.slowlight.block.SlowlightLanternBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SlowlightBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Slowlight.MOD_ID);

    public static final DeferredBlock<Block> PATIENT_KILN = BLOCKS.register("patient_kiln",
            () -> new PatientKilnBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.DEEPSLATE_TILES)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> LONG_KILN = BLOCKS.register("long_kiln",
            () -> new LongKilnBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.DEEPSLATE_TILES)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> SLOWLIGHT_LANTERN = BLOCKS.register("slowlight_lantern",
            () -> new SlowlightLanternBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(0.4F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> state.getValue(SlowlightLanternBlock.LIT) ? 15 : 7)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .randomTicks()));

    // The Duskbloom, a night crop the sniffer digs up seeds for. It grows only after dark; ripe, it glows faintly.
    public static final DeferredBlock<Block> DUSKBLOOM_CROP = BLOCKS.register("duskbloom_crop",
            () -> new DuskbloomCropBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT)
                    .lightLevel(state -> state.getValue(DuskbloomCropBlock.AGE) == DuskbloomCropBlock.MAX_AGE
                            ? DuskbloomCropBlock.GLOW : 0)));

    // ---- branch rest: Sandglass, Keepsake Urn, Ember Kiln (begin) ----

    // An hourglass of copper and glass that measures days; its shard sand glows faintly.
    public static final DeferredBlock<Block> SANDGLASS = BLOCKS.register("sandglass",
            () -> new SandglassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> state.getValue(SandglassBlock.RUNNING) ? 4 : 1)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    // A terracotta urn, nine slots, sealed with wax for days at a time.
    public static final DeferredBlock<Block> KEEPSAKE_URN = BLOCKS.register("keepsake_urn",
            () -> new KeepsakeUrnBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_ORANGE)
                    .strength(1.25F, 4.2F)
                    .sound(SoundType.DECORATED_POT)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    // The Patient Kiln in blackstone: works only in the Nether, at half the wait.
    public static final DeferredBlock<Block> EMBER_KILN = BLOCKS.register("ember_kiln",
            () -> new EmberKilnBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.POLISHED_DEEPSLATE)
                    .lightLevel(state -> state.getValue(EmberKilnBlock.WORKING) ? 6 : 0)
                    .requiresCorrectToolForDrops()));

    // A smooth stone dial with a copper gnomon; a comparator reads the sun's hour off it.
    public static final DeferredBlock<Block> SUNDIAL = BLOCKS.register("sundial",
            () -> new SundialBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    // ---- branch rest (end) ----
    // Gloamwood, the sniffer's tree, and its whole wood set. Each block copies its oak twin; only the colours differ.
    private static final MapColor BARK = MapColor.COLOR_LIGHT_GRAY;
    private static final MapColor WOOD = MapColor.TERRACOTTA_LIGHT_BLUE;

    public static final DeferredBlock<Block> STRIPPED_GLOAMWOOD_LOG = BLOCKS.register("stripped_gloamwood_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_OAK_LOG).mapColor(WOOD)));
    public static final DeferredBlock<Block> STRIPPED_GLOAMWOOD_WOOD = BLOCKS.register("stripped_gloamwood_wood",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_OAK_WOOD).mapColor(WOOD)));
    public static final DeferredBlock<Block> GLOAMWOOD_LOG = BLOCKS.register("gloamwood_log",
            () -> new StrippableLogBlock(STRIPPED_GLOAMWOOD_LOG,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG).mapColor(state -> BARK)));
    public static final DeferredBlock<Block> GLOAMWOOD_WOOD = BLOCKS.register("gloamwood_wood",
            () -> new StrippableLogBlock(STRIPPED_GLOAMWOOD_WOOD,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_WOOD).mapColor(BARK)));
    // The first of the weathering steps (SlowlightWeathering has the rest): out in the open they silver like copper.
    public static final DeferredBlock<Block> GLOAMWOOD_PLANKS = BLOCKS.register("gloamwood_planks",
            () -> new GloamWeathering.Planks(WeatheringCopper.WeatherState.UNAFFECTED,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).mapColor(WOOD)));
    public static final DeferredBlock<Block> GLOAMWOOD_STAIRS = BLOCKS.register("gloamwood_stairs",
            () -> new GloamWeathering.Stairs(WeatheringCopper.WeatherState.UNAFFECTED, GLOAMWOOD_PLANKS.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_STAIRS).mapColor(WOOD)));
    public static final DeferredBlock<Block> GLOAMWOOD_SLAB = BLOCKS.register("gloamwood_slab",
            () -> new GloamWeathering.Slab(WeatheringCopper.WeatherState.UNAFFECTED,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SLAB).mapColor(WOOD)));
    public static final DeferredBlock<Block> GLOAMWOOD_FENCE = BLOCKS.register("gloamwood_fence",
            () -> new FenceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE).mapColor(WOOD)));
    public static final DeferredBlock<Block> GLOAMWOOD_FENCE_GATE = BLOCKS.register("gloamwood_fence_gate",
            () -> new FenceGateBlock(Gloamwood.WOOD, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE_GATE).mapColor(WOOD)));
    public static final DeferredBlock<Block> GLOAMWOOD_DOOR = BLOCKS.register("gloamwood_door",
            () -> new DoorBlock(Gloamwood.SET, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_DOOR).mapColor(WOOD)));
    public static final DeferredBlock<Block> GLOAMWOOD_TRAPDOOR = BLOCKS.register("gloamwood_trapdoor",
            () -> new TrapDoorBlock(Gloamwood.SET, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_TRAPDOOR).mapColor(WOOD)));
    public static final DeferredBlock<Block> GLOAMWOOD_BUTTON = BLOCKS.register("gloamwood_button",
            () -> new ButtonBlock(Gloamwood.SET, 30, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_BUTTON)));
    public static final DeferredBlock<Block> GLOAMWOOD_PRESSURE_PLATE = BLOCKS.register("gloamwood_pressure_plate",
            () -> new PressurePlateBlock(Gloamwood.SET,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PRESSURE_PLATE).mapColor(WOOD)));
    public static final DeferredBlock<Block> GLOAMWOOD_SIGN = BLOCKS.register("gloamwood_sign",
            () -> new StandingSignBlock(Gloamwood.WOOD, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SIGN).mapColor(WOOD)));
    public static final DeferredBlock<Block> GLOAMWOOD_WALL_SIGN = BLOCKS.register("gloamwood_wall_sign",
            () -> new WallSignBlock(Gloamwood.WOOD, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_WALL_SIGN).mapColor(WOOD)
                    .dropsLike(GLOAMWOOD_SIGN.get())));
    public static final DeferredBlock<Block> GLOAMWOOD_HANGING_SIGN = BLOCKS.register("gloamwood_hanging_sign",
            () -> new CeilingHangingSignBlock(Gloamwood.WOOD,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_HANGING_SIGN).mapColor(WOOD)));
    public static final DeferredBlock<Block> GLOAMWOOD_WALL_HANGING_SIGN = BLOCKS.register("gloamwood_wall_hanging_sign",
            () -> new WallHangingSignBlock(Gloamwood.WOOD,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_WALL_HANGING_SIGN).mapColor(WOOD)
                    .dropsLike(GLOAMWOOD_HANGING_SIGN.get())));
    public static final DeferredBlock<Block> GLOAMWOOD_LEAVES = BLOCKS.register("gloamwood_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES).mapColor(MapColor.COLOR_BLUE)));
    public static final DeferredBlock<Block> GLOAMWOOD_SAPLING = BLOCKS.register("gloamwood_sapling",
            () -> new GloamwoodSaplingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING).mapColor(MapColor.COLOR_BLUE)));
    public static final DeferredBlock<Block> POTTED_GLOAMWOOD_SAPLING = BLOCKS.register("potted_gloamwood_sapling",
            () -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, GLOAMWOOD_SAPLING,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_OAK_SAPLING)));

    // Gloamwood's fruit, hanging under its leaves, and the lamp its peel makes.
    public static final DeferredBlock<Block> LANTERNFRUIT = BLOCKS.register("hanging_lanternfruit",
            () -> new LanternfruitBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COCOA)
                    .mapColor(MapColor.COLOR_YELLOW)
                    .lightLevel(state -> state.getValue(LanternfruitBlock.AGE) == LanternfruitBlock.MAX_AGE
                            ? LanternfruitBlock.GLOW : 0)));
    public static final DeferredBlock<Block> LANTERNFRUIT_LAMP = BLOCKS.register("lanternfruit_lamp",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.SHROOMLIGHT)
                    .mapColor(MapColor.COLOR_YELLOW)
                    .lightLevel(state -> 12)));

    private SlowlightBlocks() {
    }
}
