package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import dev.slowlight.SlowlightCrafts;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tall beeswax candle in a copper dish that burns down over days of real light, a stage a day, four days in all:
 * leave it lit by a window and its height says how long you were away. Lit with flint and steel or a fire charge like
 * any candle (it is in #minecraft:candles); an empty hand puts it out, or asks how much is left.
 */
public class SlowCandleBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<SlowCandleBlock> CODEC = simpleCodec(SlowCandleBlock::new);
    public static final int STAGES = 4;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 1, STAGES);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /** The wax's height above the dish per stage; the same numbers as tools/make_crafts.py slow_candle(). */
    public static final int[] WAX = {0, 3, 6, 9, 12};
    private static final VoxelShape DISH = Block.box(4, 0, 4, 12, 1, 12);
    private static final VoxelShape[] SHAPES = new VoxelShape[STAGES + 1];

    static {
        for (int s = 1; s <= STAGES; s++) {
            SHAPES[s] = Shapes.or(DISH, Block.box(6, 1, 6, 10, 1 + WAX[s] + 1, 10));
        }
    }

    public SlowCandleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, STAGES).setValue(LIT, false).setValue(WATERLOGGED, false));
    }

    public static int light(BlockState state) {
        return state.getValue(LIT) ? 12 : 0;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, LIT, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return dir == Direction.DOWN && !canSurvive(state, level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (!state.getValue(WATERLOGGED) && fluid.getType() == Fluids.WATER) {
            // Water puts it out, as it does any candle.
            level.setBlock(pos, state.setValue(WATERLOGGED, true).setValue(LIT, false), 3);
            level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
            return true;
        }
        return false;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(STAGE)];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SlowCandleBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, SlowlightCrafts.SLOW_CANDLE_BE.get(), SlowCandleBlockEntity::serverTick);
    }

    /** Unlit, an item in hand gets its own turn first, so flint and steel or a fire charge lights it as it would any
     * candle (the empty-hand use below would show the status instead). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty() && !state.getValue(LIT)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(LIT) && !player.isSecondaryUseActive()) {
            level.setBlock(pos, state.setValue(LIT, false), 3);
            level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else if (level.getBlockEntity(pos) instanceof SlowCandleBlockEntity candle) {
            player.displayClientMessage(candle.status(), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double y = pos.getY() + (2 + WAX[state.getValue(STAGE)] + 1) / 16.0D;
        if (random.nextFloat() < 0.3F) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5D, y, pos.getZ() + 0.5D, 0.0D, 0.0D, 0.0D);
        }
        level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5D, y, pos.getZ() + 0.5D, 0.0D, 0.0D, 0.0D);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.slowlight.slow_candle.tip").withStyle(ChatFormatting.GRAY));
    }
}
