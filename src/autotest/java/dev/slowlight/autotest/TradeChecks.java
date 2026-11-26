package dev.slowlight.autotest;

import com.google.gson.JsonParser;
import dev.slowlight.SlowlightItems;
import dev.slowlight.SlowlightTrades;
import dev.slowlight.SlowlightVillagers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Trades are data: the shipped files load, the Potter's trade lists come from them, and a pack's file (an override, a
 * switched-off trade, a bad item) is read the way DATA.md says. At the end of the run a world datapack makes mead
 * cost 1 emerald, and after a /reload a fresh Potter's level 3 list must offer the new price, once, without doubling.
 */
final class TradeChecks {
    private TradeChecks() {
    }

    static void run(ServerLevel level, ServerPlayer player) {
        long potter = SlowlightTrades.trades().stream().filter(t -> t.trader().equals(SlowlightTrades.POTTER)).count();
        long wanderer = SlowlightTrades.trades().stream().filter(t -> t.trader().equals(SlowlightTrades.WANDERER)).count();
        ServerChecks.check("trades.loaded", potter == 13 && wanderer == 4, potter + " potter, " + wanderer + " wandering trader trades");

        var lists = VillagerTrades.TRADES.get(SlowlightVillagers.POTTER.get());
        boolean mead = false;
        int total = 0;
        if (lists != null) {
            for (int lv = 1; lv <= 5; lv++) {
                VillagerTrades.ItemListing[] row = lists.get(lv);
                total += row == null ? 0 : row.length;
            }
            VillagerTrades.ItemListing[] three = lists.get(3);
            if (three != null) {
                for (VillagerTrades.ItemListing l : three) {
                    MerchantOffer o = l.getOffer(player, level.getRandom());
                    mead |= o != null && o.getResult().is(SlowlightItems.MEAD.get()) && o.getBaseCostA().is(Items.EMERALD)
                            && o.getBaseCostA().getCount() == 3;
                }
            }
        }
        ServerChecks.check("trades.potter_from_data", total == 13 && mead, total + " potter listings, mead for 3 emeralds at level 3=" + mead);

        String override = """
                {"trader": "slowlight:potter", "level": 3, "wants": {"id": "minecraft:emerald", "count": 1},
                 "gives": {"id": "slowlight:mead", "count": 2}, "max_uses": 20, "xp": 4}""";
        SlowlightTrades.Trade t = SlowlightTrades.parse(id("override"), JsonParser.parseString(override).getAsJsonObject());
        MerchantOffer o = t.offer(level.getRandom());
        ServerChecks.check("trades.override", o.getBaseCostA().getCount() == 1 && o.getResult().getCount() == 2
                        && o.getResult().is(SlowlightItems.MEAD.get()) && o.getMaxUses() == 20 && o.getXp() == 4,
                "pack file gives " + o.getResult().getCount() + "x mead for " + o.getBaseCostA().getCount() + " emerald, "
                        + o.getMaxUses() + " uses, " + o.getXp() + " xp");

        SlowlightTrades.Trade off = SlowlightTrades.parse(id("off"), JsonParser.parseString(
                "{\"enabled\": false, \"trader\": \"slowlight:potter\"}").getAsJsonObject());
        ServerChecks.check("trades.disabled", off == null, "\"enabled\": false turns a trade off");

        String bad = "{\"trader\": \"slowlight:potter\", \"level\": 1, \"wants\": {\"id\": \"minecraft:no_such_item\"},"
                + " \"gives\": {\"id\": \"minecraft:emerald\"}, \"max_uses\": 1, \"xp\": 1}";
        String why;
        try {
            SlowlightTrades.parse(id("bad"), JsonParser.parseString(bad).getAsJsonObject());
            why = null;
        } catch (RuntimeException e) {
            why = e.getMessage();
        }
        ServerChecks.check("trades.bad_item", why != null && why.contains("no_such_item"), "unknown item is refused: " + why);
    }

    static volatile boolean wantReload, reloadChecked;
    private static CompletableFuture<Void> reload;
    private static VillagerTrades.ItemListing[] beforeThree;
    private static int settle;

    /** The client asks for this last; the server reloads its datapacks the way /reload does and checks the lists. */
    static void reloadStep(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        if (!wantReload || reloadChecked) {
            return;
        }
        if (reload == null) {
            var lists = VillagerTrades.TRADES.get(SlowlightVillagers.POTTER.get());
            beforeThree = lists == null ? null : lists.get(3);
            Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("slowlight_reload");
            try {
                Path file = pack.resolve("data/slowlight/slowlight_trades/potter/3/emerald_mead.json");
                Files.createDirectories(file.getParent());
                Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\": {\"description\": \"reload test\", \"pack_format\": 48}}");
                Files.writeString(file, "{\"trader\": \"slowlight:potter\", \"level\": 3, \"wants\": {\"id\": \"minecraft:emerald\", \"count\": 1},"
                        + " \"gives\": {\"id\": \"slowlight:mead\", \"count\": 1}, \"max_uses\": 12, \"xp\": 10}");
            } catch (java.io.IOException e) {
                ServerChecks.fail("trades.after_reload", "could not write the world datapack: " + e);
                reloadChecked = true;
                return;
            }
            server.getPackRepository().reload();
            var ids = new ArrayList<>(server.getPackRepository().getSelectedIds());
            if (!ids.contains("file/slowlight_reload")) {
                ids.add("file/slowlight_reload");
            }
            reload = server.reloadResources(ids);
            settle = 5;
            return;
        }
        if (!reload.isDone() || --settle > 0) {
            return;
        }
        long potter = SlowlightTrades.trades().stream().filter(t -> t.trader().equals(SlowlightTrades.POTTER)).count();
        var lists = VillagerTrades.TRADES.get(SlowlightVillagers.POTTER.get());
        int total = 0;
        VillagerTrades.ItemListing[] three = null;
        if (lists != null) {
            for (int lv = 1; lv <= 5; lv++) {
                VillagerTrades.ItemListing[] row = lists.get(lv);
                total += row == null ? 0 : row.length;
            }
            three = lists.get(3);
        }
        boolean rebuilt = three != null && three != beforeThree;
        int meadPrice = -1;
        if (three != null) {
            for (VillagerTrades.ItemListing l : three) {
                MerchantOffer o = l.getOffer(player, level.getRandom());
                if (o != null && o.getResult().is(SlowlightItems.MEAD.get())) {
                    meadPrice = o.getBaseCostA().getCount();
                }
            }
        }
        ServerChecks.check("trades.after_reload", !reload.isCompletedExceptionally() && potter == 13 && total == 13 && rebuilt && meadPrice == 1,
                potter + " potter trades loaded, " + total + " potter listings, lists rebuilt=" + rebuilt + ", mead now costs " + meadPrice);
        reloadChecked = true;
        // The test world is kept between runs, so the pack goes again; its trade stays in memory until the next reload.
        try {
            Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("slowlight_reload");
            try (var walk = Files.walk(pack)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            }
        } catch (java.io.IOException e) {
            ServerChecks.info("trades.after_reload", "could not remove the world datapack: " + e);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("slowlight_test", path);
    }
}
