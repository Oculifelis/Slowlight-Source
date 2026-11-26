package dev.slowlight.block;

import dev.slowlight.SlowlightWays;
import dev.slowlight.recipe.AgeingRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Which pair this stone belongs to, and what has arrived at it and not been taken yet.
 *
 * <p>Hoppers and pipes work it like the other stations: in from the top and sides, out from the bottom. Chorus
 * Pearls fed in go to a small store that pays for sends. Anything else waits in the outbox and goes once it is a full
 * stack or nothing more has come in for five seconds, as long as the twin is placed, nothing else is on the way and a
 * stored pearl is there to pay. Arrived items come out of the bottom.
 */
public class WaystoneBlockEntity extends BlockEntity {
    public static final int PEARL_STORE = 16;
    /** Ticks with nothing new in the outbox before a part stack goes. */
    public static final int OUTBOX_WAIT = 100;
    private @Nullable UUID pair;
    private @Nullable Component name;
    private final List<ItemStack> arrived = new ArrayList<>();
    private int pearls;
    private ItemStack outbox = ItemStack.EMPTY;
    private long lastIn;
    private @Nullable IItemHandler sides, bottom, all;

    public WaystoneBlockEntity(BlockPos pos, BlockState state) {
        super(SlowlightWays.WAYSTONE_BE.get(), pos, state);
    }

    public @Nullable UUID pair() {
        return pair;
    }

    public void setPair(@Nullable UUID pair) {
        this.pair = pair;
        setChanged();
    }

    public @Nullable Component name() {
        return name;
    }

    public void setName(@Nullable Component name) {
        this.name = name;
        setChanged();
    }

    public GlobalPos here() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    public List<ItemStack> takeArrived() {
        List<ItemStack> out = new ArrayList<>(arrived);
        arrived.clear();
        setChanged();
        return out;
    }

    public boolean hasArrived() {
        return !arrived.isEmpty();
    }

    public List<ItemStack> arrived() {
        return arrived;
    }

    public int pearls() {
        return pearls;
    }

    public ItemStack outbox() {
        return outbox;
    }

    /** Pays one send from the store. */
    public boolean usePearl() {
        if (pearls <= 0) {
            return false;
        }
        pearls--;
        setChanged();
        return true;
    }

    /** The stored pearls and the outbox, emptied, for dropping when the stone is broken. */
    public List<ItemStack> takeStored() {
        List<ItemStack> out = new ArrayList<>();
        if (pearls > 0) {
            out.add(new ItemStack(SlowlightWays.CHORUS_PEARL.get(), pearls));
        }
        if (!outbox.isEmpty()) {
            out.add(outbox);
        }
        pearls = 0;
        outbox = ItemStack.EMPTY;
        setChanged();
        return out;
    }

    public IItemHandler itemHandler(@Nullable Direction side) {
        if (side == null) {
            return all == null ? all = new Handler(true, true) : all;
        }
        if (side == Direction.DOWN) {
            return bottom == null ? bottom = new Handler(false, true) : bottom;
        }
        return sides == null ? sides = new Handler(true, false) : sides;
    }

    private void sendOutbox(ServerLevel server, BlockPos pos, BlockState state) {
        if (outbox.isEmpty() || pair == null || pearls <= 0) {
            return;
        }
        if (outbox.getCount() < outbox.getMaxStackSize() && server.getGameTime() - lastIn < OUTBOX_WAIT) {
            return;
        }
        WaystoneNetwork net = WaystoneNetwork.get(server.getServer());
        Optional<GlobalPos> partner = net.partner(pair, here());
        if (partner.isEmpty() || net.outgoing(pair, here()).isPresent()) {
            return;
        }
        pearls--;
        ItemStack sent = outbox;
        outbox = ItemStack.EMPTY;
        setChanged();
        WaystoneBlock.dispatch(server, pos, state, this, partner.get(), sent);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WaystoneBlockEntity stone) {
        if (level.getGameTime() % 20L != 0L || stone.pair == null || !(level instanceof ServerLevel server)) {
            return;
        }
        stone.sendOutbox(server, pos, state);
        WaystoneNetwork net = WaystoneNetwork.get(server.getServer());
        List<ItemStack> got = net.collect(stone.pair, stone.here(), WaystoneNetwork.now(server.getServer()));
        if (got.isEmpty()) {
            return;
        }
        stone.arrived.addAll(got);
        stone.setChanged();
        WaystoneBlock.pulse(server, pos, state);
        server.playSound(null, pos, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.BLOCKS, 0.8F, 0.8F);
    }

    /** What Jade shows: the pairing, anything on the way out, what waits here, the pearl store and the outbox. */
    public List<Component> jadeLines(ServerLevel server) {
        List<Component> lines = new ArrayList<>();
        if (name != null) {
            lines.add(name);
        }
        lines.add(WaystoneBlock.status(server, this));
        if (pair != null) {
            WaystoneNetwork net = WaystoneNetwork.get(server.getServer());
            long now = WaystoneNetwork.now(server.getServer());
            net.outgoing(pair, here()).ifPresent(s -> lines.add(Component.translatable(
                    "block.slowlight.waystone.jade_sending", when(s.arriveAt() - now))));
        }
        int waiting = 0;
        for (ItemStack s : arrived) {
            waiting += s.getCount();
        }
        if (waiting > 0) {
            lines.add(Component.translatable("block.slowlight.waystone.jade_arrived", waiting));
        }
        lines.add(Component.translatable("block.slowlight.waystone.jade_pearls", pearls, PEARL_STORE));
        if (!outbox.isEmpty()) {
            lines.add(Component.translatable("block.slowlight.waystone.jade_outbox", outbox.getCount(), outbox.getHoverName()));
        }
        return lines;
    }

    /** "in 2 days", "today", for a tick count still to wait. */
    static Component when(long ticks) {
        if (ticks <= AgeingRecipe.TICKS_PER_DAY) {
            return Component.translatable("block.slowlight.waystone.today");
        }
        return Component.translatable("block.slowlight.waystone.days",
                (ticks + AgeingRecipe.TICKS_PER_DAY - 1L) / AgeingRecipe.TICKS_PER_DAY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (pair != null) {
            tag.putUUID("pair", pair);
        }
        if (name != null) {
            tag.putString("CustomName", Component.Serializer.toJson(name, registries));
        }
        ListTag items = new ListTag();
        arrived.stream().filter(i -> !i.isEmpty()).forEach(i -> items.add(i.save(registries)));
        tag.put("arrived", items);
        tag.putInt("pearls", pearls);
        if (!outbox.isEmpty()) {
            tag.put("outbox", outbox.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pair = tag.hasUUID("pair") ? tag.getUUID("pair") : null;
        name = tag.contains("CustomName", Tag.TAG_STRING)
                ? Component.Serializer.fromJson(tag.getString("CustomName"), registries) : null;
        arrived.clear();
        for (Tag t : tag.getList("arrived", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, t).ifPresent(arrived::add);
        }
        pearls = tag.getInt("pearls");
        outbox = ItemStack.parseOptional(registries, tag.getCompound("outbox"));
    }

    /** Slot 0 the pearl store, slot 1 the outbox, then one slot per arrived stack. */
    private final class Handler implements IItemHandler {
        private final boolean in, out;

        Handler(boolean in, boolean out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public int getSlots() {
            return 2 + arrived.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot == 0) {
                return pearls > 0 ? new ItemStack(SlowlightWays.CHORUS_PEARL.get(), pearls) : ItemStack.EMPTY;
            }
            if (slot == 1) {
                return outbox;
            }
            return slot - 2 < arrived.size() ? arrived.get(slot - 2) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!in || stack.isEmpty() || !isItemValid(slot, stack)) {
                return stack;
            }
            int room;
            if (slot == 0) {
                room = PEARL_STORE - pearls;
            } else if (outbox.isEmpty()) {
                room = stack.getMaxStackSize();
            } else {
                room = ItemStack.isSameItemSameComponents(outbox, stack) ? outbox.getMaxStackSize() - outbox.getCount() : 0;
            }
            int n = Math.min(room, stack.getCount());
            if (n <= 0) {
                return stack;
            }
            if (!simulate) {
                if (slot == 0) {
                    pearls += n;
                } else {
                    outbox = outbox.isEmpty() ? stack.copyWithCount(n) : outbox.copyWithCount(outbox.getCount() + n);
                    lastIn = level == null ? 0L : level.getGameTime();
                }
                setChanged();
            }
            return stack.copyWithCount(stack.getCount() - n);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!out || slot < 2 || slot - 2 >= arrived.size() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack have = arrived.get(slot - 2);
            int n = Math.min(amount, have.getCount());
            ItemStack got = have.copyWithCount(n);
            if (!simulate) {
                have.shrink(n);
                if (have.isEmpty()) {
                    arrived.remove(slot - 2);
                }
                setChanged();
            }
            return got;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? PEARL_STORE : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == 0) {
                return stack.is(SlowlightWays.CHORUS_PEARL.get());
            }
            return slot == 1 && pair != null;
        }
    }
}
