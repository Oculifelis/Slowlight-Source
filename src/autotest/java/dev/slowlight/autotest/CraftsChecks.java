package dev.slowlight.autotest;

import dev.slowlight.SlowlightBlocks;
import dev.slowlight.SlowlightCrafts;
import dev.slowlight.block.FlowerPressBlockEntity;
import dev.slowlight.block.PressedFlowersBlock;
import dev.slowlight.block.PressedFlowersBlockEntity;
import dev.slowlight.block.SlowCandleBlock;
import dev.slowlight.block.SlowCandleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import static dev.slowlight.autotest.ServerChecks.check;
import static dev.slowlight.autotest.ServerChecks.info;

/**
 * Play-test checks for weathered Gloamwood, the Flower Press and the Slow Candle, in a patch at x 66..72, z 12.
 * Time is skipped by rewriting the block entities' saved start times, not by waiting days.
 */
@EventBusSubscriber(modid = "slowlight_autotest")
public final class CraftsChecks {
    private static final int Y = -60;
    private static final BlockPos OUT = new BlockPos(66, Y, 12);
    private static final BlockPos IN = new BlockPos(68, Y, 12);
    private static final BlockPos PRESS = new BlockPos(70, Y, 12);
    private static final BlockPos CANDLE = new BlockPos(72, Y, 12);
    private static final long DAY = 24000L;

    private static int t = -1;
    /** The client asks for the world view of everything new (ClientDriver step 82); the server says when it's built. */
    public static volatile boolean clientWantsView;
    public static volatile boolean viewReady;
    /** Then a second look, straight down at the flowers on the ground. */
    public static volatile boolean clientWantsGround;
    public static volatile boolean groundReady;

    private CraftsChecks() {
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("slowlight", id));
    }

    static void start(ServerLevel level) {
        t = 0;
        Block planks = SlowlightBlocks.GLOAMWOOD_PLANKS.get();
        check("weathering.steps", WeatheringCopper.getNext(planks).orElse(null) == block("faded_gloamwood_planks")
                        && WeatheringCopper.getNext(block("weathered_gloamwood_stairs")).orElse(null) == block("silvered_gloamwood_stairs")
                        && WeatheringCopper.getNext(block("silvered_gloamwood_slab")).isEmpty()
                        && WeatheringCopper.getPrevious(block("faded_gloamwood_planks")).orElse(null) == planks,
                "Gloamwood -> Faded -> Weathered -> Silvered, from the oxidizables data map; an axe scrapes back");
        check("weathering.waxes", HoneycombItem.getWaxed(block("silvered_gloamwood_planks").defaultBlockState())
                        .map(BlockState::getBlock).orElse(null) == block("waxed_silvered_gloamwood_planks")
                        && HoneycombItem.getWaxed(planks.defaultBlockState()).map(BlockState::getBlock).orElse(null) == block("waxed_gloamwood_planks")
                        && WeatheringCopper.getNext(block("waxed_faded_gloamwood_planks")).isEmpty(),
                "a honeycomb waxes each step, and waxed ones stay");
        check("weathering.planks_tag", block("silvered_gloamwood_planks").defaultBlockState().is(BlockTags.PLANKS),
                "weathered planks are still #planks");

        // One plank in the open, one shut in stone: give each a few hundred random ticks.
        level.setBlock(OUT, planks.defaultBlockState(), 3);
        level.setBlock(IN, planks.defaultBlockState(), 3);
        for (Direction d : Direction.values()) {
            if (d != Direction.DOWN) {
                level.setBlock(IN.relative(d), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int i = 0; i < 2000; i++) {
            for (BlockPos p : new BlockPos[]{OUT, IN}) {
                BlockState s = level.getBlockState(p);
                if (s.isRandomlyTicking()) {
                    s.randomTick(level, p, level.random);
                }
            }
        }
        check("weathering.outdoors", level.getBlockState(OUT).getBlock() != planks,
                "2000 random ticks in the open: now " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(OUT).getBlock()));
        check("weathering.indoors_keeps", level.getBlockState(IN).getBlock() == planks,
                "2000 random ticks shut in stone: still " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(IN).getBlock()));

        // The press: a poppy goes in, dirt does not; then three days are skipped.
        level.setBlock(PRESS, SlowlightCrafts.FLOWER_PRESS.get().defaultBlockState(), 3);
        FlowerPressBlockEntity press = (FlowerPressBlockEntity) level.getBlockEntity(PRESS);
        check("press.only_flowers", !press.insert(new ItemStack(Items.DIRT)) && press.insert(new ItemStack(Items.POPPY)),
                "#slowlight:pressable takes a poppy and not dirt");
        CompoundTag tag = press.saveWithoutMetadata(level.registryAccess());
        tag.putLong("Since", level.getGameTime() - 3 * DAY - 1);
        press.loadWithComponents(tag, level.registryAccess());

        // The candle: lit, with a day and a half already burned.
        level.setBlock(CANDLE.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(CANDLE, SlowlightCrafts.SLOW_CANDLE.get().defaultBlockState(), 3);
        BlockState c = level.getBlockState(CANDLE);
        check("candle.lights_like_a_candle", CandleBlock.canLight(c), "flint and steel and fire charges can light it");
        // Through the whole click, as a player would: flint and steel lights it (not the status message).
        FakePlayer lighter = FakePlayerFactory.getMinecraft(level);
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        lighter.setItemInHand(InteractionHand.MAIN_HAND, flint);
        lighter.gameMode.useItemOn(lighter, level, flint, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(CANDLE), Direction.UP, CANDLE, false));
        check("candle.flint_lights", level.getBlockState(CANDLE).getValue(SlowCandleBlock.LIT),
                "flint and steel used on the unlit candle: " + level.getBlockState(CANDLE));
        c = level.getBlockState(CANDLE);
        level.setBlock(CANDLE, c.setValue(SlowCandleBlock.LIT, true), 3);
        SlowCandleBlockEntity candle = (SlowCandleBlockEntity) level.getBlockEntity(CANDLE);
        CompoundTag ct = candle.saveWithoutMetadata(level.registryAccess());
        ct.putLong("Burned", DAY + DAY / 2);
        ct.putString("State", "lit");
        ct.putLong("LitAt", level.getGameTime());
        candle.loadWithComponents(ct, level.registryAccess());
        // The burn saves whole and comes back the same; a save from before State still loads lit, and a lit State
        // without its start time loads out rather than half lit.
        CompoundTag again = candle.saveWithoutMetadata(level.registryAccess());
        SlowCandleBlockEntity copy = new SlowCandleBlockEntity(CANDLE, level.getBlockState(CANDLE));
        copy.loadWithComponents(again, level.registryAccess());
        CompoundTag legacy = again.copy();
        legacy.remove("State");
        SlowCandleBlockEntity old = new SlowCandleBlockEntity(CANDLE, level.getBlockState(CANDLE));
        old.loadWithComponents(legacy, level.registryAccess());
        CompoundTag half = again.copy();
        half.remove("LitAt");
        SlowCandleBlockEntity broken = new SlowCandleBlockEntity(CANDLE, level.getBlockState(CANDLE));
        broken.loadWithComponents(half, level.registryAccess());
        check("candle.burn_saves", candle.burn() == SlowCandleBlockEntity.Burn.LIT
                        && copy.saveWithoutMetadata(level.registryAccess()).equals(again)
                        && old.burn() == SlowCandleBlockEntity.Burn.LIT
                        && broken.burn() == SlowCandleBlockEntity.Burn.OUT && !broken.saveWithoutMetadata(level.registryAccess()).contains("LitAt"),
                "saved " + again + "; old save " + old.burn() + "; lit without LitAt " + broken.burn());

        // Pressed flowers laid down by the item, as a player would: four on the ground share a block, a fifth won't
        // fit; one used on the side of a block goes on the wall. Breaking gives each back, still its own flower.
        BlockPos base = new BlockPos(74, Y - 1, 12);
        BlockPos onFloor = base.above();
        BlockPos stone = new BlockPos(76, Y, 12);
        level.setBlock(onFloor, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(stone.west(), Blocks.AIR.defaultBlockState(), 3);
        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        Item[] laid = {Items.POPPY, Items.CORNFLOWER, Items.ALLIUM, Items.DANDELION, Items.AZURE_BLUET};
        int took = 0;
        for (Item f : laid) {
            if (use(level, fake, f, base, Direction.UP)) {
                took++;
            }
        }
        BlockState fs = level.getBlockState(onFloor);
        check("pressed.floor_quarters", took == 4 && fs.getBlock() instanceof PressedFlowersBlock
                        && fs.getValue(PressedFlowersBlock.FLOWERS) == 4 && fs.getValue(PressedFlowersBlock.FACE) == AttachFace.FLOOR
                        && level.getBlockEntity(onFloor) instanceof PressedFlowersBlockEntity be && be.flowers().size() == 4,
                "five used on the ground: " + took + " laid, state " + fs);
        java.util.List<ItemStack> drops = Block.getDrops(fs, level, onFloor, level.getBlockEntity(onFloor));
        check("pressed.drops_each", drops.size() == 4 && drops.get(1).get(SlowlightCrafts.PRESSED_FROM.get()) != null
                        && drops.get(1).get(SlowlightCrafts.PRESSED_FROM.get()).value() == Items.CORNFLOWER,
                "breaking it gives back " + drops);
        boolean wallOk = use(level, fake, Items.OXEYE_DAISY, stone, Direction.WEST);
        BlockState ws = level.getBlockState(stone.west());
        check("pressed.on_wall", wallOk && ws.getBlock() instanceof PressedFlowersBlock
                        && ws.getValue(PressedFlowersBlock.FACE) == AttachFace.WALL && ws.getValue(PressedFlowersBlock.FACING) == Direction.WEST,
                "used on the west side of stone: " + ws);

        // A glowing flower still glows pressed: glow lichen's light (7) passes to the block, a poppy alone gives none.
        BlockPos glowBase = new BlockPos(78, Y - 1, 12);
        level.setBlock(glowBase.above(), Blocks.AIR.defaultBlockState(), 3);
        use(level, fake, Items.POPPY, glowBase, Direction.UP);
        int dark = level.getBlockState(glowBase.above()).getLightEmission();
        use(level, fake, Items.GLOW_LICHEN, glowBase, Direction.UP);
        BlockState gs = level.getBlockState(glowBase.above());
        check("pressed.inherits_glow", dark == 0 && gs.getBlock() instanceof PressedFlowersBlock && gs.getLightEmission() == 7,
                "poppy alone gives " + dark + ", with pressed glow lichen " + gs.getLightEmission() + " (" + gs + ")");
    }

    /** A fake player uses one Pressed Flower of this flower on this face of this block; true if it was laid. */
    private static boolean use(ServerLevel level, FakePlayer player, Item flower, BlockPos pos, Direction face) {
        ItemStack stack = SlowlightCrafts.pressed(flower.builtInRegistryHolder());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(pos).relative(face, 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false)));
        return stack.isEmpty();
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (clientWantsView) {
            clientWantsView = false;
            buildView(event.getServer().overworld());
        }
        if (clientWantsGround) {
            clientWantsGround = false;
            for (var p : event.getServer().getPlayerList().getPlayers()) {
                p.connection.teleport(60.5, Y + 0.6, 33.0, 180F, 62F);
            }
            groundReady = true;
        }
        if (t < 0 || ++t != 45) {
            return;
        }
        t = -1;
        ServerLevel level = event.getServer().overworld();
        try {
            FlowerPressBlockEntity press = (FlowerPressBlockEntity) level.getBlockEntity(PRESS);
            ItemStack out = press.held();
            check("press.presses", press.done() && out.is(SlowlightCrafts.PRESSED_FLOWER.get())
                            && out.get(SlowlightCrafts.PRESSED_FROM.get()) != null
                            && out.get(SlowlightCrafts.PRESSED_FROM.get()).value() == Items.POPPY,
                    "after three days: " + out + " from " + out.get(SlowlightCrafts.PRESSED_FROM.get()));
            check("press.names_it", out.getHoverName().getString().equals("Pressed Poppy"), "named '" + out.getHoverName().getString() + "'");
            check("press.comparator", level.getBlockState(PRESS).getAnalogOutputSignal(level, PRESS) == 15, "a done press reads 15");
            BlockState c = level.getBlockState(CANDLE);
            check("candle.burns_down", c.is(SlowlightCrafts.SLOW_CANDLE.get()) && c.getValue(SlowCandleBlock.STAGE) == 3
                            && c.getLightEmission(level, CANDLE) == 12,
                    "a day and a half lit: stage " + (c.hasProperty(SlowCandleBlock.STAGE) ? c.getValue(SlowCandleBlock.STAGE) : -1)
                            + " of 4, light " + c.getLightEmission(level, CANDLE));
            info("crafts", "done");
        } catch (Throwable e) {
            ServerChecks.LOG.error("SLTEST FAIL crafts.exception :: crafts check threw", e);
        }
    }

    /** Everything new in one view, by daylight: a Gloamwood wall with pressed flowers on it, flowers on the ground in
     * ones to fours, the four weathering steps with a lit Slow Candle on each (full down to one day left), and an empty
     * and a full press. The player stands south of it, looking north. */
    private static void buildView(ServerLevel level) {
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set 6000");
        int z = 30;
        for (int x = 57; x <= 74; x++) {
            for (int dz = -1; dz <= 6; dz++) {
                for (int y = Y; y <= Y + 3; y++) {
                    level.setBlock(new BlockPos(x, y, z + dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
            for (int y = Y; y <= Y + 2; y++) {
                level.setBlock(new BlockPos(x, y, z - 1), SlowlightBlocks.GLOAMWOOD_PLANKS.get().defaultBlockState(), 3);
            }
        }
        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        Item[][] wall = {{Items.POPPY, Items.CORNFLOWER, Items.OXEYE_DAISY, Items.ALLIUM}, {Items.BLUE_ORCHID, Items.LILY_OF_THE_VALLEY},
                {Items.TORCHFLOWER, Items.PINK_TULIP, Items.AZURE_BLUET}};
        for (int i = 0; i < wall.length; i++) {
            for (Item f : wall[i]) {
                use(level, fake, f, new BlockPos(60 + i, Y + 1, z - 1), Direction.SOUTH);
            }
        }
        Item[] ground = {Items.DANDELION, Items.RED_TULIP, Items.CORNFLOWER, Items.ORANGE_TULIP, Items.WHITE_TULIP,
                Items.ALLIUM, Items.POPPY, Items.OXEYE_DAISY, Items.BLUE_ORCHID, Items.LILY_OF_THE_VALLEY};
        int g = 0;
        for (int n = 1; n <= 4; n++) {
            for (int k = 0; k < n; k++) {
                use(level, fake, ground[g++ % ground.length], new BlockPos(58 + n, Y - 1, z + 1), Direction.UP);
            }
        }
        String[] steps = {"gloamwood_planks", "faded_gloamwood_planks", "weathered_gloamwood_planks", "silvered_gloamwood_planks"};
        for (int i = 0; i < 4; i++) {
            BlockPos p = new BlockPos(64 + i, Y, z);
            level.setBlock(p, block(steps[i]).defaultBlockState(), 3);
            level.setBlock(p.above(), SlowlightCrafts.SLOW_CANDLE.get().defaultBlockState()
                    .setValue(SlowCandleBlock.STAGE, 4 - i).setValue(SlowCandleBlock.LIT, true), 3);
        }
        BlockState press = SlowlightCrafts.FLOWER_PRESS.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH);
        level.setBlock(new BlockPos(64, Y, z + 2), press, 3);
        BlockPos full = new BlockPos(66, Y, z + 2);
        level.setBlock(full, press, 3);
        if (level.getBlockEntity(full) instanceof FlowerPressBlockEntity be) {
            be.insert(new ItemStack(Items.POPPY));
        }
        for (var p : server.getPlayerList().getPlayers()) {
            p.connection.teleport(63.5, Y + 1, z + 5.0, 180F, 36F);
        }
        viewReady = true;
    }
}
