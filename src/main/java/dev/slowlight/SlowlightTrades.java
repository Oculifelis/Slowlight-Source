package dev.slowlight;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Potter and wandering-trader trades, read from {@code data/<ns>/slowlight_trades/*.json}. NeoForge re-posts the trade
 * events after every data load, so a datapack change shows up on the next {@code /reload} for traders that restock.
 */
@EventBusSubscriber(modid = Slowlight.MOD_ID)
public final class SlowlightTrades extends SimpleJsonResourceReloadListener {
    public static final String POTTER = "slowlight:potter";
    public static final String WANDERER = "minecraft:wandering_trader";

    /** One trade as the data describes it. {@code level} is 1 to 5 for the Potter; for the wandering trader 1 is common, 2 rare. */
    public record Trade(ResourceLocation id, String trader, int level, ItemStack wants, ItemStack wantsAlso,
                        List<Item> gives, int givesCount, int maxUses, int xp, float priceMultiplier) {
        public MerchantOffer offer(net.minecraft.util.RandomSource random) {
            Item item = gives.get(gives.size() == 1 ? 0 : random.nextInt(gives.size()));
            ItemCost a = new ItemCost(wants.getItem(), wants.getCount());
            Optional<ItemCost> b = wantsAlso.isEmpty() ? Optional.empty()
                    : Optional.of(new ItemCost(wantsAlso.getItem(), wantsAlso.getCount()));
            return new MerchantOffer(a, b, new ItemStack(item, givesCount), maxUses, xp, priceMultiplier);
        }

        public VillagerTrades.ItemListing listing() {
            return (trader, random) -> offer(random);
        }
    }

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static volatile List<Trade> trades = List.of();

    public SlowlightTrades() {
        super(new com.google.gson.Gson(), "slowlight_trades");
    }

    public static List<Trade> trades() {
        return trades;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<Trade> out = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                Trade t = parse(id, json.getAsJsonObject());
                if (t != null) {
                    out.add(t);
                }
            } catch (RuntimeException e) {
                LOGGER.error("Skipping trade {}: {}", id, e.getMessage());
            }
        });
        out.sort((x, y) -> x.id().compareTo(y.id()));
        trades = List.copyOf(out);
    }

    /** Reads one trade file; null when it is switched off with {@code "enabled": false}. */
    public static Trade parse(ResourceLocation id, JsonObject o) {
        if (!GsonHelper.getAsBoolean(o, "enabled", true)) {
            return null;
        }
        String trader = GsonHelper.getAsString(o, "trader");
        if (!trader.equals(POTTER) && !trader.equals(WANDERER)) {
            throw new JsonParseException("trader must be " + POTTER + " or " + WANDERER);
        }
        int level = trader.equals(POTTER) ? GsonHelper.getAsInt(o, "level")
                : (GsonHelper.getAsBoolean(o, "rare", false) ? 2 : 1);
        if (level < 1 || level > 5) {
            throw new JsonParseException("level must be 1 to 5");
        }
        ItemStack wants = stack(GsonHelper.getAsJsonObject(o, "wants"));
        ItemStack wantsAlso = o.has("wants_also") ? stack(GsonHelper.getAsJsonObject(o, "wants_also")) : ItemStack.EMPTY;
        JsonObject g = GsonHelper.getAsJsonObject(o, "gives");
        List<Item> gives = new ArrayList<>();
        if (g.has("one_of")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(g, "one_of")) {
                gives.add(item(e.getAsString()));
            }
        } else {
            gives.add(item(GsonHelper.getAsString(g, "id")));
        }
        if (gives.isEmpty()) {
            throw new JsonParseException("gives names no item");
        }
        return new Trade(id, trader, level, wants, wantsAlso, List.copyOf(gives), GsonHelper.getAsInt(g, "count", 1),
                GsonHelper.getAsInt(o, "max_uses"), GsonHelper.getAsInt(o, "xp"),
                GsonHelper.getAsFloat(o, "price_multiplier", 0.05F));
    }

    private static ItemStack stack(JsonObject o) {
        return new ItemStack(item(GsonHelper.getAsString(o, "id")), GsonHelper.getAsInt(o, "count", 1));
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
                .orElseThrow(() -> new JsonParseException("unknown item " + id));
    }

    @SubscribeEvent
    static void addListener(AddReloadListenerEvent event) {
        event.addListener(new SlowlightTrades());
    }

    @SubscribeEvent
    static void villagerTrades(VillagerTradesEvent event) {
        if (event.getType() != SlowlightVillagers.POTTER.get()) {
            return;
        }
        for (Trade t : trades) {
            if (t.trader().equals(POTTER)) {
                event.getTrades().get(t.level()).add(t.listing());
            }
        }
    }

    @SubscribeEvent
    static void wandererTrades(WandererTradesEvent event) {
        for (Trade t : trades) {
            if (t.trader().equals(WANDERER)) {
                (t.level() == 2 ? event.getRareTrades() : event.getGenericTrades()).add(t.listing());
            }
        }
    }
}
