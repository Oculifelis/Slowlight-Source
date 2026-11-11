package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import dev.slowlight.SlowlightBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A kiln with no firebox. Put something in, close it, and come back another day.
 *
 * <p>It is deliberately dull to watch: there is no progress bar and no screen, only a lid that sits
 * shut while something is happening inside. A comparator will tell you how far along it is, which is
 * the polite way to ask.
 */
public class PatientKilnBlock extends BaseEntityBlock {
    public static final MapCodec<PatientKilnBlock> CODEC = simpleCodec(PatientKilnBlock::new);

    /** Shut while a batch is ageing. The model shows a sealed lid; nothing else moves. */
    public static final BooleanProperty WORKING = BlockStateProperties.LIT;

    public PatientKilnBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WORKING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(WORKING);
    }

    /** The open kiln is a walled pot, 14 high, with an 8x8 mouth 3 deep; working, a lid brings it to a full block. */
    private static final VoxelShape OPEN = Shapes.join(Block.box(0, 0, 0, 16, 14, 16),
            Block.box(4, 11, 4, 12, 14, 12), BooleanOp.ONLY_FIRST);
    private static final VoxelShape SEALED = Shapes.or(Block.box(0, 0, 0, 16, 14, 16), Block.box(3, 14, 3, 13, 16, 13));

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(WORKING) ? SEALED : OPEN;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PatientKilnBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, SlowlightBlockEntities.PATIENT_KILN.get(), PatientKilnBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PatientKilnBlockEntity kiln)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemStack left = kiln.offer(stack);
        if (left.getCount() == stack.getCount()) {
            if (kiln.accepts(stack)) {
                // A good item, but the kiln is busy with something else: let it say how it is getting on.
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("block.slowlight.patient_kiln.unknown"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        player.setItemInHand(hand, left);
        if (!level.isClientSide) {
            level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 0.7F, 1.1F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PatientKilnBlockEntity kiln)) {
            return InteractionResult.PASS;
        }
        // A plain click takes finished work and otherwise says how the wait is going. Pulling a
        // batch back out mid-wait throws the wait away, so it asks for a sneak first.
        ItemStack taken = player.isSecondaryUseActive() ? kiln.takeOut() : kiln.takeFinished();
        if (taken.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(kiln.status(), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            kiln.claimedBy(player);
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false);
            }
            level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 0.7F, 0.8F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PatientKilnBlockEntity kiln) {
            kiln.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING) || random.nextInt(6) != 0) {
            return;
        }
        // A thread of ash off the lid: the only outward sign that anything is happening.
        level.addParticle(ParticleTypes.WHITE_ASH,
                pos.getX() + 0.3D + random.nextDouble() * 0.4D,
                pos.getY() + 1.02D,
                pos.getZ() + 0.3D + random.nextDouble() * 0.4D,
                0.0D, 0.015D, 0.0D);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.slowlight.patient_kiln.tip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PatientKilnBlockEntity kiln ? kiln.comparatorSignal() : 0;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }
}
