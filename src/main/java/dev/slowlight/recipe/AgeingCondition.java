package dev.slowlight.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;

/**
 * What the world around a kiln has to be like for the days to count. While it does not hold, the wait stands still
 * rather than starting over: a batch that needs rain keeps what it has had and goes on when the rain comes back.
 *
 * <p>In JSON it is an optional {@code condition} object on an ageing recipe, every field optional:
 * <pre>{@code "condition": { "sky": "open", "weather": "rain", "time": "night" }}</pre>
 * {@code sky} is {@code open} (nothing but air between the kiln and the sky), {@code covered}, or {@code void}: open to the
 * End's empty sky, which only the End has (nothing above the kiln in a dimension with End skies); {@code weather} is
 * {@code clear}, {@code rain} or {@code thunder} (rain and thunder only count where rain actually falls on the kiln,
 * so under open sky in a biome where it rains); {@code time} is {@code day} or {@code night}. Leaving a field out, or
 * {@code any}, means it does not matter.
 *
 * <p>{@code dimension} (a dimension id, such as {@code minecraft:the_nether}) is different: a recipe that names one
 * only exists in that dimension, and there it comes before a recipe for the same item that names none. So cobblestone
 * can turn mossy in the Overworld and to blackstone in the Nether.
 */
public record AgeingCondition(Sky sky, Weather weather, Time time, Optional<ResourceLocation> dimension) {
    public static final AgeingCondition ANY = new AgeingCondition(Sky.ANY, Weather.ANY, Time.ANY, Optional.empty());

    public AgeingCondition(Sky sky, Weather weather, Time time) {
        this(sky, weather, time, Optional.empty());
    }

    public static final Codec<AgeingCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Sky.CODEC.optionalFieldOf("sky", Sky.ANY).forGetter(AgeingCondition::sky),
            Weather.CODEC.optionalFieldOf("weather", Weather.ANY).forGetter(AgeingCondition::weather),
            Time.CODEC.optionalFieldOf("time", Time.ANY).forGetter(AgeingCondition::time),
            ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(AgeingCondition::dimension)
    ).apply(instance, AgeingCondition::new));

    public static final StreamCodec<ByteBuf, AgeingCondition> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Sky.values()[i], Sky::ordinal), AgeingCondition::sky,
            ByteBufCodecs.VAR_INT.map(i -> Weather.values()[i], Weather::ordinal), AgeingCondition::weather,
            ByteBufCodecs.VAR_INT.map(i -> Time.values()[i], Time::ordinal), AgeingCondition::time,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), AgeingCondition::dimension,
            AgeingCondition::new
    );

    public boolean isAny() {
        return sky == Sky.ANY && weather == Weather.ANY && time == Time.ANY && dimension.isEmpty();
    }

    /** Whether a recipe with this condition exists at all in this level's dimension. */
    public boolean inDimension(Level level) {
        return dimension.isEmpty() || dimension.get().equals(level.dimension().location());
    }

    /** Whether the days count for a kiln at this position right now. */
    public boolean holds(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        if (sky == Sky.VOID) {
            // The End has no sky light, so canSeeSky never holds there; ask the heightmap instead.
            return level.dimensionType().effectsLocation().equals(net.minecraft.world.level.dimension.BuiltinDimensionTypes.END_EFFECTS)
                    && above.getY() >= level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        }
        boolean open = level.canSeeSky(above);
        if (sky == Sky.OPEN && !open || sky == Sky.COVERED && open) {
            return false;
        }
        boolean wet = level.isRainingAt(above);
        switch (weather) {
            case CLEAR -> {
                if (level.isRaining()) {
                    return false;
                }
            }
            case RAIN -> {
                if (!wet) {
                    return false;
                }
            }
            case THUNDER -> {
                if (!wet || !level.isThundering()) {
                    return false;
                }
            }
            default -> { }
        }
        return switch (time) {
            case DAY -> level.isDay();
            case NIGHT -> !level.isDay();
            default -> true;
        };
    }

    /** "Open sky, in the rain, at night": what has to be true, for tooltips, Jade and the recipe browser. */
    public Component describe() {
        List<Component> parts = new ArrayList<>();
        if (sky != Sky.ANY) {
            parts.add(Component.translatable("slowlight.condition.sky." + sky.getSerializedName()));
        }
        if (weather != Weather.ANY) {
            parts.add(Component.translatable("slowlight.condition.weather." + weather.getSerializedName()));
        }
        if (time != Time.ANY) {
            parts.add(Component.translatable("slowlight.condition.time." + time.getSerializedName()));
        }
        dimension.ifPresent(dim -> parts.add(Component.translatableWithFallback(
                "slowlight.condition.dimension." + dim.getNamespace() + "." + dim.getPath(),
                "in " + dim.getPath().replace('_', ' '))));
        Component out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            out = i == 0 ? parts.get(i).copy() : Component.translatable("slowlight.condition.and", out, parts.get(i));
        }
        return out;
    }

    public enum Sky implements StringRepresentable {
        ANY, OPEN, COVERED, VOID;
        public static final Codec<Sky> CODEC = StringRepresentable.fromEnum(Sky::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Weather implements StringRepresentable {
        ANY, CLEAR, RAIN, THUNDER;
        public static final Codec<Weather> CODEC = StringRepresentable.fromEnum(Weather::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Time implements StringRepresentable {
        ANY, DAY, NIGHT;
        public static final Codec<Time> CODEC = StringRepresentable.fromEnum(Time::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
