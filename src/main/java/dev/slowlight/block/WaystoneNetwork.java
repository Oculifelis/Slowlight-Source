package dev.slowlight.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every pair of Waystones in the world, and everything on its way between them. It lives with the Overworld's data
 * and keeps the Overworld's clock, so a shipment counts down the same whichever dimension its stones are in, and
 * whether or not either end is loaded. Items wait here until the stone they are going to is next loaded.
 */
public class WaystoneNetwork extends SavedData {
    private static final String NAME = "slowlight_waystones";
    private static final Factory<WaystoneNetwork> FACTORY = new Factory<>(WaystoneNetwork::new, WaystoneNetwork::load, null);

    /** A stack, or the stacks a route turned it into, between two stones of a pair. */
    public record Shipment(UUID pair, GlobalPos from, List<ItemStack> items, long arriveAt) {
    }

    private final Map<UUID, List<GlobalPos>> pairs = new HashMap<>();
    private final List<Shipment> shipments = new ArrayList<>();

    public static WaystoneNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public List<GlobalPos> stones(UUID pair) {
        return pairs.getOrDefault(pair, List.of());
    }

    /** The other stone of this one's pair, if it has been set yet. */
    public Optional<GlobalPos> partner(UUID pair, GlobalPos self) {
        return stones(pair).stream().filter(p -> !p.equals(self)).findFirst();
    }

    public boolean join(UUID pair, GlobalPos stone) {
        List<GlobalPos> list = pairs.computeIfAbsent(pair, k -> new ArrayList<>());
        if (list.contains(stone)) {
            return true;
        }
        if (list.size() >= 2) {
            return false;
        }
        list.add(stone);
        setDirty();
        return true;
    }

    public void leave(UUID pair, GlobalPos stone) {
        List<GlobalPos> list = pairs.get(pair);
        if (list != null && list.remove(stone)) {
            if (list.isEmpty()) {
                pairs.remove(pair);
            }
            setDirty();
        }
    }

    public Optional<Shipment> outgoing(UUID pair, GlobalPos from) {
        return shipments.stream().filter(s -> s.pair.equals(pair) && s.from.equals(from)).findFirst();
    }

    /** The next shipment coming to this stone: anything of its pair that it did not send itself. */
    public Optional<Shipment> incoming(UUID pair, GlobalPos to) {
        return shipments.stream().filter(s -> s.pair.equals(pair) && !s.from.equals(to))
                .min((a, b) -> Long.compare(a.arriveAt, b.arriveAt));
    }

    public void send(Shipment shipment) {
        shipments.add(shipment);
        setDirty();
    }

    /** Takes out everything that has reached this stone by now. */
    public List<ItemStack> collect(UUID pair, GlobalPos to, long now) {
        List<ItemStack> out = new ArrayList<>();
        for (Iterator<Shipment> it = shipments.iterator(); it.hasNext(); ) {
            Shipment s = it.next();
            if (s.pair.equals(pair) && !s.from.equals(to) && s.arriveAt <= now) {
                s.items.forEach(i -> out.add(i.copy()));
                it.remove();
            }
        }
        if (!out.isEmpty()) {
            setDirty();
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag pairList = new ListTag();
        pairs.forEach((pair, list) -> {
            CompoundTag p = new CompoundTag();
            p.putUUID("pair", pair);
            ListTag stones = new ListTag();
            list.forEach(s -> GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, s).result().ifPresent(stones::add));
            p.put("stones", stones);
            pairList.add(p);
        });
        tag.put("pairs", pairList);
        ListTag shipList = new ListTag();
        for (Shipment s : shipments) {
            CompoundTag t = new CompoundTag();
            t.putUUID("pair", s.pair);
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, s.from).result().ifPresent(f -> t.put("from", f));
            ListTag items = new ListTag();
            s.items.stream().filter(i -> !i.isEmpty()).forEach(i -> items.add(i.save(registries)));
            t.put("items", items);
            t.putLong("arrive_at", s.arriveAt);
            shipList.add(t);
        }
        tag.put("shipments", shipList);
        return tag;
    }

    private static WaystoneNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        WaystoneNetwork net = new WaystoneNetwork();
        for (Tag t : tag.getList("pairs", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            List<GlobalPos> list = new ArrayList<>();
            for (Tag s : p.getList("stones", Tag.TAG_COMPOUND)) {
                GlobalPos.CODEC.parse(NbtOps.INSTANCE, s).result().ifPresent(list::add);
            }
            if (!list.isEmpty()) {
                net.pairs.put(p.getUUID("pair"), list);
            }
        }
        for (Tag t : tag.getList("shipments", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            Optional<GlobalPos> from = GlobalPos.CODEC.parse(NbtOps.INSTANCE, s.get("from")).result();
            if (from.isEmpty()) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>();
            for (Tag i : s.getList("items", Tag.TAG_COMPOUND)) {
                ItemStack.parse(registries, i).ifPresent(items::add);
            }
            net.shipments.add(new Shipment(s.getUUID("pair"), from.get(), items, s.getLong("arrive_at")));
        }
        return net;
    }
}
