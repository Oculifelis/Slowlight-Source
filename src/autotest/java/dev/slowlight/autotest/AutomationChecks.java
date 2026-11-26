package dev.slowlight.autotest;

import dev.slowlight.SlowlightCrafts;
import dev.slowlight.SlowlightWays;
import dev.slowlight.block.DisplayPedestalBlockEntity;
import dev.slowlight.block.PearlLanternBlock;
import dev.slowlight.block.WaystoneBlockEntity;
import dev.slowlight.block.WaystoneNetwork;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import static dev.slowlight.autotest.ServerChecks.check;
import static dev.slowlight.autotest.ServerChecks.info;

/**
 * Hoppers and pipes on every station: in from the top and sides, out from the bottom. Covers the Waystone (pearl store,
 * outbox, arrived items), the Display Pedestal and the Flower Press, and the Pearl Lantern on a wall. WaysChecks starts
 * it; it uses x 80..88, z 28..30.
 */
@EventBusSubscriber(modid = "slowlight_autotest")
public final class AutomationChecks {
    private static final int Y = -60;
    private static final BlockPos SEND = new BlockPos(80, Y, 28);
    private static final BlockPos TWIN = new BlockPos(82, Y, 28);
    private static final BlockPos TWIN_HOPPER = TWIN.below();
    private static final BlockPos STAND = new BlockPos(84, Y, 28);
    private static final BlockPos PRESS = new BlockPos(86, Y, 28);
    private static final BlockPos WALL = new BlockPos(88, Y, 28);

    private static int t = -1;
    private static UUID pair;

    private AutomationChecks() {
    }

    private static IItemHandler cap(ServerLevel level, BlockPos pos, Direction side) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
    }

    static void start(ServerLevel level, ServerPlayer player) {
        // The stones sit near the edge of the test client's simulation distance; keep their chunk ticking.
        level.setChunkForced(SEND.getX() >> 4, SEND.getZ() >> 4, true);
        waystone(level);
        pedestal(level);
        press(level);
        wallLantern(level, player);
        t = 0;
    }

    private static void waystone(ServerLevel level) {
        level.setBlock(SEND, SlowlightWays.WAYSTONE.get().defaultBlockState(), 3);
        level.setBlock(TWIN, SlowlightWays.WAYSTONE.get().defaultBlockState(), 3);
        WaystoneBlockEntity a = (WaystoneBlockEntity) level.getBlockEntity(SEND);
        WaystoneBlockEntity b = (WaystoneBlockEntity) level.getBlockEntity(TWIN);

        IItemHandler top = cap(level, SEND, Direction.UP);
        IItemHandler side = cap(level, SEND, Direction.NORTH);
        IItemHandler under = cap(level, SEND, Direction.DOWN);
        if (top == null || side == null || under == null) {
            check("auto.waystone_has_handler", false, "no item handler on the waystone");
            return;
        }
        ItemStack cargo = new ItemStack(Items.COBBLESTONE, 64);
        check("auto.waystone_unlinked_refuses", side.insertItem(1, cargo, true).getCount() == 64,
                "an unlinked stone takes no cargo, so a hopper never fills it");

        pair = UUID.randomUUID();
        WaystoneNetwork net = WaystoneNetwork.get(level.getServer());
        a.setPair(pair);
        b.setPair(pair);
        net.join(pair, a.here());
        net.join(pair, b.here());

        ItemStack left = top.insertItem(0, new ItemStack(SlowlightWays.CHORUS_PEARL.get(), 20), false);
        check("auto.waystone_pearl_store", a.pearls() == WaystoneBlockEntity.PEARL_STORE && left.getCount() == 4,
                "20 Chorus Pearls in: stored " + a.pearls() + ", " + left.getCount() + " left over");
        check("auto.waystone_bottom_no_insert", under.insertItem(1, new ItemStack(Items.COBBLESTONE), true).getCount() == 1,
                "nothing goes in from below");
        ItemStack rest = side.insertItem(1, cargo.copy(), false);
        check("auto.waystone_outbox", rest.isEmpty() && a.outbox().getCount() == 64,
                "a full stack in from the side waits in the outbox: " + a.outbox());
        // The send itself happens on the stone's tick; tick() checks it.
    }

    private static void pedestal(ServerLevel level) {
        level.setBlock(STAND, SlowlightWays.DISPLAY_PEDESTAL.get().defaultBlockState(), 3);
        DisplayPedestalBlockEntity stand = (DisplayPedestalBlockEntity) level.getBlockEntity(STAND);
        IItemHandler top = cap(level, STAND, Direction.UP);
        IItemHandler side = cap(level, STAND, Direction.EAST);
        IItemHandler under = cap(level, STAND, Direction.DOWN);
        if (top == null || side == null || under == null) {
            check("auto.pedestal_has_handler", false, "no item handler on the pedestal");
            return;
        }
        ItemStack left = top.insertItem(0, new ItemStack(Items.DIAMOND, 3), false);
        check("auto.pedestal_in_from_top", left.getCount() == 2 && stand.item().is(Items.DIAMOND) && stand.item().getCount() == 1,
                "3 diamonds offered: on show " + stand.item() + ", " + left.getCount() + " back");
        check("auto.pedestal_one_only", side.insertItem(0, new ItemStack(Items.EMERALD), true).getCount() == 1,
                "a second item is refused while one is on show");
        check("auto.pedestal_side_no_take", side.extractItem(0, 1, true).isEmpty(), "the side gives nothing back");
        ItemStack out = under.extractItem(0, 1, false);
        check("auto.pedestal_out_bottom", out.is(Items.DIAMOND) && stand.item().isEmpty(),
                "the bottom takes it off: " + out);
    }

    private static void press(ServerLevel level) {
        level.setBlock(PRESS, SlowlightCrafts.FLOWER_PRESS.get().defaultBlockState(), 3);
        IItemHandler top = cap(level, PRESS, Direction.UP);
        IItemHandler under = cap(level, PRESS, Direction.DOWN);
        if (top == null || under == null) {
            check("auto.press_has_handler", false, "no item handler on the flower press");
            return;
        }
        check("auto.press_only_flowers", top.insertItem(0, new ItemStack(Items.DIRT), true).getCount() == 1,
                "dirt is refused from above");
        ItemStack left = top.insertItem(0, new ItemStack(Items.POPPY, 2), false);
        check("auto.press_in_from_top", left.getCount() == 1, "a poppy goes in from above, " + left.getCount() + " left");
        check("auto.press_holds_till_done", under.extractItem(0, 1, true).isEmpty(),
                "nothing comes out of the bottom before it is pressed");
    }

    private static void wallLantern(ServerLevel level, ServerPlayer player) {
        level.setBlock(WALL, Blocks.STONE.defaultBlockState(), 3);
        BlockPos at = WALL.east();
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        ItemStack lantern = new ItemStack(SlowlightWays.PEARL_LANTERN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, lantern);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(WALL).add(0.5, 0, 0), Direction.EAST, WALL, false);
        player.gameMode.useItemOn(player, level, lantern, InteractionHand.MAIN_HAND, hit);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var state = level.getBlockState(at);
        boolean placed = state.is(SlowlightWays.PEARL_LANTERN.get());
        check("lantern.wall_mount", placed && state.getValue(PearlLanternBlock.WALL) == PearlLanternBlock.Wall.WEST
                        && !state.getValue(PearlLanternBlock.HANGING),
                "clicked the east face of a stone: " + state);
        level.setBlock(WALL, Blocks.AIR.defaultBlockState(), 3);
        check("lantern.wall_falls", !level.getBlockState(at).is(SlowlightWays.PEARL_LANTERN.get()),
                "with the wall gone the lantern drops: " + level.getBlockState(at));
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (t < 0) {
            return;
        }
        try {
            ServerLevel level = event.getServer().overworld();
            t++;
            if (t == 45) {
                WaystoneBlockEntity a = (WaystoneBlockEntity) level.getBlockEntity(SEND);
                WaystoneNetwork net = WaystoneNetwork.get(level.getServer());
                var out = net.outgoing(pair, a.here());
                check("auto.waystone_sends", out.isPresent() && a.outbox().isEmpty()
                                && a.pearls() == WaystoneBlockEntity.PEARL_STORE - 1,
                        "full outbox went on the stone's tick: shipment=" + out.map(WaystoneNetwork.Shipment::items)
                                + ", pearls left " + a.pearls());
                // Bring the day forward, and put a plain hopper under the twin to pull what arrives.
                List<ItemStack> items = net.collect(pair, ((WaystoneBlockEntity) level.getBlockEntity(TWIN)).here(), Long.MAX_VALUE);
                net.send(new WaystoneNetwork.Shipment(pair, a.here(), items, WaystoneNetwork.now(level.getServer())));
                level.setBlock(TWIN_HOPPER, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
                level.setBlock(TWIN_HOPPER.below(), Blocks.STONE.defaultBlockState(), 3);
            }
            if (t == 120) {
                WaystoneBlockEntity b = (WaystoneBlockEntity) level.getBlockEntity(TWIN);
                int got = 0;
                if (level.getBlockEntity(TWIN_HOPPER) instanceof HopperBlockEntity hopper) {
                    for (int i = 0; i < hopper.getContainerSize(); i++) {
                        if (hopper.getItem(i).is(Items.COBBLESTONE)) {
                            got += hopper.getItem(i).getCount();
                        }
                    }
                }
                check("auto.waystone_hopper_out", got > 0, "a hopper under the twin pulled " + got
                        + " cobblestone, " + b.arrived() + " still waiting");
                WaystoneNetwork net = WaystoneNetwork.get(level.getServer());
                net.leave(pair, ((WaystoneBlockEntity) level.getBlockEntity(SEND)).here());
                net.leave(pair, b.here());
                for (BlockPos p : new BlockPos[] {SEND, TWIN, TWIN_HOPPER, STAND, PRESS, WALL.east()}) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
                level.setChunkForced(SEND.getX() >> 4, SEND.getZ() >> 4, false);
                info("auto", "done");
                t = -1;
            }
        } catch (Throwable e) {
            ServerChecks.LOG.error("SLTEST FAIL auto.exception :: automation step threw", e);
            t = -1;
        }
    }
}
