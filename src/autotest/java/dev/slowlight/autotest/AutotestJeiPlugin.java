package dev.slowlight.autotest;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/** Only here to get hold of the JEI runtime, so the test can open the Patient Kiln category and photograph it. */
@JeiPlugin
public class AutotestJeiPlugin implements IModPlugin {
    static volatile IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("slowlight_autotest", "jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }
}
