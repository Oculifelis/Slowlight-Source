package dev.slowlight.autotest;

import dev.slowlight.SlowlightDecor;
import dev.slowlight.block.LapseBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

import static dev.slowlight.autotest.ServerChecks.check;
import static dev.slowlight.autotest.ServerChecks.info;

/**
 * Play-test checks for the decor batch: the shard and plank variant sets, their stonecutter and Rechiseled data, the
 * bookshelf, the compat foods' conditions, and the Lapse Block's timing. ServerChecks starts it from setScene; it runs
 * its own ticks in a patch of ground at x 62..64, z 12, clear of the rest checks.
 */
@EventBusSubscriber(modid = "slowlight_autotest")
public final class DecorChecks {
    private static final int Y = -60;
    private static final BlockPos A = new BlockPos(62, Y, 12);
    private static final BlockPos B = A.east();
    private static final BlockPos C = B.east();
    private static final BlockPos POWER = A.north();

    private static int t = -1;

    private DecorChecks() {
    }

    static void start(MinecraftServer server, ServerLevel level) {
        t = 0;
        dataChecks(server);
        for (BlockPos p : List.of(A, B, C)) {
            level.setBlock(p, SlowlightDecor.LAPSE_BLOCK.get().defaultBlockState(), 3);
        }
        level.setBlock(POWER, Blocks.AIR.defaultBlockState(), 3);
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (t < 0) {
            return;
        }
        try {
            step(event.getServer().overworld());
        } catch (Throwable e) {
            ServerChecks.LOG.error("SLTEST FAIL decor.exception :: decor step threw", e);
            t = -1;
        }
    }

    private static boolean lapsed(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getValue(LapseBlock.LAPSED);
    }

    private static void step(ServerLevel level) {
        t++;
        if (t == 5) {
            level.setBlock(POWER, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            check("lapse.lapses_on_power", lapsed(level, A) && !lapsed(level, B),
                    "powered A lapsed=" + lapsed(level, A) + ", B (not powered yet) lapsed=" + lapsed(level, B));
            check("lapse.no_collision", level.getBlockState(A).getCollisionShape(level, A, CollisionContext.empty()).isEmpty(),
                    "a lapsed block has no collision");
        } else if (t == 5 + 2 * LapseBlock.SPREAD_TICKS + 1) {
            check("lapse.spreads", lapsed(level, B) && lapsed(level, C),
                    "B lapsed=" + lapsed(level, B) + ", C lapsed=" + lapsed(level, C) + " " + (2 * LapseBlock.SPREAD_TICKS) + " ticks on");
        } else if (t == 5 + LapseBlock.LAPSE_TICKS + 1) {
            check("lapse.returns", !lapsed(level, A) && lapsed(level, C),
                    "after " + LapseBlock.LAPSE_TICKS + " ticks A back=" + !lapsed(level, A) + ", C (lapsed later) still out=" + lapsed(level, C));
        } else if (t == 5 + LapseBlock.LAPSE_TICKS + 20) {
            check("lapse.held_power_lapses_once", !lapsed(level, A) && !lapsed(level, B) && !lapsed(level, C),
                    "with the redstone block still there, all three stay back: " + lapsed(level, A) + lapsed(level, B) + lapsed(level, C));
            info("decor", "done");
            t = -1;
        }
    }

    private static void dataChecks(MinecraftServer server) {
        int expected = 2 * (1 + 3 * SlowlightDecor.GEM_VARIANTS.size()) + 3 * SlowlightDecor.PLANK_VARIANTS.size() + 2 + 1 + 1
                + SlowlightDecor.CHIPPED_SHELVES.size();
        check("decor.block_items", SlowlightDecor.BLOCK_ITEMS.size() == expected,
                SlowlightDecor.BLOCK_ITEMS.size() + " decor block items (expected " + expected + ")");
        var recipes = server.getRecipeManager();
        for (String id : List.of("slowlight:slowlight_shard_block", "slowlight:still_shard_from_block", "slowlight:lapse_block",
                "slowlight:gloamwood_bookshelf", "slowlight:stonecutting/gloamwood_planks_wavy_from_gloamwood_planks",
                "slowlight:stonecutting/still_shard_block_grid_slab_from_still_shard_block_grid",
                "slowlight:stonecutting/slowlight_shard_cluster_from_slowlight_shard_block")) {
            check("decor.recipe." + id.substring(10), recipes.byKey(ResourceLocation.parse(id)).isPresent(), id);
        }
        // The compat foods' mods are not in the test, so their kiln recipes must be absent.
        check("decor.compat_foods_hidden", recipes.byKey(ResourceLocation.parse("slowlight:ageing/compat/aether/white_apple_cider")).isEmpty()
                && recipes.byKey(ResourceLocation.parse("slowlight:ageing/compat/twilightforest/torchberry_preserve")).isEmpty(),
                "Aether and Twilight Forest foods have no recipe without their mods");
        if (ModList.get().isLoaded("aerialhell")) {
            List<String> missing = new java.util.ArrayList<>();
            for (String id : List.of("aerial_berry_preserve", "ruby_aerial_berry_preserve", "volucite_aerial_berry_preserve", "vibrant_aerial_berry_preserve", "shadow_fruit_preserve", "candied_glowing_stick_fruit", "stellar_rock_sugar", "cured_phantom_meat", "cured_turtle_meat", "stellar_rusk", "shadow_fruit_preserve_from_dark_shadow_fruit", "leather_from_venomous_snake_skin", "leather_from_rotten_leather")) {
                if (recipes.byKey(ResourceLocation.parse("slowlight:ageing/compat/aerialhell/" + id)).isEmpty()) {
                    missing.add(id);
                }
            }
            check("decor.aerialhell_recipes", missing.isEmpty(), "Aerial Hell's kiln recipes load with it; missing " + missing);
            check("decor.aerialhell_pressable", new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("aerialhell:bellflower")))
                    .is(ItemTags.create(ResourceLocation.parse("slowlight:pressable"))), "aerialhell:bellflower goes in the Flower Press");
        }
        for (String set : List.of("slowlight_shard_block", "still_shard_block", "gloamwood_planks")) {
            check("decor.rechiseled." + set, server.getResourceManager().getResource(
                    ResourceLocation.fromNamespaceAndPath("slowlight", "chiseling_recipes/" + set + ".json")).isPresent(),
                    "data/slowlight/chiseling_recipes/" + set + ".json ships");
        }
        Block shelf = SlowlightDecor.GLOAMWOOD_BOOKSHELF.get();
        check("decor.bookshelf_powers_enchanting", shelf.defaultBlockState().is(BlockTags.ENCHANTMENT_POWER_PROVIDER), "in #enchantment_power_provider");
        Block full = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("slowlight:full_gloamwood_bookshelf"));
        check("decor.chipped_shelves", full.defaultBlockState().is(BlockTags.ENCHANTMENT_POWER_PROVIDER)
                && full.defaultBlockState().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                        ResourceLocation.parse("chipped:bookshelf")))
                && recipes.byKey(ResourceLocation.parse("slowlight:stonecutting/full_gloamwood_bookshelf_from_gloamwood_bookshelf")).isPresent(),
                "Gloamwood Chipped shelves: in #chipped:bookshelf and #enchantment_power_provider, stonecutter route");
        Block wavy = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("slowlight:gloamwood_planks_wavy"));
        check("decor.plank_variant_is_planks", wavy.defaultBlockState().is(BlockTags.PLANKS) && wavy.defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE),
                "gloamwood_planks_wavy in #planks and #mineable/axe");
        Block grid = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("slowlight:still_shard_block_grid_stairs"));
        check("decor.gem_variant_tags", grid.defaultBlockState().is(BlockTags.STAIRS) && grid.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                "still_shard_block_grid_stairs in #stairs and #mineable/pickaxe");
    }
}
