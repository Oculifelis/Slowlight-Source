package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import dev.slowlight.SlowlightWays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A candle that came back from the End already burning, with a violet flame. It is placed lit and stacks up to four
 * like any candle, but it never goes on a cake.
 */
public class StarwickCandleBlock extends CandleBlock {
    public static final MapCodec<StarwickCandleBlock> CODEC = simpleCodec(StarwickCandleBlock::new);

    public StarwickCandleBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<CandleBlock> codec() {
        return (MapCodec<CandleBlock>) (MapCodec<?>) CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(LIT, !state.getValue(WATERLOGGED));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        for (Vec3 offset : getParticleOffsets(state)) {
            double x = pos.getX() + offset.x;
            double y = pos.getY() + offset.y;
            double z = pos.getZ() + offset.z;
            float f = random.nextFloat();
            if (f < 0.3F) {
                level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.0D, 0.0D);
                if (f < 0.17F) {
                    level.playLocalSound(x + 0.5D, y + 0.5D, z + 0.5D, SoundEvents.CANDLE_AMBIENT, SoundSource.BLOCKS,
                            1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false);
                }
            }
            level.addParticle(SlowlightWays.STARWICK_FLAME.get(), x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }
}
