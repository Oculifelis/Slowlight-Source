package dev.slowlight;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

@Mod(Slowlight.MOD_ID)
public class Slowlight {
    public static final String MOD_ID = "slowlight";

    public Slowlight(IEventBus modBus) {
        SlowlightDecor.init();
        SlowlightWeathering.init();
        SlowlightCrafts.init();
        SlowlightWays.init();
        SlowlightNights.init();
        SlowlightBlocks.BLOCKS.register(modBus);
        SlowlightItems.ITEMS.register(modBus);
        SlowlightCrafts.COMPONENTS.register(modBus);
        SlowlightPotPatterns.PATTERNS.register(modBus);
        SlowlightSounds.SOUNDS.register(modBus);
        SlowlightBlockEntities.BLOCK_ENTITIES.register(modBus);
        SlowlightRecipes.RECIPE_TYPES.register(modBus);
        SlowlightRecipes.RECIPE_SERIALIZERS.register(modBus);
        SlowlightItems.TABS.register(modBus);
        SlowlightWays.PARTICLES.register(modBus);
        SlowlightNights.STATS.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(dev.slowlight.item.UnfallingFeatherItem::tick);
        // branch rest
        SlowlightVillagers.POI_TYPES.register(modBus);
        SlowlightVillagers.PROFESSIONS.register(modBus);
        modBus.addListener(Slowlight::registerCapabilities);
        modBus.addListener(Slowlight::signBlocks);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            Gloamwood.setup();
            SlowlightNights.registerStats();
        }));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            dev.slowlight.client.SlowlightClient.init(modBus);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // The flower press is a one-slot WorldlyContainer; this lets a Create funnel see its sides as a hopper does.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SlowlightCrafts.FLOWER_PRESS_BE.get(),
                (be, side) -> side == null ? new net.neoforged.neoforge.items.wrapper.InvWrapper(be)
                        : new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(be, side));
        // The kiln takes work from above and gives it up below, so a Create funnel or a plain hopper
        // can tend it without a player ever lifting the lid.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SlowlightBlockEntities.PATIENT_KILN.get(),
                (be, side) -> be.getItemHandler(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SlowlightBlockEntities.LONG_KILN.get(),
                (be, side) -> be.getItemHandler(side)
        );
        // branch rest
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SlowlightBlockEntities.EMBER_KILN.get(),
                (be, side) -> be.getItemHandler(side)
        );
        // The urn is a plain container to anything that asks, except through its wax.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SlowlightBlockEntities.KEEPSAKE_URN.get(),
                (be, side) -> side == null ? new net.neoforged.neoforge.items.wrapper.InvWrapper(be)
                        : new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(be, side)
        );
    }

    /** Gloamwood's signs are vanilla signs; the vanilla sign block entities only have to be told they may sit in them. */
    private static void signBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityType.SIGN, SlowlightBlocks.GLOAMWOOD_SIGN.get(), SlowlightBlocks.GLOAMWOOD_WALL_SIGN.get());
        event.modify(BlockEntityType.HANGING_SIGN, SlowlightBlocks.GLOAMWOOD_HANGING_SIGN.get(),
                SlowlightBlocks.GLOAMWOOD_WALL_HANGING_SIGN.get());
    }
}
