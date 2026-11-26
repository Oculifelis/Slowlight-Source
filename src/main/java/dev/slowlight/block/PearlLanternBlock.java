package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A lantern with a Chorus Pearl for a flame. One block, three cages: sneak-use it with an empty hand to go from cage
 * to cradle to spire and round again. Otherwise a lantern: it stands, hangs, and can be waterlogged. Placed against
 * the side of a block it hangs from an obsidian bracket on that wall.
 */
public class PearlLanternBlock extends LanternBlock {
    public static final MapCodec<PearlLanternBlock> CODEC = simpleCodec(PearlLanternBlock::new);
    public static final EnumProperty<Style> SHAPE = EnumProperty.create("shape", Style.class);
    /** The wall it hangs from, or none. */
    public static final EnumProperty<Wall> WALL = EnumProperty.create("wall", Wall.class);

    public enum Wall implements StringRepresentable {
        NONE(null), NORTH(Direction.NORTH), SOUTH(Direction.SOUTH), WEST(Direction.WEST), EAST(Direction.EAST);

        final Direction dir;

        Wall(Direction dir) {
            this.dir = dir;
        }

        static Wall of(Direction d) {
            for (Wall w : values()) {
                if (w.dir == d) {
                    return w;
                }
            }
            return NONE;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Map<Direction, VoxelShape> WALL_SHAPES = new EnumMap<>(Direction.class);

    static {
        // lantern body plus the bracket, drawn for a north wall and turned for the others
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape body = Block.box(4, 0, 4, 12, 15, 12);
            WALL_SHAPES.put(d, Shapes.or(body, turned(d, 6, 11, 0, 10, 16, 1), turned(d, 7, 14, 1, 9, 16, 9)));
        }
    }

    private static VoxelShape turned(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (d) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    public enum Style implements StringRepresentable {
        CAGE, CRADLE, SPIRE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public PearlLanternBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SHAPE, Style.CAGE).setValue(WALL, Wall.NONE));
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<LanternBlock> codec() {
        return (MapCodec<LanternBlock>) (MapCodec<?>) CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SHAPE, WALL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isHorizontal()) {
            FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
            BlockState wall = defaultBlockState().setValue(WALL, Wall.of(face.getOpposite())).setValue(HANGING, false)
                    .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
            if (wall.canSurvive(context.getLevel(), context.getClickedPos())) {
                return wall;
            }
        }
        return super.getStateForPlacement(context);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction wall = state.getValue(WALL).dir;
        if (wall == null) {
            return super.canSurvive(state, level, pos);
        }
        BlockPos at = pos.relative(wall);
        return level.getBlockState(at).isFaceSturdy(level, at, wall.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Direction wall = state.getValue(WALL).dir;
        if (wall == null) {
            return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        }
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return direction == wall && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction wall = state.getValue(WALL).dir;
        return wall == null ? super.getShape(state, level, pos, context) : WALL_SHAPES.get(wall);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction wall = state.getValue(WALL).dir;
        return wall == null ? state : state.setValue(WALL, Wall.of(rotation.rotate(wall)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction wall = state.getValue(WALL).dir;
        return wall == null ? state : state.setValue(WALL, Wall.of(mirror.mirror(wall)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Style next = Style.values()[(state.getValue(SHAPE).ordinal() + 1) % Style.values().length];
            level.setBlock(pos, state.setValue(SHAPE, next), 3);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7F, 1.3F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable(getDescriptionId() + ".tip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
