package com.example.blackholeport;

import net.minecraftforge.common.ForgeConfigSpec;

/** Defaults match the original mod. The /blackhole commands change the live values, not this file. */
public class BlackHoleConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue SIZE_LIMIT;
    public static final ForgeConfigSpec.BooleanValue FOLLOW;
    public static final ForgeConfigSpec.DoubleValue FOLLOW_SPEED;
    public static final ForgeConfigSpec.BooleanValue GROW;
    public static final ForgeConfigSpec.DoubleValue GROW_SPEED;
    public static final ForgeConfigSpec.BooleanValue PULL;
    public static final ForgeConfigSpec.DoubleValue PULL_SPEED;
    public static final ForgeConfigSpec.BooleanValue DAMAGE;
    public static final ForgeConfigSpec.IntValue COUNTDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue BLOCKS_CLEARED_PER_TICK;
    public static final ForgeConfigSpec.IntValue BLOCKS_SCANNED_PER_TICK;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("black_hole");
        SIZE_LIMIT = b.comment("Largest size (radius in blocks) the hole can grow to.")
                .defineInRange("limit", 64, 1, 64);
        FOLLOW = b.comment("Whether the hole drifts toward the nearest survival player.")
                .define("follow", true);
        FOLLOW_SPEED = b.comment("Blocks per tick it moves toward its target (the original default is tiny).")
                .defineInRange("followSpeed", 0.00125, 0.0, 10.0);
        GROW = b.comment("Whether the hole grows over time.")
                .define("grow", true);
        GROW_SPEED = b.comment("Size is multiplied by (1 + this) every tick. 3.851E-5 doubles it about every 15 minutes.")
                .defineInRange("growSpeed", 3.851E-5, 0.0, 0.1);
        PULL = b.comment("Whether it pulls in and kills players and entities.")
                .define("pull", true);
        PULL_SPEED = b.comment("Pull strength. Pull falls off as pullSpeed ^ (0.6 * distance / size).")
                .defineInRange("pullSpeed", 0.125, 0.0, 4.0);
        DAMAGE = b.comment("Whether it destroys terrain (clears a sphere and peels the surface).")
                .define("damage", true);
        COUNTDOWN_TICKS = b.comment("Ticks after spawning before it does anything (100 = 5 seconds).")
                .defineInRange("countdownTicks", 100, 0, 72000);
        b.pop();

        b.push("performance");
        BLOCKS_CLEARED_PER_TICK = b.comment(
                        "Most blocks the clearing sphere deletes per tick. The original deletes the whole sphere at once;",
                        "this cap keeps big holes from freezing the server. Raise it to clear faster.")
                .defineInRange("blocksClearedPerTick", 100, 1, 1000000);
        BLOCKS_SCANNED_PER_TICK = b.comment("Block positions checked per tick while clearing, nearest first.")
                .defineInRange("blocksScannedPerTick", 6000, 100, 1000000);
        b.pop();

        SPEC = b.build();
    }
}
