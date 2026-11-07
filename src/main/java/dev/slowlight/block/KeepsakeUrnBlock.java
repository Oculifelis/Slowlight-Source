package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import dev.slowlight.SlowlightBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A terracotta urn for putting things away for later. Open, it is a small nine-slot store. Sneak-click to choose how
 * long, then click with a honeycomb to seal it with wax; it stays shut until the days are up. Breaking a sealed urn
 * early cracks it: the urn is lost and everything spills out.
 */
public class KeepsakeUrnBlock extends BaseEntityBlock {
    public static final MapCodec<KeepsakeUrnBlock> CODEC = simpleCodec(KeepsakeUrnBlock::new);
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

    /** Foot, belly, and neck with its lip; the same numbers as tools/make_models.py keepsake_urn(). */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 2, 12), Block.box(2, 2, 2, 14, 11, 14),
            Block.box(4, 11, 4, 12, 16, 12));

    public KeepsakeUrnBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SEALED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEALED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KeepsakeUrnBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, SlowlightBlockEntities.KEEPSAKE_URN.get(), KeepsakeUrnBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.HONEYCOMB) || !(level.getBlockEntity(pos) instanceof KeepsakeUrnBlockEntity urn)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (urn.sealed()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            urn.seal(level, pos, state);
            stack.consume(1, player);
            player.displayClientMessage(Component.translatable("block.slowlight.keepsake_urn.sealed", urn.planned()), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof KeepsakeUrnBlockEntity urn)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (urn.sealed()) {
            player.displayClientMessage(urn.status(), true);
        } else if (player.isSecondaryUseActive()) {
            urn.cyclePlanned();
            player.displayClientMessage(urn.status(), true);
        } else {
            urn.openedBy(player);
            player.openMenu(urn);
            level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 0.6F, 0.9F);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof KeepsakeUrnBlockEntity urn) {
            // The loot table drops the urn itself only when unsealed; sealed, the urn cracks (bricks) and spills.
            if (urn.sealed()) {
                level.playSound(null, pos, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, 1.0F, 0.9F);
            }
            Containers.dropContents(level, pos, urn);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.slowlight.keepsake_urn.tip").withStyle(ChatFormatting.GRAY));
    }
}
