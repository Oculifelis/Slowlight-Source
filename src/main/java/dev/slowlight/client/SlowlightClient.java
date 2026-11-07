package dev.slowlight.client;

import dev.slowlight.Gloamwood;
import dev.slowlight.Slowlight;
import dev.slowlight.SlowlightBlockEntities;
import net.minecraft.client.renderer.Sheets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only wiring, hooked up from {@link Slowlight} only on the physical client. */
public final class SlowlightClient {
    private SlowlightClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(SlowlightClient::renderers);
        // Laid-down Pressed Flowers read their colours from the flowers' icons; read them again after a reload.
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> {
                    FlowerColours.clear();
                    PressedLooks.clear();
                }));
        modBus.addListener((net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) ->
                event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
                    @Override
                    public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        return PressedFlowerRenderer.get();
                    }
                }, dev.slowlight.SlowlightCrafts.PRESSED_FLOWER.get()));
        // Gloamwood's sign textures, entity/signs/gloamwood and entity/signs/hanging/gloamwood, join the sign sheet.
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> Sheets.addWoodType(Gloamwood.WOOD)));
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SlowlightBlockEntities.PATIENT_KILN.get(), PatientKilnRenderer::new);
        event.registerBlockEntityRenderer(dev.slowlight.SlowlightCrafts.PRESSED_FLOWERS_BE.get(), PressedFlowersRenderer::new);
        event.registerBlockEntityRenderer(SlowlightBlockEntities.LONG_KILN.get(), PatientKilnRenderer::new);
        event.registerBlockEntityRenderer(SlowlightBlockEntities.EMBER_KILN.get(), PatientKilnRenderer::new);
    }
}
