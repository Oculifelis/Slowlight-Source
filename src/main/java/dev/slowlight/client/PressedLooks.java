package dev.slowlight.client;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.google.gson.JsonParser;
import dev.slowlight.Slowlight;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Which petal looks a laid-down flower can take, by band (assets/slowlight/pressed_looks.json, written by
 * tools/make_crafts.py pressed_petals): small, mid and big blooms of one colour, two_small and two_big for flowers with
 * a second colour. Within its band a flower always gets the same look, chosen by its id, so flowers of the same size
 * still differ. A resource pack can add looks by listing more textures in the file.
 */
public final class PressedLooks {
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "pressed_looks.json");
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static Map<String, List<String>> bands;

    private PressedLooks() {
    }

    public static void clear() {
        bands = null;
    }

    public static String band(FlowerColours.Look l) {
        int b = l.bloom();
        if (l.twoTone()) {
            return b >= 33 ? "two_big" : "two_small";
        }
        return b >= 28 ? "big" : b >= 18 ? "mid" : "small";
    }

    public static String of(Item flower, FlowerColours.Look l) {
        List<String> looks = bands().getOrDefault(band(l), List.of());
        if (looks.isEmpty()) {
            return "a_pink_petals";
        }
        int h = BuiltInRegistries.ITEM.getKey(flower).hashCode();
        return looks.get(Math.floorMod(h ^ (h >>> 16), looks.size()));
    }

    private static Map<String, List<String>> bands() {
        if (bands == null) {
            bands = new HashMap<>();
            try (Reader in = new InputStreamReader(Minecraft.getInstance().getResourceManager().open(FILE), StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                for (String band : o.keySet()) {
                    List<String> names = new ArrayList<>();
                    o.getAsJsonArray(band).forEach(e -> names.add(e.getAsString()));
                    bands.put(band, names);
                }
            } catch (Exception e) {
                LOGGER.warn("Could not read {}: {}", FILE, e.toString());
            }
        }
        return bands;
    }
}
