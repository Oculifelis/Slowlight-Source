package dev.slowlight.locker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** How many columns and rows one locker holds. A wide locker shows two of these side by side. */
public record LockerSize(int columns, int rows) {
    public static final Codec<LockerSize> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 15).fieldOf("columns").forGetter(LockerSize::columns),
            Codec.intRange(1, 4).fieldOf("rows").forGetter(LockerSize::rows)
    ).apply(i, LockerSize::new));

    public int slots() {
        return columns * rows;
    }
}
