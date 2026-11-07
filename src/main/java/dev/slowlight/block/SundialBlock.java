package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * A smooth stone dial with a copper gnomon that tells the hour by the sun. A comparator reads it: 0 all night, rising
 * through the morning to 15 at noon and falling again to sunset. With no sky above it, or in a world with no sun, it
 * reads nothing. It is the quiet daytime twin of the Sandglass: one counts days, this one the hours of one.
 */
public class SundialBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SundialBlock> CODEC = simpleCodec(SundialBlock::new);

    /** What the shadow says, 0 to 15, kept in the state so a comparator reads it without any work. */
    public static final IntegerProperty HOUR = IntegerProperty.create("hour", 0, 15);

    /** Once a second is plenty: a shadow moves slowly. */
    private static final int CHECK_TICKS = 20;

    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(7.5, 2, 4, 8.5, 6, 12));

    public SundialBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HOUR, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HOUR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** The reading for this spot right now: a sine over the day, 15 at noon, 0 from sunset to sunrise. */
    public static int reading(Level level, BlockPos pos) {
        if (!level.dimensionType().natural() || level.dimensionType().hasFixedTime() || pos.getY() + 1 < level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) {
            return 0;
        }
        long t = level.getDayTime() % 24000L;  // 0 sunrise, 6000 noon, 12000 sunset
        if (t >= 12000L) {
            return 0;
        }
        return Mth.clamp(Math.round(15.0F * Mth.sin((float) Math.PI * t / 12000.0F)), 0, 15);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide && !old.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int hour = reading(level, pos);
        if (hour != state.getValue(HOUR)) {
            level.setBlock(pos, state.setValue(HOUR, hour), 3);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        level.scheduleTick(pos, this, CHECK_TICKS);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int hour = reading(level, pos);
            player.displayClientMessage(hour == 0
                    ? Component.translatable("block.slowlight.sundial.none")
                    : Component.translatable("block.slowlight.sundial.reads", hour), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(HOUR);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.slowlight.sundial.tip").withStyle(ChatFormatting.GRAY));
    }
}
