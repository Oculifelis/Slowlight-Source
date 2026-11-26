package dev.slowlight.locker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.slowlight.SlowlightLockers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A locker: two blocks tall. The lower half holds the block entity and the upper half passes clicks down to it.
 * TYPE pairs two lockers side by side the way chests pair, with the same meaning as on a chest: a LEFT locker
 * has its partner on its facing's clockwise side.
 */
public class LockerBlock extends BaseEntityBlock {
    public static final MapCodec<LockerBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.fieldOf("lockable").forGetter(b -> b.lockable),
            propertiesCodec()
    ).apply(i, LockerBlock::new));
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<ChestType> TYPE = BlockStateProperties.CHEST_TYPE;

    private final boolean lockable;

    public LockerBlock(boolean lockable, Properties properties) {
        super(properties);
        this.lockable = lockable;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER).setValue(TYPE, ChestType.SINGLE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public boolean lockable() {
        return lockable;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, TYPE);
    }

    // --- placing and pairing ---

    public static Direction connectedDirection(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(TYPE) == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
    }

    public static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return null;
        }
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        ChestType type = ChestType.SINGLE;
        if (!ctx.isSecondaryUseActive()) {
            if (canPair(level.getBlockState(pos.relative(facing.getClockWise())), facing)) {
                type = ChestType.LEFT;
            } else if (canPair(level.getBlockState(pos.relative(facing.getCounterClockWise())), facing)) {
                type = ChestType.RIGHT;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(TYPE, type);
    }

    private boolean canPair(BlockState other, Direction facing) {
        return other.is(this) && other.getValue(HALF) == DoubleBlockHalf.LOWER
                && other.getValue(TYPE) == ChestType.SINGLE && other.getValue(FACING) == facing;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        if (lower && dir == Direction.UP) {
            return neighbour.is(this) && neighbour.getValue(HALF) == DoubleBlockHalf.UPPER ? state : Blocks.AIR.defaultBlockState();
        }
        if (!lower && dir == Direction.DOWN) {
            if (!neighbour.is(this) || neighbour.getValue(HALF) != DoubleBlockHalf.LOWER) {
                return Blocks.AIR.defaultBlockState();
            }
            return state.setValue(FACING, neighbour.getValue(FACING)).setValue(TYPE, neighbour.getValue(TYPE));
        }
        if (!lower || !dir.getAxis().isHorizontal()) {
            return state;
        }
        if (neighbour.is(this) && neighbour.getValue(HALF) == DoubleBlockHalf.LOWER) {
            ChestType other = neighbour.getValue(TYPE);
            if (state.getValue(TYPE) == ChestType.SINGLE && other != ChestType.SINGLE
                    && state.getValue(FACING) == neighbour.getValue(FACING)
                    && connectedDirection(neighbour) == dir.getOpposite()) {
                return state.setValue(TYPE, other.getOpposite());
            }
        } else if (state.getValue(TYPE) != ChestType.SINGLE && connectedDirection(state) == dir) {
            return state.setValue(TYPE, ChestType.SINGLE);
        }
        return state;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // In creative the lower half would otherwise drop the locker when the upper half is broken.
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LockerBlockEntity be) {
            Containers.dropContents(level, pos, be);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.takeLock());
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState rotated = state.rotate(mirror.getRotation(state.getValue(FACING)));
        return mirror == Mirror.NONE ? rotated : rotated.setValue(TYPE, rotated.getValue(TYPE).getOpposite());
    }

    // --- block entity ---

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new LockerBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? createTickerHelper(type, SlowlightLockers.LOCKER_BE.get(), LockerBlockEntity::clientTick) : null;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof LockerBlockEntity be) {
            be.recheckOpen();
        }
    }

    @Nullable
    public static LockerBlockEntity entity(BlockGetter level, BlockState state, BlockPos pos) {
        return level.getBlockEntity(lowerPos(state, pos)) instanceof LockerBlockEntity be ? be : null;
    }

    /** The block entity of the locker paired with this one, given the lower half's state and position. */
    @Nullable
    public static LockerBlockEntity partner(BlockGetter level, BlockState lowerState, BlockPos lowerPos) {
        if (!(lowerState.getBlock() instanceof LockerBlock) || lowerState.getValue(TYPE) == ChestType.SINGLE) {
            return null;
        }
        BlockPos at = lowerPos.relative(connectedDirection(lowerState));
        BlockState other = level.getBlockState(at);
        if (other.is(lowerState.getBlock()) && other.getValue(HALF) == DoubleBlockHalf.LOWER
                && other.getValue(FACING) == lowerState.getValue(FACING)
                && other.getValue(TYPE) == lowerState.getValue(TYPE).getOpposite()
                && level.getBlockEntity(at) instanceof LockerBlockEntity be) {
            return be;
        }
        return null;
    }

    // --- locks ---

    /** This locker and its partner, if it has one. */
    static List<LockerBlockEntity> pieces(LockerBlockEntity be) {
        List<LockerBlockEntity> list = new ArrayList<>(2);
        list.add(be);
        LockerBlockEntity other = be.partner();
        if (other != null) {
            list.add(other);
        }
        return list;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        LockerBlockEntity be = entity(level, state, pos);
        if (be != null && be.guarded() && !player.isCreative()) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        LockerBlockEntity be = entity(level, state, pos);
        return be != null && be.guarded() ? 3_600_000.0F : super.getExplosionResistance(state, level, pos, explosion);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!lockable || !stack.is(SlowlightLockers.LOCK.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        LockerBlockEntity be = entity(level, state, pos);
        if (be == null || be.isLocked()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.has(SlowlightLockers.LOCK_ID.get())) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.slowlight.lock_blank"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            be.setLock(stack.copyWithCount(1));
            stack.consume(1, player);
            level.playSound(null, be.getBlockPos().above(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 1.3F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        LockerBlockEntity be = entity(level, state, pos);
        if (be == null) {
            return InteractionResult.CONSUME;
        }
        List<LockerBlockEntity> pieces = pieces(be);
        if (!KeyItem.opens(player.getMainHandItem(), pieces)) {
            player.displayClientMessage(Component.translatable("container.isLocked", be.getDisplayName()), true);
            level.playSound(null, be.getBlockPos().above(), SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }
        open(player, be, pieces);
        return InteractionResult.CONSUME;
    }

    private static void open(Player player, LockerBlockEntity be, List<LockerBlockEntity> pieces) {
        LockerSize size = be.size();
        Container container = be;
        int rows = size.rows();
        Component title = be.getDisplayName();
        if (pieces.size() == 2 && pieces.get(1).size().columns() == size.columns()) {
            LockerBlockEntity other = pieces.get(1);
            // As with chests, the RIGHT piece sits on the viewer's left and comes first.
            boolean selfFirst = be.getBlockState().getValue(TYPE) == ChestType.RIGHT;
            LockerBlockEntity first = selfFirst ? be : other;
            LockerBlockEntity second = selfFirst ? other : be;
            container = new CompoundContainer(first, second);
            rows = first.size().rows() + second.size().rows();
            if (first.hasCustomName()) {
                title = first.getDisplayName();
            } else if (second.hasCustomName()) {
                title = second.getDisplayName();
            } else {
                title = Component.translatable("container.slowlight.large_"
                        + BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath());
            }
            first.unpackLootTable(player);
            second.unpackLootTable(player);
        } else {
            be.unpackLootTable(player);
        }
        int columns = size.columns();
        int totalRows = rows;
        Container shown = container;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new LockerMenu(id, inv, shown, columns, totalRows), title),
                buf -> {
                    buf.writeVarInt(columns);
                    buf.writeVarInt(totalRows);
                });
    }

    // --- comparators ---

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(lowerPos(state, pos)));
    }
}
