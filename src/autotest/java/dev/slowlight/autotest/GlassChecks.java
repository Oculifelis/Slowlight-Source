package dev.slowlight.autotest;

import dev.slowlight.SlowlightBlocks;
import dev.slowlight.block.SandglassBlock;
import dev.slowlight.block.SandglassBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import static dev.slowlight.autotest.ServerChecks.check;

/**
 * Connected nether glass in L, T, plus and hollow-frame layouts on a wall, and a Sandglass close up for its turn.
 * The client asks for each view and takes the pictures.
 */
@EventBusSubscriber(modid = "slowlight_autotest")
public final class GlassChecks {
    private static final int Y = -60;
    private static final int WALL_Z = 60;
    static final BlockPos SANDGLASS = new BlockPos(110, Y + 1, 80);

    public static volatile boolean clientWantsWall;
    public static volatile boolean wallReady;
    public static volatile boolean clientWantsSandglass;
    public static volatile boolean sandglassReady;
    public static volatile boolean clientWantsTurn;
    public static volatile boolean turnStarted;
    /** Client asks for the sand this far through its turn (0 to 1), or -1; -1 again once it is set. */
    public static volatile float drainWant = -1.0F;

    /** Rows top to bottom, X where glass goes. */
    private static final String[][] LAYOUTS = {
            {"X..", "X..", "XXX"},
            {"XXX", ".X.", ".X."},
            {".X.", "XXX", ".X."},
            {"XXXX", "X..X", "X..X", "XXXX"},
    };
    private static final String[][] GLASS = {
            {"crimson_mosaic_glass", "warped_rose_glass", "soul_lattice_glass", "magma_mosaic_glass"},
            {"soul_rose_glass", "magma_lattice_glass", "crimson_rose_glass", "warped_lattice_glass"},
            {"warped_mosaic_glass", "crimson_lattice_glass", "magma_rose_glass", "soul_mosaic_glass"},
    };

    private GlassChecks() {
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("slowlight", id));
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (clientWantsWall) {
            clientWantsWall = false;
            buildWall(level);
        }
        if (clientWantsSandglass) {
            clientWantsSandglass = false;
            level.setBlock(SANDGLASS.below(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
            level.setBlock(SANDGLASS, SlowlightBlocks.SANDGLASS.get().defaultBlockState(), 3);
            for (var p : event.getServer().getPlayerList().getPlayers()) {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                p.connection.teleport(SANDGLASS.getX() + 2.3, Y, SANDGLASS.getZ() + 2.3, 135F, 4F);
            }
            sandglassReady = true;
        }
        if (clientWantsTurn) {
            clientWantsTurn = false;
            BlockState s = level.getBlockState(SANDGLASS);
            if (level.getBlockEntity(SANDGLASS) instanceof SandglassBlockEntity glass) {
                glass.cycleDays(level, SANDGLASS, s);
            }
            check("sandglass.turns", level.getBlockState(SANDGLASS).getValue(SandglassBlock.TURNING),
                    "a click sets the next day count and turns the glass over");
            turnStarted = true;
        }
        if (drainWant >= 0.0F && level.getBlockEntity(SANDGLASS) instanceof SandglassBlockEntity glass) {
            // Wind the clock back as if the glass had turned that long ago; the renderer reads it from the client copy.
            CompoundTag tag = glass.saveWithoutMetadata(level.registryAccess());
            long total = (long) Math.max(1, glass.days()) * dev.slowlight.recipe.AgeingRecipe.TICKS_PER_DAY;
            tag.putLong("StartedAt", level.getGameTime() - (long) (drainWant * total));
            glass.loadWithComponents(tag, level.registryAccess());
            glass.setChanged();
            level.sendBlockUpdated(SANDGLASS, glass.getBlockState(), glass.getBlockState(), 3);
            drainWant = -1.0F;
        }
    }

    private static void buildWall(ServerLevel level) {
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set 6000");
        for (int x = 98; x <= 124; x++) {
            for (int y = Y; y <= Y + 16; y++) {
                for (int z = WALL_Z - 1; z <= WALL_Z + 14; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            // A pale backdrop two blocks behind, so the glass reads against something even.
            for (int y = Y; y <= Y + 16; y++) {
                level.setBlock(new BlockPos(x, y, WALL_Z - 2), Blocks.SMOOTH_QUARTZ.defaultBlockState(), 2);
            }
        }
        int placed = 0;
        for (int row = 0; row < GLASS.length; row++) {
            int top = Y + 15 - row * 5;
            int x0 = 100;
            for (int i = 0; i < LAYOUTS.length; i++) {
                String[] rows = LAYOUTS[i];
                Block glass = block(GLASS[row][i]);
                for (int r = 0; r < rows.length; r++) {
                    for (int c = 0; c < rows[r].length(); c++) {
                        if (rows[r].charAt(c) == 'X') {
                            level.setBlock(new BlockPos(x0 + c, top - r, WALL_Z), glass.defaultBlockState(), 3);
                            placed++;
                        }
                    }
                }
                x0 += rows[0].length() + 2;
            }
        }
        check("glass.layouts", placed == 3 * (5 + 5 + 5 + 12), placed + " glass blocks in 12 layouts");
        for (var p : server.getPlayerList().getPlayers()) {
            // standing on the ground well back and looking up, so all three rows are in the picture
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            p.connection.teleport(110.5, Y, WALL_Z + 20.5, 180F, -16F);
        }
        wallReady = true;
    }
}
