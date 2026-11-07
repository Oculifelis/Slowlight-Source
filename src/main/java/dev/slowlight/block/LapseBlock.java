package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Lapse Block, Slowlight's reappearing block. A redstone pulse (the rising edge, so a held lever lapses it once)
 * takes it out of the world for {@link #LAPSE_TICKS}: no collision, no outline, a faint ghost left behind. The lapse
 * spreads to touching Lapse Blocks {@link #SPREAD_TICKS} later each, so a wall opens like a ripple and closes the same
 * way. It never closes on anything standing in it; it waits until the space is clear.
 */
public class LapseBlock extends Block {
    public static final MapCodec<LapseBlock> CODEC = simpleCodec(LapseBlock::new);
    public static final BooleanProperty LAPSED = BooleanProperty.create("lapsed");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final int LAPSE_TICKS = 60;
    public static final int SPREAD_TICKS = 2;
    private static final int BLOCKED_RETRY_TICKS = 10;

    public LapseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAPSED, false).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAPSED, POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(LAPSED) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(LAPSED) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) {
            return;
        }
        BlockState next = state.setValue(POWERED, powered);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        if (powered && !state.getValue(LAPSED)) {
            lapse((ServerLevel) level, pos, next);
        }
    }

    /** One scheduled tick does both jobs: a solid block lapses (the spread reaching it), a lapsed one comes back. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LAPSED)) {
            lapse(level, pos, state);
        } else if (!level.getEntitiesOfClass(Entity.class, new AABB(pos), e -> !e.isSpectator()).isEmpty()) {
            level.scheduleTick(pos, this, BLOCKED_RETRY_TICKS);
        } else {
            level.setBlock(pos, state.setValue(LAPSED, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.6F, 0.8F);
        }
    }

    private void lapse(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(LAPSED, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.6F);
        level.scheduleTick(pos, this, LAPSE_TICKS);
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState ns = level.getBlockState(n);
            if (ns.is(this) && !ns.getValue(LAPSED) && !level.getBlockTicks().hasScheduledTick(n, this)) {
                level.scheduleTick(n, this, SPREAD_TICKS);
            }
        }
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LAPSED);
    }
}
