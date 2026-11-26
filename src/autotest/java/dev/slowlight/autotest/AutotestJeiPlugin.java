package dev.slowlight.autotest;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
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

    /** Every item that has a JEI info page must still have one; looks each up in the live runtime. */
    static void checkInfoPages() {
        java.util.Set<net.minecraft.world.item.Item> withInfo = new java.util.HashSet<>();
        runtime.getRecipeManager().createRecipeLookup(RecipeTypes.INFORMATION).get()
                .forEach(r -> r.getIngredients().forEach(i -> i.getItemStack().ifPresent(st -> withInfo.add(st.getItem()))));
        Object[][] wanted = {
                {"flower_press", dev.slowlight.SlowlightCrafts.FLOWER_PRESS_ITEM.get()},
                {"pressed_flower", dev.slowlight.SlowlightCrafts.PRESSED_FLOWER.get()},
                {"letter_to_later", dev.slowlight.SlowlightNights.LETTER_TO_LATER.get()},
                {"chorus_pearl", dev.slowlight.SlowlightWays.CHORUS_PEARL.get()},
                {"twin_pearl", dev.slowlight.SlowlightWays.TWIN_PEARL.get()},
                {"half_pearl", dev.slowlight.SlowlightWays.HALF_PEARL.get()},
                {"lanternfruit_peel", dev.slowlight.SlowlightItems.LANTERNFRUIT_PEEL.get()},
        };
        for (Object[] w : wanted) {
            if (withInfo.contains(w[1])) {
                ServerChecks.pass("jei.info." + w[0], "info page found");
            } else {
                ServerChecks.fail("jei.info." + w[0], "no JEI info page");
            }
        }
    }
}
