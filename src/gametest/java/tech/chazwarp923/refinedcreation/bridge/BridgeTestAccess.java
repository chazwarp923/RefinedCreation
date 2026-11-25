package tech.chazwarp923.refinedcreation.bridge;

/**
 * Lets the benchmarks call the bridge's package-private tick methods. Lives in the gametest source set, which
 * shares this package with the mod in dev runs.
 */
public final class BridgeTestAccess {
    private BridgeTestAccess() {
    }

    public static void serverTick(LogisticsBridgeBlockEntity bridge) {
        bridge.serverTick(bridge.getBlockState());
    }

    public static void craftingTick(BridgeCrafting crafting) {
        crafting.tick();
    }
}
