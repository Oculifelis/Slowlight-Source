package dev.slowlight.block;

import dev.slowlight.Slowlight;
import dev.slowlight.SlowlightBlockEntities;
import dev.slowlight.recipe.AgeingRecipe;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Nine slots and a wax seal. Fill it, choose how long, and seal it with a honeycomb: until the days are up it will not
 * open, not for you and not for a hopper. Then the wax softens and gives, and whatever was put away is there again.
 */
public class KeepsakeUrnBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SLOTS = 9;
    /** The seal lengths a sneaking click steps through. */
    public static final int[] SEAL_DAYS = {1, 2, 3, 4, 5, 6, 7, 10, 14, 21, 30};
    private static final int[] NO_SLOTS = new int[0];
    private static final int[] ALL_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final ResourceLocation KEEPSAKE = ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "keepsake");

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    /** Days the next seal will hold. */
    private int planned = 7;
    /** Game time the seal goes, or -1 while unsealed. */
    private long opensAt = -1L;
    /** Set when a seal ran its full time; the first person to open it afterwards gets the advancement. */
    private boolean ripe;

    public KeepsakeUrnBlockEntity(BlockPos pos, BlockState state) {
        super(SlowlightBlockEntities.KEEPSAKE_URN.get(), pos, state);
    }

    public boolean sealed() {
        return opensAt >= 0L;
    }

    public int planned() {
        return planned;
    }

    public void cyclePlanned() {
        int i = 0;
        while (i < SEAL_DAYS.length && SEAL_DAYS[i] != planned) {
            i++;
        }
        planned = SEAL_DAYS[(i + 1) % SEAL_DAYS.length];
        setChanged();
    }

    public void seal(Level level, BlockPos pos, BlockState state) {
        opensAt = level.getGameTime() + (long) planned * AgeingRecipe.TICKS_PER_DAY;
        ripe = false;
        setChanged();
        level.setBlock(pos, state.setValue(KeepsakeUrnBlock.SEALED, true), 3);
        level.playSound(null, pos, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0F, 0.9F);
    }

    public Component status() {
        if (!sealed() || level == null) {
            return Component.translatable("block.slowlight.keepsake_urn.plan", planned);
        }
        long left = opensAt - level.getGameTime();
        if (left <= AgeingRecipe.TICKS_PER_DAY) {
            return Component.translatable("block.slowlight.keepsake_urn.today");
        }
        return Component.translatable("block.slowlight.keepsake_urn.days",
                (left + AgeingRecipe.TICKS_PER_DAY - 1L) / AgeingRecipe.TICKS_PER_DAY);
    }

    /** Opened by a player: if the seal ran its time, that is worth remembering. */
    public void openedBy(Player player) {
        if (ripe && player instanceof ServerPlayer sp) {
            ripe = false;
            setChanged();
            AdvancementHolder adv = sp.server.getAdvancements().get(KEEPSAKE);
            if (adv != null) {
                sp.getAdvancements().award(adv, "opened");
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, KeepsakeUrnBlockEntity urn) {
        if (level.getGameTime() % 20L != 0L || !urn.sealed() || level.getGameTime() < urn.opensAt) {
            return;
        }
        // The wax gives.
        urn.opensAt = -1L;
        urn.ripe = true;
        urn.setChanged();
        level.setBlock(pos, state.setValue(KeepsakeUrnBlock.SEALED, false), 3);
        level.playSound(null, pos, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 0.8F, 0.6F);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 0.5F, 1.3F);
    }

    // ---- container ----------------------------------------------------------------------------------------------

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.slowlight.keepsake_urn");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new DispenserMenu(id, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean stillValid(Player player) {
        return !sealed() && super.stillValid(player);
    }

    /** Hoppers may fill and empty it like a barrel, but not through the wax. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return sealed() ? NO_SLOTS : ALL_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return !sealed();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return !sealed();
    }

    // ---- saving -------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Planned", planned);
        tag.putLong("OpensAt", opensAt);
        tag.putBoolean("Ripe", ripe);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        planned = tag.contains("Planned") ? tag.getInt("Planned") : 7;
        opensAt = tag.contains("OpensAt") ? tag.getLong("OpensAt") : -1L;
        ripe = tag.getBoolean("Ripe");
    }
}
