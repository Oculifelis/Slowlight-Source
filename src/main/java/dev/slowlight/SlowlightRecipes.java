package dev.slowlight;

import dev.slowlight.recipe.AgeingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SlowlightRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, Slowlight.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Slowlight.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<AgeingRecipe>> AGEING_TYPE =
            RECIPE_TYPES.register("ageing", id -> RecipeType.simple(id));

    public static final DeferredHolder<RecipeSerializer<?>, AgeingRecipe.Serializer> AGEING_SERIALIZER =
            RECIPE_SERIALIZERS.register("ageing", () -> AgeingRecipe.Serializer.INSTANCE);

    private SlowlightRecipes() {
    }
}
