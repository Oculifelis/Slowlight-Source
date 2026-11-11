package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A lamp that keeps the opposite hours to everyone else: dim through the day, full dark-hour bright.
 * It is a lantern in every other respect: it stands on a block or hangs under one, and can be waterlogged.
 *
 * <p>It is checked on a scheduled tick every five seconds rather than a random one, so a row of them
 * comes up together at dusk instead of straggling in one by one.
 */
public class SlowlightLanternBlock extends LanternBlock {
    public static final MapCodec<SlowlightLanternBlock> CODEC = simpleCodec(SlowlightLanternBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final int CHECK_INTERVAL = 100;
    /** Foot, glass case and cap; hanging, the same shape 2px higher with its ring hooked into the block above. */
    private static final VoxelShape STANDING = shape(0);
    private static final VoxelShape HUNG = shape(2);

    private static VoxelShape shape(int up) {
        return Shapes.or(Block.box(4, up, 4, 12, up + 1, 12), Block.box(5, up + 1, 5, 11, up + 10, 11),
                Block.box(6, up + 10, 6, 10, Math.min(16, up + 16), 10));
    }

    public SlowlightLanternBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<LanternBlock> codec() {
        return (MapCodec<LanternBlock>) (MapCodec<?>) CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HANGING) ? HUNG : STANDING;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(LIT, shouldBeLit(context.getLevel()));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        level.scheduleTick(pos, this, CHECK_INTERVAL);
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, RandomSource random) {
        boolean lit = shouldBeLit(level);
        if (state.getValue(LIT) != lit) {
            level.setBlock(pos, state.setValue(LIT, lit), 3);
        }
        level.scheduleTick(pos, this, CHECK_INTERVAL);
    }

    /**
     * A backstop. The lantern normally runs on scheduled ticks, which persist with the chunk, but a
     * lantern placed by a structure or moved by some other mod may end up without one; a random tick
     * puts it back on the schedule rather than leaving it stuck at whatever it was.
     */
    @Override
    protected void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, RandomSource random) {
        tick(state, level, pos, random);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (random.nextInt(40) == 0) {
            // Now and then a moth comes to the light and circles it a while.
            level.addParticle(dev.slowlight.SlowlightNights.MOTH.get(), pos.getX() + 0.5D,
                    pos.getY() + (state.getValue(HANGING) ? 0.35D : 0.5D), pos.getZ() + 0.5D, 0.0D, 0.0D, 0.0D);
        }
        if (random.nextInt(5) != 0) {
            return;
        }
        // One slow mote, drifting down out of the case.
        double y = state.getValue(HANGING) ? 0.3D : 0.15D;
        level.addParticle(ParticleTypes.END_ROD,
                pos.getX() + 0.4D + random.nextDouble() * 0.2D,
                pos.getY() + y,
                pos.getZ() + 0.4D + random.nextDouble() * 0.2D,
                0.0D, -0.008D, 0.0D);
    }

    private static boolean shouldBeLit(LevelAccessor level) {
        if (!(level instanceof Level real)) {
            return false;
        }
        // Dusk through dawn by the world's own clock. In dimensions with no day cycle the sky never
        // brightens, so the lantern simply stays up.
        if (!real.dimensionType().hasSkyLight()) {
            return true;
        }
        return !real.isDay();
    }
}
