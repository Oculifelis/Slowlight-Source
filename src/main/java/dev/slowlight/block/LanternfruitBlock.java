package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import dev.slowlight.SlowlightBlocks;
import dev.slowlight.SlowlightItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Lanternfruit hanging under Gloamwood leaves, the way cocoa hangs on jungle logs. It swells through four ages over
 * about three days; ripe, it glows at light 7, so a fruiting tree lights itself at night. Picking a ripe one gives
 * the fruit and leaves the stalk to start again at age 0. A Lanternfruit pressed against the underside of Gloamwood
 * leaves hangs there as a new one.
 */
public class LanternfruitBlock extends Block implements BonemealableBlock {
    public static final MapCodec<LanternfruitBlock> CODEC = simpleCodec(LanternfruitBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final int MAX_AGE = 3;
    public static final int GLOW = 7;
    /**
     * A block gets a random tick about every 1365 game ticks at the default speed, so one in 18 is about one age a
     * day, and three days from a bare stalk to ripe.
     */
    private static final int RIPEN_ONE_IN = 18;

    private static final VoxelShape[] SHAPES = {Block.box(7, 10, 7, 9, 16, 9), Block.box(6, 8, 6, 10, 16, 10),
            Block.box(5, 5, 5, 11, 16, 11), Block.box(5, 3, 5, 11, 16, 11)};

    public LanternfruitBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(AGE)];
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).is(SlowlightBlocks.GLOAMWOOD_LEAVES.get());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.UP && !canSurvive(state, level, pos)
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < MAX_AGE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(AGE) < MAX_AGE && random.nextInt(RIPEN_ONE_IN) == 0) {
            level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(AGE) < MAX_AGE) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            popResource(level, pos, new ItemStack(SlowlightItems.LANTERNFRUIT.get()));
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F,
                    0.8F + level.random.nextFloat() * 0.4F);
            BlockState picked = state.setValue(AGE, 0);
            level.setBlock(pos, picked, 2);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, picked));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(SlowlightItems.LANTERNFRUIT.get());
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(AGE) < MAX_AGE;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(AGE) < MAX_AGE || random.nextInt(14) != 0) {
            return;
        }
        // A spark drifting down off the ripe fruit.
        level.addParticle(ParticleTypes.WAX_ON, pos.getX() + 0.35D + random.nextDouble() * 0.3D, pos.getY() + 0.3D,
                pos.getZ() + 0.35D + random.nextDouble() * 0.3D, 0.0D, -0.02D, 0.0D);
    }
}
