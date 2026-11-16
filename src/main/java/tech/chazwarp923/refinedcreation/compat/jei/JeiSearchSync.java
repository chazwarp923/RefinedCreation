package tech.chazwarp923.refinedcreation.compat.jei;

import javax.annotation.Nullable;

import mezz.jei.api.runtime.IJeiRuntime;

/**
 * Access to JEI's search bar, for syncing it with the Logistics Workbench's search, as Create's Stock Keeper does.
 * <p>
 * Only touch this class after checking that JEI is loaded: it refers to JEI's API.
 */
public final class JeiSearchSync {
    @Nullable
    private static IJeiRuntime runtime;

    private JeiSearchSync() {
    }

    static void setRuntime(@Nullable IJeiRuntime runtime) {
        JeiSearchSync.runtime = runtime;
    }

    /**
     * @return JEI's search text, or {@code null} if JEI isn't ready
     */
    @Nullable
    public static String getFilterText() {
        return runtime == null ? null : runtime.getIngredientFilter().getFilterText();
    }

    public static void setFilterText(String text) {
        if (runtime != null && !text.equals(runtime.getIngredientFilter().getFilterText())) {
            runtime.getIngredientFilter().setFilterText(text);
        }
    }

    /**
     * @return whether the player is typing in JEI's search bar
     */
    public static boolean isTyping() {
        return runtime != null && runtime.getIngredientListOverlay().hasKeyboardFocus();
    }
}
