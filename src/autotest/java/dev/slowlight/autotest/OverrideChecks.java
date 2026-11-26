package dev.slowlight.autotest;

import dev.slowlight.recipe.AgeingRecipe;
import dev.slowlight.recipe.TransitRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.resources.ResourceLocation;

import static dev.slowlight.autotest.ServerChecks.check;

/**
 * A datapack can change shipped data. The test mod loads after Slowlight, so its own data files act as a pack on top:
 * kiln podzol takes 9 days instead of 4, light blue glass sent to the Nether arrives as 2 soul sand, and a Gloamwood
 * slab weathers into smooth stone slab. Every other test still sees the shipped values.
 */
final class OverrideChecks {
    private OverrideChecks() {}

    static void run(MinecraftServer server) {
        Object r = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("slowlight", "ageing/podzol")).map(h -> (Object) h.value()).orElse(null);
        check("data.override.ageing_days", r instanceof AgeingRecipe a && a.days() == 9,
                "podzol " + (r instanceof AgeingRecipe a ? a.days() + " days (shipped 4)" : "missing"));
        r = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("slowlight", "transit/soul_mosaic_glass")).map(h -> (Object) h.value()).orElse(null);
        check("data.override.transit_result", r instanceof TransitRecipe t && t.result().is(Items.SOUL_SAND) && t.result().getCount() == 2,
                "light blue glass to the Nether gives " + (r instanceof TransitRecipe t ? t.result() : "nothing"));
        Block slab = block("gloamwood_slab"), next = WeatheringCopper.getNext(slab).orElse(null);
        check("data.override.weathering", next == Blocks.SMOOTH_STONE_SLAB, "gloamwood_slab weathers to " + next);
        Block sand = WeatheringCopper.getNext(Blocks.CUT_SANDSTONE).orElse(null);
        check("data.add.weathering_vanilla", sand == Blocks.SMOOTH_SANDSTONE, "cut_sandstone weathers to " + sand);
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("slowlight", path));
    }
}
