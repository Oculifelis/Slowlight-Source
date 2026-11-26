package dev.slowlight.autotest;

import dev.slowlight.SlowlightCrafts;
import dev.slowlight.SlowlightWays;
import dev.slowlight.block.FlowerPressBlockEntity;
import dev.slowlight.block.PearlLanternBlock;
import dev.slowlight.block.WaystoneBlockEntity;
import dev.slowlight.block.WaystoneNetwork;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import static dev.slowlight.autotest.ServerChecks.check;

/**
 * Views for the client's pictures: Jade on a pressing Flower Press, Jade on a paired Waystone with pearls and an
 * outbox, and the Pearl Lantern standing, hanging from a chain and on a wall beside a press. Uses x 130..160, z 100.
 */
@EventBusSubscriber(modid = "slowlight_autotest")
public final class JadeScenes {
    private static final int Y = -60;
    private static final BlockPos PRESS = new BlockPos(130, Y, 100);
    private static final BlockPos STONE = new BlockPos(136, Y, 100);
    private static final BlockPos TWIN = new BlockPos(142, Y, 100);
    private static final BlockPos LAMPS = new BlockPos(150, Y, 100);

    /** Which view the client wants next (0 none), and the last one made ready. */
    public static volatile int want;
    public static volatile int ready;

    private JadeScenes() {
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        int w = want;
        if (w == 0) {
            return;
        }
        want = 0;
        ServerLevel level = event.getServer().overworld();
        switch (w) {
            case 1 -> press(level);
            case 2 -> waystone(level);
            case 3 -> lanterns(level);
            default -> { }
        }
        ready = w;
    }

    private static void look(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        for (var p : level.getServer().getPlayerList().getPlayers()) {
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.connection.teleport(x, y, z, yaw, pitch);
        }
    }

    private static void press(ServerLevel level) {
        level.setBlock(PRESS, SlowlightCrafts.FLOWER_PRESS.get().defaultBlockState(), 3);
        boolean in = level.getBlockEntity(PRESS) instanceof FlowerPressBlockEntity press
                && press.insert(new ItemStack(Items.CORNFLOWER));
        check("jade.press_scene", in, "a cornflower in the press for the Jade picture");
        look(level, PRESS.getX() + 0.5, Y, PRESS.getZ() + 2.4, 180F, 30F);
    }

    private static void waystone(ServerLevel level) {
        level.setBlock(STONE, SlowlightWays.WAYSTONE.get().defaultBlockState(), 3);
        level.setBlock(TWIN, SlowlightWays.WAYSTONE.get().defaultBlockState(), 3);
        WaystoneBlockEntity a = (WaystoneBlockEntity) level.getBlockEntity(STONE);
        WaystoneBlockEntity b = (WaystoneBlockEntity) level.getBlockEntity(TWIN);
        UUID pair = UUID.randomUUID();
        WaystoneNetwork net = WaystoneNetwork.get(level.getServer());
        a.setPair(pair);
        b.setPair(pair);
        net.join(pair, a.here());
        net.join(pair, b.here());
        IItemHandler top = level.getCapability(Capabilities.ItemHandler.BLOCK, STONE, Direction.UP);
        boolean ok = top != null;
        if (ok) {
            top.insertItem(0, new ItemStack(SlowlightWays.CHORUS_PEARL.get(), 12), false);
            top.insertItem(1, new ItemStack(Items.BREAD, 20), false);
        }
        check("jade.waystone_scene", ok && a.pearls() == 12 && a.outbox().getCount() == 20,
                "a paired stone with 12 pearls and 20 bread in the outbox");
        look(level, STONE.getX() + 0.5, Y, STONE.getZ() + 2.4, 180F, 30F);
    }

    private static void lanterns(ServerLevel level) {
        var lantern = SlowlightWays.PEARL_LANTERN.get().defaultBlockState();
        // standing
        level.setBlock(LAMPS, lantern, 3);
        // hanging from a pearl chain under a beam
        BlockPos beam = LAMPS.offset(2, 3, 0);
        level.setBlock(beam, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
        level.setBlock(beam.below(), SlowlightWays.PEARL_CHAIN.get().defaultBlockState(), 3);
        level.setBlock(beam.below(2), lantern.setValue(PearlLanternBlock.HANGING, true), 3);
        // on a wall
        BlockPos wall = LAMPS.offset(4, 1, 0);
        level.setBlock(wall, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
        level.setBlock(wall.south(), lantern.setValue(PearlLanternBlock.WALL, PearlLanternBlock.Wall.NORTH), 3);
        // a press beside them, to show it sits clean on the ground
        level.setBlock(LAMPS.offset(6, 0, 0), SlowlightCrafts.FLOWER_PRESS.get().defaultBlockState(), 3);
        boolean all = level.getBlockState(beam.below(2)).is(SlowlightWays.PEARL_LANTERN.get())
                && level.getBlockState(wall.south()).is(SlowlightWays.PEARL_LANTERN.get());
        check("lantern.scene", all, "standing, hanging and wall lanterns all stay put");
        look(level, LAMPS.getX() + 3.5, Y + 0.6, LAMPS.getZ() + 5.2, 180F, 8F);
    }
}
