package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import dev.slowlight.Gloamwood;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Gloamwood Seedling. A vanilla sapling grows in daylight on one random tick in seven; this one grows on the
 * same odds but only after dark (or where there is no sky to keep time by), and light does not matter to it. Bone
 * meal hurries it as it does any sapling.
 */
public class GloamwoodSaplingBlock extends SaplingBlock {
    public static final MapCodec<GloamwoodSaplingBlock> CODEC = simpleCodec(GloamwoodSaplingBlock::new);

    public GloamwoodSaplingBlock(Properties properties) {
        super(Gloamwood.GROWER, properties);
    }

    @Override
    public MapCodec<? extends SaplingBlock> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1) || !DuskbloomCropBlock.isNight(level)) {
            return;
        }
        if (random.nextInt(7) == 0) {
            advanceTree(level, pos, state, random);
        }
    }
}
