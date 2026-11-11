package dev.slowlight.autotest;

import dev.slowlight.SlowlightWays;
import dev.slowlight.block.DisplayPedestalBlockEntity;
import dev.slowlight.block.PearlLanternBlock;
import dev.slowlight.block.WaystoneBlock;
import dev.slowlight.block.WaystoneBlockEntity;
import dev.slowlight.block.WaystoneNetwork;
import dev.slowlight.item.HalfPearlItem;
import dev.slowlight.recipe.AgeingRecipe;
import dev.slowlight.recipe.TransitRecipe;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static dev.slowlight.autotest.ServerChecks.check;
import static dev.slowlight.autotest.ServerChecks.info;

/**
 * Play-test checks for the Waystones batch: pairing two stones with a Twin Pearl, sending a stack (paid with a Chorus
 * Pearl), the shipment arriving at the twin, transit routes by dimension, the Display Pedestal, the Pearl Lantern's
 * cages, the Starwick Candle and the data. ServerChecks starts it from setScene; it uses x 66..72, z 20 (Crafts has z 12).
 */
@EventBusSubscriber(modid = "slowlight_autotest")
public final class WaysChecks {
    private static final int Y = -60;
    private static final BlockPos A = new BlockPos(66, Y, 20);
    private static final BlockPos B = new BlockPos(68, Y, 20);
    private static final BlockPos STAND = new BlockPos(70, Y, 20);
    private static final BlockPos LANTERN = new BlockPos(72, Y, 20);
    private static final BlockPos CANDLE_GROUND = new BlockPos(72, Y - 1, 22);

    private static int t = -1;

    private WaysChecks() {
    }

    static void start(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        dataChecks(server, level);
        GameType mode = player.gameMode.getGameModeForPlayer();
        player.setGameMode(GameType.SURVIVAL);
        try {
            pairAndSend(level, player);
            pedestal(level, player);
            lanternAndCandle(level, player);
        } finally {
            player.setGameMode(mode);
            player.getInventory().clearOrCountMatchingItems(s -> BuiltInRegistries.ITEM.getKey(s.getItem())
                    .getNamespace().equals("slowlight") || s.is(Items.DIRT) || s.is(Items.DIAMOND), -1,
                    player.inventoryMenu.getCraftSlots());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        t = 0;
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (t < 0) {
            return;
        }
        try {
            ServerLevel level = event.getServer().overworld();
            if (++t == 45) {
                WaystoneBlockEntity b = (WaystoneBlockEntity) level.getBlockEntity(B);
                List<ItemStack> got = b.hasArrived() ? b.takeArrived() : List.of();
                check("ways.arrives_at_twin", got.size() == 1 && got.get(0).is(Items.DIRT) && got.get(0).getCount() == 5,
                        "after the trip the twin stone holds " + got);
                info("ways", "done");
                t = -1;
            }
        } catch (Throwable e) {
            ServerChecks.LOG.error("SLTEST FAIL ways.exception :: ways step threw", e);
            t = -1;
        }
    }

    private static void click(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack stack, boolean sneak) {
        player.setShiftKeyDown(sneak);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
        player.setShiftKeyDown(false);
    }

    private static void pairAndSend(ServerLevel level, ServerPlayer player) {
        level.setBlock(A, SlowlightWays.WAYSTONE.get().defaultBlockState(), 3);
        level.setBlock(B, SlowlightWays.WAYSTONE.get().defaultBlockState(), 3);
        WaystoneBlockEntity a = (WaystoneBlockEntity) level.getBlockEntity(A);
        WaystoneBlockEntity b = (WaystoneBlockEntity) level.getBlockEntity(B);

        ItemStack dirt = new ItemStack(Items.DIRT, 5);
        click(level, player, A, dirt, false);
        check("ways.unpaired_refuses", dirt.getCount() == 5 && a.pair() == null, "an unpaired stone sends nothing");

        click(level, player, A, new ItemStack(SlowlightWays.TWIN_PEARL.get()), false);
        ItemStack half = ItemStack.EMPTY;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(SlowlightWays.HALF_PEARL.get())) {
                half = s;
            }
        }
        HalfPearlItem.Link link = half.get(SlowlightWays.PEARL_LINK.get());
        check("ways.twin_pearl_keeps_half", a.pair() != null && level.getBlockState(A).getValue(WaystoneBlock.LINKED)
                        && link != null && link.pair().equals(a.pair()) && link.twin().map(p -> p.pos().equals(A)).orElse(false),
                "stone A paired=" + a.pair() + ", player got a Half Pearl linked to " + link);
        if (half.isEmpty()) {
            return;
        }
        click(level, player, B, half.copy(), false);
        half.setCount(0);
        WaystoneNetwork net = WaystoneNetwork.get(level.getServer());
        check("ways.half_pearl_pairs", a.pair().equals(b.pair())
                        && net.partner(a.pair(), a.here()).map(p -> p.pos().equals(B)).orElse(false),
                "stone B pair=" + b.pair() + ", A's partner=" + net.partner(a.pair(), a.here()));

        ItemStack third = new ItemStack(SlowlightWays.HALF_PEARL.get());
        third.set(SlowlightWays.PEARL_LINK.get(), new HalfPearlItem.Link(a.pair(), Optional.empty()));
        level.setBlock(STAND, SlowlightWays.WAYSTONE.get().defaultBlockState(), 3);
        click(level, player, STAND, third, false);
        check("ways.pair_of_two_only", ((WaystoneBlockEntity) level.getBlockEntity(STAND)).pair() == null,
                "a third stone can't join a pair");
        level.setBlock(STAND, Blocks.AIR.defaultBlockState(), 3);

        click(level, player, A, dirt, false);
        check("ways.needs_pearl", dirt.getCount() == 5 && net.outgoing(a.pair(), a.here()).isEmpty(),
                "with no Chorus Pearl nothing is sent");

        player.getInventory().add(new ItemStack(SlowlightWays.CHORUS_PEARL.get(), 2));
        long now = WaystoneNetwork.now(level.getServer());
        click(level, player, A, dirt, false);
        var out = net.outgoing(a.pair(), a.here());
        check("ways.sends_paid", dirt.isEmpty() && pearls(player) == 1 && out.isPresent()
                        && out.get().arriveAt() == now + WaystoneBlock.SAME_DIMENSION_DAYS * AgeingRecipe.TICKS_PER_DAY,
                "hand emptied=" + dirt.isEmpty() + ", pearls left=" + pearls(player) + ", shipment=" + out);

        ItemStack more = new ItemStack(Items.DIRT, 3);
        click(level, player, A, more, false);
        check("ways.one_at_a_time", more.getCount() == 3 && pearls(player) == 1, "a second send waits for the first");

        // Bring the shipment's day forward so the twin collects it within the test.
        List<ItemStack> items = net.collect(a.pair(), b.here(), Long.MAX_VALUE);
        net.send(new WaystoneNetwork.Shipment(a.pair(), a.here(), items, now));
    }

    private static int pearls(ServerPlayer player) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(SlowlightWays.CHORUS_PEARL.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void pedestal(ServerLevel level, ServerPlayer player) {
        level.setBlock(STAND, SlowlightWays.DISPLAY_PEDESTAL.get().defaultBlockState(), 3);
        DisplayPedestalBlockEntity stand = (DisplayPedestalBlockEntity) level.getBlockEntity(STAND);
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 3);
        click(level, player, STAND, diamonds, false);
        check("pedestal.holds_one", diamonds.getCount() == 2 && stand.item().is(Items.DIAMOND) && stand.item().getCount() == 1,
                "pedestal holds " + stand.item() + ", hand " + diamonds);
        click(level, player, STAND, ItemStack.EMPTY, false);
        check("pedestal.gives_back", stand.item().isEmpty() && player.getInventory().countItem(Items.DIAMOND) >= 1,
                "empty hand takes it back");
    }

    private static void lanternAndCandle(ServerLevel level, ServerPlayer player) {
        level.setBlock(LANTERN, SlowlightWays.PEARL_LANTERN.get().defaultBlockState(), 3);
        click(level, player, LANTERN, ItemStack.EMPTY, true);
        check("lantern.sneak_cycles", level.getBlockState(LANTERN).getValue(PearlLanternBlock.SHAPE) == PearlLanternBlock.Style.CRADLE,
                "sneak-use with an empty hand: " + level.getBlockState(LANTERN).getValue(PearlLanternBlock.SHAPE));

        level.setBlock(CANDLE_GROUND, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(CANDLE_GROUND.above(), Blocks.AIR.defaultBlockState(), 3);
        click(level, player, CANDLE_GROUND, new ItemStack(SlowlightWays.STARWICK_CANDLE.get()), false);
        var candle = level.getBlockState(CANDLE_GROUND.above());
        check("starwick.placed_lit", candle.is(SlowlightWays.STARWICK_CANDLE.get()) && candle.getValue(CandleBlock.LIT),
                "placed: " + candle);
        check("starwick.candle_tags", candle.is(BlockTags.CANDLES)
                        && !new ItemStack(SlowlightWays.STARWICK_CANDLE.get()).is(ItemTags.CANDLES),
                "in the candles block tag, not the item tag (no cake for it)");
    }

    private static void dataChecks(MinecraftServer server, ServerLevel level) {
        var recipes = server.getRecipeManager();
        for (String id : List.of("waystone", "pearl_lantern", "pearl_block", "chorus_pearl_from_block", "pearl_chain",
                "display_pedestal", "hollowstone_wall", "ageing/chorus_pearl", "ageing/crimson_mosaic_glass",
                "transit/starglass", "transit/unfalling_feather", "stonecutting/hollowstone_slab_from_hollowstone",
                "stonecutting/warped_rose_glass_from_warped_mosaic_glass")) {
            check("ways.recipe." + id, recipes.byKey(ResourceLocation.fromNamespaceAndPath("slowlight", id)).isPresent(), id);
        }
        ResourceKey<Level> over = Level.OVERWORLD;
        ResourceKey<Level> nether = Level.NETHER;
        ResourceKey<Level> end = Level.END;
        route(recipes, level, Items.GLASS, over, end, "slowlight:starglass");
        route(recipes, level, Items.GLASS, over, over, null);
        route(recipes, level, Items.GLASS, end, nether, "minecraft:tinted_glass");
        route(recipes, level, Items.FEATHER, end, nether, "slowlight:unfalling_feather");
        route(recipes, level, Items.RED_STAINED_GLASS, over, nether, "slowlight:crimson_mosaic_glass");
        route(recipes, level, Items.APPLE, end, over, "slowlight:wandering_apple");

        check("ways.walls_tag", SlowlightWays.HOLLOWSTONE_WALL.get().defaultBlockState().is(BlockTags.WALLS)
                && new ItemStack(SlowlightWays.HOLLOWSTONE_WALL.get()).is(ItemTags.WALLS), "Hollowstone Wall in #walls");
        check("ways.nether_glass_count", SlowlightWays.NETHER_GLASS.size() == 12, SlowlightWays.NETHER_GLASS.size() + " Nether glasses");
        check("ways.curios_charm", server.getResourceManager().getResource(
                ResourceLocation.fromNamespaceAndPath("slowlight", "curios/entities/unfalling_feather.json")).isPresent(),
                "the charm slot file ships");
        check("ways.kubejs_schema", server.getResourceManager().getResource(
                ResourceLocation.fromNamespaceAndPath("slowlight", "kubejs/recipe_schema/transit.json")).isPresent(),
                "KubeJS transit schema ships");
    }

    private static void route(net.minecraft.world.item.crafting.RecipeManager recipes, ServerLevel level, Item item,
                              ResourceKey<Level> from, ResourceKey<Level> to, String expected) {
        Optional<TransitRecipe> r = TransitRecipe.find(recipes, new ItemStack(item), level, from, to);
        String got = r.map(x -> BuiltInRegistries.ITEM.getKey(x.result().getItem()).toString()).orElse(null);
        String name = BuiltInRegistries.ITEM.getKey(item).getPath() + "." + from.location().getPath() + "_to_" + to.location().getPath();
        check("ways.route." + name, java.util.Objects.equals(got, expected), name + " -> " + got + " (expected " + expected + ")");
    }
}
