package dev.slowlight.block;

import dev.slowlight.SlowlightWays;
import dev.slowlight.recipe.AgeingRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
import org.jetbrains.annotations.Nullable;

/** Which pair this stone belongs to, and what has arrived at it and not been taken yet. */
public class WaystoneBlockEntity extends BlockEntity {
    private @Nullable UUID pair;
    private final List<ItemStack> arrived = new ArrayList<>();

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

    public static void serverTick(Level level, BlockPos pos, BlockState state, WaystoneBlockEntity stone) {
        if (level.getGameTime() % 20L != 0L || stone.pair == null || !(level instanceof ServerLevel server)) {
            return;
        }
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
        ListTag items = new ListTag();
        arrived.stream().filter(i -> !i.isEmpty()).forEach(i -> items.add(i.save(registries)));
        tag.put("arrived", items);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pair = tag.hasUUID("pair") ? tag.getUUID("pair") : null;
        arrived.clear();
        for (Tag t : tag.getList("arrived", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, t).ifPresent(arrived::add);
        }
    }
}
