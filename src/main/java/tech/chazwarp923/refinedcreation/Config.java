package tech.chazwarp923.refinedcreation;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.LongValue BRIDGE_ENERGY_USAGE = BUILDER
            .comment("Refined Storage energy consumed per tick by the Logistics Bridge.")
            .defineInRange("bridgeEnergyUsage", 8L, 0L, Long.MAX_VALUE);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
