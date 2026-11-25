package tech.chazwarp923.refinedcreation.bridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceList;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.root.RootStorageListener;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
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
 * Each slot's stack is cached until the network reports a change to that item, so a Packager rescanning thousands
 * of slots after one change only asks the network about the item that changed. The slot layout only changes when an
 * item type joins the network, and then at most once per game tick, so slot indices stay stable while a Packager
 * iterates them. An item that leaves the network keeps an empty slot until the next rebuild.
 * <p>
 * While a Packager packages an order out of the bridge, {@link #packageOrder} narrows extraction to the ordered
 * items. The Packager tries every slot for each of a package's nine slots, so without this a large network would
 * cost thousands of simulated network extractions per package.
 */
public class NetworkItemHandler extends VersionedInventoryWrapper {
    private final Supplier<StorageNetworkComponent> storageSupplier;
    private final Supplier<Long> gameTimeSupplier;
    private final Actor actor;
    private final RootStorageListener listener = new RootStorageListener() {
        @Override
        public void changed(MutableResourceList.OperationResult change) {
            onResourceChanged(change);
        }
    };

    private final List<ItemResource> slots = new ArrayList<>();
    private final Map<ItemResource, Integer> slotIndex = new HashMap<>();
    /**
     * Per slot: what {@link #getStackInSlot} returns, or {@code null} until it is next read from the network.
     */
    private ItemStack[] stacks = new ItemStack[0];
    /**
     * Per slot: a one-item stack of the slot's item, made on first use, to copy extraction results from.
     */
    private ItemStack[] prototypes = new ItemStack[0];
    @Nullable
    private StorageNetworkComponent listenedStorage;
    private boolean layoutDirty = true;
    private int layoutVersion;
    private long lastRebuildTime = Long.MIN_VALUE;
    private long lastSyncTime = Long.MIN_VALUE;
    /**
     * The items of the order being packaged, or {@code null} if extraction is unrestricted.
     */
    @Nullable
    private Set<ItemResource> orderFilter;
    /**
     * Per slot: whether its item is in {@link #orderFilter}, for the layout {@link #orderSlotsVersion}.
     */
    private boolean[] orderSlots = new boolean[0];
    private int orderSlotsVersion = -1;
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
        invalidateAll();
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
        slotIndex.clear();
        stacks = new ItemStack[0];
        prototypes = new ItemStack[0];
        layoutVersion++;
        invalidateAll();
    }

    /**
     * Forgets every cached stack and rebuilds the layout on next access, e.g. after switching networks.
     */
    private void invalidateAll() {
        layoutDirty = true;
        Arrays.fill(stacks, null);
        incrementVersion();
    }

    /**
     * Called by the network for every change to its contents.
     */
    private void onResourceChanged(MutableResourceList.OperationResult change) {
        // Fluids and other resources don't appear in the item view.
        if (!(change.resource() instanceof ItemResource item)) {
            return;
        }
        Integer slot = slotIndex.get(item);
        if (slot != null) {
            stacks[slot] = null;
        } else if (change.available()) {
            // A new item type needs a slot.
            layoutDirty = true;
        }
        incrementVersion();
    }

    /**
     * @return the network's storage. The network is looked up at most once per tick, because Packagers call into
     * this handler once per slot.
     */
    @Nullable
    private StorageNetworkComponent storage() {
        long now = gameTimeSupplier.get();
        if (now != lastSyncTime) {
            lastSyncTime = now;
            syncNetwork();
        }
        return listenedStorage;
    }

    private void ensureLayout() {
        long now = gameTimeSupplier.get();
        if (now != lastSyncTime) {
            lastSyncTime = now;
            syncNetwork();
        }
        if (!layoutDirty || now == lastRebuildTime) {
            // Rebuilt at most once per tick, so indices stay stable until the next one.
            return;
        }
        lastRebuildTime = now;
        layoutDirty = false;
        Map<ItemResource, ItemStack> oldPrototypes = new HashMap<>();
        for (int i = 0; i < slots.size(); i++) {
            if (prototypes[i] != null) {
                oldPrototypes.put(slots.get(i), prototypes[i]);
            }
        }
        slots.clear();
        slotIndex.clear();
        StorageNetworkComponent storage = listenedStorage;
        if (storage != null) {
            busy = true;
            try {
                for (ResourceAmount resourceAmount : storage.getAll()) {
                    if (resourceAmount.resource() instanceof ItemResource item) {
                        slotIndex.put(item, slots.size());
                        slots.add(item);
                    }
                }
            } finally {
                busy = false;
            }
        }
        stacks = new ItemStack[slots.size()];
        prototypes = new ItemStack[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            prototypes[i] = oldPrototypes.get(slots.get(i));
        }
        layoutVersion++;
    }

    @Nullable
    private ItemResource resourceAt(int slot) {
        if (slot < 0 || slot >= slots.size()) {
            return null;
        }
        return slots.get(slot);
    }

    /**
     * @return a one-item stack of the slot's item. Never hand this out; copy it.
     */
    private ItemStack prototype(int slot) {
        ItemStack prototype = prototypes[slot];
        if (prototype == null) {
            prototype = slots.get(slot).toItemStack(1);
            prototypes[slot] = prototype;
        }
        return prototype;
    }

    /**
     * @return whether the order being packaged includes the slot's item. Only valid while {@link #orderFilter} is
     * set.
     */
    private boolean orderIncludes(int slot) {
        if (orderSlotsVersion != layoutVersion) {
            orderSlots = new boolean[slots.size()];
            for (int i = 0; i < slots.size(); i++) {
                orderSlots[i] = orderFilter.contains(slots.get(i));
            }
            orderSlotsVersion = layoutVersion;
        }
        return slot >= 0 && slot < orderSlots.length && orderSlots[slot];
    }

    private static Action action(boolean simulate) {
        return simulate ? Action.SIMULATE : Action.EXECUTE;
    }

    @Override
    public int getSlots() {
        if (busy) {
            return 0;
        }
        // While packaging, the layout is settled for the tick; the Packager asks for the size on every slot.
        if (orderFilter == null) {
            ensureLayout();
        }
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
        // Callers must not modify the returned stack (see IItemHandler#getStackInSlot), so it can be cached.
        ItemStack cached = stacks[slot];
        if (cached != null) {
            return cached;
        }
        long amount;
        busy = true;
        try {
            amount = storage.get(resource);
        } finally {
            busy = false;
        }
        cached = amount <= 0
                ? ItemStack.EMPTY
                : prototype(slot).copyWithCount((int) Math.min(amount, Integer.MAX_VALUE));
        stacks[slot] = cached;
        return cached;
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
        if (orderFilter != null) {
            // The Packager tries every slot for each of a package's slots; answer the others without the network.
            if (!orderIncludes(slot)) {
                return ItemStack.EMPTY;
            }
        } else {
            ensureLayout();
        }
        StorageNetworkComponent storage = listenedStorage;
        ItemResource resource = resourceAt(slot);
        if (storage == null || resource == null) {
            return ItemStack.EMPTY;
        }
        int maxStackSize = prototype(slot).getMaxStackSize();
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
        return prototype(slot).copyWithCount((int) extracted);
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
     * @return the handler of the bridge this Packager targets, or {@code null} if it doesn't target one
     */
    @Nullable
    public static NetworkItemHandler forPackager(@Nullable PackagerBlockEntity packager) {
        if (packager == null || packager.targetInventory == null) {
            return null;
        }
        return packager.targetInventory.getInventory() instanceof NetworkItemHandler handler ? handler : null;
    }

    /**
     * Runs {@code packaging} with extraction limited to the items in {@code requests}. The Packager skips every
     * other item anyway, so this only saves it from asking the network about them.
     */
    public void packageOrder(List<PackagingRequest> requests, Runnable packaging) {
        Set<ItemResource> filter = new HashSet<>();
        for (PackagingRequest request : requests) {
            filter.add(ItemResource.ofItemStack(request.item()));
        }
        Set<ItemResource> previous = orderFilter;
        boolean[] previousSlots = orderSlots;
        int previousVersion = orderSlotsVersion;
        // Settle the layout for this tick first: while the filter is set, the Packager's calls skip that check.
        ensureLayout();
        orderFilter = filter;
        orderSlotsVersion = -1;
        try {
            packaging.run();
        } finally {
            orderFilter = previous;
            orderSlots = previousSlots;
            orderSlotsVersion = previousVersion;
        }
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
