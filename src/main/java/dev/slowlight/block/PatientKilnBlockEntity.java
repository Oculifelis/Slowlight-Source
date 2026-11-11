package dev.slowlight.block;

import dev.slowlight.SlowlightBlockEntities;
import dev.slowlight.SlowlightRecipes;
import dev.slowlight.recipe.AgeingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Two slots and a timestamp. The kiln holds what it was given, remembers when it was given, and
 * checks once a second whether enough of the world has gone by.
 *
 * <p>Time is read off {@link Level#getGameTime()} rather than the day/night clock, so a slept night
 * or a {@code /time set} does not cheat the wait, and nothing is lost while the chunk is unloaded —
 * the kiln simply finds, when it next ticks, that its day has already passed.
 */
public class PatientKilnBlockEntity extends BlockEntity {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    private static final int INPUT_LIMIT = 16;

    protected final ItemStackHandler items;
    /** Output slots run from {@link #OUTPUT} up to, not including, this. */
    private final int outputEnd;

    private @Nullable IItemHandler topView;
    private @Nullable IItemHandler bottomView;

    /**
     * The recipe for what is currently in the input slot. Looking this up means a scan of every
     * ageing recipe, and a kiln asks once a second for days on end, so the answer is kept until the
     * input changes (see {@code onContentsChanged}) and re-checked occasionally in case a datapack
     * reload has moved the ground underneath it.
     */
    private @Nullable RecipeHolder<AgeingRecipe> cached;

    private long lastInputChange;

    /** The last signal a comparator was told about, so neighbours are only woken when it changes. */
    private int lastSignal;

    /** Game time the current batch started, or -1 when nothing is ageing. */
    private long startedAt = -1L;
    /** Ticks the current batch needs, cached so a comparator need not look the recipe up. */
    private long needed = 0L;
    /** False while the recipe's condition (rain, open sky, night...) is not met and the wait is standing still. */
    private boolean conditionHolds = true;
    /** Waiting (ticks) and batches finished since a player last took work out, credited to the next one who does. */
    private long unclaimedTicks;
    private int unclaimedBatches;

    public PatientKilnBlockEntity(BlockPos pos, BlockState state) {
        this(SlowlightBlockEntities.PATIENT_KILN.get(), pos, state, 1);
    }

    protected PatientKilnBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int outputSlots) {
        super(type, pos, state);
        outputEnd = OUTPUT + outputSlots;
        items = new ItemStackHandler(outputEnd) {
            @Override
            protected void onContentsChanged(int slot) {
                if (slot == INPUT) {
                    cached = null;
                    if (level != null) {
                        lastInputChange = level.getGameTime();
                    }
                }
                setChanged();
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                }
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                // Only things the kiln knows what to do with, so a hopper cannot fill it with junk.
                return slot == INPUT && accepts(stack);
            }

            @Override
            protected int getStackLimit(int slot, ItemStack stack) {
                return slot == INPUT ? Math.min(inputLimit(stack), stack.getMaxStackSize()) : stack.getMaxStackSize();
            }
        };
    }

    // ---- what kind of kiln --------------------------------------------------------------------

    /** How many of this item the input may hold. The Patient Kiln takes up to 16 and works through them one by one. */
    protected int inputLimit(ItemStack stack) {
        return INPUT_LIMIT;
    }

    /** How long the kiln waits before {@link #finish} is next called. */
    protected long waitTicks(AgeingRecipe recipe) {
        return recipe.ticks();
    }

    /**
     * The wait is over: turn input into output. The Patient Kiln finishes one item per wait. Returns false, and
     * changes nothing, when there is nowhere to put the result; the batch then keeps until there is.
     */
    protected boolean finish(Level level, AgeingRecipe recipe, ItemStack input) {
        ItemStack result = recipe.assemble(new SingleRecipeInput(input), level.registryAccess());
        ItemStack existing = items.getStackInSlot(OUTPUT);
        if (existing.isEmpty()) {
            items.setStackInSlot(OUTPUT, result);
        } else if (ItemStack.isSameItemSameComponents(existing, result)
                && existing.getCount() + result.getCount() <= existing.getMaxStackSize()) {
            existing.grow(result.getCount());
            setChanged();
        } else {
            return false;
        }
        ItemStack remainder = input.getCraftingRemainingItem();
        input.shrink(1);
        giveBack(level, remainder);
        return true;
    }

    /**
     * What a finished ingredient leaves behind (the bucket from a milk bucket): into a free output slot if there is
     * one, otherwise set down on top of the kiln.
     */
    protected void giveBack(Level level, ItemStack remainder) {
        // Not insertItem: the handler only lets things into the input slot.
        for (int slot = OUTPUT; slot < outputEnd && !remainder.isEmpty(); slot++) {
            ItemStack there = items.getStackInSlot(slot);
            if (there.isEmpty()) {
                items.setStackInSlot(slot, remainder);
                remainder = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(there, remainder)) {
                int n = Math.min(remainder.getCount(), there.getMaxStackSize() - there.getCount());
                there.grow(n);
                remainder.shrink(n);
                setChanged();
            }
        }
        if (!remainder.isEmpty()) {
            BlockPos pos = getBlockPos();
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY() + 1.0, pos.getZ(), remainder);
        }
    }

    /** The recipe this item ages by, if any. */
    protected Optional<RecipeHolder<AgeingRecipe>> recipeFor(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return Optional.empty();
        }
        // A recipe made for this dimension comes before the everywhere one for the same item.
        List<RecipeHolder<AgeingRecipe>> found = level.getRecipeManager()
                .getRecipesFor(SlowlightRecipes.AGEING_TYPE.get(), new SingleRecipeInput(stack), level);
        return found.stream().filter(h -> h.value().condition().dimension().isPresent()).findFirst()
                .or(() -> found.stream().findFirst());
    }

    /** Whether the clock may start on what has been put in. The Patient Kiln starts at once. */
    protected boolean readyToStart(Level level) {
        return true;
    }

    /** Game time the input last changed, so a kiln can wait for a hopper to finish filling it. */
    protected long lastInputChange() {
        return lastInputChange;
    }

    /** True while a batch is under way: the clock has started on it. */
    protected boolean underWay() {
        return startedAt >= 0L;
    }

    protected int outputEnd() {
        return outputEnd;
    }

    // ---- outside world -------------------------------------------------------------------------

    /** Input from any side but the bottom; finished work only out of the bottom. */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == Direction.DOWN) {
            if (bottomView == null) {
                bottomView = new RangedWrapper(items, OUTPUT, outputEnd);
            }
            return bottomView;
        }
        if (side == null) {
            return items;
        }
        if (topView == null) {
            topView = new RangedWrapper(items, INPUT, INPUT + 1);
        }
        return topView;
    }

    /** Whether some ageing recipe takes this item. True when there is no level yet to ask. */
    public boolean accepts(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return level == null;
        }
        return recipeFor(stack).isPresent();
    }

    /** Hand the kiln part of a stack. Returns what is left. */
    public ItemStack offer(ItemStack stack) {
        return items.insertItem(INPUT, stack, false);
    }

    /** What is in the kiln now, for anything that wants to report on it. Never modify this. */
    public ItemStack working() {
        return items.getStackInSlot(INPUT);
    }

    /** The finished work waiting to be taken (the first stack of it). Never modify this. */
    public ItemStack finished() {
        for (int slot = OUTPUT; slot < outputEnd; slot++) {
            if (!items.getStackInSlot(slot).isEmpty()) {
                return items.getStackInSlot(slot);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Take a stack of the finished work, leaving any batch still ageing alone. */
    public ItemStack takeFinished() {
        for (int slot = OUTPUT; slot < outputEnd; slot++) {
            ItemStack out = items.extractItem(slot, 64, false);
            if (!out.isEmpty()) {
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * What the kiln has to say when asked and it has nothing to hand over: how long is left, or
     * that it is empty, or that it does not know what to do with what it was given.
     */
    public Component status() {
        if (items.getStackInSlot(INPUT).isEmpty()) {
            return Component.translatable("block.slowlight.patient_kiln.empty");
        }
        if (startedAt < 0L || needed <= 0L || level == null) {
            return Component.translatable("block.slowlight.patient_kiln.unknown");
        }
        if (!conditionHolds) {
            Optional<RecipeHolder<AgeingRecipe>> recipe = recipeFor(items.getStackInSlot(INPUT));
            if (recipe.isPresent()) {
                return Component.translatable("block.slowlight.patient_kiln.paused", recipe.get().value().condition().describe());
            }
        }
        long left = needed - (level.getGameTime() - startedAt);
        if (left <= 0L) {
            return Component.translatable("block.slowlight.patient_kiln.ready");
        }
        if (left <= AgeingRecipe.TICKS_PER_DAY) {
            return Component.translatable("block.slowlight.patient_kiln.today");
        }
        long days = (left + AgeingRecipe.TICKS_PER_DAY - 1L) / AgeingRecipe.TICKS_PER_DAY;
        return Component.translatable("block.slowlight.patient_kiln.days", days);
    }

    /** Take the finished work, or, if there is none, take back what was put in. */
    public ItemStack takeOut() {
        ItemStack out = takeFinished();
        if (!out.isEmpty()) {
            return out;
        }
        ItemStack back = items.extractItem(INPUT, 64, false);
        if (!back.isEmpty()) {
            startedAt = -1L;
            needed = 0L;
            setChanged();
        }
        return back;
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStackInSlot(slot));
        }
    }

    /**
     * 1-14 as the wait runs down, 15 when finished work is waiting to be taken, 0 when there is nothing in it
     * worth asking about.
     */
    public int comparatorSignal() {
        if (startedAt < 0L || needed <= 0L || level == null) {
            return finished().isEmpty() ? 0 : 15;
        }
        long elapsed = level.getGameTime() - startedAt;
        if (elapsed >= needed) {
            return 15;
        }
        return 1 + (int) (elapsed * 14L / needed);
    }

    // ---- the wait -----------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, PatientKilnBlockEntity kiln) {
        // Once a second is plenty for something measured in days.
        if (level.getGameTime() % 20L != 0L) {
            return;
        }
        age(level, pos, state, kiln);

        // The signal creeps up as the days pass without anything else changing, so a comparator has to be told.
        int signal = kiln.comparatorSignal();
        if (signal != kiln.lastSignal) {
            kiln.lastSignal = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    private static void age(Level level, BlockPos pos, BlockState state, PatientKilnBlockEntity kiln) {

        ItemStack input = kiln.items.getStackInSlot(INPUT);
        if (input.isEmpty()) {
            kiln.stop(level, pos, state);
            return;
        }

        RecipeHolder<AgeingRecipe> holder = kiln.recipeFor(input, level);
        if (holder == null) {
            kiln.stop(level, pos, state);
            return;
        }

        AgeingRecipe recipe = holder.value();
        if (kiln.startedAt < 0L) {
            if (!kiln.readyToStart(level)) {
                return;
            }
            kiln.startedAt = level.getGameTime();
            kiln.needed = recipe.scaledForCellar(kiln.waitTicks(recipe), level, pos);
            kiln.setWorking(level, pos, state, true);
            kiln.setChanged();
            return;
        }

        kiln.needed = recipe.scaledForCellar(kiln.waitTicks(recipe), level, pos);
        // A recipe that wants rain, open sky or night holds its wait while the world is otherwise: the start moves
        // forward with the clock, so the days already counted stay counted.
        boolean holds = recipe.condition().holds(level, pos);
        if (holds != kiln.conditionHolds) {
            kiln.conditionHolds = holds;
            kiln.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
        if (!holds) {
            kiln.startedAt += 20L;
            kiln.setChanged();
            return;
        }
        if (level.getGameTime() - kiln.startedAt < kiln.needed) {
            return;
        }
        if (!kiln.finish(level, recipe, input)) {
            // Nowhere to put it. The batch keeps; it has waited this long already.
            return;
        }

        kiln.unclaimedTicks += kiln.needed;
        kiln.unclaimedBatches++;
        kiln.startedAt = -1L;
        kiln.needed = 0L;
        kiln.setChanged();
    }

    /** A player took finished work: theirs is the waiting it took (for the statistics and the hundred days). */
    public void claimedBy(net.minecraft.world.entity.player.Player player) {
        if (unclaimedBatches > 0 || unclaimedTicks > 0L) {
            dev.slowlight.SlowlightNights.creditWaiting(player, unclaimedTicks, unclaimedBatches);
            unclaimedTicks = 0L;
            unclaimedBatches = 0;
            setChanged();
        }
    }

    private @Nullable RecipeHolder<AgeingRecipe> recipeFor(ItemStack input, Level level) {
        // Re-check every ten seconds even when nothing has changed, so a /reload that alters or
        // removes a recipe is noticed rather than waiting out a stale one.
        if (cached != null && level.getGameTime() % 200L != 0L) {
            return cached;
        }
        cached = recipeFor(input).orElse(null);
        return cached;
    }

    private void stop(Level level, BlockPos pos, BlockState state) {
        if (startedAt >= 0L || needed != 0L) {
            startedAt = -1L;
            needed = 0L;
            setChanged();
        }
        setWorking(level, pos, state, false);
    }

    private void setWorking(Level level, BlockPos pos, BlockState state, boolean working) {
        if (state.hasProperty(PatientKilnBlock.WORKING) && state.getValue(PatientKilnBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(PatientKilnBlock.WORKING, working), 3);
            // The lid going on is a heavy wooden thunk with a clink of the clay rim; coming off, a lighter lift.
            if (working) {
                level.playSound(null, pos, dev.slowlight.SlowlightSounds.KILN_SEAL.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 0.35F, 0.7F);
            } else {
                level.playSound(null, pos, dev.slowlight.SlowlightSounds.KILN_OPEN.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            if (!working) {
                conditionHolds = true;
            }
        }
    }

    // ---- saving -------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putLong("StartedAt", startedAt);
        tag.putLong("Needed", needed);
        tag.putBoolean("Paused", !conditionHolds);
        tag.putLong("UnclaimedTicks", unclaimedTicks);
        tag.putInt("UnclaimedBatches", unclaimedBatches);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        startedAt = tag.contains("StartedAt") ? tag.getLong("StartedAt") : -1L;
        needed = tag.getLong("Needed");
        conditionHolds = !tag.getBoolean("Paused");
        unclaimedTicks = tag.getLong("UnclaimedTicks");
        unclaimedBatches = tag.getInt("UnclaimedBatches");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
