package dev.slowlight.autotest;

import com.mojang.logging.LogUtils;
import dev.slowlight.SlowlightBlocks;
import dev.slowlight.SlowlightItems;
import dev.slowlight.recipe.AgeingRecipe;
import dev.slowlight.SlowlightRecipes;
import dev.slowlight.block.LongKilnBlock;
import dev.slowlight.block.PatientKilnBlock;
import dev.slowlight.block.PatientKilnBlockEntity;
import dev.slowlight.block.SlowlightLanternBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server half of the play-test. Runs on the integrated server's tick, as a little state machine: set the scene,
 * use the blocks the way a player would (through ServerPlayerGameMode), sprint the world seven days, then read
 * everything back. Every check logs one "SLTEST PASS|FAIL|INFO" line.
 */
@EventBusSubscriber(modid = "slowlight_autotest")
public final class ServerChecks {
    static final Logger LOG = LogUtils.getLogger();
    static final boolean ON = System.getProperty("slowlight.autotest") != null;

    /** Handshake with the client thread. */
    static volatile int phase;
    static final int WAIT_CLIENT = 0, SCENE_READY = 1, SPRINT = 2, NIGHT_READY = 3, DONE = 4, KILNS_READY = 5, KILNS_CHECKED = 6,
            LONG_READY = 7, LONG_CHECKED = 8;
    static volatile boolean clientWantsSprint;
    static volatile boolean clientWantsWideView;
    static volatile boolean clientWantsKilnView;
    static volatile boolean clientTookKilnShot;
    static volatile boolean clientWantsLongView;
    static volatile boolean clientTookLongShot;

    private static final int Y = -60;
    private static final BlockPos K1 = new BlockPos(0, Y, 0);          // honey -> mead, with comparator
    private static final BlockPos COMP = new BlockPos(0, Y, 1);
    private static final BlockPos K2 = new BlockPos(3, Y + 1, 0);      // hopper-fed berries
    private static final BlockPos HOP_IN = K2.above();
    private static final BlockPos HOP_OUT = K2.below();
    private static final BlockPos KSNEAK = new BlockPos(-3, Y, 0);     // sneak-click takes the batch back
    private static final BlockPos KJUNK = new BlockPos(-5, Y, 0);      // something with no recipe
    private static final BlockPos LANTERN = new BlockPos(-8, Y, 0);
    private static final BlockPos JUKEBOX = new BlockPos(-10, Y, 0);
    private static final BlockPos KBREAK_PICK = new BlockPos(-12, Y, 3);
    private static final BlockPos KBREAK_HAND = new BlockPos(-14, Y, 3);
    private static final BlockPos LBREAK = new BlockPos(-16, Y, 3);
    private static final BlockPos ROOF = new BlockPos(-6, Y + 3, 3);
    private static final BlockPos LK1 = new BlockPos(-3, Y, -4);       // Long Kiln, a stack of honey by hand
    private static final BlockPos LK2 = new BlockPos(0, Y, -4);        // Long Kiln, fed mud one at a time by a hopper
    private static final BlockPos LK2_IN = LK2.above();
    private static final BlockPos KRAIN = new BlockPos(30, Y, 0);      // dirt, wants rain; the weather stays clear
    private static final BlockPos KROOF = new BlockPos(32, Y, 0);      // kelp, wants open sky; stone two above
    private static final BlockPos DIG = new BlockPos(34, Y, 0);        // where the kiln-made suspicious gravel is placed

    /** The other kilns: position, what goes in, what should come out after the sprint. */
    private record Batch(BlockPos pos, Item in, int inCount, Item out, int outCount, int days) {
    }

    private static final List<Batch> BATCHES = new ArrayList<>();

    private static int ticks;
    private static long sprintStart = -1;
    private static long k1Start = -1;
    private static final Map<Long, String> comparatorSamples = new LinkedHashMap<>();
    private static int nightWait;
    private static boolean comparatorAllMatch = true;

    private ServerChecks() {
    }

    static void pass(String name, String detail) {
        LOG.info("SLTEST PASS {} :: {}", name, detail);
    }

    static void fail(String name, String detail) {
        LOG.info("SLTEST FAIL {} :: {}", name, detail);
    }

    static void info(String name, String detail) {
        LOG.info("SLTEST INFO {} :: {}", name, detail);
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass(name, detail);
        } else {
            fail(name, detail);
        }
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (!ON) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer player = players.get(0);
        ServerLevel level = server.overworld();
        try {
            step(server, level, player);
        } catch (Throwable t) {
            LOG.error("SLTEST FAIL exception :: server step threw", t);
            phase = DONE;
        }
    }

    private static void step(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        ticks++;
        if (ticks == 40) {
            setScene(server, level, player);
        } else if (ticks == 60) {
            interact(level, player);
        } else if (ticks == 120) {
            firstLook(level, player);
            player.connection.teleport(0.5, Y, 3.5, 180F, 30F);
            phase = SCENE_READY;
        }

        if (phase == SCENE_READY && clientWantsWideView) {
            clientWantsWideView = false;
            player.connection.teleport(1.5, Y + 2, 12.5, 180F, 12F);
        }

        if (phase == SCENE_READY && clientWantsSprint) {
            clientWantsSprint = false;
            sprintStart = level.getGameTime();
            boolean ok = server.tickRateManager().requestGameToSprint(7 * 24000 + 2000);
            info("sprint", "requested 170000 ticks from game time " + sprintStart + ", accepted=" + ok);
            phase = SPRINT;
        }

        if (phase == SPRINT) {
            sampleDuringSprint(level);
            if (!server.tickRateManager().isSprinting()) {
                info("sprint", "ended at game time " + level.getGameTime() + " (" + (level.getGameTime() - sprintStart) + " ticks)");
                afterSprint(server, level, player);
                run(server, "time set 18000");
                nightWait = 200;
                phase = NIGHT_READY - 100; // waiting for the lantern's 100-tick check
            }
        } else if (phase == NIGHT_READY - 100) {
            if (--nightWait <= 0) {
                lanternSample(level, "after time set 18000");
                player.connection.teleport(-7.5, Y + 1, 4.5, 200F, 20F);
                phase = NIGHT_READY;
            }
        } else if (phase == NIGHT_READY && clientWantsKilnView) {
            // Daylight, standing over the row of finished batches, looking down into their open mouths.
            clientWantsKilnView = false;
            run(server, "time set 6000");
            player.connection.teleport(11.0, Y + 2, 2.8, 180F, 50F);
            phase = KILNS_READY;
        } else if (phase == KILNS_READY && clientTookKilnShot) {
            batchChecks(level);
            phase = KILNS_CHECKED;
        } else if (phase == KILNS_CHECKED && clientWantsLongView) {
            // Standing in front of the two Long Kilns' doors, which face south.
            clientWantsLongView = false;
            player.connection.teleport(-1.0, Y, -0.6, 180F, 22F);
            phase = LONG_READY;
        } else if (phase == LONG_READY && clientTookLongShot) {
            longChecks(level);
            phase = LONG_CHECKED;
        }
    }

    // ---- scene ---------------------------------------------------------------------------------

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void setScene(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        for (String c : List.of("gamerule doMobSpawning false", "gamerule doWeatherCycle false", "weather clear",
                "difficulty peaceful", "gamerule doDaylightCycle true", "time set 1000", "fill -20 -60 -6 20 -50 6 minecraft:air")) {
            run(server, c);
        }
        BlockState kiln = dev.slowlight.SlowlightBlocks.PATIENT_KILN.get().defaultBlockState();
        level.setBlock(K1, kiln, 3);
        level.setBlock(COMP, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.NORTH), 3);
        level.setBlock(HOP_OUT, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
        level.setBlock(K2, kiln, 3);
        level.setBlock(HOP_IN, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
        if (level.getBlockEntity(HOP_IN) instanceof HopperBlockEntity hopper) {
            hopper.setItem(0, new ItemStack(Items.SWEET_BERRIES, 3));
        }
        level.setBlock(KSNEAK, kiln, 3);
        level.setBlock(KJUNK, kiln, 3);
        level.setBlock(LANTERN, dev.slowlight.SlowlightBlocks.SLOWLIGHT_LANTERN.get().defaultBlockState(), 3);
        level.setBlock(JUKEBOX, Blocks.JUKEBOX.defaultBlockState(), 3);
        level.setBlock(KBREAK_PICK, kiln, 3);
        level.setBlock(KBREAK_HAND, kiln, 3);
        level.setBlock(LBREAK, dev.slowlight.SlowlightBlocks.SLOWLIGHT_LANTERN.get().defaultBlockState(), 3);

        BlockState longKiln = dev.slowlight.SlowlightBlocks.LONG_KILN.get().defaultBlockState()
                .setValue(LongKilnBlock.FACING, Direction.SOUTH);
        level.setBlock(LK1, longKiln, 3);
        level.setBlock(LK2, longKiln, 3);
        level.setBlock(LK2_IN, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
        if (level.getBlockEntity(LK2_IN) instanceof HopperBlockEntity hopper) {
            hopper.setItem(0, new ItemStack(Items.MUD, 10));
        }

        BATCHES.add(new Batch(new BlockPos(6, Y, 0), Items.MUSIC_DISC_MELLOHI, 1, SlowlightItems.MUSIC_DISC_PATIENT_LIGHT.get(), 1, 4));
        BATCHES.add(new Batch(new BlockPos(8, Y, 0), Items.AMETHYST_SHARD, 1, SlowlightItems.SLOWLIGHT_SHARD.get(), 1, 7));
        BATCHES.add(new Batch(new BlockPos(10, Y, 0), Items.ROTTEN_FLESH, 2, Items.LEATHER, 2, 3));
        BATCHES.add(new Batch(new BlockPos(12, Y, 0), Items.MUD, 1, Items.CLAY_BALL, 2, 2));
        BATCHES.add(new Batch(new BlockPos(14, Y, 0), Items.MUSIC_DISC_FAR, 1, SlowlightItems.MUSIC_DISC_A_WEEK_IN.get(), 1, 7));
        BATCHES.add(new Batch(new BlockPos(16, Y, 0), Items.MUSIC_DISC_STAL, 1, SlowlightItems.MUSIC_DISC_LANTERN_HOURS.get(), 1, 5));
        BATCHES.add(new Batch(new BlockPos(18, Y, 0), Items.STRING, 1, Items.COBWEB, 1, 3));
        BATCHES.add(new Batch(new BlockPos(20, Y, 0), Items.COPPER_BLOCK, 1, Items.EXPOSED_COPPER, 1, 3));
        BATCHES.add(new Batch(new BlockPos(22, Y, 0), Items.TORCHFLOWER_SEEDS, 1, Items.TORCHFLOWER, 1, 2));
        BATCHES.add(new Batch(new BlockPos(24, Y, 0), Items.GRAVEL, 1, Items.SUSPICIOUS_GRAVEL, 1, 7));
        // conditions that the sprint's open sky, clear weather and days and nights all meet
        BATCHES.add(new Batch(new BlockPos(26, Y, 0), Items.INK_SAC, 1, Items.GLOW_INK_SAC, 1, 2));
        BATCHES.add(new Batch(new BlockPos(28, Y, 0), Items.KELP, 1, Items.DRIED_KELP, 1, 1));
        BATCHES.add(new Batch(new BlockPos(36, Y, 0), SlowlightItems.DUSKBLOOM_PETALS.get(), 1, SlowlightItems.DRIED_DUSKBLOOM_PETALS.get(), 1, 1));
        for (Batch b : BATCHES) {
            level.setBlock(b.pos(), kiln, 3);
        }
        // and two that it does not: dirt waiting for rain that never comes, kelp waiting for sun under a roof
        level.setBlock(KRAIN, kiln, 3);
        level.setBlock(KROOF, kiln, 3);
        level.setBlock(KROOF.above(2), Blocks.STONE.defaultBlockState(), 3);

        // Registry and data checks.
        int ageing = server.getRecipeManager().getAllRecipesFor(SlowlightRecipes.AGEING_TYPE.get()).size();
        // master ships 43; branch rest adds six (still_shard, then fermented_spider_eye, poisonous_potato, bone_meal,
        // paper, dead_bush); Waystones add five (chorus pearl, two mosaic glasses and more), nightfall six (cheese, ice,
        // blue ice, three moon recipes).
        // With KubeJS, src/autotest/kubejs/server_scripts/sltest.js adds two and removes one.
        boolean kubejs = net.neoforged.fml.ModList.get().isLoaded("kubejs");
        int expected = kubejs ? 61 : 60;
        // With Aerial Hell (in libs/ for this test), its compat recipes load too: every file in compat/aerialhell.
        boolean aerialhell = net.neoforged.fml.ModList.get().isLoaded("aerialhell");
        if (aerialhell) {
            expected += 16;
        }
        check("recipes.ageing_loaded", ageing == expected, ageing + " ageing recipes (expected " + expected + ", kubejs loaded=" + kubejs + ", aerialhell loaded=" + aerialhell + ")");
        kubejsChecks(server, kubejs);
        for (String id : List.of("slowlight:patient_kiln", "slowlight:long_kiln", "slowlight:slowlight_lantern",
                "slowlight:patience_armor_trim_smithing_template", "slowlight:light_blue_dye_from_duskbloom_petals",
                "slowlight:duskbloom_tea", "slowlight:duskbloom_jam", "slowlight:dusk_cider",
                "slowlight:gloamwood_planks", "slowlight:gloamwood_boat", "slowlight:gloamwood_chest_boat",
                "slowlight:gloamwood_hanging_sign", "slowlight:gloamwood_door", "slowlight:lanternfruit_seeds",
                "slowlight:lanternfruit_must", "slowlight:lanternfruit_lamp")) {
            check("recipes.crafting." + id, server.getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent(), id);
        }
        var songs = server.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG);
        for (String s : List.of("patient_light", "a_week_in", "lantern_hours")) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("slowlight", s);
            check("jukebox_song." + s, songs.containsKey(id), "registry has " + id);
            ResourceLocation ev = ResourceLocation.fromNamespaceAndPath("slowlight", "music_disc." + s);
            check("sound_event." + s, BuiltInRegistries.SOUND_EVENT.containsKey(ev), "registry has " + ev);
        }
        trimChecks(server, level);
        boolean patchouli = net.neoforged.fml.ModList.get().isLoaded("patchouli");
        check("guide.recipe_when_patchouli", server.getRecipeManager().byKey(ResourceLocation.parse("slowlight:guide_book")).isPresent() == patchouli,
                "patchouli loaded=" + patchouli + ", guide book recipe present=" + server.getRecipeManager().byKey(ResourceLocation.parse("slowlight:guide_book")).isPresent());
        for (String s : List.of("block.patient_kiln.seal", "block.patient_kiln.open")) {
            ResourceLocation ev = ResourceLocation.fromNamespaceAndPath("slowlight", s);
            check("sound_event." + s, BuiltInRegistries.SOUND_EVENT.containsKey(ev), "registry has " + ev);
        }
        snifferCheck(level);
        duskbloomCheck(level);
        gloamwoodCheck(level, player);
        dimensionCheck(server, level);
        check("sherd.stamps_patience", net.minecraft.world.level.block.entity.DecoratedPotPatterns.getPatternFromItem(
                        SlowlightItems.PATIENCE_POTTERY_SHERD.get()) == dev.slowlight.SlowlightPotPatterns.PATIENCE
                        && BuiltInRegistries.DECORATED_POT_PATTERN.containsKey(dev.slowlight.SlowlightPotPatterns.PATIENCE)
                        && new ItemStack(SlowlightItems.PATIENCE_POTTERY_SHERD.get()).is(net.minecraft.tags.ItemTags.DECORATED_POT_SHERDS),
                "sherd maps to " + net.minecraft.world.level.block.entity.DecoratedPotPatterns.getPatternFromItem(SlowlightItems.PATIENCE_POTTERY_SHERD.get()));
        int advs = 0;
        for (var holder : server.getAdvancements().getAllAdvancements()) {
            if (holder.id().getNamespace().equals("slowlight")) {
                advs++;
            }
        }
        // master ships 12; branch rest adds four (sandglass_week, keepsake, ember_kiln, still_shard); nightfall two
        check("advancements.loaded", advs == 18, advs + " Slowlight advancements (expected 18)");
        RestChecks.start(server, level, player); // branch rest
        DecorChecks.start(server, level); // decor batch
        CraftsChecks.start(level); // weathering, flower press, slow candle
        WaysChecks.start(server, level, player); // waystones
        NightsChecks.start(server, level, player); // cheese, letter, moondial, stats
        player.setGameMode(GameType.SURVIVAL);
        info("scene", "placed");
    }

    private static InteractionResult use(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack stack, boolean sneak) {
        player.setShiftKeyDown(sneak);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        InteractionResult r = player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
        player.setShiftKeyDown(false);
        return r;
    }

    private static PatientKilnBlockEntity kiln(ServerLevel level, BlockPos pos) {
        return (PatientKilnBlockEntity) level.getBlockEntity(pos);
    }

    private static void interact(ServerLevel level, ServerPlayer player) {
        InteractionResult r = use(level, player, K1, new ItemStack(Items.HONEY_BOTTLE), false);
        ItemStack hand = player.getMainHandItem();
        check("kiln.insert_by_hand", hand.isEmpty() && kiln(level, K1).working().is(Items.HONEY_BOTTLE),
                "result=" + r + ", hand after=" + hand + ", kiln holds " + kiln(level, K1).working());

        for (Batch b : BATCHES) {
            use(level, player, b.pos(), new ItemStack(b.in(), b.inCount()), false);
            info("kiln.loaded", b.pos().toShortString() + " holds " + kiln(level, b.pos()).working());
        }
        use(level, player, LK1, new ItemStack(Items.HONEY_BOTTLE, 16), false);
        check("long_kiln.takes_whole_stack", kiln(level, LK1).working().getCount() == 16 && player.getMainHandItem().isEmpty(),
                "Long Kiln offered 16 honey holds " + kiln(level, LK1).working() + ", hand keeps " + player.getMainHandItem());
        use(level, player, KRAIN, new ItemStack(Items.DIRT), false);
        use(level, player, KROOF, new ItemStack(Items.KELP), false);
        use(level, player, KSNEAK, new ItemStack(Items.SWEET_BERRIES, 4), false);
        use(level, player, KJUNK, new ItemStack(Items.STICK, 5), false);
        check("kiln.rejects_junk", kiln(level, KJUNK).working().isEmpty() && player.getMainHandItem().getCount() == 5,
                "kiln offered 5 sticks holds " + kiln(level, KJUNK).working() + ", hand keeps " + player.getMainHandItem());

        // A lantern placed against the underside of a block hangs from it.
        level.setBlock(ROOF, Blocks.STONE.defaultBlockState(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SlowlightItems.SLOWLIGHT_LANTERN.get()));
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ROOF).add(0, -0.5, 0), Direction.DOWN, ROOF, false));
        BlockState hung = level.getBlockState(ROOF.below());
        check("lantern.hangs_under_block", hung.is(dev.slowlight.SlowlightBlocks.SLOWLIGHT_LANTERN.get())
                && hung.getValue(net.minecraft.world.level.block.LanternBlock.HANGING), "placed under stone: " + hung);

        // Jukebox with a Slowlight disc, used the way a player would.
        use(level, player, JUKEBOX, new ItemStack(SlowlightItems.MUSIC_DISC_A_WEEK_IN.get()), false);
        if (level.getBlockEntity(JUKEBOX) instanceof JukeboxBlockEntity jb) {
            var sp = jb.getSongPlayer();
            check("jukebox.accepts_disc", jb.getTheItem().is(SlowlightItems.MUSIC_DISC_A_WEEK_IN.get()), "jukebox holds " + jb.getTheItem());
            check("jukebox.playing", sp.isPlaying(), "playing=" + sp.isPlaying() + ", song=" + (sp.getSong() == null ? null : sp.getSong().description().getString())
                    + ", length=" + (sp.getSong() == null ? 0 : sp.getSong().lengthInSeconds()) + "s, comparator=" + jb.getComparatorOutput());
        } else {
            fail("jukebox.accepts_disc", "no jukebox block entity");
        }

        // Mead: drink it in survival and see what is left in the hand.
        ItemStack mead = new ItemStack(SlowlightItems.MEAD.get());
        int food = player.getFoodData().getFoodLevel();
        player.getFoodData().setFoodLevel(10);
        ItemStack left = mead.finishUsingItem(level, player);
        check("mead.returns_bottle", left.is(Items.GLASS_BOTTLE) || player.getInventory().contains(new ItemStack(Items.GLASS_BOTTLE)),
                "left in hand: " + left + ", food 10 -> " + player.getFoodData().getFoodLevel());
        player.getFoodData().setFoodLevel(food);

        // Breaking: with a stone pickaxe the kiln should drop itself; by hand it should not.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_PICKAXE));
        player.gameMode.destroyBlock(KBREAK_PICK);
        check("kiln.drops_with_pickaxe", countDrops(level, KBREAK_PICK, SlowlightItems.PATIENT_KILN.get()) == 1,
                "kiln items dropped: " + countDrops(level, KBREAK_PICK, SlowlightItems.PATIENT_KILN.get()));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.gameMode.destroyBlock(KBREAK_HAND);
        info("kiln.drops_by_hand", "kiln items dropped breaking by hand: " + countDrops(level, KBREAK_HAND, SlowlightItems.PATIENT_KILN.get()) + " (0 expected: needs a pickaxe)");
        player.gameMode.destroyBlock(LBREAK);
        check("lantern.drops_by_hand", countDrops(level, LBREAK, SlowlightItems.SLOWLIGHT_LANTERN.get()) == 1,
                "lantern items dropped: " + countDrops(level, LBREAK, SlowlightItems.SLOWLIGHT_LANTERN.get()));
        player.setGameMode(GameType.CREATIVE);
    }

    private static int countDrops(ServerLevel level, BlockPos pos, Item item) {
        int n = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) {
            if (e.getItem().is(item)) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    private static void firstLook(ServerLevel level, ServerPlayer player) {
        PatientKilnBlockEntity k1 = kiln(level, K1);
        boolean sealed = level.getBlockState(K1).getValue(PatientKilnBlock.WORKING);
        check("kiln.lid_seals", sealed, "K1 lit=" + sealed + " ~60 ticks after the honey went in");
        info("kiln.status_text", "K1 says: \"" + k1.status().getString() + "\"");
        check("kiln.status_two_days", k1.status().getString().contains("2"), k1.status().getString());
        k1Start = level.getGameTime();

        // A plain click with an empty hand must leave the batch alone.
        use(level, player, K1, ItemStack.EMPTY, false);
        check("kiln.plain_click_keeps_batch", k1.working().is(Items.HONEY_BOTTLE) && player.getMainHandItem().isEmpty(),
                "after empty-hand click kiln holds " + k1.working() + ", hand " + player.getMainHandItem());

        // A sneak click takes the batch back.
        player.getInventory().clearContent();
        use(level, player, KSNEAK, ItemStack.EMPTY, true);
        PatientKilnBlockEntity ks = kiln(level, KSNEAK);
        check("kiln.sneak_click_takes_back", ks.working().isEmpty() && player.getInventory().countItem(Items.SWEET_BERRIES) == 4,
                "kiln now holds " + ks.working() + ", player has " + player.getInventory().countItem(Items.SWEET_BERRIES) + " berries");
        player.getInventory().clearContent();

        PatientKilnBlockEntity kj = kiln(level, KJUNK);
        info("kiln.junk_status", "kiln with sticks says \"" + kj.status().getString() + "\", lit=" + level.getBlockState(KJUNK).getValue(PatientKilnBlock.WORKING));

        check("kiln.hopper_feeds_top", !kiln(level, K2).working().isEmpty() || level.getBlockState(K2).getValue(PatientKilnBlock.WORKING),
                "hopper-fed kiln holds " + kiln(level, K2).working() + ", hopper above has " + hopperContents(level, HOP_IN));
        check("long_kiln.seals_when_full", level.getBlockState(LK1).getValue(PatientKilnBlock.WORKING),
                "LK1 lit=" + level.getBlockState(LK1).getValue(PatientKilnBlock.WORKING) + ", says \"" + kiln(level, LK1).status().getString() + "\"");
        check("long_kiln.status_six_days", kiln(level, LK1).status().getString().contains("6"), kiln(level, LK1).status().getString());
        use(level, player, LK1, new ItemStack(Items.HONEY_BOTTLE, 1), false);
        check("long_kiln.shut_while_working", kiln(level, LK1).working().getCount() == 16 && player.getMainHandItem().getCount() == 1,
                "one more honey offered to the sealed kiln: it holds " + kiln(level, LK1).working() + ", hand keeps " + player.getMainHandItem());
        player.getInventory().clearContent();
        info("long_kiln.hopper_filling", "LK2 holds " + kiln(level, LK2).working() + ", lit=" + level.getBlockState(LK2).getValue(PatientKilnBlock.WORKING)
                + ", says \"" + kiln(level, LK2).status().getString() + "\", hopper above has " + hopperContents(level, LK2_IN));
        sampleComparator(level, "start+" + (level.getGameTime() - k1Start));
        lanternSample(level, "placed at time 1000, ~80 ticks later");
    }

    private static String hopperContents(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof HopperBlockEntity h)) {
            return "no hopper";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < h.getContainerSize(); i++) {
            if (!h.getItem(i).isEmpty()) {
                sb.append(h.getItem(i)).append(' ');
            }
        }
        return sb.append(']').toString();
    }

    // ---- during and after the sprint ----------------------------------------------------------

    private static void sampleComparator(ServerLevel level, String label) {
        int shown = level.getBlockEntity(COMP) instanceof ComparatorBlockEntity c ? c.getOutputSignal() : -1;
        int fresh = kiln(level, K1).comparatorSignal();
        String s = "comparator shows " + shown + ", kiln would say " + fresh + " (" + label + ")";
        comparatorSamples.put(level.getGameTime(), s);
        comparatorAllMatch &= shown == fresh;
        info("comparator.sample", s);
    }

    private static void lanternSample(ServerLevel level, String label) {
        BlockState st = level.getBlockState(LANTERN);
        boolean lit = st.getValue(SlowlightLanternBlock.LIT);
        int emission = st.getLightEmission(level, LANTERN);
        int blockLight = level.getBrightness(LightLayer.BLOCK, LANTERN.above());
        info("lantern.sample", "daytime " + (level.getDayTime() % 24000) + " (" + label + "): lit=" + lit
                + ", emission=" + emission + ", block light above=" + blockLight + ", isDay=" + level.isDay());
    }

    private static boolean noonSeen, nightSeen;
    private static boolean lanternNoonOk = true, lanternNightOk = true;
    private static int lanternSamples;

    private static void sampleDuringSprint(ServerLevel level) {
        long el = level.getGameTime() - k1Start;
        if (el == 12000 || el == 24000 || el == 36000 || el == 47000 || el == 48100) {
            sampleComparator(level, "start+" + el);
        }
        long dt = level.getDayTime() % 24000;
        if (dt == 6000 || dt == 18000) {
            BlockState st = level.getBlockState(LANTERN);
            boolean lit = st.getValue(SlowlightLanternBlock.LIT);
            int emission = st.getLightEmission(level, LANTERN);
            if (dt == 6000) {
                noonSeen = true;
                lanternNoonOk &= !lit && emission == 7;
            } else {
                nightSeen = true;
                lanternNightOk &= lit && emission == 15;
            }
            if (lanternSamples++ < 4) {
                lanternSample(level, "during sprint");
            }
        }
    }

    private static void afterSprint(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        check("lantern.dim_at_noon", noonSeen && lanternNoonOk, "every noon sample unlit with light 7 (seen=" + noonSeen + ")");
        check("lantern.bright_at_midnight", nightSeen && lanternNightOk, "every midnight sample lit with light 15 (seen=" + nightSeen + ")");

        PatientKilnBlockEntity k1 = kiln(level, K1);
        int shown = level.getBlockEntity(COMP) instanceof ComparatorBlockEntity c ? c.getOutputSignal() : -1;
        check("comparator.ready_reads_15", shown == 15, "with the mead finished and waiting, the comparator shows " + shown);
        info("kiln.k1_after", "K1 input " + k1.working() + ", lit=" + level.getBlockState(K1).getValue(PatientKilnBlock.WORKING)
                + ", says \"" + k1.status().getString() + "\"");
        player.getInventory().clearContent();
        use(level, player, K1, ItemStack.EMPTY, false);
        int mead = player.getInventory().countItem(SlowlightItems.MEAD.get());
        check("kiln.honey_to_mead_2_days", mead == 1, "plain click on K1 after the sprint gave " + mead + " mead");
        check("kiln.lid_opens_when_done", !level.getBlockState(K1).getValue(PatientKilnBlock.WORKING), "K1 lit after finishing = "
                + level.getBlockState(K1).getValue(PatientKilnBlock.WORKING));
        player.getInventory().clearContent();

        String below = hopperContents(level, HOP_OUT);
        check("kiln.hopper_draws_bottom", below.contains("preserved_berries"), "hopper below K2 holds " + below
                + "; hopper above holds " + hopperContents(level, HOP_IN) + "; K2 input " + kiln(level, K2).working());
        check("comparator.tracks_progress", comparatorAllMatch, String.join(" | ", comparatorSamples.values()));
    }

    /**
     * The Patience trim and the shard as a trim material: both registered, the smithing table puts them together,
     * and an armour stand by the Long Kilns wears the result for the last screenshot.
     */
    private static void trimChecks(MinecraftServer server, ServerLevel level) {
        ResourceLocation pattern = ResourceLocation.fromNamespaceAndPath("slowlight", "patience");
        ResourceLocation material = ResourceLocation.fromNamespaceAndPath("slowlight", "slowlight");
        check("trim.pattern_registered", server.registryAccess().registryOrThrow(Registries.TRIM_PATTERN).containsKey(pattern),
                "registry has " + pattern);
        check("trim.material_registered", server.registryAccess().registryOrThrow(Registries.TRIM_MATERIAL).containsKey(material),
                "registry has " + material);
        check("trim.shard_is_material", new ItemStack(SlowlightItems.SLOWLIGHT_SHARD.get()).is(net.minecraft.tags.ItemTags.TRIM_MATERIALS),
                "slowlight_shard in #minecraft:trim_materials");
        SmithingRecipeInput in = new SmithingRecipeInput(new ItemStack(SlowlightItems.PATIENCE_TRIM_TEMPLATE.get()),
                new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(SlowlightItems.SLOWLIGHT_SHARD.get()));
        ItemStack out = server.getRecipeManager().getRecipeFor(RecipeType.SMITHING, in, level)
                .map(h -> h.value().assemble(in, server.registryAccess())).orElse(ItemStack.EMPTY);
        ArmorTrim trim = out.get(DataComponents.TRIM);
        check("trim.smithing", trim != null && trim.pattern().is(pattern) && trim.material().is(material),
                "template + iron chestplate + shard gives " + out + " with trim " + trim);
        if (trim == null) {
            return;
        }
        ArmorStand stand = new ArmorStand(level, 2.2, Y, -3.6);
        stand.setYRot(0F);
        stand.setYBodyRot(0F);
        stand.setYHeadRot(0F);
        Item[] armour = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            ItemStack piece = new ItemStack(armour[i]);
            piece.set(DataComponents.TRIM, trim);
            stand.setItemSlot(slots[i], piece);
        }
        level.addFreshEntity(stand);
        trimmed = trim;
    }

    /** The trim the smithing check made, for the client's model check. */
    static volatile ArmorTrim trimmed;

    /** The Long Kilns, after the sprint: each batch done whole, at once, and the door open again. */
    private static void longChecks(ServerLevel level) {
        for (BlockPos pos : List.of(LK1, LK2)) {
            PatientKilnBlockEntity k = kiln(level, pos);
            int meads = 0, clay = 0;
            ItemStack out;
            StringBuilder seen = new StringBuilder();
            while (!(out = k.takeFinished()).isEmpty()) {
                seen.append(out).append(' ');
                if (out.is(SlowlightItems.MEAD.get())) {
                    meads += out.getCount();
                }
                if (out.is(Items.CLAY_BALL)) {
                    clay += out.getCount();
                }
            }
            boolean open = !level.getBlockState(pos).getValue(PatientKilnBlock.WORKING);
            if (pos.equals(LK1)) {
                check("long_kiln.stack_of_honey_in_6_days", meads == 16 && k.working().isEmpty() && open,
                        "LK1 gave " + seen + "; input left " + k.working() + ", door open=" + open);
            } else {
                check("long_kiln.hopper_batch_whole", clay == 20 && k.working().isEmpty(),
                        "LK2 gave " + seen + "(want 20 clay balls from one batch of 10 mud); input left " + k.working()
                                + ", hopper above has " + hopperContents(level, LK2_IN));
            }
        }
    }

    /** Sniffers now and then dig up a Slowlight Shard, through a loot modifier on their digging table. */
    // The README's two KubeJS routes: event.custom through the recipe codec, and the named builder from the
    // data-only schema (data/slowlight/kubejs/recipe_schema/ageing.json). Plus event.remove by output.
    private static void kubejsChecks(MinecraftServer server, boolean kubejs) {
        if (!kubejs) {
            info("kubejs.skipped", "KubeJS not loaded (put its and Rhino's jars in libs/ to test it)");
            return;
        }
        var recipes = server.getRecipeManager();
        var custom = recipes.byKey(ResourceLocation.parse("sltest:kjs_custom"));
        check("kubejs.custom", custom.isPresent() && custom.get().value() instanceof AgeingRecipe r
                        && r.result().is(Items.LEATHER) && r.result().getCount() == 1 && r.days() == 3
                        && r.ingredient().test(new ItemStack(Items.ROTTEN_FLESH)),
                "event.custom slowlight:ageing rotten_flesh -> leather, 3 days: " + custom.map(h -> h.value().toString()).orElse("missing"));
        var schema = recipes.byKey(ResourceLocation.parse("sltest:kjs_schema"));
        check("kubejs.schema", schema.isPresent() && schema.get().value() instanceof AgeingRecipe r
                        && r.result().is(Items.BONE_MEAL) && r.result().getCount() == 4 && r.days() == 2
                        && r.ingredient().test(new ItemStack(Items.BONE_BLOCK)),
                "event.recipes.slowlight.ageing bone_block -> 4 bone_meal, 2 days: " + schema.map(h -> h.value().toString()).orElse("missing"));
        check("kubejs.remove", recipes.byKey(ResourceLocation.parse("slowlight:ageing/blue_dye")).isEmpty(),
                "event.remove output minecraft:blue_dye took slowlight:ageing/blue_dye out");
    }

    private static void snifferCheck(ServerLevel level) {
        var table = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("gameplay/sniffer_digging")));
        var sniffer = net.minecraft.world.entity.EntityType.SNIFFER.create(level);
        if (sniffer == null) {
            fail("sniffer.digs_up_shards", "could not make a sniffer");
            return;
        }
        sniffer.setPos(0.5, Y, 8.5);
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, sniffer.position())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, sniffer)
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.GIFT);
        int shards = 0, seeds = 0, dusk = 0, saplings = 0;
        for (int i = 0; i < 400; i++) {
            for (ItemStack s : table.getRandomItems(params)) {
                if (s.is(SlowlightItems.SLOWLIGHT_SHARD.get())) {
                    shards += s.getCount();
                } else if (s.is(SlowlightItems.DUSKBLOOM_SEEDS.get())) {
                    dusk += s.getCount();
                } else if (s.is(SlowlightItems.GLOAMWOOD_SAPLING.get())) {
                    saplings += s.getCount();
                } else {
                    seeds += s.getCount();
                }
            }
        }
        check("sniffer.digs_up_shards", shards > 5 && shards < 80 && seeds >= 400,
                "400 digs gave " + shards + " Slowlight Shards (8% chance each, so about 32) and " + seeds + " seeds");
        check("sniffer.digs_up_duskbloom_seeds", dusk > 10 && dusk < 90,
                "400 digs gave " + dusk + " Duskbloom Seeds (10% chance each, so about 40)");
        check("sniffer.digs_up_gloamwood", saplings > 5 && saplings < 60,
                "400 digs gave " + saplings + " Gloamwood Seedlings (6% chance each, so about 24)");
    }

    /**
     * Gloamwood: a whole vanilla wood set (tags, signs, boat type, stripping), a seedling that grows only at night,
     * and Lanternfruit that ripen to light 7 and go back to age 0 when picked.
     */
    private static void gloamwoodCheck(ServerLevel level, ServerPlayer player) {
        check("gloamwood.tags", new ItemStack(SlowlightItems.GLOAMWOOD_LOG.get()).is(net.minecraft.tags.ItemTags.LOGS_THAT_BURN)
                        && new ItemStack(SlowlightItems.GLOAMWOOD_PLANKS.get()).is(net.minecraft.tags.ItemTags.PLANKS)
                        && new ItemStack(SlowlightItems.GLOAMWOOD_BOAT.get()).is(net.minecraft.tags.ItemTags.BOATS)
                        && SlowlightBlocks.GLOAMWOOD_LEAVES.get().defaultBlockState().is(net.minecraft.tags.BlockTags.LEAVES)
                        && SlowlightBlocks.GLOAMWOOD_DOOR.get().defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)
                        && SlowlightBlocks.GLOAMWOOD_SAPLING.get().defaultBlockState().is(net.minecraft.tags.BlockTags.SAPLINGS),
                "logs_that_burn, planks, boats, leaves, mineable/axe, saplings");
        var type = net.minecraft.world.entity.vehicle.Boat.Type.byName(dev.slowlight.Gloamwood.NAME);
        check("gloamwood.boat_type", type == dev.slowlight.GloamwoodBoat.BOAT.getValue()
                        && type.getPlanks() == SlowlightBlocks.GLOAMWOOD_PLANKS.get(),
                "Boat.Type.byName(" + dev.slowlight.Gloamwood.NAME + ") = " + type);
        check("gloamwood.signs_have_block_entities",
                net.minecraft.world.level.block.entity.BlockEntityType.SIGN.isValid(SlowlightBlocks.GLOAMWOOD_SIGN.get().defaultBlockState())
                        && net.minecraft.world.level.block.entity.BlockEntityType.SIGN.isValid(SlowlightBlocks.GLOAMWOOD_WALL_SIGN.get().defaultBlockState())
                        && net.minecraft.world.level.block.entity.BlockEntityType.HANGING_SIGN.isValid(SlowlightBlocks.GLOAMWOOD_HANGING_SIGN.get().defaultBlockState())
                        && net.minecraft.world.level.block.entity.BlockEntityType.HANGING_SIGN.isValid(SlowlightBlocks.GLOAMWOOD_WALL_HANGING_SIGN.get().defaultBlockState()),
                "sign and hanging sign block entity types accept all four Gloamwood signs");
        BlockState log = SlowlightBlocks.GLOAMWOOD_LOG.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X);
        // the block's own hook, called straight: the state-level wrapper fires a NeoForge event that needs a real
        // UseOnContext, which a headless check has none of
        BlockState stripped = log.getBlock().getToolModifiedState(log, null, net.neoforged.neoforge.common.ItemAbilities.AXE_STRIP, true);
        check("gloamwood.axe_strips_log", stripped != null && stripped.is(SlowlightBlocks.STRIPPED_GLOAMWOOD_LOG.get())
                        && stripped.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) == Direction.Axis.X,
                "stripped " + stripped);
        int burn = SlowlightBlocks.GLOAMWOOD_PLANKS.get().defaultBlockState().getFlammability(level, BlockPos.ZERO, Direction.UP);
        check("gloamwood.flammable", burn > 0, "planks flammability " + burn);
        drops(level, "gloamwood.wall_sign_drops_own_sign", SlowlightBlocks.GLOAMWOOD_WALL_SIGN.get().defaultBlockState(),
                SlowlightItems.GLOAMWOOD_SIGN.get());
        drops(level, "gloamwood.leaves_drop_no_sapling", SlowlightBlocks.GLOAMWOOD_LEAVES.get().defaultBlockState(), null);

        // The seedling: four hundred random ticks at noon do nothing, the same at midnight grow a tree.
        BlockPos sap = new BlockPos(-20, Y, 16);
        level.setBlockAndUpdate(sap.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(sap, SlowlightBlocks.GLOAMWOOD_SAPLING.get().defaultBlockState());
        long time = level.getDayTime();
        var random = net.minecraft.util.RandomSource.create(7);
        for (int i = 0; i < 400 && level.getBlockState(sap).is(SlowlightBlocks.GLOAMWOOD_SAPLING.get()); i++) {
            level.getBlockState(sap).randomTick(level, sap, random);
        }
        boolean dayStill = level.getBlockState(sap).is(SlowlightBlocks.GLOAMWOOD_SAPLING.get());
        level.setDayTime(18000);
        level.updateSkyBrightness();
        boolean night = dev.slowlight.block.DuskbloomCropBlock.isNight(level);
        for (int i = 0; i < 400 && level.getBlockState(sap).is(SlowlightBlocks.GLOAMWOOD_SAPLING.get()); i++) {
            level.getBlockState(sap).randomTick(level, sap, random);
        }
        BlockState grown = level.getBlockState(sap);
        int leaves = 0, fruit = 0;
        for (BlockPos q : BlockPos.betweenClosed(sap.offset(-5, 0, -5), sap.offset(5, 9, 5))) {
            BlockState st = level.getBlockState(q);
            if (st.is(SlowlightBlocks.GLOAMWOOD_LEAVES.get())) leaves++;
            if (st.is(SlowlightBlocks.LANTERNFRUIT.get())) fruit++;
        }
        level.setDayTime(time);
        level.updateSkyBrightness();
        check("gloamwood.sapling_waits_for_night", dayStill, "after 400 noon ticks still a seedling: " + dayStill);
        check("gloamwood.sapling_grows_at_night", night && grown.is(SlowlightBlocks.GLOAMWOOD_LOG.get()) && leaves > 10,
                "night=" + night + ", at the seedling now " + grown + ", " + leaves + " leaves, " + fruit + " Lanternfruit");
        for (BlockPos q : BlockPos.betweenClosed(sap.offset(-5, 0, -5), sap.offset(5, 9, 5))) {
            level.setBlock(q, Blocks.AIR.defaultBlockState(), 2 | 16);
        }

        // Lanternfruit: under persistent leaves, ripe = light 7; a click picks it and leaves age 0; no leaves, no fruit.
        var lf = (dev.slowlight.block.LanternfruitBlock) SlowlightBlocks.LANTERNFRUIT.get();
        BlockState ripe = lf.defaultBlockState().setValue(dev.slowlight.block.LanternfruitBlock.AGE, 3);
        check("lanternfruit.glows_when_ripe", ripe.getLightEmission() == 7 && lf.defaultBlockState().getLightEmission() == 0,
                "ripe light " + ripe.getLightEmission() + ", young " + lf.defaultBlockState().getLightEmission());
        BlockPos leaf = new BlockPos(-24, Y + 3, 16), at = leaf.below();
        level.setBlockAndUpdate(leaf, SlowlightBlocks.GLOAMWOOD_LEAVES.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        level.setBlockAndUpdate(at, ripe);
        int before = player.getInventory().countItem(SlowlightItems.LANTERNFRUIT.get());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult r = player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        BlockState picked = level.getBlockState(at);
        long dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2))
                .stream().filter(e -> e.getItem().is(SlowlightItems.LANTERNFRUIT.get())).count();
        check("lanternfruit.pick_resets_to_age_0", r.consumesAction() && picked.is(lf)
                        && picked.getValue(dev.slowlight.block.LanternfruitBlock.AGE) == 0 && dropped > 0,
                "use " + r + ", now " + picked + ", dropped fruit entities " + dropped + " (inventory had " + before + ")");
        level.setBlockAndUpdate(leaf, Blocks.AIR.defaultBlockState());
        check("lanternfruit.falls_without_leaves", level.getBlockState(at).isAir(), "after the leaves went: " + level.getBlockState(at));
        level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3)).forEach(e -> e.discard());
        check("lanternfruit.food_and_peel", SlowlightItems.LANTERNFRUIT.get().getDefaultInstance().get(DataComponents.FOOD) != null
                        && SlowlightItems.LANTERNFRUIT.get().getDefaultInstance().getCraftingRemainingItem().is(SlowlightItems.LANTERNFRUIT_PEEL.get()),
                "fruit is food and leaves its peel in the grid");
    }

    /** What a block drops when broken by hand with nothing special; expect = null means it must drop nothing of its own kind. */
    private static void drops(ServerLevel level, String name, BlockState state, Item expect) {
        BlockPos at = new BlockPos(-26, Y, 16);
        var drops = net.minecraft.world.level.block.Block.getDrops(state, level, at, null);
        boolean ok = expect == null
                ? drops.stream().noneMatch(st -> st.is(SlowlightItems.GLOAMWOOD_SAPLING.get()) || st.is(Items.APPLE))
                : drops.size() == 1 && drops.get(0).is(expect);
        check(name, ok, "drops " + drops);
    }

    /** A recipe that names a dimension exists only there, and there it comes before the everywhere one. */
    private static void dimensionCheck(net.minecraft.server.MinecraftServer server, ServerLevel overworld) {
        ServerLevel nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
        var input = new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(Items.COBBLESTONE));
        var here = server.getRecipeManager().getRecipesFor(dev.slowlight.SlowlightRecipes.AGEING_TYPE.get(), input, overworld);
        check("dimension.overworld_only_moss", here.size() == 1 && here.get(0).value().getResultItem(server.registryAccess()).is(Items.MOSSY_COBBLESTONE),
                "overworld cobblestone recipes: " + here.stream().map(h -> h.id().toString()).toList());
        if (nether == null) {
            fail("dimension.nether_blackstone", "no Nether level");
            return;
        }
        var there = server.getRecipeManager().getRecipesFor(dev.slowlight.SlowlightRecipes.AGEING_TYPE.get(), input, nether);
        boolean black = there.stream().anyMatch(h -> h.value().condition().dimension().isPresent()
                && h.value().getResultItem(server.registryAccess()).is(Items.BLACKSTONE));
        check("dimension.nether_blackstone", there.size() == 2 && black,
                "nether cobblestone recipes: " + there.stream().map(h -> h.id().toString()).toList());
    }

    /** The Duskbloom crop: four stages, grows only at night, glows faintly when ripe, and gives petals. */
    private static void duskbloomCheck(ServerLevel level) {
        var crop = (dev.slowlight.block.DuskbloomCropBlock) SlowlightBlocks.DUSKBLOOM_CROP.get();
        BlockState ripe = crop.getStateForAge(crop.getMaxAge());
        BlockState young = crop.getStateForAge(0);
        check("duskbloom.glows_when_ripe", crop.getMaxAge() == 3 && ripe.getLightEmission() == 3 && young.getLightEmission() == 0,
                "max age " + crop.getMaxAge() + ", ripe light " + ripe.getLightEmission() + ", young light " + young.getLightEmission());
        check("duskbloom.seeds_plant_it", SlowlightItems.DUSKBLOOM_SEEDS.get() instanceof net.minecraft.world.item.BlockItem bi
                && bi.getBlock() == crop, "seeds place " + SlowlightItems.DUSKBLOOM_SEEDS.get());
        check("duskbloom.is_a_crop", young.is(net.minecraft.tags.BlockTags.CROPS)
                        && SlowlightItems.DUSKBLOOM_PETALS.get().builtInRegistryHolder().getData(net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps.COMPOSTABLES) != null,
                "crops tag and compostable petals");
        BlockPos at = new BlockPos(0, Y, 12);
        level.setBlockAndUpdate(at.below(), Blocks.FARMLAND.defaultBlockState());
        level.setBlockAndUpdate(at, ripe);
        var drops = net.minecraft.world.level.block.Block.getDrops(ripe, level, at, null);
        boolean petals = drops.stream().anyMatch(st -> st.is(SlowlightItems.DUSKBLOOM_PETALS.get()));
        check("duskbloom.ripe_drops_petals", petals, "ripe drops " + drops);
        level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(at.below(), Blocks.GRASS_BLOCK.defaultBlockState());
    }

    /** Runs after the client has photographed the finished work standing in the open kilns. */
    private static void batchChecks(ServerLevel level) {
        // The waits that should stand still: no rain came for the dirt, no sky reaches the roofed kelp.
        PatientKilnBlockEntity rain = kiln(level, KRAIN);
        check("condition.no_rain_no_mud", rain.working().is(Items.DIRT) && rain.finished().isEmpty()
                        && rain.status().getString().contains("rain"),
                "dirt kiln after seven clear days holds " + rain.working() + ", finished " + rain.finished() + ", says \"" + rain.status().getString() + "\"");
        PatientKilnBlockEntity roof = kiln(level, KROOF);
        check("condition.roof_stops_sun_drying", roof.working().is(Items.KELP) && roof.finished().isEmpty()
                        && roof.status().getString().contains("open sky"),
                "roofed kelp kiln holds " + roof.working() + ", finished " + roof.finished() + ", says \"" + roof.status().getString() + "\"");
        // and once it rains, the dirt kiln's condition holds
        var mud = level.getRecipeManager().byKey(ResourceLocation.parse("slowlight:ageing/mud"));
        level.setWeatherParameters(0, 6000, true, false);
        level.setRainLevel(1.0F);
        boolean wet = mud.isPresent() && ((dev.slowlight.recipe.AgeingRecipe) mud.get().value()).condition().holds(level, KRAIN);
        boolean roofedWet = mud.isPresent() && ((dev.slowlight.recipe.AgeingRecipe) mud.get().value()).condition().holds(level, KROOF);
        level.setWeatherParameters(6000, 0, false, false);
        level.setRainLevel(0.0F);
        check("condition.rain_counts_in_the_open", wet && !roofedWet, "in rain: open kiln holds=" + wet + ", roofed kiln holds=" + roofedWet);

        for (Batch b : BATCHES) {
            PatientKilnBlockEntity k = kiln(level, b.pos());
            ItemStack out = k.takeFinished();
            if (out.is(Items.SUSPICIOUS_GRAVEL)) {
                digCheck(level, out.copy());
            }
            check("kiln." + BuiltInRegistries.ITEM.getKey(b.in()).getPath() + "_to_" + BuiltInRegistries.ITEM.getKey(b.out()).getPath(),
                    out.is(b.out()) && out.getCount() == b.outCount(),
                    b.days() + "d recipe: output " + out + " (want " + b.outCount() + " " + BuiltInRegistries.ITEM.getKey(b.out())
                            + "), input left " + k.working());
        }
    }

    /** The kiln's suspicious gravel: placed, it carries Slowlight's loot table, and brushing it turns up a find. */
    private static void digCheck(ServerLevel level, ItemStack gravel) {
        ServerPlayer player = level.players().get(0);
        use(level, player, DIG.below(), gravel, false);
        var be = level.getBlockEntity(DIG);
        String table = be == null ? "none" : be.saveWithoutMetadata(level.registryAccess()).getString("LootTable");
        check("archaeology.kiln_gravel_has_loot", level.getBlockState(DIG).is(Blocks.SUSPICIOUS_GRAVEL) && table.equals("slowlight:archaeology/kiln_gravel"),
                "placed " + level.getBlockState(DIG) + " with loot table '" + table + "'");
        if (be instanceof net.minecraft.world.level.block.entity.BrushableBlockEntity brushable) {
            long t = level.getGameTime();
            boolean done = false;
            for (int i = 0; i < 30 && !done; i++) {
                done = brushable.brush(t + i * 20L, player, Direction.UP);
            }
            int found = 0;
            StringBuilder what = new StringBuilder();
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(DIG).inflate(2))) {
                found += e.getItem().getCount();
                what.append(e.getItem()).append(' ');
            }
            check("archaeology.brushing_finds_something", done && found > 0 && level.getBlockState(DIG).is(Blocks.GRAVEL),
                    "brushed done=" + done + ", found " + what + ", block now " + level.getBlockState(DIG));
        }
    }
}
