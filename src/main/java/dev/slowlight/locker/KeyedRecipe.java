package dev.slowlight.locker;

import com.mojang.serialization.MapCodec;
import dev.slowlight.SlowlightLockers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/**
 * A shaped recipe with a numbered Key in it: the result takes the key's number and the key stays in the grid.
 * Used for the Lock and for copying a Key. The JSON is a plain shaped recipe with {@code "type": "slowlight:keyed"}.
 */
public class KeyedRecipe extends ShapedRecipe {
    public KeyedRecipe(ShapedRecipe shaped) {
        super(shaped.getGroup(), shaped.category(), shaped.pattern, shaped.getResultItem(null), shaped.showNotification());
    }

    private static Integer number(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(SlowlightLockers.KEY.get()) && KeyItem.number(stack) != null) {
                return KeyItem.number(stack);
            }
        }
        return null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return super.matches(input, level) && number(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = super.assemble(input, registries);
        Integer number = number(input);
        if (number != null) {
            result.set(SlowlightLockers.LOCK_ID.get(), number);
        }
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> left = super.getRemainingItems(input);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(SlowlightLockers.KEY.get()) && KeyItem.number(stack) != null) {
                left.set(i, stack.copyWithCount(1));
                break;
            }
        }
        return left;
    }

    @Override
    public boolean isSpecial() {
        return false;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SlowlightLockers.KEYED.get();
    }

    public static final class Serializer implements RecipeSerializer<KeyedRecipe> {
        private static final MapCodec<KeyedRecipe> CODEC = ShapedRecipe.Serializer.CODEC.xmap(KeyedRecipe::new, r -> r);
        private static final StreamCodec<RegistryFriendlyByteBuf, KeyedRecipe> STREAM_CODEC =
                ShapedRecipe.Serializer.STREAM_CODEC.map(KeyedRecipe::new, r -> r);

        @Override
        public MapCodec<KeyedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, KeyedRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
