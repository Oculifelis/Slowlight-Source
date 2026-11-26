package dev.slowlight.autotest;

import dev.slowlight.SlowlightBlocks;
import dev.slowlight.SlowlightWays;
import dev.slowlight.block.KeepsakeUrnBlockEntity;
import dev.slowlight.block.PatientKilnBlockEntity;
import dev.slowlight.block.SandglassBlockEntity;
import dev.slowlight.block.WaystoneBlockEntity;
import dev.slowlight.block.WaystoneNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;

/**
 * Reload: a working Patient Kiln, Long Kiln, Sandglass and sealed Keepsake Urn in a far chunk that is unloaded (saved
 * to disk) and loaded again. Each block must come back as a new block entity with the same status line. The reload
 * waits for the chunk's real unload event, since a chunk can leave the loaded set while its holder still keeps it.
 * A paired Waystone sends a stack to its twin in the same chunk before the unload. After the reload both stones must
 * still be paired, the shipment must still be on its way, and once it is due the twin must get it exactly once.
 */
@EventBusSubscriber(modid = "slowlight_autotest")
public final class ReloadChecks {
    private static final int X = -3000, Y = -60, Z = -3000, CX = X >> 4, CZ = Z >> 4;
    private static final int START = 32, GIVE_UP = 600;
    private static final Map<BlockPos, String> BEFORE = new HashMap<>();
    private static final Map<BlockPos, BlockEntity> OLD = new HashMap<>();
    private static final BlockPos STONE_A = new BlockPos(X, Y, Z + 2), STONE_B = new BlockPos(X + 7, Y, Z + 2);
    private static final int ARRIVE_WAIT = 60;
    private static UUID pair;
    private static long arriveAt;
    private static int reloadedAt;
    private static int stage;
    private static volatile boolean unloaded;

    private ReloadChecks() {
    }

    static void step(ServerLevel level, int ticks) {
        if (stage == 0 && ticks == START) {
            build(level);
            level.setChunkForced(CX, CZ, false);
            stage = 1;
        } else if (stage == 1) {
            if (unloaded) {
                ServerChecks.info("reload.unloaded", "chunk " + CX + "," + CZ + " unloaded after " + (ticks - START) + " ticks");
                level.getChunk(CX, CZ);
                compare(level);
                level.setChunkForced(CX, CZ, true);
                waystonesAfterReload(level);
                reloadedAt = ticks;
                stage = 2;
            } else if (ticks > START + GIVE_UP) {
                ServerChecks.fail("reload.unloaded", "chunk never unloaded in " + GIVE_UP + " ticks");
                stage = 3;
            }
        } else if (stage == 2 && ticks == reloadedAt + ARRIVE_WAIT) {
            waystonesArrived(level);
            level.setChunkForced(CX, CZ, false);
            stage = 3;
        }
    }

    @SubscribeEvent
    public static void onUnload(ChunkEvent.Unload event) {
        if (stage == 1 && event.getChunk().getPos().x == CX && event.getChunk().getPos().z == CZ) {
            unloaded = true;
        }
    }

    private static void build(ServerLevel level) {
        level.setChunkForced(CX, CZ, true);
        for (int x = X; x < X + 8; x++) {
            for (int z = Z; z < Z + 3; z++) {
                level.setBlock(new BlockPos(x, Y - 1, z), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, Y, z), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, Y + 1, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        BlockPos kiln = place(level, 0, SlowlightBlocks.PATIENT_KILN.get());
        BlockPos longKiln = place(level, 2, SlowlightBlocks.LONG_KILN.get());
        BlockPos glass = place(level, 4, SlowlightBlocks.SANDGLASS.get());
        BlockPos urn = place(level, 6, SlowlightBlocks.KEEPSAKE_URN.get());
        if (level.getBlockEntity(kiln) instanceof PatientKilnBlockEntity k) {
            k.offer(new ItemStack(Items.HONEY_BOTTLE));
        }
        if (level.getBlockEntity(longKiln) instanceof PatientKilnBlockEntity k) {
            k.offer(new ItemStack(Items.ROTTEN_FLESH, 16));
        }
        if (level.getBlockEntity(glass) instanceof SandglassBlockEntity g) {
            g.cycleDays(level, glass, level.getBlockState(glass));
            g.cycleDays(level, glass, level.getBlockState(glass));
        }
        if (level.getBlockEntity(urn) instanceof KeepsakeUrnBlockEntity u) {
            u.setItem(0, new ItemStack(Items.DIAMOND, 3));
            u.seal(level, urn, level.getBlockState(urn));
        }
        waystonesBefore(level);
        for (BlockPos p : new BlockPos[] {kiln, longKiln, glass, urn}) {
            BlockEntity be = level.getBlockEntity(p);
            OLD.put(p, be);
            BEFORE.put(p, describe(be));
        }
    }

    private static void waystonesBefore(ServerLevel level) {
        level.setBlock(STONE_A, SlowlightWays.WAYSTONE.get().defaultBlockState(), 3);
        level.setBlock(STONE_B, SlowlightWays.WAYSTONE.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(STONE_A) instanceof WaystoneBlockEntity a) || !(level.getBlockEntity(STONE_B) instanceof WaystoneBlockEntity b)) {
            ServerChecks.fail("reload.waystone_sent", "waystones were not placed");
            return;
        }
        WaystoneNetwork net = WaystoneNetwork.get(level.getServer());
        pair = UUID.randomUUID();
        a.setPair(pair);
        b.setPair(pair);
        net.join(pair, a.here());
        net.join(pair, b.here());
        // Far enough ahead that it cannot arrive before the unload, which can take a few hundred ticks.
        arriveAt = WaystoneNetwork.now(level.getServer()) + 20L * 60 * 60;
        net.send(new WaystoneNetwork.Shipment(pair, a.here(), List.of(new ItemStack(Items.AMETHYST_SHARD, 5)), arriveAt));
        ServerChecks.info("reload.waystone_sent", "5 amethyst shards sent, due at " + arriveAt);
    }

    private static void waystonesAfterReload(ServerLevel level) {
        if (pair == null) {
            return;
        }
        WaystoneNetwork net = WaystoneNetwork.get(level.getServer());
        boolean paired = level.getBlockEntity(STONE_A) instanceof WaystoneBlockEntity a && pair.equals(a.pair())
                && level.getBlockEntity(STONE_B) instanceof WaystoneBlockEntity b && pair.equals(b.pair());
        var out = net.outgoing(pair, GlobalPos.of(level.dimension(), STONE_A));
        boolean onWay = out.isPresent() && out.get().arriveAt() == arriveAt && out.get().items().size() == 1
                && out.get().items().get(0).is(Items.AMETHYST_SHARD) && out.get().items().get(0).getCount() == 5;
        if (paired && onWay) {
            ServerChecks.pass("reload.waystone_in_transit", "both stones still paired, shipment still due at " + arriveAt);
        } else {
            ServerChecks.fail("reload.waystone_in_transit", "paired=" + paired + " shipment=" + out);
        }
        // Bring the day forward so the twin collects it within the test.
        if (out.isPresent()) {
            List<ItemStack> items = net.collect(pair, GlobalPos.of(level.dimension(), STONE_B), Long.MAX_VALUE);
            net.send(new WaystoneNetwork.Shipment(pair, out.get().from(), items, WaystoneNetwork.now(level.getServer())));
        }
    }

    private static void waystonesArrived(ServerLevel level) {
        if (pair == null) {
            return;
        }
        WaystoneNetwork net = WaystoneNetwork.get(level.getServer());
        List<ItemStack> atB = level.getBlockEntity(STONE_B) instanceof WaystoneBlockEntity b ? b.takeArrived() : List.of();
        List<ItemStack> atA = level.getBlockEntity(STONE_A) instanceof WaystoneBlockEntity a ? a.takeArrived() : List.of();
        int shards = atB.stream().filter(i -> i.is(Items.AMETHYST_SHARD)).mapToInt(ItemStack::getCount).sum();
        boolean left = net.outgoing(pair, GlobalPos.of(level.dimension(), STONE_A)).isPresent();
        if (atB.size() == 1 && shards == 5 && atA.isEmpty() && !left) {
            ServerChecks.pass("reload.waystone_arrives_once", "the twin got 5 amethyst shards once, nothing left in transit");
        } else {
            ServerChecks.fail("reload.waystone_arrives_once", "twin got " + atB + ", sender got " + atA + ", still in transit=" + left);
        }
        net.leave(pair, GlobalPos.of(level.dimension(), STONE_A));
        net.leave(pair, GlobalPos.of(level.dimension(), STONE_B));
    }

    private static BlockPos place(ServerLevel level, int dx, Block block) {
        BlockPos p = new BlockPos(X + dx, Y, Z + 1);
        level.setBlock(p, block.defaultBlockState(), 3);
        return p;
    }

    private static String describe(BlockEntity be) {
        if (be instanceof PatientKilnBlockEntity k) {
            return k.status().getString();
        }
        if (be instanceof SandglassBlockEntity g) {
            return g.days() + " days | " + g.status().getString();
        }
        if (be instanceof KeepsakeUrnBlockEntity u) {
            return "sealed=" + u.sealed() + " planned=" + u.planned() + " holds " + u.getItem(0).getCount() + "x"
                    + u.getItem(0).getItem() + " | " + u.status().getString();
        }
        return String.valueOf(be);
    }

    private static void compare(ServerLevel level) {
        for (Map.Entry<BlockPos, String> e : BEFORE.entrySet()) {
            BlockEntity now = level.getBlockEntity(e.getKey());
            String id = level.getBlockState(e.getKey()).getBlock().getDescriptionId();
            String name = "reload." + id.substring(id.lastIndexOf('.') + 1);
            String after = describe(now);
            if (now != null && now != OLD.get(e.getKey()) && after.equals(e.getValue()) && !after.isBlank()) {
                ServerChecks.pass(name, "same after reload: " + after);
            } else {
                ServerChecks.fail(name, "before [" + e.getValue() + "] after [" + after + "] new entity=" + (now != OLD.get(e.getKey())));
            }
        }
        OLD.clear();
    }
}
