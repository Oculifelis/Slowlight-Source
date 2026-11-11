package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A lantern with a Chorus Pearl for a flame. One block, three cages: sneak-use it with an empty hand to go from cage
 * to cradle to spire and round again. Otherwise a lantern: it stands, hangs, and can be waterlogged.
 */
public class PearlLanternBlock extends LanternBlock {
    public static final MapCodec<PearlLanternBlock> CODEC = simpleCodec(PearlLanternBlock::new);
    public static final EnumProperty<Style> SHAPE = EnumProperty.create("shape", Style.class);

    public enum Style implements StringRepresentable {
        CAGE, CRADLE, SPIRE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public PearlLanternBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SHAPE, Style.CAGE));
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<LanternBlock> codec() {
        return (MapCodec<LanternBlock>) (MapCodec<?>) CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SHAPE);
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
}
