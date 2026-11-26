package dev.slowlight.autotest.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The hidden test client never grabs the mouse: joining a world would otherwise lock the cursor to its window. */
@Mixin(MouseHandler.class)
public class NoGrabMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void slowlightAutotest$noGrab(CallbackInfo ci) {
        ci.cancel();
    }
}
