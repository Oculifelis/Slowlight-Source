package dev.slowlight.compat;

import dev.slowlight.Slowlight;
import dev.slowlight.SlowlightItems;
import dev.slowlight.recipe.AgeingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The kiln's page in a recipe browser: what goes in, what comes out, and how long it takes.
 *
 * <p>There is no progress arrow to animate, because the thing being shown takes days. The wait is
 * written out underneath instead, which is the only number that matters here.
 */
public class AgeingRecipeCategory implements IRecipeCategory<RecipeHolder<AgeingRecipe>> {
    public static final RecipeType<RecipeHolder<AgeingRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "ageing"));

    private static final int WIDTH = 120;
    private static final int HEIGHT = 58;

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;

    public AgeingRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(SlowlightItems.PATIENT_KILN.get());
        this.slot = guiHelper.getSlotDrawable();
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<RecipeHolder<AgeingRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("slowlight.jei.ageing");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AgeingRecipe> holder, IFocusGroup focuses) {
        AgeingRecipe recipe = holder.value();
        builder.addInputSlot(1, 9).addIngredients(recipe.ingredient());
        builder.addOutputSlot(99, 9).addItemStack(recipe.result());
    }

    @Override
    public void draw(RecipeHolder<AgeingRecipe> holder, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        slot.draw(graphics, 0, 8);
        slot.draw(graphics, 98, 8);
        arrow.draw(graphics, 48, 9);

        int days = holder.value().days();
        Component label = Component.translatable(
                days == 1 ? "slowlight.jei.day" : "slowlight.jei.days", days);
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, label, (WIDTH - font.width(label)) / 2, 1, 0xFF808080, false);

        // What the world has to be like for the days to count, wrapped if it is long.
        int y = 30;
        if (!holder.value().condition().isAny()) {
            Component needs = Component.translatable("slowlight.jei.needs", holder.value().condition().describe());
            for (var line : font.split(needs, WIDTH)) {
                graphics.drawString(font, line, (WIDTH - font.width(line)) / 2, y, 0xFF5A7FB0, false);
                y += 10;
            }
        }
        // Quicker in a dark cellar, for the drinks that like one.
        int cellar = holder.value().cellarDays();
        if (cellar > 0 && cellar < days) {
            Component c = Component.translatable("slowlight.jei.cellar", cellar);
            for (var line : font.split(c, WIDTH)) {
                graphics.drawString(font, line, (WIDTH - font.width(line)) / 2, y, 0xFF6A5A40, false);
                y += 10;
            }
        }
        // The Long Kiln's wait, which is over a whole stack rather than one; nothing to say for things that do not stack.
        ItemStack[] ins = holder.value().ingredient().getItems();
        if (ins.length > 0 && ins[0].getMaxStackSize() > 1) {
            Component batch = Component.translatable("slowlight.jei.long_kiln", holder.value().batchDays());
            graphics.drawString(font, batch, (WIDTH - font.width(batch)) / 2, Math.max(y, 34) + 2, 0xFF808080, false);
        }
    }
}
