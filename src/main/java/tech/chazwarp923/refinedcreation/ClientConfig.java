package tech.chazwarp923.refinedcreation;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only preferences, saved in {@code config/refinedcreation-client.toml}.
 */
public class ClientConfig {
    /** How the Logistics Workbench orders the network's stock. */
    public enum WorkbenchSort {
        QUANTITY,
        NAME,
        ID
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<WorkbenchSort> WORKBENCH_SORT = BUILDER
            .comment("How the Logistics Workbench sorts the network's stock. Left-click the sort button right of",
                    "its search bar to change it.")
            .defineEnum("workbenchSort", WorkbenchSort.QUANTITY);

    public static final ModConfigSpec.BooleanValue WORKBENCH_SORT_DESCENDING = BUILDER
            .comment("Whether the Logistics Workbench sorts its stock in descending order. Right-click the sort",
                    "button right of its search bar to change it.")
            .define("workbenchSortDescending", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
