package dev.slowlight.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import dev.slowlight.Slowlight;
import dev.slowlight.block.DisplayPedestalBlock;
import dev.slowlight.block.DisplayPedestalBlockEntity;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Draws what sits in a Display Pedestal's bowl. Things with a display model of their own (listed in
 * assets/&lt;ns&gt;/slowlight_displays/*.json as {"item": id, "model": "ns:block/display/name"}, or "items": [...]) sit as
 * that model, drawn in the pedestal's own block space; anything else turns slowly in the bowl as its item.
 */
public class DisplayPedestalRenderer implements BlockEntityRenderer<DisplayPedestalBlockEntity> {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final String MAPPINGS = "slowlight_displays";
    private static final String MODELS = "models/block/display";
    private static Map<Item, ModelResourceLocation> displays;

    public DisplayPedestalRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void clear() {
        displays = null;
    }

    /** Every model under models/block/display gets baked, whether or not a mapping uses it yet. */
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        Minecraft.getInstance().getResourceManager().listResources(MODELS, rl -> rl.getPath().endsWith(".json"))
                .keySet().forEach(rl -> event.register(ModelResourceLocation.standalone(model(rl))));
    }

    /** models/block/display/x.json -> ns:block/display/x */
    private static ResourceLocation model(ResourceLocation file) {
        String path = file.getPath();
        return ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
                path.substring("models/".length(), path.length() - ".json".length()));
    }

    private static Map<Item, ModelResourceLocation> displays() {
        if (displays == null) {
            displays = new HashMap<>();
            var resources = Minecraft.getInstance().getResourceManager();
            resources.listResources(MAPPINGS, rl -> rl.getPath().endsWith(".json")).forEach((file, resource) -> {
                try (Reader in = resource.openAsReader()) {
                    JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                    ModelResourceLocation model = ModelResourceLocation.standalone(
                            ResourceLocation.parse(o.get("model").getAsString()));
                    if (o.has("item")) {
                        put(o.get("item"), model);
                    }
                    if (o.has("items")) {
                        o.getAsJsonArray("items").forEach(e -> put(e, model));
                    }
                } catch (Exception e) {
                    LOGGER.warn("Could not read display {}: {}", file, e.toString());
                }
            });
        }
        return displays;
    }

    private static void put(JsonElement id, ModelResourceLocation model) {
        BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id.getAsString())).ifPresent(i -> displays.put(i, model));
    }

    @Override
    public void render(DisplayPedestalBlockEntity stand, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        ItemStack item = stand.item();
        if (item.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ModelResourceLocation name = displays().get(item.getItem());
        if (name != null) {
            BakedModel model = mc.getModelManager().getModel(name);
            if (model != mc.getModelManager().getMissingModel()) {
                mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                        buffers.getBuffer(Sheets.cutoutBlockSheet()), null, model, 1.0F, 1.0F, 1.0F, light, overlay);
                return;
            }
        }
        float time = stand.getLevel() == null ? 0.0F : stand.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5D, DisplayPedestalBlock.BOWL_Y / 16.0D + 0.22D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(time * 1.5F % 360.0F));
        pose.scale(0.5F, 0.5F, 0.5F);
        mc.getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, light, overlay, pose, buffers,
                stand.getLevel(), (int) stand.getBlockPos().asLong());
        pose.popPose();
    }
}
