package dev.slowlight.block;

import dev.slowlight.SlowlightBlockEntities;
import dev.slowlight.recipe.AgeingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Patient Kiln's slower sibling: a whole stack, one long wait, all of it done at once.
 *
 * <p>Patience here scales by batch rather than by rate. Where the Patient Kiln works through what it holds one
 * item per wait, the Long Kiln is filled, sealed and left for {@link AgeingRecipe#batchDays()} (three times one
 * item's wait unless the recipe says otherwise), and then everything inside is finished together. Once the lid is
 * on nothing more goes in, so a batch is a batch: what you sealed is what you get back.
 */
public class LongKilnBlockEntity extends PatientKilnBlockEntity {
    /** Enough room for a stack of anything that comes out four to one. */
    private static final int OUTPUT_SLOTS = 4;
    /** Ten seconds without anything new going in, and the lid goes on. */
    private static final long SETTLE = 200L;

    public LongKilnBlockEntity(BlockPos pos, BlockState state) {
        super(SlowlightBlockEntities.LONG_KILN.get(), pos, state, OUTPUT_SLOTS);
    }

    /**
     * A full stack, or as many as will still fit the output when they are all finished at once. None at all while a
     * batch is under way: the count already inside is the limit, so nothing more goes in.
     */
    @Override
    protected int inputLimit(ItemStack stack) {
        if (underWay()) {
            return working().getCount();
        }
        return recipeFor(stack).map(holder -> {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            int room = OUTPUT_SLOTS * result.getMaxStackSize();
            return Math.max(1, Math.min(stack.getMaxStackSize(), room / Math.max(1, result.getCount())));
        }).orElse(stack.getMaxStackSize());
    }

    /**
     * Seal once full, or once nothing more has been added for {@link #SETTLE} ticks. A hopper adds one item at a time,
     * so sealing at the first item would shut a batch of one.
     */
    @Override
    protected boolean readyToStart(Level level) {
        ItemStack in = working();
        return in.getCount() >= inputLimit(in) || level.getGameTime() - lastInputChange() >= SETTLE;
    }

    @Override
    public Component status() {
        if (!underWay() && !working().isEmpty() && accepts(working())) {
            return Component.translatable("block.slowlight.long_kiln.filling");
        }
        return super.status();
    }

    @Override
    protected long waitTicks(AgeingRecipe recipe) {
        return recipe.batchTicks();
    }

    /** All of it at once, or nothing: only into empty output slots, so a batch never comes out half done. */
    @Override
    protected boolean finish(Level level, AgeingRecipe recipe, ItemStack input) {
        for (int slot = OUTPUT; slot < outputEnd(); slot++) {
            if (!items.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        ItemStack one = recipe.assemble(new SingleRecipeInput(input), level.registryAccess());
        int total = one.getCount() * input.getCount();
        int per = one.getMaxStackSize();
        if (total > per * (outputEnd() - OUTPUT)) {
            return false;
        }
        for (int slot = OUTPUT; slot < outputEnd() && total > 0; slot++) {
            int n = Math.min(per, total);
            items.setStackInSlot(slot, one.copyWithCount(n));
            total -= n;
        }
        ItemStack remainder = input.getCraftingRemainingItem();
        int count = input.getCount();
        input.setCount(0);
        if (!remainder.isEmpty()) {
            giveBack(level, remainder.copyWithCount(remainder.getCount() * count));
        }
        setChanged();
        return true;
    }
}
