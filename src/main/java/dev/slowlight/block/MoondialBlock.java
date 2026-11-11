package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * The Sundial's night twin: a slate dial with a silver disc that shows the moon. A comparator reads 1 at the full moon,
 * counting up through the waning and waxing phases to 8 the night before the next full one (5 is the new moon). With no
 * sky above it, or in a world with no moon, it reads nothing. The phase belongs to the whole day, so it reads in
 * daylight too, and a farm can be readied before the full moon rises.
 */
public class MoondialBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<MoondialBlock> CODEC = simpleCodec(MoondialBlock::new);

    /** 0 for no moon at all, else the moon phase plus one. Kept in the state for the comparator and the model. */
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 8);

    private static final int CHECK_TICKS = 20;

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 3, 15);

    public MoondialBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PHASE, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PHASE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** The reading for this spot right now: the moon phase plus one, or 0 with no moon to see. */
    public static int reading(Level level, BlockPos pos) {
        if (!level.dimensionType().natural() || level.dimensionType().hasFixedTime() || pos.getY() + 1 < level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) {
            return 0;
        }
        return level.getMoonPhase() + 1;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide && !old.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int phase = reading(level, pos);
        if (phase != state.getValue(PHASE)) {
            level.setBlock(pos, state.setValue(PHASE, phase), 3);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        level.scheduleTick(pos, this, CHECK_TICKS);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int phase = reading(level, pos);
            player.displayClientMessage(phase == 0
                    ? Component.translatable("block.slowlight.moondial.none")
                    : Component.translatable("block.slowlight.moondial.reads",
                            Component.translatable("block.slowlight.moondial.phase." + (phase - 1)), phase), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(PHASE);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.slowlight.moondial.tip").withStyle(ChatFormatting.GRAY));
    }
}
