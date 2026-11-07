package dev.slowlight.compat;

import dev.slowlight.Slowlight;
import dev.slowlight.block.PatientKilnBlock;
import dev.slowlight.block.PatientKilnBlockEntity;
// branch rest: Sandglass and Keepsake Urn
import dev.slowlight.block.KeepsakeUrnBlock;
import dev.slowlight.block.KeepsakeUrnBlockEntity;
import dev.slowlight.block.SandglassBlock;
import dev.slowlight.block.SandglassBlockEntity;
// end branch rest
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Shows the kiln's business in the Jade overlay: what it is working on and how long is left.
 *
 * <p>The block entity keeps its timing on the server, and Jade only sees the client copy, so the
 * numbers are sent across as server data rather than read locally. Looking at the lid tells you
 * nothing; this is the honest way to check without opening it.
 */
@WailaPlugin
public class SlowlightJadePlugin implements IWailaPlugin {
    private static final ResourceLocation KILN = ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "patient_kiln");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(KilnProvider.INSTANCE, PatientKilnBlockEntity.class);
        // branch rest
        registration.registerBlockDataProvider(StatusProvider.SANDGLASS, SandglassBlockEntity.class);
        registration.registerBlockDataProvider(StatusProvider.URN, KeepsakeUrnBlockEntity.class);
        // end branch rest
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(KilnProvider.INSTANCE, PatientKilnBlock.class);
        // branch rest
        registration.registerBlockComponent(StatusProvider.SANDGLASS, SandglassBlock.class);
        registration.registerBlockComponent(StatusProvider.URN, KeepsakeUrnBlock.class);
        // end branch rest
    }

    public enum KilnProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof PatientKilnBlockEntity kiln) {
                data.putString("SlowlightStatus", Component.Serializer.toJson(kiln.status(), accessor.getLevel().registryAccess()));
                ItemStack working = kiln.working();
                if (!working.isEmpty()) {
                    data.putString("SlowlightWorking", working.getHoverName().getString());
                }
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data.contains("SlowlightWorking")) {
                tooltip.add(Component.translatable("block.slowlight.patient_kiln.jade_working",
                        data.getString("SlowlightWorking")));
            }
            if (data.contains("SlowlightStatus")) {
                Component status = Component.Serializer.fromJson(
                        data.getString("SlowlightStatus"), accessor.getLevel().registryAccess());
                if (status != null) {
                    tooltip.add(status);
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return KILN;
        }
    }

    // branch rest: the Sandglass and Keepsake Urn keep their days on the server too, so their status line comes across
    // the same way the kiln's does.
    public enum StatusProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        SANDGLASS("sandglass"),
        URN("keepsake_urn");

        private final ResourceLocation uid;

        StatusProvider(String name) {
            uid = ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, name);
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            Component status = null;
            if (accessor.getBlockEntity() instanceof SandglassBlockEntity glass) {
                status = glass.status();
            } else if (accessor.getBlockEntity() instanceof KeepsakeUrnBlockEntity urn) {
                status = urn.status();
            }
            if (status != null) {
                data.putString("SlowlightStatus", Component.Serializer.toJson(status, accessor.getLevel().registryAccess()));
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data.contains("SlowlightStatus")) {
                Component status = Component.Serializer.fromJson(
                        data.getString("SlowlightStatus"), accessor.getLevel().registryAccess());
                if (status != null) {
                    tooltip.add(status);
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return uid;
        }
    }
    // end branch rest
}
