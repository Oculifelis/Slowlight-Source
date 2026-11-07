package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import dev.slowlight.SlowlightBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A low vaulted kiln with an arched door at the front and a chimney at the back. It takes a whole stack, is bricked
 * shut, and is opened again only when all of it is done. Everything else (hoppers above and below, a comparator, the
 * plain and sneaking clicks) works as on the Patient Kiln.
 */
public class LongKilnBlock extends PatientKilnBlock {
    public static final MapCodec<LongKilnBlock> CODEC = simpleCodec(LongKilnBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Floor of the door arch, in pixels. The renderer stands the finished work here. */
    public static final double DOOR_FLOOR = 2.0D;

    private static final Map<Direction, VoxelShape> OPEN = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> SEALED = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape body = Shapes.or(box(d, 0, 0, 0, 16, 10, 16), box(d, 2, 10, 0, 14, 13, 16),
                    box(d, 4, 13, 0, 12, 14, 16), box(d, 6, 10, 11, 10, 16, 15));
            SEALED.put(d, body);
            OPEN.put(d, Shapes.join(body, box(d, 5, 2, 0, 11, 9, 5), BooleanOp.ONLY_FIRST));
        }
    }

    /** A box drawn for a kiln whose door faces north, turned to face {@code facing}. */
    private static VoxelShape box(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    public LongKilnBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WORKING, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The door faces whoever placed it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(WORKING) ? SEALED : OPEN).get(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LongKilnBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, SlowlightBlockEntities.LONG_KILN.get(), PatientKilnBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING) || random.nextInt(4) != 0) {
            return;
        }
        // Ash off the chimney at the back, the only sign that anything is happening.
        Direction back = state.getValue(FACING).getOpposite();
        level.addParticle(ParticleTypes.WHITE_ASH,
                pos.getX() + 0.5D + back.getStepX() * 0.32D + (random.nextDouble() - 0.5D) * 0.15D,
                pos.getY() + 1.02D,
                pos.getZ() + 0.5D + back.getStepZ() * 0.32D + (random.nextDouble() - 0.5D) * 0.15D,
                0.0D, 0.02D, 0.0D);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.slowlight.long_kiln.tip").withStyle(ChatFormatting.GRAY));
    }
}
