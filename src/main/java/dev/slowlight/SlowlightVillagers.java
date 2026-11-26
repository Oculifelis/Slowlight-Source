package dev.slowlight;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashSet;
import java.util.Set;

/**
 * The Potter, a villager who works at a Patient Kiln. Clay and bricks first, then terracotta and aged goods, and at
 * the top the things only patience makes: the Patience sherd and its trim template. The trades themselves are
 * data, read by {@link SlowlightTrades}.
 */
public final class SlowlightVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Slowlight.MOD_ID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, Slowlight.MOD_ID);

    public static final ResourceKey<PoiType> POTTER_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
            ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "potter"));

    public static final DeferredHolder<PoiType, PoiType> POTTER_POI_TYPE = POI_TYPES.register("potter", () -> {
        // Every state of the Patient Kiln, open or sealed, is the Potter's workstation.
        Set<BlockState> states = new HashSet<>(SlowlightBlocks.PATIENT_KILN.get().getStateDefinition().getPossibleStates());
        return new PoiType(ImmutableSet.copyOf(states), 1, 1);
    });

    public static final DeferredHolder<VillagerProfession, VillagerProfession> POTTER = PROFESSIONS.register("potter",
            () -> new VillagerProfession("potter", holder -> holder.is(POTTER_POI), holder -> holder.is(POTTER_POI),
                    ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_MASON));

    private SlowlightVillagers() {
    }
}
