package dev.slowlight.mixin;

import dev.slowlight.SlowlightItems;
import dev.slowlight.SlowlightPotPatterns;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.DecoratedPotPattern;
import net.minecraft.world.level.block.entity.DecoratedPotPatterns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla maps sherd items to pot patterns in a fixed table; this adds the Patience sherd to it. */
@Mixin(DecoratedPotPatterns.class)
public abstract class DecoratedPotPatternsMixin {
    @Inject(method = "getPatternFromItem", at = @At("HEAD"), cancellable = true)
    private static void slowlight$patienceSherd(Item item, CallbackInfoReturnable<ResourceKey<DecoratedPotPattern>> cir) {
        if (SlowlightItems.PATIENCE_POTTERY_SHERD.isBound() && item == SlowlightItems.PATIENCE_POTTERY_SHERD.get()) {
            cir.setReturnValue(SlowlightPotPatterns.PATIENCE);
        }
    }
}
