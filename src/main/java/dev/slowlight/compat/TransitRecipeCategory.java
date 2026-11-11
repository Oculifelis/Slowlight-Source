package dev.slowlight.compat;

import dev.slowlight.Slowlight;
import dev.slowlight.SlowlightWays;
import dev.slowlight.recipe.TransitRecipe;
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
import net.minecraft.world.item.crafting.RecipeHolder;

/** The Waystone's page in a recipe browser: what is sent, what arrives, and between which dimensions. */
public class TransitRecipeCategory implements IRecipeCategory<RecipeHolder<TransitRecipe>> {
    public static final RecipeType<RecipeHolder<TransitRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "transit"));

    private static final int WIDTH = 120;
    private static final int HEIGHT = 58;

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;

    public TransitRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(SlowlightWays.WAYSTONE.get());
        this.slot = guiHelper.getSlotDrawable();
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<RecipeHolder<TransitRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("slowlight.jei.transit");
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
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<TransitRecipe> holder, IFocusGroup focuses) {
        TransitRecipe recipe = holder.value();
        builder.addInputSlot(1, 9).addIngredients(recipe.ingredient());
        builder.addOutputSlot(99, 9).addItemStack(recipe.result());
    }

    @Override
    public void draw(RecipeHolder<TransitRecipe> holder, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        slot.draw(graphics, 0, 8);
        slot.draw(graphics, 98, 8);
        arrow.draw(graphics, 48, 9);
        TransitRecipe recipe = holder.value();
        Font font = Minecraft.getInstance().font;
        Component route = Component.translatable("slowlight.jei.route", place(recipe.from()), place(recipe.to()));
        int y = 30;
        for (var line : font.split(route, WIDTH)) {
            graphics.drawString(font, line, (WIDTH - font.width(line)) / 2, y, 0xFF5A7FB0, false);
            y += 10;
        }
        if (recipe.days() > 0) {
            Component extra = Component.translatable("slowlight.jei.transit_days", recipe.days());
            graphics.drawString(font, extra, (WIDTH - font.width(extra)) / 2, 1, 0xFF808080, false);
        }
    }

    /** "the End", "anywhere", or a modded dimension's id as written. */
    private static Component place(String dim) {
        String key = "slowlight.jei.place." + dim.replace(':', '.');
        return Component.translatableWithFallback(key, dim);
    }
}
