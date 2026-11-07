package dev.slowlight;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.DecoratedPotPattern;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Patience pottery pattern. The registry entry only names the texture (under entity/decorated_pot/); which sherd
 * stamps it is vanilla's hardcoded item map, which {@link dev.slowlight.mixin.DecoratedPotPatternsMixin} extends.
 */
public final class SlowlightPotPatterns {
    public static final DeferredRegister<DecoratedPotPattern> PATTERNS =
            DeferredRegister.create(Registries.DECORATED_POT_PATTERN, Slowlight.MOD_ID);

    public static final ResourceKey<DecoratedPotPattern> PATIENCE = ResourceKey.create(
            Registries.DECORATED_POT_PATTERN, ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "patience"));

    static {
        PATTERNS.register("patience", () -> new DecoratedPotPattern(
                ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "patience_pottery_pattern")));
    }

    private SlowlightPotPatterns() {
    }
}
