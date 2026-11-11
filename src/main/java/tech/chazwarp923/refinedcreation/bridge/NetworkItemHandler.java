package tech.chazwarp923.refinedcreation.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceList;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.root.RootStorageListener;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryWrapper;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Presents a Refined Storage network's item storage as an {@link net.neoforged.neoforge.items.IItemHandler}.
 * <p>
 * Every distinct item in the network occupies one virtual slot whose stack count is the network's total amount
 * (clamped to {@code int}), followed by one always-empty slot. Insertions go straight into the network regardless of
 * the slot targeted; extractions pull up to one stack of the slot's item out of the network.
 * <p>
 * This extends Create's {@link VersionedInventoryWrapper} so Create's packagers cache their stock summary and only
 * rescan when the network reports a change, rather than walking every slot on every stock check.
 * <p>
 * The slot layout is rebuilt at most once per game tick so slot indices stay stable while a packager iterates them.
 * Amounts are always read live from the network.
 */
public class NetworkItemHandler extends VersionedInventoryWrapper {
    private final Supplier<StorageNetworkComponent> storageSupplier;
    private final Supplier<Long> gameTimeSupplier;
    private final Actor actor;
    private final RootStorageListener listener = new RootStorageListener() {
        @Override
        public void changed(MutableResourceList.OperationResult change) {
            onNetworkChanged();
        }
    };

    private final List<ItemResource> slots = new ArrayList<>();
    @Nullable
    private StorageNetworkComponent listenedStorage;
    private boolean dirty = true;
    private long lastRebuildTime = Long.MIN_VALUE;
    /**
     * Guards against the network calling back into this handler, e.g. if someone points an RS External Storage at
     * the bridge. Without it, such a setup would recurse forever.
     */
    private boolean busy;
    @Nullable
    private BridgeCrafting crafting;

    /**
     * @param storageSupplier  the network's storage, or {@code null} while the bridge is disconnected or inactive
     * @param gameTimeSupplier the current game time of the bridge's level
     * @param actor            the actor network operations are attributed to
     */
    public NetworkItemHandler(Supplier<StorageNetworkComponent> storageSupplier, Supplier<Long> gameTimeSupplier,
                              Actor actor) {
        // The delegate is never used: every method is overridden.
        super(new ItemStackHandler(0));
        this.storageSupplier = storageSupplier;
        this.gameTimeSupplier = gameTimeSupplier;
        this.actor = actor;
    }

    /**
     * Attaches the change listener to whichever network the bridge currently belongs to. Called every tick by the
     * bridge and before any access, so the handler follows the bridge across network merges/splits.
     */
    public void syncNetwork() {
        StorageNetworkComponent current = storageSupplier.get();
        if (current == listenedStorage) {
            return;
        }
        if (listenedStorage != null) {
            listenedStorage.removeListener(listener);
        }
        if (current != null) {
            current.addListener(listener);
        }
        listenedStorage = current;
        onNetworkChanged();
    }

    /**
     * Detaches from the network. Called when the bridge is removed or unloaded.
     */
    public void detach() {
        if (listenedStorage != null) {
            listenedStorage.removeListener(listener);
            listenedStorage = null;
        }
        slots.clear();
        dirty = true;
        incrementVersion();
    }

    private void onNetworkChanged() {
        dirty = true;
        incrementVersion();
    }

    @Nullable
    private StorageNetworkComponent storage() {
        syncNetwork();
        return listenedStorage;
    }

    private void ensureLayout() {
        StorageNetworkComponent storage = storage();
        if (!dirty) {
            return;
        }
        long now = gameTimeSupplier.get();
        if (now == lastRebuildTime) {
            // Already rebuilt this tick; keep indices stable until the next one.
            return;
        }
        lastRebuildTime = now;
        dirty = false;
        slots.clear();
        if (storage == null) {
            return;
        }
        busy = true;
        try {
            for (ResourceAmount resourceAmount : storage.getAll()) {
                if (resourceAmount.resource() instanceof ItemResource item) {
                    slots.add(item);
                }
            }
        } finally {
            busy = false;
        }
    }

    @Nullable
    private ItemResource resourceAt(int slot) {
        if (slot < 0 || slot >= slots.size()) {
            return null;
        }
        return slots.get(slot);
    }

    private static Action action(boolean simulate) {
        return simulate ? Action.SIMULATE : Action.EXECUTE;
    }

    @Override
    public int getSlots() {
        if (busy) {
            return 0;
        }
        ensureLayout();
        // One trailing empty slot so that slot-hunting inserters always find somewhere to put items.
        return slots.size() + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (busy) {
            return ItemStack.EMPTY;
        }
        ensureLayout();
        StorageNetworkComponent storage = listenedStorage;
        ItemResource resource = resourceAt(slot);
        if (storage == null || resource == null) {
            return ItemStack.EMPTY;
        }
        long amount;
        busy = true;
        try {
            amount = storage.get(resource);
        } finally {
            busy = false;
        }
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        return resource.toItemStack(Math.min(amount, Integer.MAX_VALUE));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (busy || stack.isEmpty()) {
            return stack;
        }
        StorageNetworkComponent storage = storage();
        if (storage == null) {
            return stack;
        }
        long inserted;
        busy = true;
        try {
            inserted = storage.insert(ItemResource.ofItemStack(stack), stack.getCount(), action(simulate), actor);
        } finally {
            busy = false;
        }
        if (inserted <= 0) {
            return stack;
        }
        if (inserted >= stack.getCount()) {
            return ItemStack.EMPTY;
        }
        return stack.copyWithCount(stack.getCount() - (int) inserted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (busy || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ensureLayout();
        StorageNetworkComponent storage = listenedStorage;
        ItemResource resource = resourceAt(slot);
        if (storage == null || resource == null) {
            return ItemStack.EMPTY;
        }
        int maxStackSize = resource.toItemStack().getMaxStackSize();
        long extracted;
        busy = true;
        try {
            extracted = storage.extract(resource, Math.min(amount, maxStackSize), action(simulate), actor);
        } finally {
            busy = false;
        }
        if (extracted <= 0) {
            return ItemStack.EMPTY;
        }
        return resource.toItemStack(extracted);
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !busy && storage() != null;
    }

    /**
     * Not supported: the slots are a view over the network, and overwriting one would void or duplicate items.
     */
    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        // Intentionally a no-op.
    }

    /**
     * @return the bridge's autocrafting support, which the Create mixins reach through the Packager's target inventory
     */
    @Nullable
    public BridgeCrafting getCrafting() {
        return crafting;
    }

    void setCrafting(BridgeCrafting crafting) {
        this.crafting = crafting;
    }

    /**
     * Makes Packagers rebuild their stock summary even though the stored items didn't change.
     */
    void invalidateSummary() {
        incrementVersion();
    }

    /**
     * Inserts directly into the network.
     *
     * @return the amount that was (or would be) inserted
     */
    public long insertDirect(ItemStack stack, boolean simulate) {
        return stack.getCount() - insertItem(0, stack, simulate).getCount();
    }
}
