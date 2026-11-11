package dev.slowlight.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.slowlight.SlowlightRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * One thing becoming another, measured in days rather than in fuel.
 *
 * <p>Everything a kiln knows lives in these three fields, so a datapack is the whole authoring
 * story: drop a JSON into {@code data/<pack>/recipe/} and the kiln can make it. KubeJS reads the
 * same recipes through the codec below, and the schema shipped at
 * {@code data/slowlight/kubejs/recipe_schema/ageing.json} gives its scripts a named builder.
 *
 * @param ingredient what goes in; one is taken per finished batch
 * @param result     what comes out
 * @param days       in-world days of waiting, counted on the world clock and not on the player
 * @param longDays   days a Long Kiln takes over a whole stack of the ingredient; 0 means {@link #LONG_FACTOR} times
 *                   {@code days}. Optional in JSON as {@code long_days}, so recipes written before it stay valid.
 * @param condition  what the world has to be like for the days to count (open sky, rain, night...); optional in
 *                   JSON as {@code condition}, and by default the days always count
 * @param cellarDays days it takes instead in a dark cellar (see {@link AgeingCondition#inCellar}); 0, the default,
 *                   means a cellar makes no difference. Optional in JSON as {@code cellar_days}. Every kind of kiln
 *                   scales its own wait by {@code cellarDays / days}.
 */
public record AgeingRecipe(Ingredient ingredient, ItemStack result, int days, int longDays, AgeingCondition condition,
                           int cellarDays) implements Recipe<SingleRecipeInput> {

    public AgeingRecipe(Ingredient ingredient, ItemStack result, int days, int longDays) {
        this(ingredient, result, days, longDays, AgeingCondition.ANY, 0);
    }

    public AgeingRecipe(Ingredient ingredient, ItemStack result, int days, int longDays, AgeingCondition condition) {
        this(ingredient, result, days, longDays, condition, 0);
    }

    /** A kiln's own wait for this recipe, shortened when the recipe likes a cellar and the kiln stands in one. */
    public long scaledForCellar(long ticks, Level level, net.minecraft.core.BlockPos pos) {
        if (cellarDays <= 0 || cellarDays >= days || !AgeingCondition.inCellar(level, pos)) {
            return ticks;
        }
        return Math.max(1L, ticks * cellarDays / days);
    }

    /** Ticks in a Minecraft day. A day is the only unit this recipe speaks in. */
    public static final int TICKS_PER_DAY = 24000;

    /** A Long Kiln takes this many times as long as one item, but over the whole stack at once. */
    public static final int LONG_FACTOR = 3;

    public long ticks() {
        return (long) days * TICKS_PER_DAY;
    }

    /** Days the Long Kiln waits over a batch. */
    public int batchDays() {
        return longDays > 0 ? longDays : days * LONG_FACTOR;
    }

    public long batchTicks() {
        return (long) batchDays() * TICKS_PER_DAY;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item()) && condition.inDimension(level);
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    /** Keeps these out of the vanilla recipe book, which has no tab for them and warns on every login. */
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
        return SlowlightRecipes.AGEING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return SlowlightRecipes.AGEING_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<AgeingRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        private static final MapCodec<AgeingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(AgeingRecipe::ingredient),
                ItemStack.CODEC.fieldOf("result").forGetter(AgeingRecipe::result),
                com.mojang.serialization.Codec.intRange(1, 365).optionalFieldOf("days", 1)
                        .forGetter(AgeingRecipe::days),
                com.mojang.serialization.Codec.intRange(0, 3650).optionalFieldOf("long_days", 0)
                        .forGetter(AgeingRecipe::longDays),
                AgeingCondition.CODEC.optionalFieldOf("condition", AgeingCondition.ANY)
                        .forGetter(AgeingRecipe::condition),
                com.mojang.serialization.Codec.intRange(0, 365).optionalFieldOf("cellar_days", 0)
                        .forGetter(AgeingRecipe::cellarDays)
        ).apply(instance, AgeingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, AgeingRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, AgeingRecipe::ingredient,
                ItemStack.STREAM_CODEC, AgeingRecipe::result,
                ByteBufCodecs.VAR_INT, AgeingRecipe::days,
                ByteBufCodecs.VAR_INT, AgeingRecipe::longDays,
                AgeingCondition.STREAM_CODEC, AgeingRecipe::condition,
                ByteBufCodecs.VAR_INT, AgeingRecipe::cellarDays,
                AgeingRecipe::new
        );

        @Override
        public MapCodec<AgeingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, AgeingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
