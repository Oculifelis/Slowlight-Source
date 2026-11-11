package dev.slowlight.autotest;

import dev.slowlight.SlowlightBlockEntities;
import dev.slowlight.SlowlightBlocks;
import dev.slowlight.SlowlightItems;
import dev.slowlight.SlowlightVillagers;
import dev.slowlight.block.EmberKilnBlockEntity;
import dev.slowlight.block.KeepsakeUrnBlock;
import dev.slowlight.block.KeepsakeUrnBlockEntity;
import dev.slowlight.block.PatientKilnBlock;
import dev.slowlight.block.PatientKilnBlockEntity;
import dev.slowlight.block.SandglassBlock;
import dev.slowlight.block.SandglassBlockEntity;
import dev.slowlight.block.SundialBlock;
import dev.slowlight.recipe.AgeingCondition;
import dev.slowlight.recipe.AgeingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Optional;

import static dev.slowlight.autotest.ServerChecks.check;
import static dev.slowlight.autotest.ServerChecks.info;

/**
 * Play-test checks for branch rest: the Sandglass, the Keepsake Urn, the Potter, the Ember Kiln, the void-sky condition
 * and Still Shard, and the old kiln ruin. ServerChecks starts it from setScene; it then runs on its own few ticks, all
 * done well before the main test's seven-day sprint, in its own patch of ground east of the kiln row (x 40..60).
 */
@EventBusSubscriber(modid = "slowlight_autotest")
public final class RestChecks {
    private static final int Y = -60;
    private static final BlockPos GLASS = new BlockPos(40, Y, 4);
    private static final BlockPos URN = new BlockPos(42, Y, 4);
    private static final BlockPos URN_EARLY = new BlockPos(44, Y, 4);
    private static final BlockPos URN_OPEN = new BlockPos(46, Y, 4);
    private static final BlockPos EMBER = new BlockPos(48, Y, 4);
    private static final BlockPos RUIN = new BlockPos(50, Y - 1, 8);
    private static final BlockPos SUNDIAL = new BlockPos(56, Y, 4);
    private static final BlockPos VILLAGER = new BlockPos(44, Y, 8);
    private static final BlockPos BUTTON = new BlockPos(52, Y, 0);

    /** Ticks since start(); -1 until ServerChecks starts us. */
    private static int t = -1;
    private static boolean pulsed;
    private static boolean turning;
    private static long glassShiftedAt;

    private RestChecks() {
    }

    /** Called once from ServerChecks.setScene: lay out the blocks and run the checks that need no waiting. */
    static void start(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        t = 0;
        registryChecks(server, level);
        level.setBlock(GLASS, SlowlightBlocks.SANDGLASS.get().defaultBlockState(), 3);
        for (BlockPos p : List.of(URN, URN_EARLY, URN_OPEN)) {
            level.setBlock(p, SlowlightBlocks.KEEPSAKE_URN.get().defaultBlockState(), 3);
        }
        level.setBlock(EMBER, SlowlightBlocks.EMBER_KILN.get().defaultBlockState(), 3);
        ruinCheck(server, level);
        potterCheck(level);
        voidSkyCheck(server, level);
        touchesChecks(server, level);
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (t < 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        ServerPlayer player = players.get(0);
        try {
            step(level, player);
        } catch (Throwable e) {
            ServerChecks.LOG.error("SLTEST FAIL rest.exception :: rest step threw", e);
            t = -1;
        }
    }

    private static void step(ServerLevel level, ServerPlayer player) {
        t++;
        if (t > 35 && t < 75) {
            pulsed |= level.getBlockState(GLASS).getValue(SandglassBlock.POWERED);
            turning |= level.getBlockState(GLASS).getValue(SandglassBlock.TURNING);
        }
        // ServerChecks uses the player at its ticks 60 and 120 (our 20 and 80); ours stay clear of those.
        if (t == 30) {
            glassFirstClick(level, player);
            urnSetUp(level, player);
            ember(level).offer(new ItemStack(Items.HONEY_BOTTLE));
            buttonPress(level);
        } else if (t == 33) {
            buttonAgain(level);
        } else if (t == 35) {
            // Wind the glass on to a second before the sand runs out, and the first urn's seal to its last second.
            SandglassBlockEntity glass = (SandglassBlockEntity) level.getBlockEntity(GLASS);
            CompoundTag tag = glass.saveWithoutMetadata(level.registryAccess());
            // The glass looks at its sand every 20 game ticks, so it runs out on the next such tick: the turn
            // (pulse 4 ticks, tumble 12) then always finishes well before t=75, whatever time the world started at.
            long left = 20L - level.getGameTime() % 20L;
            tag.putLong("StartedAt", level.getGameTime() - AgeingRecipe.TICKS_PER_DAY + left);
            glass.loadWithComponents(tag, level.registryAccess());
            glassShiftedAt = level.getGameTime();
            KeepsakeUrnBlockEntity urn = (KeepsakeUrnBlockEntity) level.getBlockEntity(URN);
            CompoundTag u = urn.saveWithoutMetadata(level.registryAccess());
            u.putLong("OpensAt", level.getGameTime() + 20);
            urn.loadWithComponents(u, level.registryAccess());
        } else if (t == 60) {
            sundialTicked(level);
        } else if (t == 75) {
            glassAfterTurn(level);
            urnAfterSeal(level, player);
            emberCold(level);
        } else if (t == 100) {
            urnBreaks(level, player);
            info("rest", "done");
            t = -1;
        }
    }

    // ---- gloamwood button: a 1-tick pulse, pressable again at once ----------------------------------------------

    private static void buttonPress(ServerLevel level) {
        level.setBlock(BUTTON.below(), Blocks.STONE.defaultBlockState(), 3);
        BlockState state = SlowlightBlocks.GLOAMWOOD_BUTTON.get().defaultBlockState()
                .setValue(ButtonBlock.FACE, AttachFace.FLOOR);
        level.setBlock(BUTTON, state, 3);
        ((ButtonBlock) state.getBlock()).press(state, level, BUTTON, null);
        check("button.pressed", level.getBlockState(BUTTON).getValue(ButtonBlock.POWERED), "first press powers it");
    }

    /** Three ticks on, a wooden button would still be held down for 27 more; ours has popped up and takes a new press. */
    private static void buttonAgain(ServerLevel level) {
        BlockState state = level.getBlockState(BUTTON);
        check("button.one_tick", !state.getValue(ButtonBlock.POWERED), "released after one tick, powered=" + state.getValue(ButtonBlock.POWERED));
        ((ButtonBlock) state.getBlock()).press(state, level, BUTTON, null);
        check("button.repress", level.getBlockState(BUTTON).getValue(ButtonBlock.POWERED), "pressed again straight away");
    }

    // ---- registries and data ------------------------------------------------------------------------------------

    private static void registryChecks(MinecraftServer server, ServerLevel level) {
        for (String id : List.of("sandglass", "keepsake_urn", "ember_kiln", "sundial")) {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("slowlight", id);
            check("rest.block." + id, BuiltInRegistries.BLOCK.containsKey(rl) && BuiltInRegistries.ITEM.containsKey(rl)
                    && (id.equals("sundial") || BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(rl)),
                    id.equals("sundial") ? "block and item " + rl + " (no block entity)" : "block, item and block entity " + rl);
            check("rest.recipe." + id, server.getRecipeManager().byKey(rl).isPresent(), "crafting recipe " + rl);
            check("rest.loot." + id, server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath("slowlight", "blocks/" + id))) != net.minecraft.world.level.storage.loot.LootTable.EMPTY,
                    "loot table blocks/" + id);
            check("rest.mineable." + id, BuiltInRegistries.BLOCK.get(rl).defaultBlockState()
                    .is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE), id + " is pickaxe-mineable");
        }
        check("rest.still_shard.item", BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("slowlight:still_shard")),
                "item slowlight:still_shard");
        check("rest.still_shard.trim_material", server.registryAccess().registryOrThrow(Registries.TRIM_MATERIAL)
                        .containsKey(ResourceLocation.parse("slowlight:still"))
                        && new ItemStack(SlowlightItems.STILL_SHARD.get()).is(net.minecraft.tags.ItemTags.TRIM_MATERIALS),
                "trim material slowlight:still, shard in #trim_materials");
        Optional<RecipeHolder<?>> still = server.getRecipeManager().byKey(ResourceLocation.parse("slowlight:ageing/still_shard"));
        boolean voidSky = still.isPresent() && still.get().value() instanceof AgeingRecipe r
                && r.condition().sky() == AgeingCondition.Sky.VOID
                && r.condition().dimension().map(d -> d.equals(Level.END.location())).orElse(false);
        check("rest.still_shard.recipe_end_void", voidSky, "ageing/still_shard wants void sky in the End: " + still.map(h -> h.value().toString()).orElse("missing"));
        int advs = 0;
        for (String a : List.of("sandglass_week", "keepsake", "ember_kiln", "still_shard")) {
            if (server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("slowlight", a)) != null) {
                advs++;
            }
        }
        check("rest.advancements", advs == 4, advs + " of 4 branch-rest advancements loaded");
    }

    // ---- Sandglass ----------------------------------------------------------------------------------------------

    private static void glassFirstClick(ServerLevel level, ServerPlayer player) {
        click(level, player, GLASS, ItemStack.EMPTY, false);
        SandglassBlockEntity glass = (SandglassBlockEntity) level.getBlockEntity(GLASS);
        var state = level.getBlockState(GLASS);
        check("sandglass.click_sets_one_day", glass.days() == 1 && state.getValue(SandglassBlock.SAND) == 7
                && state.getValue(SandglassBlock.RUNNING), "days=" + glass.days() + ", state=" + state);
        check("sandglass.comparator_full", glass.comparatorSignal() == 15, "signal " + glass.comparatorSignal());
        click(level, player, GLASS, ItemStack.EMPTY, false);
        click(level, player, GLASS, ItemStack.EMPTY, false);
        check("sandglass.clicks_count_up", glass.days() == 3, "three clicks, days=" + glass.days());
        for (int i = 0; i < 5; i++) {
            click(level, player, GLASS, ItemStack.EMPTY, false);
        }
        check("sandglass.wraps_after_seven", glass.days() == 1, "eight clicks, days=" + glass.days());
    }

    private static void glassAfterTurn(ServerLevel level) {
        var state = level.getBlockState(GLASS);
        SandglassBlockEntity glass = (SandglassBlockEntity) level.getBlockEntity(GLASS);
        check("sandglass.pulses_when_run_out", pulsed, "POWERED seen in the 40 ticks after winding on to the last second");
        check("sandglass.turns_itself_over", state.getValue(SandglassBlock.SAND) == 7 && !state.getValue(SandglassBlock.POWERED)
                        && glass.comparatorSignal() == 15,
                "after the turn: " + state + ", signal " + glass.comparatorSignal() + " (wound at " + glassShiftedAt + ")");
        check("sandglass.turn_animates", turning && !state.getValue(SandglassBlock.TURNING),
                "TURNING seen while it ran out, cleared by t=75: " + state);
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        check("sandglass.lost_in_nether", SandglassBlockEntity.canTell(level) && nether != null && !SandglassBlockEntity.canTell(nether),
                "overworld tells time, nether does not");
    }

    // ---- Keepsake Urn -------------------------------------------------------------------------------------------

    private static void urnSetUp(ServerLevel level, ServerPlayer player) {
        KeepsakeUrnBlockEntity urn = (KeepsakeUrnBlockEntity) level.getBlockEntity(URN);
        urn.setItem(0, new ItemStack(Items.DIAMOND, 3));
        click(level, player, URN, ItemStack.EMPTY, true);
        check("urn.sneak_click_plans_days", urn.planned() == 10, "7 -> " + urn.planned());
        ItemStack comb = new ItemStack(Items.HONEYCOMB, 2);
        click(level, player, URN, comb, false);
        check("urn.honeycomb_seals", urn.sealed() && level.getBlockState(URN).getValue(KeepsakeUrnBlock.SEALED)
                && player.getMainHandItem().getCount() == (player.getAbilities().instabuild ? 2 : 1), "sealed=" + urn.sealed() + ", comb left " + player.getMainHandItem());
        check("urn.hopper_shut_out", urn.getSlotsForFace(Direction.DOWN).length == 0 && !urn.canTakeItemThroughFace(0, urn.getItem(0), Direction.DOWN),
                "no slots through the wax");
        click(level, player, URN, ItemStack.EMPTY, false);
        check("urn.sealed_will_not_open", !(player.containerMenu instanceof net.minecraft.world.inventory.DispenserMenu),
                "menu after clicking sealed urn: " + player.containerMenu.getClass().getSimpleName());

        KeepsakeUrnBlockEntity early = (KeepsakeUrnBlockEntity) level.getBlockEntity(URN_EARLY);
        early.setItem(4, new ItemStack(Items.GOLD_INGOT, 5));
        early.seal(level, URN_EARLY, level.getBlockState(URN_EARLY));
        KeepsakeUrnBlockEntity open = (KeepsakeUrnBlockEntity) level.getBlockEntity(URN_OPEN);
        open.setItem(2, new ItemStack(Items.EMERALD, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void urnAfterSeal(ServerLevel level, ServerPlayer player) {
        KeepsakeUrnBlockEntity urn = (KeepsakeUrnBlockEntity) level.getBlockEntity(URN);
        check("urn.wax_gives_when_due", !urn.sealed() && !level.getBlockState(URN).getValue(KeepsakeUrnBlock.SEALED)
                && urn.getItem(0).is(Items.DIAMOND), "sealed=" + urn.sealed() + ", slot 0 " + urn.getItem(0));
        click(level, player, URN, ItemStack.EMPTY, false);
        boolean menu = player.containerMenu instanceof net.minecraft.world.inventory.DispenserMenu;
        var adv = level.getServer().getAdvancements().get(ResourceLocation.parse("slowlight:keepsake"));
        boolean got = adv != null && player.getAdvancements().getOrStartProgress(adv).isDone();
        check("urn.opens_after_seal", menu && got, "menu=" + menu + ", keepsake advancement=" + got);
        player.closeContainer();
    }

    private static void urnBreaks(ServerLevel level, ServerPlayer player) {
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        // one at a time: the two urns stand two blocks apart, so their drop boxes overlap
        AABB early = new AABB(URN_EARLY).inflate(1.5);
        AABB open = new AABB(URN_OPEN).inflate(1.5);
        player.gameMode.destroyBlock(URN_EARLY);
        check("urn.early_break_cracks", count(level, early, SlowlightItems.KEEPSAKE_URN.get().asItem()) == 0
                        && count(level, early, Items.BRICK) == 2 && count(level, early, Items.GOLD_INGOT) == 5,
                "sealed urn broken: urns " + count(level, early, SlowlightItems.KEEPSAKE_URN.get().asItem())
                        + ", bricks " + count(level, early, Items.BRICK) + ", gold " + count(level, early, Items.GOLD_INGOT));
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, early).forEach(e -> e.discard());
        player.gameMode.destroyBlock(URN_OPEN);
        check("urn.open_break_keeps_urn", count(level, open, SlowlightItems.KEEPSAKE_URN.get().asItem()) == 1
                        && count(level, open, Items.EMERALD) == 2,
                "open urn broken: urns " + count(level, open, SlowlightItems.KEEPSAKE_URN.get().asItem()) + ", emeralds " + count(level, open, Items.EMERALD));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    // ---- Ember Kiln, void sky -----------------------------------------------------------------------------------

    private static PatientKilnBlockEntity ember(ServerLevel level) {
        return (PatientKilnBlockEntity) level.getBlockEntity(EMBER);
    }

    private static void emberCold(ServerLevel level) {
        PatientKilnBlockEntity kiln = ember(level);
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        check("ember_kiln.waits_for_heat", kiln.working().is(Items.HONEY_BOTTLE) && !level.getBlockState(EMBER).getValue(PatientKilnBlock.WORKING)
                        && kiln.status().getString().contains("Nether"),
                "overworld: holds " + kiln.working() + ", lit=" + level.getBlockState(EMBER).getValue(PatientKilnBlock.WORKING) + ", says " + kiln.status().getString());
        check("ember_kiln.hot_in_nether", nether != null && EmberKilnBlockEntity.hotEnough(nether) && !EmberKilnBlockEntity.hotEnough(level),
                "ultrawarm nether only");
        check("ember_kiln.is_kiln_catalyst_be", level.getBlockEntity(EMBER).getType() == SlowlightBlockEntities.EMBER_KILN.get(),
                "block entity type " + BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(level.getBlockEntity(EMBER).getType()));
    }

    private static void voidSkyCheck(MinecraftServer server, ServerLevel overworld) {
        AgeingCondition voidSky = new AgeingCondition(AgeingCondition.Sky.VOID, AgeingCondition.Weather.ANY, AgeingCondition.Time.ANY);
        ServerLevel end = server.getLevel(Level.END);
        boolean here = voidSky.holds(overworld, new BlockPos(38, Y, 0));
        boolean there = end != null && voidSky.holds(end, new BlockPos(1000, 100, 1000));
        check("condition.void_sky_only_in_end", !here && there, "overworld open sky=" + here + ", End void at y 100=" + there);
        for (String id : new String[] {"still_shard", "crying_obsidian", "popped_chorus_fruit"}) {
            var recipe = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("slowlight", "ageing/" + id));
            boolean voids = recipe.isPresent() && recipe.get().value() instanceof AgeingRecipe r
                    && r.condition().sky() == AgeingCondition.Sky.VOID;
            check("condition.void_sky." + id, voids, "recipe slowlight:ageing/" + id + " wants the End's empty sky");
        }
    }

    // ---- small touches: tags, wandering trader, five recipes, the Sundial -------------------------------------------

    private static void touchesChecks(MinecraftServer server, ServerLevel level) {
        check("touches.sniffer_food", new ItemStack(SlowlightItems.DUSKBLOOM_SEEDS.get()).is(net.minecraft.tags.ItemTags.SNIFFER_FOOD),
                "duskbloom seeds in #minecraft:sniffer_food");
        check("touches.saplings_tag", new ItemStack(SlowlightItems.GLOAMWOOD_SAPLING.get()).is(net.minecraft.tags.ItemTags.SAPLINGS),
                "gloamwood seedling in #minecraft:saplings");

        // the wandering trader: every listing he can roll, generic (1) and rare (2), made into offers
        var trader = EntityType.WANDERING_TRADER.create(level);
        java.util.Set<Item> sold = new java.util.HashSet<>();
        if (trader != null) {
            for (int tier : new int[] {1, 2}) {
                var listings = net.minecraft.world.entity.npc.VillagerTrades.WANDERING_TRADER_TRADES.get(tier);
                for (var listing : listings) {
                    var offer = listing.getOffer(trader, level.random);
                    if (offer != null) {
                        sold.add(offer.getResult().getItem());
                    }
                }
            }
            trader.discard();
        }
        check("touches.wanderer_trades", sold.contains(SlowlightItems.DUSKBLOOM_SEEDS.get())
                        && sold.contains(SlowlightItems.GLOAMWOOD_SAPLING.get())
                        && sold.contains(SlowlightItems.PATIENCE_POTTERY_SHERD.get())
                        && sold.contains(SlowlightItems.SLOWLIGHT_SHARD.get()),
                "wanderer sells seeds/seedling/sherd/shard among " + sold.size() + " items");

        // five ageing recipes, each with its result and, where it has one, its condition
        Object[][] want = {{"fermented_spider_eye", Items.FERMENTED_SPIDER_EYE, 1, AgeingCondition.Sky.ANY},
                {"poisonous_potato", Items.POISONOUS_POTATO, 1, AgeingCondition.Sky.COVERED},
                {"bone_meal", Items.BONE_MEAL, 6, AgeingCondition.Sky.ANY},
                {"paper", Items.PAPER, 1, AgeingCondition.Sky.OPEN},
                {"dead_bush", Items.DEAD_BUSH, 1, AgeingCondition.Sky.COVERED}};
        for (Object[] w : want) {
            var r = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("slowlight", "ageing/" + w[0]));
            boolean ok = r.isPresent() && r.get().value() instanceof AgeingRecipe a && a.result().is((Item) w[1])
                    && a.result().getCount() == (int) w[2] && a.condition().sky() == w[3];
            check("touches.recipe." + w[0], ok, "slowlight:ageing/" + w[0] + " -> " + w[2] + " " + w[1] + ", sky " + w[3]);
        }
        check("touches.sundial_recipe", server.getRecipeManager().byKey(ResourceLocation.parse("slowlight:sundial")).isPresent(),
                "crafting recipe slowlight:sundial");

        // the Sundial's reading over a day, under open sky and then roofed over
        level.setBlock(SUNDIAL.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(SUNDIAL, SlowlightBlocks.SUNDIAL.get().defaultBlockState(), 3);
        long was = level.getDayTime();
        level.setDayTime(6000L);
        int noon = SundialBlock.reading(level, SUNDIAL);
        level.setDayTime(2000L);
        int morning = SundialBlock.reading(level, SUNDIAL);
        level.setDayTime(18000L);
        int night = SundialBlock.reading(level, SUNDIAL);
        level.setDayTime(6000L);
        level.setBlock(SUNDIAL.above(2), Blocks.STONE.defaultBlockState(), 3);
        int roofed = SundialBlock.reading(level, SUNDIAL);
        level.setBlock(SUNDIAL.above(2), Blocks.AIR.defaultBlockState(), 3);
        level.setDayTime(was);
        check("sundial.reads_the_sun", noon == 15 && morning > 0 && morning < 15 && night == 0 && roofed == 0,
                "noon " + noon + ", morning " + morning + ", midnight " + night + ", roofed noon " + roofed);
        ServerLevel nether = server.getLevel(Level.NETHER);
        check("sundial.no_sun_in_nether", nether == null || SundialBlock.reading(nether, new BlockPos(0, 128, 0)) == 0,
                "nether reads 0");
    }

    /** Some ticks on, the dial's own scheduled tick has written the reading into its state for the comparator. */
    private static void sundialTicked(ServerLevel level) {
        BlockState state = level.getBlockState(SUNDIAL);
        int expect = SundialBlock.reading(level, SUNDIAL);
        int got = state.is(SlowlightBlocks.SUNDIAL.get()) ? state.getAnalogOutputSignal(level, SUNDIAL) : -1;
        check("sundial.comparator_follows", got == expect, "comparator " + got + ", reading " + expect);
    }

    // ---- Potter -------------------------------------------------------------------------------------------------

    private static void potterCheck(ServerLevel level) {
        var poi = PoiTypes.forState(SlowlightBlocks.PATIENT_KILN.get().defaultBlockState());
        check("potter.kiln_is_workstation", poi.isPresent() && poi.get().is(SlowlightVillagers.POTTER_POI)
                        && poi.get().is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE),
                "kiln poi " + poi.map(h -> h.unwrapKey().map(Object::toString).orElse("?")).orElse("none"));
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) {
            check("potter.trades", false, "could not make a villager");
            return;
        }
        v.moveTo(Vec3.atBottomCenterOf(VILLAGER));
        v.setVillagerData(v.getVillagerData().setProfession(SlowlightVillagers.POTTER.get()).setLevel(1));
        level.addFreshEntity(v);
        int offers = v.getOffers().size();
        boolean clay = v.getOffers().stream().anyMatch(o -> o.getBaseCostA().is(Items.CLAY_BALL) && o.getResult().is(Items.EMERALD));
        check("potter.trades", offers >= 2 && clay, offers + " novice offers, buys clay=" + clay
                + ", name " + v.getDisplayName().getString());
        v.discard();
    }

    // ---- old kiln ruin ------------------------------------------------------------------------------------------

    private static void ruinCheck(MinecraftServer server, ServerLevel level) {
        ResourceLocation id = ResourceLocation.parse("slowlight:kiln_ruin");
        check("ruin.structure_registered", server.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(id)
                && server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).containsKey(id), "structure + structure set " + id);
        var plains = server.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        check("ruin.biomes", plains.is(TagKey.create(Registries.BIOME, ResourceLocation.parse("slowlight:has_structure/kiln_ruin"))),
                "plains in #slowlight:has_structure/kiln_ruin");
        Optional<StructureTemplate> template = level.getStructureManager().get(id);
        if (template.isEmpty()) {
            check("ruin.template_places", false, "no template " + id);
            return;
        }
        level.setBlock(RUIN.offset(5, 1, 4), Blocks.AIR.defaultBlockState(), 3);
        template.get().placeInWorld(level, RUIN, RUIN, new StructurePlaceSettings(), level.random, 2);
        BlockPos kilnAt = RUIN.offset(5, 1, 4);
        boolean ready = level.getBlockEntity(kilnAt) instanceof PatientKilnBlockEntity k && k.finished().is(SlowlightItems.MEAD.get())
                && k.working().isEmpty();
        boolean gravel = level.getBlockEntity(RUIN.offset(8, 0, 2)) instanceof net.minecraft.world.level.block.entity.BrushableBlockEntity b
                && b.saveWithoutMetadata(level.registryAccess()).getString("LootTable").equals("slowlight:archaeology/kiln_gravel");
        boolean pot = level.getBlockEntity(RUIN.offset(3, 1, 6)) instanceof net.minecraft.world.level.block.entity.DecoratedPotBlockEntity p
                && p.getDecorations().front().equals(Optional.of(SlowlightItems.PATIENCE_POTTERY_SHERD.get()))
                || level.getBlockEntity(RUIN.offset(3, 1, 6)) instanceof net.minecraft.world.level.block.entity.DecoratedPotBlockEntity p2
                && p2.getDecorations().ordered().contains(SlowlightItems.PATIENCE_POTTERY_SHERD.get());
        check("ruin.template_places", ready && gravel && pot, "kiln ready with mead=" + ready + ", dig-site gravel=" + gravel
                + ", pot with Patience sherd=" + pot);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private static void click(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack stack, boolean sneak) {
        player.setShiftKeyDown(sneak);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
        player.setShiftKeyDown(false);
    }

    private static int count(ServerLevel level, AABB box, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (e.getItem().is(item)) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }
}
