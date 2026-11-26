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
        modBus.addListener(DisplayPedestalRenderer::registerModels);
        modBus.addListener(LockerRenderer::registerModels);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) ->
                event.register(dev.slowlight.SlowlightLockers.LOCKER_MENU.get(), LockerScreen::new));
        // The nether glass joins up with its neighbours: block models name {"loader": "slowlight:connected"}.
        modBus.addListener(ConnectedGlassModel::register);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) ->
                event.registerSpriteSet(dev.slowlight.SlowlightWays.STARWICK_FLAME.get(),
                        net.minecraft.client.particle.FlameParticle.Provider::new));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) ->
                event.registerSpriteSet(dev.slowlight.SlowlightNights.MOTH.get(), MothParticle.Provider::new));
        // Laid-down Pressed Flowers read their colours from the flowers' icons; read them again after a reload.
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> {
                    FlowerColours.clear();
                    PressedLooks.clear();
                    DisplayPedestalRenderer.clear();
                }));
        // A Pressed Flower's petals and pollen take the colours of the flower in it.
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) ->
                event.register((stack, layer) -> {
                    var flower = stack.get(dev.slowlight.SlowlightCrafts.PRESSED_FROM.get());
                    var item = flower == null ? net.minecraft.world.item.Items.POPPY : flower.value();
                    return layer == 0 ? FlowerColours.petalsTint(item) : FlowerColours.pollenTint(item);
                }, dev.slowlight.SlowlightCrafts.PRESSED_FLOWER.get()));
        // Gloamwood's sign textures, entity/signs/gloamwood and entity/signs/hanging/gloamwood, join the sign sheet.
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            Sheets.addWoodType(Gloamwood.WOOD);
            dev.slowlight.SlowlightWeathering.WOODS.forEach(Sheets::addWoodType);
        }));
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SlowlightBlockEntities.PATIENT_KILN.get(), PatientKilnRenderer::new);
        event.registerBlockEntityRenderer(dev.slowlight.SlowlightCrafts.PRESSED_FLOWERS_BE.get(), PressedFlowersRenderer::new);
        event.registerBlockEntityRenderer(SlowlightBlockEntities.LONG_KILN.get(), PatientKilnRenderer::new);
        event.registerBlockEntityRenderer(SlowlightBlockEntities.EMBER_KILN.get(), PatientKilnRenderer::new);
        event.registerBlockEntityRenderer(SlowlightBlockEntities.SANDGLASS.get(), SandglassRenderer::new);
        event.registerBlockEntityRenderer(dev.slowlight.SlowlightWays.DISPLAY_PEDESTAL_BE.get(), DisplayPedestalRenderer::new);
        event.registerBlockEntityRenderer(dev.slowlight.SlowlightLockers.LOCKER_BE.get(), LockerRenderer::new);
    }
}
