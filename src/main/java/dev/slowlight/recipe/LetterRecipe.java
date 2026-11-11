package dev.slowlight.recipe;

import dev.slowlight.SlowlightNights;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** A signed book and a honeycomb, anywhere in the grid: a Letter to Later holding that book. */
public class LetterRecipe extends CustomRecipe {
    public LetterRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !book(input).isEmpty();
    }

    /** The one written book, if the grid holds exactly that and one honeycomb; otherwise empty. */
    private static ItemStack book(CraftingInput input) {
        ItemStack book = ItemStack.EMPTY;
        int wax = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(Items.WRITTEN_BOOK) && book.isEmpty()) {
                book = stack;
            } else if (stack.is(Items.HONEYCOMB)) {
                wax++;
            } else {
                return ItemStack.EMPTY;
            }
        }
        return wax == 1 ? book : ItemStack.EMPTY;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack book = book(input);
        if (book.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack letter = new ItemStack(SlowlightNights.LETTER_TO_LATER.get());
        WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content != null) {
            letter.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        }
        return letter;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SlowlightNights.LETTER_RECIPE.get();
    }
}
