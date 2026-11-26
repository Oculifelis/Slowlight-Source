package dev.slowlight.autotest;

import dev.slowlight.block.EmberKilnBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;

import static dev.slowlight.autotest.ServerChecks.check;

/** The Ember Kiln heat tag. The test mod's own data adds the desert to #slowlight:ember_kiln_heat, as a pack would. */
final class HeatChecks {
    private HeatChecks() {}

    static void run(ServerLevel level) {
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        boolean desert = biomes.getHolderOrThrow(Biomes.DESERT).is(EmberKilnBlockEntity.HEAT);
        boolean plains = biomes.getHolderOrThrow(Biomes.PLAINS).is(EmberKilnBlockEntity.HEAT);
        check("ember_kiln.heat_tag_override", desert && !plains, "desert in tag=" + desert + ", plains in tag=" + plains);
        BlockPos at = BlockPos.ZERO;
        boolean inTag = level.getBiome(at).is(EmberKilnBlockEntity.HEAT);
        check("ember_kiln.heat_follows_biome", EmberKilnBlockEntity.hotEnough(level, at) == inTag,
                "overworld at 0,0 biome in tag=" + inTag + ", hot=" + EmberKilnBlockEntity.hotEnough(level, at));
    }
}
