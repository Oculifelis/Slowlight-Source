package dev.slowlight.autotest;

import dev.slowlight.block.PatientKilnBlockEntity;
import dev.slowlight.block.SandglassBlockEntity;
import dev.slowlight.block.WaystoneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Tick cost: a far-off field of 150 Patient Kilns and 50 Long Kilns (all working), 20 running Sandglasses and 20
 * paired Waystones. Their own tick methods are timed over three 20-tick cycles (they work once every 20 ticks); the
 * last cycle counts, after the first two have warmed up the JIT.
 */
final class TickCostChecks {
    private static final int X0 = 3000, Y = -60, Z0 = 3000, COLS = 20;
    private static final int CYCLES = 3;
    /** Average budget for the whole field, in microseconds per server tick: 2% of a 50 ms tick. */
    private static final long BUDGET_US = 1000;

    private static final List<BlockPos> KILNS = new ArrayList<>(), GLASSES = new ArrayList<>(), STONES = new ArrayList<>();
    private static long[] nanos;
    private static int measured = -1;
    private static boolean done;

    private TickCostChecks() {
    }

    static void step(ServerLevel level, ServerPlayer player, int ticks) {
        if (done) {
            return;
        }
        if (ticks == 30) {
            build(level, player);
            return;
        }
        if (ticks < 100 || KILNS.isEmpty()) {
            return;
        }
        if (measured < 0) {
            if (level.getGameTime() % 20L != 0L) {
                return;
            }
            nanos = new long[20 * CYCLES];
            measured = 0;
        }
        long t0 = System.nanoTime();
        for (BlockPos p : KILNS) {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof PatientKilnBlockEntity kiln) {
                PatientKilnBlockEntity.serverTick(level, p, level.getBlockState(p), kiln);
            }
        }
        for (BlockPos p : GLASSES) {
            if (level.getBlockEntity(p) instanceof SandglassBlockEntity glass) {
                SandglassBlockEntity.serverTick(level, p, level.getBlockState(p), glass);
            }
        }
        for (BlockPos p : STONES) {
            if (level.getBlockEntity(p) instanceof WaystoneBlockEntity stone) {
                WaystoneBlockEntity.serverTick(level, p, level.getBlockState(p), stone);
            }
        }
        nanos[measured++] = System.nanoTime() - t0;
        if (measured == nanos.length) {
            report(level);
        }
    }

    private static void build(ServerLevel level, ServerPlayer player) {
        for (int cx = X0 >> 4; cx <= (X0 + COLS + 2) >> 4; cx++) {
            for (int cz = Z0 >> 4; cz <= (Z0 + 30) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        for (int x = X0 - 1; x <= X0 + COLS + 1; x++) {
            for (int z = Z0 - 1; z <= Z0 + 28; z++) {
                level.setBlock(new BlockPos(x, Y - 1, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = Y; y <= Y + 3; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        // rows 0-7 Patient Kilns, 8-9 Long Kilns, 10 Sandglasses, 12-13 Waystone pairs: every other block, so lids open
        for (int i = 0; i < 200; i++) {
            int row = i / 20, col = i % 20;
            BlockPos p = new BlockPos(X0 + col, Y, Z0 + row * 2);
            boolean longKiln = row >= 8;
            level.setBlock(p, (longKiln ? dev.slowlight.SlowlightBlocks.LONG_KILN : dev.slowlight.SlowlightBlocks.PATIENT_KILN)
                    .get().defaultBlockState(), 3);
            if (level.getBlockEntity(p) instanceof PatientKilnBlockEntity kiln) {
                kiln.offer(new ItemStack(Items.ROTTEN_FLESH, longKiln ? 16 : 1));
                KILNS.add(p);
            }
        }
        for (int col = 0; col < 20; col++) {
            BlockPos p = new BlockPos(X0 + col, Y, Z0 + 20);
            level.setBlock(p, dev.slowlight.SlowlightBlocks.SANDGLASS.get().defaultBlockState(), 3);
            if (level.getBlockEntity(p) instanceof SandglassBlockEntity glass) {
                glass.cycleDays(level, p, level.getBlockState(p));
                GLASSES.add(p);
            }
        }
        for (int col = 0; col < 10; col++) {
            BlockPos a = new BlockPos(X0 + col * 2, Y, Z0 + 24), b = new BlockPos(X0 + col * 2, Y, Z0 + 26);
            if (WaysChecks.pairForBench(level, player, a, b)) {
                STONES.add(a);
                STONES.add(b);
            }
        }
        ServerChecks.info("tick.field", KILNS.size() + " kilns, " + GLASSES.size() + " sandglasses, " + STONES.size()
                + " paired waystones");
    }

    private static void report(ServerLevel level) {
        long sum = 0, max = 0;
        for (int i = nanos.length - 20; i < nanos.length; i++) {
            sum += nanos[i];
            max = Math.max(max, nanos[i]);
        }
        long avgUs = sum / 20 / 1000, maxUs = max / 1000;
        String detail = "avg " + avgUs + " us/tick, busiest tick " + maxUs + " us (" + KILNS.size() + " kilns, "
                + GLASSES.size() + " sandglasses, " + STONES.size() + " waystones; budget " + BUDGET_US + " us)";
        boolean filled = KILNS.size() == 200 && GLASSES.size() == 20 && STONES.size() == 20;
        if (filled && avgUs <= BUDGET_US) {
            ServerChecks.pass("tick.cost", detail);
        } else {
            ServerChecks.fail("tick.cost", detail);
        }
        for (int cx = X0 >> 4; cx <= (X0 + COLS + 2) >> 4; cx++) {
            for (int cz = Z0 >> 4; cz <= (Z0 + 30) >> 4; cz++) {
                level.setChunkForced(cx, cz, false);
            }
        }
        done = true;
    }
}
