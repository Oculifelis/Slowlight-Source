package dev.slowlight.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.slowlight.SlowlightWays;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * What a thing becomes on its way between two Waystones.
 *
 * <p>{@code from} and {@code to} are dimension ids, or {@code "any"}. When several recipes fit an item, the one naming
 * more of the route wins: an exact from and to beats one exact end, which beats {@code any -> any}. Each item sent
 * becomes {@code result}'s count of the result, so a stack of 64 glass becomes 64 Starglass.
 *
 * @param days how long the trip takes; 0 keeps the stone's own pace (about a day, three across dimensions)
 */
public record TransitRecipe(Ingredient ingredient, ItemStack result, String from, String to, int days)
        implements Recipe<SingleRecipeInput> {
    public static final String ANY = "any";

    /** How closely this recipe fits a trip; -1 when it doesn't. */
    public int fit(ResourceKey<Level> fromDim, ResourceKey<Level> toDim) {
        int a = end(from, fromDim);
        int b = end(to, toDim);
        return a < 0 || b < 0 ? -1 : a + b;
    }

    private static int end(String wanted, ResourceKey<Level> dim) {
        if (ANY.equals(wanted)) {
            return 0;
        }
        return dim.location().toString().equals(wanted) ? 1 : -1;
    }

    /** The best-fitting route for this item, if any. */
    public static Optional<TransitRecipe> find(RecipeManager recipes, ItemStack stack, Level level,
                                               ResourceKey<Level> fromDim, ResourceKey<Level> toDim) {
        TransitRecipe best = null;
        int bestFit = -1;
        for (RecipeHolder<TransitRecipe> holder : recipes.getRecipesFor(SlowlightWays.TRANSIT_TYPE.get(),
                new SingleRecipeInput(stack), level)) {
            int fit = holder.value().fit(fromDim, toDim);
            if (fit > bestFit) {
                best = holder.value();
                bestFit = fit;
            }
        }
        return Optional.ofNullable(best);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(ingredient);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SlowlightWays.TRANSIT_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return SlowlightWays.TRANSIT_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<TransitRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        private static final MapCodec<TransitRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(TransitRecipe::ingredient),
                ItemStack.CODEC.fieldOf("result").forGetter(TransitRecipe::result),
                Codec.STRING.optionalFieldOf("from", ANY).forGetter(TransitRecipe::from),
                Codec.STRING.optionalFieldOf("to", ANY).forGetter(TransitRecipe::to),
                Codec.intRange(0, 365).optionalFieldOf("days", 0).forGetter(TransitRecipe::days)
        ).apply(instance, TransitRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, TransitRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, TransitRecipe::ingredient,
                ItemStack.STREAM_CODEC, TransitRecipe::result,
                ByteBufCodecs.STRING_UTF8, TransitRecipe::from,
                ByteBufCodecs.STRING_UTF8, TransitRecipe::to,
                ByteBufCodecs.VAR_INT, TransitRecipe::days,
                TransitRecipe::new
        );

        @Override
        public MapCodec<TransitRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TransitRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
