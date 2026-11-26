package dev.slowlight.autotest;

import dev.slowlight.SlowlightBlocks;
import dev.slowlight.SlowlightCrafts;
import dev.slowlight.SlowlightLockers;
import dev.slowlight.SlowlightWays;
import dev.slowlight.block.DisplayPedestalBlockEntity;
import dev.slowlight.block.FlowerPressBlockEntity;
import dev.slowlight.block.PatientKilnBlockEntity;
import dev.slowlight.block.WaystoneBlockEntity;
import dev.slowlight.locker.LockerBlock;
import dev.slowlight.locker.LockerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static dev.slowlight.autotest.ServerChecks.check;
import static dev.slowlight.autotest.ServerChecks.fail;
import static dev.slowlight.autotest.ServerChecks.info;

/**
 * Edge cases: blocks broken or blown up while holding work, taking things back with a full inventory,
 * mixed items fed into a filling kiln, and every container's saved data read back into a fresh block entity.
 */
final class EdgeChecks {
    private static final int Y = -60;
    private static final int Z = 24;

    private EdgeChecks() {
    }

    private static BlockPos at(int i) {
        return new BlockPos(40 + i * 6, Y, Z);
    }

    static void run(ServerLevel level, ServerPlayer player) {
        try {
            runAll(level, player);
        } catch (Throwable t) {
            ServerChecks.LOG.error("SLTEST FAIL edge.exception :: edge checks threw", t);
        }
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void runAll(ServerLevel level, ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        for (int i = 0; i < 12; i++) {
            BlockPos p = at(i);
            level.setBlock(p.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 3);
        }

        // 1. A Patient Kiln broken with a pickaxe while it holds a batch drops the kiln and the batch.
        BlockPos k = at(0);
        PatientKilnBlockEntity kiln = placeKiln(level, k, SlowlightBlocks.PATIENT_KILN.get());
        kiln.offer(new ItemStack(Items.HONEY_BOTTLE));
        roundTrip(level, k, "patient_kiln");
        breakWith(level, player, k, Items.STONE_PICKAXE);
        check("edge.kiln_break_drops_batch", drops(level, k, Items.HONEY_BOTTLE) == 1 && drops(level, k, SlowlightBlocks.PATIENT_KILN.get().asItem()) == 1,
                "honey " + drops(level, k, Items.HONEY_BOTTLE) + ", kiln " + drops(level, k, SlowlightBlocks.PATIENT_KILN.get().asItem()));
        clearDrops(level, k);

        // 2. A sealed Long Kiln with a stack inside drops the whole stack when broken.
        BlockPos lk = at(1);
        PatientKilnBlockEntity longKiln = placeKiln(level, lk, SlowlightBlocks.LONG_KILN.get());
        longKiln.offer(new ItemStack(Items.HONEY_BOTTLE, 16));
        roundTrip(level, lk, "long_kiln");
        breakWith(level, player, lk, Items.STONE_PICKAXE);
        check("edge.long_kiln_break_drops_stack", drops(level, lk, Items.HONEY_BOTTLE) == 16,
                "honey dropped " + drops(level, lk, Items.HONEY_BOTTLE) + " of 16");
        clearDrops(level, lk);

        // 3. A filling Long Kiln refuses a second kind of item from a hopper.
        BlockPos mix = at(2);
        PatientKilnBlockEntity mixing = placeKiln(level, mix, SlowlightBlocks.LONG_KILN.get());
        var top = mixing.getItemHandler(Direction.UP);
        if (top == null) {
            fail("edge.long_kiln_refuses_mix", "no item handler on top");
        } else {
            top.insertItem(0, new ItemStack(Items.HONEY_BOTTLE, 4), false);
            ItemStack left = ItemStack.EMPTY;
            for (int s = 0; s < top.getSlots(); s++) {
                left = top.insertItem(s, new ItemStack(Items.SWEET_BERRIES, 4), false);
                if (left.getCount() < 4) {
                    break;
                }
            }
            check("edge.long_kiln_refuses_mix", left.getCount() == 4 && mixing.working().is(Items.HONEY_BOTTLE) && mixing.working().getCount() == 4,
                    "after 4 honey then 4 berries: kiln holds " + mixing.working() + ", berries left over " + left.getCount());
        }

        // 4. Taking a batch back with a full inventory drops it at the player's feet instead of losing it.
        BlockPos full = at(3);
        PatientKilnBlockEntity fullKiln = placeKiln(level, full, SlowlightBlocks.PATIENT_KILN.get());
        fullKiln.offer(new ItemStack(Items.HONEY_BOTTLE));
        fillInventory(player);
        player.teleportTo(full.getX() + 0.5, Y, full.getZ() + 2.5);
        player.setShiftKeyDown(true);
        level.getBlockState(full).useWithoutItem(level, player, hit(full));
        player.setShiftKeyDown(false);
        int nearPlayer = dropsNear(level, player.blockPosition(), Items.HONEY_BOTTLE);
        check("edge.kiln_take_back_full_inventory", fullKiln.working().isEmpty() && nearPlayer == 1,
                "kiln holds " + fullKiln.working() + ", honey dropped by the player " + nearPlayer);
        clearDrops(level, player.blockPosition());
        player.getInventory().clearContent();

        // 5. The same for a Display Pedestal.
        BlockPos ped = at(4);
        level.setBlock(ped, SlowlightWays.DISPLAY_PEDESTAL.get().defaultBlockState(), 3);
        if (level.getBlockEntity(ped) instanceof DisplayPedestalBlockEntity stand) {
            stand.setItem(new ItemStack(Items.DIAMOND));
            roundTrip(level, ped, "display_pedestal");
            fillInventory(player);
            player.teleportTo(ped.getX() + 0.5, Y, ped.getZ() + 2.5);
            level.getBlockState(ped).useWithoutItem(level, player, hit(ped));
            int d = dropsNear(level, player.blockPosition(), Items.DIAMOND);
            check("edge.pedestal_take_full_inventory", stand.item().isEmpty() && d == 1, "pedestal holds " + stand.item() + ", diamond dropped " + d);
            clearDrops(level, player.blockPosition());
            player.getInventory().clearContent();

            stand.setItem(new ItemStack(Items.EMERALD, 1));
            breakWith(level, player, ped, Items.STONE_PICKAXE);
            check("edge.pedestal_break_drops_item", drops(level, ped, Items.EMERALD) == 1, "emerald dropped " + drops(level, ped, Items.EMERALD));
            clearDrops(level, ped);
        } else {
            fail("edge.pedestal_take_full_inventory", "no pedestal block entity");
        }

        // 6. A Flower Press broken mid-press drops the flower.
        BlockPos press = at(5);
        level.setBlock(press, SlowlightCrafts.FLOWER_PRESS.get().defaultBlockState(), 3);
        if (level.getBlockEntity(press) instanceof FlowerPressBlockEntity fp) {
            boolean in = fp.insert(new ItemStack(Items.POPPY));
            roundTrip(level, press, "flower_press");
            breakWith(level, player, press, Items.STONE_AXE);
            check("edge.press_break_drops_flower", in && drops(level, press, Items.POPPY) == 1,
                    "inserted=" + in + ", poppy dropped " + drops(level, press, Items.POPPY));
            clearDrops(level, press);
        } else {
            fail("edge.press_break_drops_flower", "no press block entity");
        }

        // 7. A Waystone with stored pearls drops them when broken.
        BlockPos way = at(6);
        level.setBlock(way, SlowlightWays.WAYSTONE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(way) instanceof WaystoneBlockEntity stone) {
            stone.itemHandler(Direction.UP).insertItem(0, new ItemStack(SlowlightWays.CHORUS_PEARL.get(), 4), false);
            int stored = stone.pearls();
            roundTrip(level, way, "waystone");
            breakWith(level, player, way, Items.STONE_PICKAXE);
            int got = drops(level, way, SlowlightWays.CHORUS_PEARL.get());
            check("edge.waystone_break_drops_pearls", stored == 4 && got == 4, "stored " + stored + ", pearls dropped " + got);
            clearDrops(level, way);
        } else {
            fail("edge.waystone_break_drops_pearls", "no waystone block entity");
        }

        // 8. A locker full of cobblestone drops it all when broken.
        BlockPos lockerPos = at(7);
        BlockState lower = SlowlightLockers.LOCKER.get().defaultBlockState().setValue(LockerBlock.FACING, Direction.SOUTH);
        level.setBlock(lockerPos, lower, 3);
        level.setBlock(lockerPos.above(), lower.setValue(LockerBlock.HALF, DoubleBlockHalf.UPPER), 3);
        if (level.getBlockEntity(lockerPos) instanceof LockerBlockEntity locker) {
            for (int s = 0; s < 3; s++) {
                locker.itemHandler().insertItem(s, new ItemStack(Items.COBBLESTONE, 64), false);
            }
            roundTrip(level, lockerPos, "locker");
            breakWith(level, player, lockerPos, Items.STONE_AXE);
            int c = drops(level, lockerPos, Items.COBBLESTONE);
            int l = drops(level, lockerPos, SlowlightLockers.LOCKER.get().asItem());
            check("edge.locker_break_drops_contents", c == 192 && l == 1, "cobblestone dropped " + c + " of 192, lockers " + l);
            clearDrops(level, lockerPos);
        } else {
            fail("edge.locker_break_drops_contents", "no locker block entity");
        }

        // 9. A kiln blown up by TNT still gives back its batch.
        BlockPos boom = at(9);
        PatientKilnBlockEntity blown = placeKiln(level, boom, SlowlightBlocks.PATIENT_KILN.get());
        blown.offer(new ItemStack(Items.HONEY_BOTTLE));
        level.explode(null, boom.getX() + 0.5, boom.getY() + 0.5, boom.getZ() + 0.5, 3.0F, Level.ExplosionInteraction.TNT);
        boolean gone = !level.getBlockState(boom).is(SlowlightBlocks.PATIENT_KILN.get());
        int honey = drops(level, boom, Items.HONEY_BOTTLE);
        check("edge.kiln_explosion_keeps_batch", !gone || honey == 1, "kiln destroyed=" + gone + ", honey dropped " + honey);
        clearDrops(level, boom);

        // 10. Replacing a working kiln with another block (a command or a mod swap) drops its batch.
        BlockPos swap = at(10);
        PatientKilnBlockEntity swapped = placeKiln(level, swap, SlowlightBlocks.PATIENT_KILN.get());
        swapped.offer(new ItemStack(Items.HONEY_BOTTLE));
        level.setBlock(swap, Blocks.STONE.defaultBlockState(), 3);
        check("edge.kiln_replaced_drops_batch", drops(level, swap, Items.HONEY_BOTTLE) == 1, "honey dropped " + drops(level, swap, Items.HONEY_BOTTLE));
        clearDrops(level, swap);
        player.teleportTo(0.5, Y, 3.5);

        // 11. A kiln far away, kept loaded by a ticket, seals; step() then lets its chunk unload through the sprint.
        BlockPos far = FAR;
        level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
        level.setBlock(far.below(), Blocks.STONE.defaultBlockState(), 3);
        PatientKilnBlockEntity farKiln = placeKiln(level, far, SlowlightBlocks.PATIENT_KILN.get());
        farKiln.offer(new ItemStack(Items.HONEY_BOTTLE));
        farState = 1;
        farTicks = 0;
    }

    // ---- a sealed kiln through a chunk unload ------------------------------------------------------

    private static final BlockPos FAR = new BlockPos(2008, Y, 2008);
    private static final long DAY = 24000L;
    /** 0 idle, 1 placed and loaded, 2 released (waiting for the unload), 3 loaded again, 4 done. */
    private static int farState;
    private static int farTicks;
    private static long releasedAt;
    private static long startedAt;
    private static boolean sawUnload;

    /** Runs every server tick after the scene is set. */
    static void step(ServerLevel level) {
        if (farState == 0 || farState == 4) {
            return;
        }
        try {
            farStep(level);
        } catch (Throwable t) {
            farState = 4;
            ServerChecks.LOG.error("SLTEST FAIL edge.unloaded_kiln.exception :: unload check threw", t);
        }
    }

    private static void farStep(ServerLevel level) {
        int cx = FAR.getX() >> 4;
        int cz = FAR.getZ() >> 4;
        farTicks++;
        if (farState == 1 && farTicks == 60) {
            BlockEntity be = level.getBlockEntity(FAR);
            CompoundTag tag = be == null ? new CompoundTag() : be.saveWithFullMetadata(level.registryAccess());
            startedAt = tag.contains("StartedAt") ? tag.getLong("StartedAt") : -1L;
            check("edge.kiln_sealed_before_unload", startedAt >= 0L, "far kiln saved StartedAt " + startedAt);
            roundTrip(level, FAR, "sealed_kiln");
            level.setChunkForced(cx, cz, false);
            releasedAt = level.getGameTime();
            farState = 2;
        } else if (farState == 2) {
            if (!level.hasChunk(cx, cz)) {
                sawUnload = true;
            }
            long gone = level.getGameTime() - releasedAt;
            if (sawUnload && gone > 3 * DAY) {
                level.setChunkForced(cx, cz, true);
                farTicks = 0;
                farState = 3;
            } else if (gone > 5 * DAY) {
                fail("edge.unloaded_kiln_catches_up", "the far chunk never unloaded in " + gone + " ticks");
                level.setChunkForced(cx, cz, false);
                farState = 4;
            }
        } else if (farState == 3) {
            // The chunk loads back asynchronously; give it up to 30 s to load and tick before judging.
            PatientKilnBlockEntity kiln = level.getBlockEntity(FAR) instanceof PatientKilnBlockEntity k ? k : null;
            ItemStack done = kiln == null ? ItemStack.EMPTY : kiln.finished();
            boolean ok = kiln != null && kiln.working().isEmpty() && done.getCount() == 1;
            if (!ok && farTicks < 600) {
                return;
            }
            check("edge.unloaded_kiln_catches_up", ok,
                    "kiln sealed at " + startedAt + ", chunk unloaded, loaded again " + farTicks + " ticks before "
                            + level.getGameTime() + " (ticking " + level.shouldTickBlocksAt(FAR) + "): holds "
                            + (kiln == null ? "no kiln" : kiln.working() + ", finished " + done));
            level.setBlock(FAR, Blocks.AIR.defaultBlockState(), 3);
            level.setChunkForced(cx, cz, false);
            farState = 4;
        }
    }

    private static PatientKilnBlockEntity placeKiln(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 3);
        return (PatientKilnBlockEntity) level.getBlockEntity(pos);
    }

    /** Saves a block entity, loads the save into a fresh one, and checks the second save matches the first. */
    private static void roundTrip(ServerLevel level, BlockPos pos, String name) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            fail("edge.save_round_trip." + name, "no block entity");
            return;
        }
        CompoundTag first = be.saveWithFullMetadata(level.registryAccess());
        BlockEntity copy = BlockEntity.loadStatic(pos, level.getBlockState(pos), first, level.registryAccess());
        CompoundTag second = copy == null ? null : copy.saveWithFullMetadata(level.registryAccess());
        check("edge.save_round_trip." + name, first.equals(second), "saved " + first + (first.equals(second) ? "" : " reloaded " + second));
    }

    private static void breakWith(ServerLevel level, ServerPlayer player, BlockPos pos, Item tool) {
        player.teleportTo(pos.getX() + 0.5, Y, pos.getZ() + 2.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        player.gameMode.destroyBlock(pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void fillInventory(ServerPlayer player) {
        var inv = player.getInventory();
        for (int s = 0; s < 36; s++) {
            inv.setItem(s, new ItemStack(Items.DIRT, 64));
        }
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
    }

    private static int drops(ServerLevel level, BlockPos pos, Item item) {
        return dropsNear(level, pos, item);
    }

    private static int dropsNear(ServerLevel level, BlockPos pos, Item item) {
        int n = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.5))) {
            if (e.getItem().is(item)) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    private static void clearDrops(ServerLevel level, BlockPos pos) {
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4))) {
            e.discard();
        }
    }
}
