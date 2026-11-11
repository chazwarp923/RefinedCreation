package tech.chazwarp923.refinedcreation;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.LongValue BRIDGE_ENERGY_USAGE = BUILDER
            .comment("Refined Storage energy consumed per tick by the Logistics Bridge.")
            .defineInRange("bridgeEnergyUsage", 8L, 0L, Long.MAX_VALUE);

    public static final ModConfigSpec.IntValue DISPATCHER_MAX_QUEUED_PACKAGES = BUILDER
            .comment("How many packages may wait in a Logistics Dispatcher's Packager before the dispatcher stops",
                    "accepting more autocrafting work. RS retries once the queue drains.")
            .defineInRange("dispatcherMaxQueuedPackages", 10, 1, 1000);

    public static final ModConfigSpec.BooleanValue BRIDGE_AUTOCRAFTING = BUILDER
            .comment("Whether items the RS network can autocraft show up in Create Stock Keepers (as infinite), and",
                    "whether ordering them through a Logistics Bridge starts RS autocrafting tasks.")
            .define("bridgeAutocrafting", true);

    public static final ModConfigSpec.IntValue BRIDGE_CRAFTING_TIMEOUT_TICKS = BUILDER
            .comment("How long a Logistics Bridge holds an order while waiting for RS autocrafting, in ticks.",
                    "After this, whatever is in storage is packaged even if the tasks are still running.")
            .defineInRange("bridgeCraftingTimeoutTicks", 6000, 20, Integer.MAX_VALUE);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
