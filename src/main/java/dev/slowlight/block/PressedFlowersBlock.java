package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import dev.slowlight.SlowlightCrafts;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pressed Flowers laid down, the way Pink Petals are: up to four on the ground, one in each quarter of the block, or up
 * to four on a wall. Which flower is in each quarter lives in the block entity; the client draws each one flat on its
 * card (dev.slowlight.client.PressedFlowersRenderer). Placed by the Pressed Flower item (PressedFlowerItem#useOn).
 */
public class PressedFlowersBlock extends BaseEntityBlock {
    public static final MapCodec<PressedFlowersBlock> CODEC = simpleCodec(PressedFlowersBlock::new);
    public static final int MAX = 4;
    /** On the ground, which way the first one points (the rest go round, like petals); on a wall, the way it faces. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** FLOOR or WALL. */
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final IntegerProperty FLOWERS = IntegerProperty.create("flowers", 1, MAX);
    /** The light it gives: the brightest of its flowers' own blocks (a glowing flower still glows pressed). */
    public static final IntegerProperty LIGHT = BlockStateProperties.LEVEL;

    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 1, 16);
    private static final VoxelShape NORTH = Block.box(0, 0, 15, 16, 16, 16);
    private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, 1);
    private static final VoxelShape EAST = Block.box(0, 0, 0, 1, 16, 16);
    private static final VoxelShape WEST = Block.box(15, 0, 0, 16, 16, 16);

    public PressedFlowersBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.FLOOR)
                .setValue(FLOWERS, 1).setValue(LIGHT, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, FLOWERS, LIGHT);
    }

    /** The block a state hangs from: below on the ground, behind on a wall. */
    public static BlockPos support(BlockState state, BlockPos pos) {
        return state.getValue(FACE) == AttachFace.WALL ? pos.relative(state.getValue(FACING).getOpposite()) : pos.below();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction side = state.getValue(FACE) == AttachFace.WALL ? state.getValue(FACING) : Direction.UP;
        return level.getBlockState(support(state, pos)).isFaceSturdy(level, support(state, pos), side);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return neighborPos.equals(support(state, pos)) && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(FACE) != AttachFace.WALL) {
            return FLOOR;
        }
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // The model has no faces, only the particle texture; the renderer draws the flowers.
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PressedFlowersBlockEntity(pos, state);
    }

    /** One Pressed Flower back for each laid down, each still the flower it was. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> out = new ArrayList<>();
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof PressedFlowersBlockEntity be) {
            be.flowers().forEach(f -> out.add(SlowlightCrafts.pressed(f)));
        }
        return out;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof PressedFlowersBlockEntity be && !be.flowers().isEmpty()) {
            return SlowlightCrafts.pressed(be.flowers().get(0));
        }
        return new ItemStack(SlowlightCrafts.PRESSED_FLOWER.get());
    }

    private static final java.util.Map<Item, Integer> GLOW = new java.util.concurrent.ConcurrentHashMap<>();

    /** The light a flower's own block gives at its brightest (0 for a flower with no block): any state, as glow lichen
     * only glows once it is stuck to something. */
    public static int glow(Item flower) {
        return GLOW.computeIfAbsent(flower, f -> Block.byItem(f).getStateDefinition().getPossibleStates().stream()
                .mapToInt(BlockState::getLightEmission).max().orElse(0));
    }
}
