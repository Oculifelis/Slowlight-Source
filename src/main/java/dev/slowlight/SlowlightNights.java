package dev.slowlight;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.slowlight.block.CheeseWheelBlock;
import dev.slowlight.block.MoondialBlock;
import dev.slowlight.item.LetterToLaterItem;
import dev.slowlight.recipe.LetterRecipe;
import io.netty.buffer.ByteBuf;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Night and keeping: the Moondial, the cheese wheel, the Letter to Later, the moths round a lit lantern, and the
 * statistics that count how long a player has waited.
 */
public final class SlowlightNights {
    public static final DeferredRegister<ResourceLocation> STATS = DeferredRegister.create(Registries.CUSTOM_STAT, Slowlight.MOD_ID);
    /** Ticks of ageing a player took the finished work of; shown as time. */
    public static final DeferredHolder<ResourceLocation, ResourceLocation> TIME_WAITED = STATS.register("time_waited", id -> id);
    public static final DeferredHolder<ResourceLocation, ResourceLocation> BATCHES_FINISHED = STATS.register("batches_finished", id -> id);
    public static final DeferredHolder<ResourceLocation, ResourceLocation> URNS_OPENED = STATS.register("urns_opened", id -> id);
    public static final DeferredHolder<ResourceLocation, ResourceLocation> LETTERS_OPENED = STATS.register("letters_opened", id -> id);

    /** A hundred days of waiting, in ticks. */
    public static final long HUNDRED_DAYS = 100L * dev.slowlight.recipe.AgeingRecipe.TICKS_PER_DAY;
    private static final ResourceLocation HUNDRED_DAYS_ADV = ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "hundred_days");

    public static final DeferredBlock<Block> MOONDIAL = SlowlightBlocks.BLOCKS.register("moondial",
            () -> new MoondialBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.DEEPSLATE)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> MOONDIAL_ITEM = SlowlightItems.ITEMS.registerSimpleBlockItem(MOONDIAL);

    public static final DeferredBlock<Block> CHEESE_WHEEL = SlowlightBlocks.BLOCKS.register("cheese_wheel",
            () -> new CheeseWheelBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .pushReaction(PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> CHEESE_WHEEL_ITEM = SlowlightItems.ITEMS.register("cheese_wheel",
            () -> new BlockItem(CHEESE_WHEEL.get(), new Item.Properties().stacksTo(16)));

    /** How long a Letter to Later stays shut, and when it opens (-1 while it is still being written). */
    public record Seal(int days, long openAt) {
        public static final Seal UNSEALED = new Seal(7, -1L);
        public static final Codec<Seal> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 3650).fieldOf("days").forGetter(Seal::days),
                Codec.LONG.optionalFieldOf("open_at", -1L).forGetter(Seal::openAt)
        ).apply(i, Seal::new));
        public static final StreamCodec<ByteBuf, Seal> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Seal::days, ByteBufCodecs.VAR_LONG, Seal::openAt, Seal::new);

        public boolean sealed() {
            return openAt >= 0L;
        }
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Seal>> SEAL =
            SlowlightCrafts.COMPONENTS.registerComponentType("seal", b -> b.persistent(Seal.CODEC).networkSynchronized(Seal.STREAM_CODEC));

    public static final DeferredItem<LetterToLaterItem> LETTER_TO_LATER = SlowlightItems.ITEMS.register("letter_to_later",
            () -> new LetterToLaterItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<LetterRecipe>> LETTER_RECIPE =
            SlowlightRecipes.RECIPE_SERIALIZERS.register("letter_to_later", () -> new SimpleCraftingRecipeSerializer<>(LetterRecipe::new));

    /** Moths: they come to a lit Slowlight Lantern, and to ripe Lanternfruit, after dark. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MOTH =
            SlowlightWays.PARTICLES.register("moth", () -> new SimpleParticleType(false));

    /** Makes the statistics show on the statistics screen; run once at common setup. */
    static void registerStats() {
        Stats.CUSTOM.get(TIME_WAITED.get(), StatFormatter.TIME);
        Stats.CUSTOM.get(BATCHES_FINISHED.get(), StatFormatter.DEFAULT);
        Stats.CUSTOM.get(URNS_OPENED.get(), StatFormatter.DEFAULT);
        Stats.CUSTOM.get(LETTERS_OPENED.get(), StatFormatter.DEFAULT);
    }

    /** Credits a player with finished work they took from a kiln: the waiting it took, and how many batches. */
    public static void creditWaiting(Player player, long ticks, int batches) {
        if (!(player instanceof ServerPlayer sp) || ticks <= 0L && batches <= 0) {
            return;
        }
        sp.awardStat(TIME_WAITED.get(), (int) Math.min(Integer.MAX_VALUE, ticks));
        sp.awardStat(BATCHES_FINISHED.get(), batches);
        if (sp.getStats().getValue(Stats.CUSTOM.get(TIME_WAITED.get())) >= HUNDRED_DAYS) {
            award(sp, HUNDRED_DAYS_ADV, "waited");
        }
    }

    public static void award(ServerPlayer player, ResourceLocation id, String criterion) {
        AdvancementHolder adv = player.server.getAdvancements().get(id);
        if (adv != null) {
            player.getAdvancements().award(adv, criterion);
        }
    }

    static void fillTab(CreativeModeTab.Output output) {
        output.accept(MOONDIAL_ITEM.get());
        output.accept(CHEESE_WHEEL_ITEM.get());
        output.accept(LETTER_TO_LATER.get());
    }

    /** Loads the class, so everything above is registered before the registries close. */
    static void init() {
    }

    private SlowlightNights() {
    }
}
