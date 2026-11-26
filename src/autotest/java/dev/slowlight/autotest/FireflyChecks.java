package dev.slowlight.autotest;

import dev.slowlight.SlowlightBlocks;
import dev.slowlight.SlowlightFireflies;
import dev.slowlight.recipe.AgeingCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;

import static dev.slowlight.autotest.ServerChecks.check;

/**
 * Colorfly compat: a firefly lantern hung over a kiln keeps a night-only condition true at noon, a plain block does not,
 * and Firefly Mead carries its two effects. The recipes are gated on Colorfly, so they only count when it is loaded.
 * ServerChecks starts it from setScene; it uses x 96, z 40.
 */
final class FireflyChecks {
    private static final BlockPos KILN = new BlockPos(96, -60, 40);

    private FireflyChecks() {
    }

    static void start(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        AgeingCondition night = new AgeingCondition(AgeingCondition.Sky.ANY, AgeingCondition.Weather.ANY, AgeingCondition.Time.NIGHT);
        long was = level.getDayTime();
        level.setDayTime(6000L);
        level.updateSkyBrightness();
        level.setBlock(KILN, SlowlightBlocks.PATIENT_KILN.get().defaultBlockState(), 3);
        level.setBlock(KILN.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(KILN.above(2), Blocks.AIR.defaultBlockState(), 3);
        boolean bare = night.holds(level, KILN);
        level.setBlock(KILN.above(2), Blocks.STONE.defaultBlockState(), 3);
        boolean stone = night.holds(level, KILN);
        level.setBlock(KILN.above(), SlowlightFireflies.GREENFLY_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        boolean hung = night.holds(level, KILN);
        level.setBlock(KILN.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(KILN.above(2), SlowlightFireflies.BLUEFLY_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        boolean twoUp = night.holds(level, KILN);
        level.setDayTime(was);
        level.updateSkyBrightness();
        check("fireflies.night_light", !bare && !stone && hung && twoUp,
                "night-only at noon: bare " + bare + ", stone above " + stone + ", greenfly lantern above " + hung
                        + ", bluefly two up " + twoUp);
        check("fireflies.lantern_light", level.getBlockState(KILN.above(2)).getLightEmission(level, KILN.above(2)) == 12,
                "bluefly lantern light " + level.getBlockState(KILN.above(2)).getLightEmission(level, KILN.above(2)));

        FoodProperties food = new ItemStack(SlowlightFireflies.FIREFLY_MEAD.get()).getFoodProperties(player);
        boolean nv = false, glow = false;
        if (food != null) {
            for (var e : food.effects()) {
                nv |= e.effect().is(MobEffects.NIGHT_VISION) && e.effect().getDuration() == 3600;
                glow |= e.effect().is(MobEffects.GLOWING) && e.effect().getDuration() == 600;
            }
        }
        check("fireflies.mead_effects", nv && glow, "night vision 3 min " + nv + ", glowing 30 s " + glow);

        boolean colorfly = net.neoforged.fml.ModList.get().isLoaded(SlowlightFireflies.COLORFLY);
        var recipes = server.getRecipeManager();
        for (String id : java.util.List.of("ageing/firefly_mead", "greenfly_gloamwood_lantern", "bluefly_gloamwood_lantern")) {
            boolean present = recipes.byKey(ResourceLocation.fromNamespaceAndPath("slowlight", id)).isPresent();
            check("fireflies.recipe." + id, present == colorfly, id + " loaded=" + present + ", colorfly=" + colorfly);
        }
    }
}
