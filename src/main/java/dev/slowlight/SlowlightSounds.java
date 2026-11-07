package dev.slowlight;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's sounds: three records, and the kiln's lid going on and coming off. */
public final class SlowlightSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, Slowlight.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DISC_PATIENT_LIGHT = record("patient_light");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DISC_A_WEEK_IN = record("a_week_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DISC_LANTERN_HOURS = record("lantern_hours");

    // Made from vanilla's own sound files in sounds.json (a barrel's wooden lid, pitched down), so nothing new ships.
    public static final DeferredHolder<SoundEvent, SoundEvent> KILN_SEAL = block("block.patient_kiln.seal");
    public static final DeferredHolder<SoundEvent, SoundEvent> KILN_OPEN = block("block.patient_kiln.open");

    private static DeferredHolder<SoundEvent, SoundEvent> block(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, path)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> record(String name) {
        String path = "music_disc." + name;
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, path)));
    }

    private SlowlightSounds() {
    }
}
