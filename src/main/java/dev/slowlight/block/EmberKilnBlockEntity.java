package dev.slowlight.block;

import dev.slowlight.SlowlightBlockEntities;
import dev.slowlight.recipe.AgeingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Patient Kiln's Nether cousin. It only takes to the work where the world itself is hot (an ultrawarm dimension,
 * so the Nether), and there it needs half the days. Anywhere else it holds what it is given and waits for the heat.
 */
public class EmberKilnBlockEntity extends PatientKilnBlockEntity {
    public EmberKilnBlockEntity(BlockPos pos, BlockState state) {
        super(SlowlightBlockEntities.EMBER_KILN.get(), pos, state, 1);
    }

    public static boolean hotEnough(Level level) {
        return level.dimensionType().ultraWarm();
    }

    @Override
    protected boolean readyToStart(Level level) {
        return hotEnough(level);
    }

    @Override
    protected long waitTicks(AgeingRecipe recipe) {
        return Math.max(1L, recipe.ticks() / 2L);
    }

    @Override
    public Component status() {
        if (level != null && !hotEnough(level) && !working().isEmpty() && !underWay()) {
            return Component.translatable("block.slowlight.ember_kiln.cold");
        }
        return super.status();
    }
}
