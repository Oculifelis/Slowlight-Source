package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import dev.slowlight.SlowlightBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A Patient Kiln of blackstone, for the Nether: half the wait there, and no work at all anywhere cooler. */
public class EmberKilnBlock extends PatientKilnBlock {
    public static final MapCodec<EmberKilnBlock> CODEC = simpleCodec(EmberKilnBlock::new);

    public EmberKilnBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EmberKilnBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, SlowlightBlockEntities.EMBER_KILN.get(), PatientKilnBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING) || random.nextInt(5) != 0) {
            return;
        }
        // Not ash but the odd ember lifting off the lid's edge.
        level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.SMOKE,
                pos.getX() + 0.25D + random.nextDouble() * 0.5D,
                pos.getY() + 1.02D,
                pos.getZ() + 0.25D + random.nextDouble() * 0.5D,
                0.0D, 0.01D, 0.0D);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.slowlight.ember_kiln.tip").withStyle(ChatFormatting.GRAY));
    }
}
