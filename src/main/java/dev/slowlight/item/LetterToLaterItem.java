package dev.slowlight.item;

import dev.slowlight.SlowlightNights;
import dev.slowlight.SlowlightNights.Seal;
import dev.slowlight.recipe.AgeingRecipe;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

/**
 * A signed book folded and waxed shut for later: for a friend, or for yourself in a month. Sneak-use picks how long it
 * keeps; use seals it. Until the day comes it will not open ("Not yet."); after that, use breaks the wax and gives the
 * book back as it was written.
 */
public class LetterToLaterItem extends Item {
    /** The lengths a sneaking use steps through. */
    public static final int[] DAYS = {1, 3, 7, 14, 30, 100, 365};

    public LetterToLaterItem(Properties properties) {
        super(properties);
    }

    public static Seal seal(ItemStack stack) {
        return stack.getOrDefault(SlowlightNights.SEAL.get(), Seal.UNSEALED);
    }

    /** Whole days until it opens, rounding up; 0 once it may be opened. */
    public static long daysLeft(Seal seal, long now) {
        long left = seal.openAt() - now;
        return left <= 0L ? 0L : (left + AgeingRecipe.TICKS_PER_DAY - 1L) / AgeingRecipe.TICKS_PER_DAY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Seal seal = seal(stack);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!seal.sealed()) {
            if (player.isSecondaryUseActive()) {
                int i = 0;
                while (i < DAYS.length && DAYS[i] != seal.days()) {
                    i++;
                }
                int days = DAYS[(i + 1) % DAYS.length];
                stack.set(SlowlightNights.SEAL.get(), new Seal(days, -1L));
                player.displayClientMessage(Component.translatable("item.slowlight.letter_to_later.plan", days), true);
                return InteractionResultHolder.success(stack);
            }
            stack.set(SlowlightNights.SEAL.get(),
                    new Seal(seal.days(), level.getGameTime() + (long) seal.days() * AgeingRecipe.TICKS_PER_DAY));
            level.playSound(null, player.blockPosition(), SoundEvents.HONEYCOMB_WAX_ON, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("item.slowlight.letter_to_later.sealed", seal.days()), true);
            return InteractionResultHolder.success(stack);
        }
        long left = daysLeft(seal, level.getGameTime());
        if (left > 0L) {
            player.displayClientMessage(Component.translatable("item.slowlight.letter_to_later.not_yet", left), true);
            return InteractionResultHolder.fail(stack);
        }
        // The day has come: the wax gives and it is a book again.
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content != null) {
            book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (player instanceof ServerPlayer sp) {
            sp.awardStat(SlowlightNights.LETTERS_OPENED.get());
        }
        return InteractionResultHolder.consume(book);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content != null) {
            tooltip.add(Component.literal(content.title().raw()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("book.byAuthor", content.author()).withStyle(ChatFormatting.GRAY));
        }
        Seal seal = seal(stack);
        Level level = context.level();
        if (!seal.sealed()) {
            tooltip.add(Component.translatable("item.slowlight.letter_to_later.plan", seal.days()).withStyle(ChatFormatting.DARK_GRAY));
        } else if (level != null) {
            long left = daysLeft(seal, level.getGameTime());
            tooltip.add((left > 0L
                    ? Component.translatable("item.slowlight.letter_to_later.not_yet", left)
                    : Component.translatable("item.slowlight.letter_to_later.ready")).withStyle(ChatFormatting.GOLD));
        }
    }
}
