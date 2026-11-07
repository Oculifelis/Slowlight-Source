package dev.slowlight.block;

import dev.slowlight.SlowlightCrafts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Which flower lies in each quarter of a Pressed Flowers block, in the order they were laid down. */
public class PressedFlowersBlockEntity extends BlockEntity {
    private final List<Holder<Item>> flowers = new ArrayList<>();

    public PressedFlowersBlockEntity(BlockPos pos, BlockState state) {
        super(SlowlightCrafts.PRESSED_FLOWERS_BE.get(), pos, state);
    }

    public List<Holder<Item>> flowers() {
        return Collections.unmodifiableList(flowers);
    }

    /** Lays one more down; false when all four quarters are taken. */
    public boolean add(Holder<Item> flower) {
        if (flowers.size() >= PressedFlowersBlock.MAX) {
            return false;
        }
        flowers.add(flower);
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            int light = flowers.stream().mapToInt(f -> PressedFlowersBlock.glow(f.value())).max().orElse(0);
            level.setBlock(worldPosition, state.setValue(PressedFlowersBlock.FLOWERS, Math.max(1, flowers.size()))
                    .setValue(PressedFlowersBlock.LIGHT, light), 3);
            level.sendBlockUpdated(worldPosition, state, getBlockState(), 3);
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        flowers.forEach(f -> list.add(StringTag.valueOf(f.unwrapKey().map(k -> k.location().toString()).orElse("minecraft:air"))));
        tag.put("Flowers", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        flowers.clear();
        for (Tag t : tag.getList("Flowers", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) {
                BuiltInRegistries.ITEM.getHolder(id).ifPresent(flowers::add);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
