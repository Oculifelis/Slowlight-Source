package dev.slowlight.autotest;

import dev.slowlight.SlowlightNights;
import dev.slowlight.SlowlightNights.Seal;
import dev.slowlight.block.CheeseWheelBlock;
import dev.slowlight.block.MoondialBlock;
import dev.slowlight.recipe.AgeingRecipe;
import dev.slowlight.recipe.LetterRecipe;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static dev.slowlight.autotest.ServerChecks.check;

/**
 * Play-test checks for the night batch: the new ageing recipes and cellar times, the cheese wheel eaten a wedge at a
 * time, a Letter to Later sealed, refused and opened, the Moondial's state, and the statistics. ServerChecks starts it
 * from setScene; it uses x 74..76, z 24.
 */
final class NightsChecks {
    private static final int Y = -60;
    private static final BlockPos CHEESE = new BlockPos(74, Y, 24);
    private static final BlockPos DIAL = new BlockPos(76, Y, 24);

    private NightsChecks() {
    }

    static void start(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        var recipes = server.getRecipeManager();
        for (String id : List.of("ageing/cheese_wheel", "ageing/ice", "ageing/blue_ice", "ageing/experience_bottle",
                "ageing/phantom_membrane", "ageing/skeleton_skull", "moondial", "letter_to_later")) {
            check("nights.recipe." + id, recipes.byKey(ResourceLocation.fromNamespaceAndPath("slowlight", id)).isPresent(), id);
        }
        recipes.byKey(ResourceLocation.parse("slowlight:ageing/cider")).ifPresent(h -> check("nights.cider_cellar",
                h.value() instanceof AgeingRecipe r && r.cellarDays() == 3, String.valueOf(h.value())));

        check("nights.stats", Stats.CUSTOM.contains(SlowlightNights.TIME_WAITED.get())
                && Stats.CUSTOM.contains(SlowlightNights.LETTERS_OPENED.get()), "custom stats on the statistics screen");

        cheese(level, player);
        letter(level, player);

        level.setBlock(DIAL.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(DIAL, SlowlightNights.MOONDIAL.get().defaultBlockState(), 3);
        BlockState dial = level.getBlockState(DIAL);
        check("nights.moondial.placed", dial.is(SlowlightNights.MOONDIAL.get()) && dial.hasProperty(MoondialBlock.PHASE),
                String.valueOf(dial));
    }

    private static void cheese(ServerLevel level, ServerPlayer player) {
        level.setBlock(CHEESE.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(CHEESE, SlowlightNights.CHEESE_WHEEL.get().defaultBlockState(), 3);
        player.getFoodData().setFoodLevel(2);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(CHEESE), Direction.UP, CHEESE, false);
        int wedges = 0;
        for (int i = 0; i < 3; i++) {
            level.getBlockState(CHEESE).useWithoutItem(level, player, hit);
            BlockState s = level.getBlockState(CHEESE);
            wedges = s.is(SlowlightNights.CHEESE_WHEEL.get()) ? s.getValue(CheeseWheelBlock.WEDGES) : -1;
        }
        check("nights.cheese.wedges", wedges == 3, "after three bites wedges=" + wedges);
        level.getBlockState(CHEESE).useWithoutItem(level, player, hit);
        check("nights.cheese.eaten", level.getBlockState(CHEESE).isAir(), "the last wedge clears the block");
        check("nights.cheese.food", player.getFoodData().getFoodLevel() == 2 + 4 * CheeseWheelBlock.NUTRITION,
                "food " + player.getFoodData().getFoodLevel());
    }

    private static void letter(ServerLevel level, ServerPlayer player) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("To me"), "Oculifelis",
                0, List.of(Filterable.passThrough(Component.literal("Hello again."))), true));
        LetterRecipe recipe = new LetterRecipe(CraftingBookCategory.MISC);
        CraftingInput grid = CraftingInput.of(2, 1, List.of(book, new ItemStack(Items.HONEYCOMB)));
        check("nights.letter.recipe", recipe.matches(grid, level), "book + honeycomb");
        ItemStack letter = recipe.assemble(grid, level.registryAccess());
        check("nights.letter.content", letter.has(DataComponents.WRITTEN_BOOK_CONTENT), "the book's pages carried over");

        player.setItemInHand(InteractionHand.MAIN_HAND, letter);
        letter.use(level, player, InteractionHand.MAIN_HAND); // seals it for the default 7 days
        Seal seal = letter.get(SlowlightNights.SEAL.get());
        check("nights.letter.sealed", seal != null && seal.sealed() && seal.days() == 7, String.valueOf(seal));
        var early = letter.use(level, player, InteractionHand.MAIN_HAND);
        check("nights.letter.not_yet", !early.getResult().consumesAction(), "refused before the day");

        letter.set(SlowlightNights.SEAL.get(), new Seal(7, level.getGameTime() - 1L));
        int before = player.getStats().getValue(Stats.CUSTOM.get(SlowlightNights.LETTERS_OPENED.get()));
        ItemStack out = letter.use(level, player, InteractionHand.MAIN_HAND).getObject();
        check("nights.letter.opened", out.is(Items.WRITTEN_BOOK) && out.has(DataComponents.WRITTEN_BOOK_CONTENT),
                String.valueOf(out));
        check("nights.letter.stat", player.getStats().getValue(Stats.CUSTOM.get(SlowlightNights.LETTERS_OPENED.get())) == before + 1,
                "letters opened counted");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }
}
