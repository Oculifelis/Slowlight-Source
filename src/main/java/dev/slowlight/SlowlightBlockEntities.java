package dev.slowlight;

import dev.slowlight.block.EmberKilnBlockEntity;
import dev.slowlight.block.KeepsakeUrnBlockEntity;
import dev.slowlight.block.LongKilnBlockEntity;
import dev.slowlight.block.SandglassBlockEntity;
import dev.slowlight.block.PatientKilnBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SlowlightBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Slowlight.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PatientKilnBlockEntity>> PATIENT_KILN =
            BLOCK_ENTITIES.register("patient_kiln", () -> BlockEntityType.Builder
                    .of(PatientKilnBlockEntity::new, SlowlightBlocks.PATIENT_KILN.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LongKilnBlockEntity>> LONG_KILN =
            BLOCK_ENTITIES.register("long_kiln", () -> BlockEntityType.Builder
                    .of(LongKilnBlockEntity::new, SlowlightBlocks.LONG_KILN.get())
                    .build(null));

    // ---- branch rest (begin) ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SandglassBlockEntity>> SANDGLASS =
            BLOCK_ENTITIES.register("sandglass", () -> BlockEntityType.Builder
                    .of(SandglassBlockEntity::new, SlowlightBlocks.SANDGLASS.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KeepsakeUrnBlockEntity>> KEEPSAKE_URN =
            BLOCK_ENTITIES.register("keepsake_urn", () -> BlockEntityType.Builder
                    .of(KeepsakeUrnBlockEntity::new, SlowlightBlocks.KEEPSAKE_URN.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EmberKilnBlockEntity>> EMBER_KILN =
            BLOCK_ENTITIES.register("ember_kiln", () -> BlockEntityType.Builder
                    .of(EmberKilnBlockEntity::new, SlowlightBlocks.EMBER_KILN.get())
                    .build(null));
    // ---- branch rest (end) ----

    private SlowlightBlockEntities() {
    }
}
