package dev.slowlight.compat;

import dev.slowlight.Slowlight;
import dev.slowlight.SlowlightItems;
import dev.slowlight.SlowlightRecipes;
import dev.slowlight.recipe.AgeingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/** Puts the kiln and its ageing recipes in JEI, with the kiln itself as the catalyst. */
@JeiPlugin
public class SlowlightJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AgeingRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new TransitRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        List<RecipeHolder<AgeingRecipe>> recipes =
                level.getRecipeManager().getAllRecipesFor(SlowlightRecipes.AGEING_TYPE.get());
        registration.addRecipes(AgeingRecipeCategory.TYPE, recipes);
        registration.addRecipes(TransitRecipeCategory.TYPE,
                level.getRecipeManager().getAllRecipesFor(dev.slowlight.SlowlightWays.TRANSIT_TYPE.get()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(SlowlightItems.PATIENT_KILN.get(), AgeingRecipeCategory.TYPE);
        registration.addRecipeCatalyst(SlowlightItems.LONG_KILN.get(), AgeingRecipeCategory.TYPE);
        registration.addRecipeCatalyst(SlowlightItems.EMBER_KILN.get(), AgeingRecipeCategory.TYPE);
        registration.addRecipeCatalyst(dev.slowlight.SlowlightWays.WAYSTONE.get(), TransitRecipeCategory.TYPE);
    }
}
