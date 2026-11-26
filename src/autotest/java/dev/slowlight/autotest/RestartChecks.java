package dev.slowlight.autotest;

import dev.slowlight.SlowlightBlocks;
import dev.slowlight.block.KeepsakeUrnBlockEntity;
import dev.slowlight.block.SandglassBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

import static dev.slowlight.autotest.ServerChecks.check;

/**
 * A real restart for the urn and the sandglass (SL65). The first launch (-Pautotest=1) places a sealed urn and a
 * running sandglass at the very end, writes down what each says and how many ticks each has left, and saves the
 * world. The second launch (-Pautotest=restart, tools/run_restart_test.sh) opens that same world instead of making a
 * new one, reads both back and checks they show the same days left.
 */
final class RestartChecks {
    static final boolean RESTART = "restart".equals(System.getProperty("slowlight.autotest"));
    /** Client asks for the probes at the end of the first launch; server answers when they are placed and saved. */
    static volatile boolean wantProbe;
    static volatile boolean probed;
    /** Second launch: set when the read-back is done. */
    static volatile boolean done;

    private static final int Y = -60;
    private static final BlockPos URN = new BlockPos(30, Y, 4);
    private static final BlockPos GLASS = new BlockPos(32, Y, 4);
    /** Ticks allowed between the save in launch one and the read in launch two (the world loads, the player joins). */
    private static final long SLACK = 600L;

    private RestartChecks() {
    }

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve("sltest_restart.properties");
    }

    private static String text(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() + Arrays.toString(t.getArgs()) : c.getString();
    }

    /** First launch, server thread: place, record, save. */
    static void probe(MinecraftServer server, ServerLevel level) {
        if (!wantProbe || probed) {
            return;
        }
        wantProbe = false;
        try {
            level.setBlock(URN.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(GLASS.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(URN, SlowlightBlocks.KEEPSAKE_URN.get().defaultBlockState(), 3);
            level.setBlock(GLASS, SlowlightBlocks.SANDGLASS.get().defaultBlockState(), 3);
            KeepsakeUrnBlockEntity urn = (KeepsakeUrnBlockEntity) level.getBlockEntity(URN);
            urn.setItem(0, new ItemStack(Items.EMERALD, 5));
            urn.seal(level, URN, level.getBlockState(URN));
            SandglassBlockEntity glass = (SandglassBlockEntity) level.getBlockEntity(GLASS);
            for (int i = 0; i < 3; i++) {
                glass.cycleDays(level, GLASS, level.getBlockState(GLASS));
            }
            Properties p = new Properties();
            long now = level.getGameTime();
            p.setProperty("time", Long.toString(now));
            p.setProperty("urn.left", Long.toString(urnLeft(level, urn)));
            p.setProperty("urn.status", text(urn.status()));
            p.setProperty("glass.left", Long.toString(glassLeft(level, glass)));
            p.setProperty("glass.days", Integer.toString(glass.days()));
            p.setProperty("glass.status", text(glass.status()));
            try (Writer w = Files.newBufferedWriter(file())) {
                p.store(w, "written by the first launch of the restart test");
            }
            // The mod's data-pack worldgen makes the world's settings non-stable, so reopening it shows the backup
            // prompt. Mark it confirmed, as a player's first click would, so the second launch goes straight in.
            if (server.getWorldData() instanceof net.minecraft.world.level.storage.PrimaryLevelData pld) {
                pld.withConfirmedWarning(true);
            }
            boolean saved = server.saveEverything(true, true, true);
            check("restart.probe_saved", saved && urn.sealed() && glass.days() == 3,
                    "urn " + p.getProperty("urn.status") + " left " + p.getProperty("urn.left") + ", glass "
                            + p.getProperty("glass.status") + " left " + p.getProperty("glass.left") + ", saved=" + saved);
        } catch (Exception e) {
            ServerChecks.fail("restart.probe_saved", "could not place or record the probes: " + e);
        }
        probed = true;
    }

    /** Second launch, server thread: read back once the player has been in the world a moment. */
    static void readBack(ServerLevel level, int ticks) {
        if (done || ticks < 40) {
            return;
        }
        try {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file())) {
                p.load(r);
            }
            long then = Long.parseLong(p.getProperty("time"));
            long now = level.getGameTime();
            check("restart.world_kept", now >= then && now - then < SLACK,
                    "game time " + then + " at the save, " + now + " after the restart");
            if (level.getBlockEntity(URN) instanceof KeepsakeUrnBlockEntity urn) {
                long left = urnLeft(level, urn);
                long want = Long.parseLong(p.getProperty("urn.left")) - (now - then);
                check("restart.urn_same_days_left", urn.sealed() && left == want && text(urn.status()).equals(p.getProperty("urn.status")),
                        "before " + p.getProperty("urn.status") + ", after " + text(urn.status()) + "; ticks left " + left + ", expected " + want
                                + "; first slot " + urn.getItem(0));
                check("restart.urn_keeps_items", urn.getItem(0).is(Items.EMERALD) && urn.getItem(0).getCount() == 5, "first slot " + urn.getItem(0));
            } else {
                ServerChecks.fail("restart.urn_same_days_left", "no urn at " + URN + " after the restart: " + level.getBlockState(URN));
            }
            if (level.getBlockEntity(GLASS) instanceof SandglassBlockEntity glass) {
                long left = glassLeft(level, glass);
                long want = Long.parseLong(p.getProperty("glass.left")) - (now - then);
                check("restart.sandglass_same_days_left", glass.days() == Integer.parseInt(p.getProperty("glass.days")) && left == want
                                && text(glass.status()).equals(p.getProperty("glass.status")),
                        "before " + p.getProperty("glass.status") + ", after " + text(glass.status()) + "; ticks left " + left
                                + ", expected " + want + "; days " + glass.days());
            } else {
                ServerChecks.fail("restart.sandglass_same_days_left", "no sandglass at " + GLASS + " after the restart: " + level.getBlockState(GLASS));
            }
        } catch (Exception e) {
            ServerChecks.fail("restart.read_back", "could not read the first launch's record: " + e);
        }
        done = true;
    }

    private static long urnLeft(ServerLevel level, KeepsakeUrnBlockEntity urn) {
        CompoundTag t = urn.saveWithoutMetadata(level.registryAccess());
        return t.getLong("OpensAt") - level.getGameTime();
    }

    private static long glassLeft(ServerLevel level, SandglassBlockEntity glass) {
        CompoundTag t = glass.saveWithoutMetadata(level.registryAccess());
        return (long) t.getInt("Days") * dev.slowlight.recipe.AgeingRecipe.TICKS_PER_DAY - (level.getGameTime() - t.getLong("StartedAt"));
    }
}
