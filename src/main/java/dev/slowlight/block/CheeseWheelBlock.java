package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wheel of cheese: a bucket of milk left a week in a dark cellar. Set down, it is eaten a wedge at a time like
 * cake, four wedges to a wheel, each one more filling than a slice of cake. A comparator reads how much is left.
 */
public class CheeseWheelBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CheeseWheelBlock> CODEC = simpleCodec(CheeseWheelBlock::new);
    /** Wedges eaten, 0 for a whole wheel. */
    public static final IntegerProperty WEDGES = IntegerProperty.create("wedges", 0, 3);
    public static final int NUTRITION = 4;
    public static final float SATURATION = 0.5F;

    /** Whole wheel, then with one, two and three quarters cut away (the cut side faces the way it was set down). */
    private static final VoxelShape[] SHAPES = {
            Block.box(2, 0, 2, 14, 6, 14),
            Shapes.or(Block.box(2, 0, 8, 14, 6, 14), Block.box(2, 0, 2, 8, 6, 8)),
            Block.box(2, 0, 8, 14, 6, 14),
            Block.box(8, 0, 8, 14, 6, 14),
    };

    public CheeseWheelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WEDGES, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WEDGES);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return rotate(SHAPES[state.getValue(WEDGES)], state.getValue(FACING));
    }

    /** The shapes above are drawn facing north; turn them about the block's centre to face the right way. */
    private static VoxelShape rotate(VoxelShape shape, Direction facing) {
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        VoxelShape out = shape;
        for (int i = 0; i < turns; i++) {
            VoxelShape[] next = {Shapes.empty()};
            out.forAllBoxes((x1, y1, z1, x2, y2, z2) -> next[0] = Shapes.or(next[0],
                    Shapes.box(1.0D - z2, y1, x1, 1.0D - z1, y2, x2)));
            out = next[0];
        }
        return out;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.canEat(false)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        eat(level, pos, state, player);
        return InteractionResult.CONSUME;
    }

    private static void eat(LevelAccessor level, BlockPos pos, BlockState state, Player player) {
        player.getFoodData().eat(NUTRITION, SATURATION);
        level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        level.gameEvent(player, GameEvent.EAT, pos);
        int wedges = state.getValue(WEDGES);
        if (wedges < 3) {
            level.setBlock(pos, state.setValue(WEDGES, wedges + 1), 3);
        } else {
            level.removeBlock(pos, false);
            level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return (4 - state.getValue(WEDGES)) * 3;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
