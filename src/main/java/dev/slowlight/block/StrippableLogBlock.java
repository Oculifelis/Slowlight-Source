package dev.slowlight.block;

import java.util.function.Supplier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/** A log or wood block an axe strips into its stripped twin, keeping its axis, as vanilla logs do. */
public class StrippableLogBlock extends RotatedPillarBlock {
    private final Supplier<? extends Block> stripped;

    public StrippableLogBlock(Supplier<? extends Block> stripped, Properties properties) {
        super(properties);
        this.stripped = stripped;
    }

    @Override
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility ability, boolean simulate) {
        if (ability == ItemAbilities.AXE_STRIP) {
            return stripped.get().defaultBlockState().setValue(AXIS, state.getValue(AXIS));
        }
        return super.getToolModifiedState(state, context, ability, simulate);
    }
}
