package dev.slowlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.WeatheringCopperFullBlock;
import net.minecraft.world.level.block.WeatheringCopperSlabBlock;
import net.minecraft.world.level.block.WeatheringCopperStairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gloamwood that silvers in the weather, in copper's four steps: Gloamwood, Faded, Weathered, Silvered. It uses
 * copper's own machinery, so the steps come from NeoForge's oxidizables data map, a honeycomb waxes it and an axe
 * scrapes a step (or the wax) back off. Unlike copper it only weathers out in the open: a plank with sky over one of
 * its faces. Houses built of it keep their colour indoors.
 */
public final class GloamWeathering {
    private GloamWeathering() {
    }

    /** True when the sky reaches one of the block's faces, the bottom aside. */
    public static boolean outdoors(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (d != Direction.DOWN && level.canSeeSky(pos.relative(d))
                    && !level.getBlockState(pos.relative(d)).isSolidRender(level, pos.relative(d))) {
                return true;
            }
        }
        return false;
    }

    public static class Planks extends WeatheringCopperFullBlock {
        public Planks(WeatheringCopper.WeatherState age, Properties properties) {
            super(age, properties);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (outdoors(level, pos)) {
                super.randomTick(state, level, pos, random);
            }
        }
    }

    public static class Stairs extends WeatheringCopperStairBlock {
        public Stairs(WeatheringCopper.WeatherState age, BlockState base, Properties properties) {
            super(age, base, properties);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (outdoors(level, pos)) {
                super.randomTick(state, level, pos, random);
            }
        }
    }

    public static class Slab extends WeatheringCopperSlabBlock {
        public Slab(WeatheringCopper.WeatherState age, Properties properties) {
            super(age, properties);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (outdoors(level, pos)) {
                super.randomTick(state, level, pos, random);
            }
        }
    }
}
