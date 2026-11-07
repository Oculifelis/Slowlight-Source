package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import dev.slowlight.SlowlightBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * An hourglass that measures days. Click it to set one to seven; the shard sand in the top bulb runs down over that
 * many days, so at a glance it reads like a clock face. When the last grain falls it gives one redstone pulse and turns
 * itself over to start again. A comparator reads how much sand is left on top.
 *
 * <p>Away from a world with a real day (the Nether, the End) it cannot find the time: the sand jumps about and never
 * settles, the way a vanilla clock spins there, and it gives no pulse.
 */
public class SandglassBlock extends BaseEntityBlock {
    public static final MapCodec<SandglassBlock> CODEC = simpleCodec(SandglassBlock::new);

    /** Eighths of sand left in the top bulb: 7 just turned over, 0 run out. */
    public static final IntegerProperty SAND = IntegerProperty.create("sand", 0, 7);
    /** True while it is timing something: a thin stream falls through the neck. */
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    /** On for the few ticks of the pulse when it turns over. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final int PULSE_TICKS = 4;

    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 2, 14), Block.box(3, 2, 3, 13, 14, 13),
            Block.box(2, 14, 2, 14, 16, 14));

    public SandglassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SAND, 0).setValue(RUNNING, false).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SAND, RUNNING, POWERED);
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
        return new SandglassBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, SlowlightBlockEntities.SANDGLASS.get(), SandglassBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SandglassBlockEntity glass)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            // A sneak only asks how long is left; a plain click sets the next number of days and turns it over.
            if (player.isSecondaryUseActive()) {
                player.displayClientMessage(glass.status(), true);
            } else {
                glass.cycleDays(level, pos, state);
                player.displayClientMessage(Component.translatable("block.slowlight.sandglass.set", glass.days()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---- redstone -----------------------------------------------------------------------------------------------

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Sand left on top, 0 to 15: a full bulb is 15, an empty or idle one 0. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SandglassBlockEntity glass ? glass.comparatorSignal() : 0;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // The pulse ends.
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), 3);
        }
    }

    // ---- looks --------------------------------------------------------------------------------------------------

    private static final DustParticleOptions GLINT = new DustParticleOptions(new Vector3f(0.62F, 0.77F, 0.93F), 0.5F);

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING) || random.nextInt(5) != 0) {
            return;
        }
        // A grain of light catching in the neck as it falls.
        level.addParticle(GLINT, pos.getX() + 0.5D, pos.getY() + 0.5D + random.nextDouble() * 0.1D,
                pos.getZ() + 0.5D, 0.0D, -0.02D, 0.0D);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.slowlight.sandglass.tip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}
