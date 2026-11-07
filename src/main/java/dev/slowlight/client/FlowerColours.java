package dev.slowlight.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.slowlight.block.PressedFlowersBlock;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a flower looks like, read from its own item icon, so any flower (modded ones too) can be laid down as tinted
 * petals with no art of its own. The icon's pixels are sorted into hue groups, leaving out the green of stems and
 * leaves: the biggest group is the petals' colour, the next (if there is enough of it) the centre's, as a daisy's
 * yellow middle. How much bloom there is and whether it has two colours choose the look (PressedFlowersRenderer).
 */
public final class FlowerColours {
    /** petals and centre as 0xRRGGBB, bloom = how many petal pixels the icon has, twoTone = a real second colour,
     * glow = how bright the flower is drawn whatever the light around it (0-15): its block's own light, or an emissive
     * model's (NeoForge's block_light in a model element is baked into the quads' lightmap). */
    public record Look(int petals, int centre, int bloom, boolean twoTone, int glow) {
    }

    private static final Map<Item, Look> CACHE = new HashMap<>();
    private static final Direction[] SIDES = {null, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP,
            Direction.DOWN};
    private static final Look PLAIN = new Look(0xE8A0C0, 0xC06080, 30, false, 0);

    private FlowerColours() {
    }

    /** Cleared when resources reload, as the icons may have changed. */
    public static void clear() {
        CACHE.clear();
    }

    public static Look of(Item flower) {
        return CACHE.computeIfAbsent(flower, f -> {
            Look l = read(f);
            int glow = Math.max(PressedFlowersBlock.glow(f), emissive(f));
            return new Look(l.petals(), l.centre(), l.bloom(), l.twoTone(), glow);
        });
    }

    /** The brightest block light baked into any of the flower's item model quads (0 when nothing is emissive). Each
     * vertex is 8 ints in the BLOCK format; the lightmap is the 7th, block light in its low half. */
    private static int emissive(Item flower) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(new ItemStack(flower), null, null, 0);
        RandomSource random = RandomSource.create(42);
        int glow = 0;
        for (Direction side : SIDES) {
            for (BakedQuad quad : model.getQuads(null, side, random)) {
                int[] v = quad.getVertices();
                for (int i = 6; i < v.length; i += 8) {
                    glow = Math.max(glow, (v[i] & 0xFFFF) >> 4);
                }
            }
        }
        return Math.min(glow, 15);
    }

    private static Look read(Item flower) {
        Minecraft mc = Minecraft.getInstance();
        TextureAtlasSprite sprite = mc.getItemRenderer().getModel(new ItemStack(flower), null, null, 0).getParticleIcon();
        ResourceLocation name = sprite.contents().name();
        ResourceLocation file = name.withPath(p -> "textures/" + p + ".png");
        try (InputStream in = mc.getResourceManager().open(file); NativeImage img = NativeImage.read(in)) {
            return read(img);
        } catch (Exception e) {
            return PLAIN;
        }
    }

    /** 12 hue groups of 30 degrees, then light, mid and dark greys for the pixels with little colour. */
    private static Look read(NativeImage img) {
        long[][] sum = new long[15][3];
        int[] count = new int[15];
        float[] hsv = new float[3];
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int abgr = img.getPixelRGBA(x, y);
                if ((abgr >>> 24) < 128) {
                    continue;
                }
                int r = abgr & 0xFF, g = (abgr >> 8) & 0xFF, b = (abgr >> 16) & 0xFF;
                java.awt.Color.RGBtoHSB(r, g, b, hsv);
                float deg = hsv[0] * 360F;
                if (hsv[1] > 0.25F && deg > 70F && deg < 170F) {
                    continue; // stem or leaf
                }
                int group = hsv[1] < 0.2F ? 12 + (hsv[2] > 0.7F ? 0 : hsv[2] > 0.35F ? 1 : 2) : Mth.floor(hsv[0] * 12F) % 12;
                sum[group][0] += r;
                sum[group][1] += g;
                sum[group][2] += b;
                count[group]++;
            }
        }
        // The centre is the biggest group that is not a neighbour of the petals' (the greys are all one family), so
        // a daisy's white split over light and mid grey still finds its yellow middle.
        int first = -1, second = -1;
        for (int i = 0; i < 15; i++) {
            if (count[i] > 0 && (first < 0 || count[i] > count[first])) {
                first = i;
            }
        }
        for (int i = 0; first >= 0 && i < 15; i++) {
            if (count[i] > 0 && !near(first, i) && (second < 0 || count[i] > count[second])) {
                second = i;
            }
        }
        if (first < 0) {
            return PLAIN;
        }
        int petals = mean(sum[first], count[first]);
        int bloom = 0;
        for (int c : count) {
            bloom += c;
        }
        boolean two = second >= 0 && count[second] * 100 >= count[first] * 8;
        int centre = two ? mean(sum[second], count[second]) : darker(petals);
        return new Look(petals, centre, bloom, two, 0);
    }

    private static boolean near(int a, int b) {
        if (a >= 12 || b >= 12) {
            return a >= 12 && b >= 12;
        }
        int d = Math.abs(a - b);
        return d <= 1 || d == 11;
    }

    private static int mean(long[] s, int n) {
        return (int) (s[0] / n) << 16 | (int) (s[1] / n) << 8 | (int) (s[2] / n);
    }

    private static int darker(int rgb) {
        return (((rgb >> 16) & 0xFF) * 3 / 4) << 16 | (((rgb >> 8) & 0xFF) * 3 / 4) << 8 | (rgb & 0xFF) * 3 / 4;
    }
}
