package dev.slowlight.autotest.mixin;

import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The test client never opens the audio device. The sound engine stays unloaded, so nothing can play, whatever
 * options.txt says, and the device never clicks on open or close.
 */
@Mixin(SoundEngine.class)
public class NoSoundMixin {
    @Inject(method = "loadLibrary", at = @At("HEAD"), cancellable = true)
    private void slowlightAutotest$noSound(CallbackInfo ci) {
        ci.cancel();
    }
}
