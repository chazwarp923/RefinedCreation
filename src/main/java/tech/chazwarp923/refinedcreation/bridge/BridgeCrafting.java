package tech.chazwarp923.refinedcreation.bridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.autocrafting.status.TaskStatus;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;

import tech.chazwarp923.refinedcreation.Config;
import tech.chazwarp923.refinedcreation.RefinedCreation;

/**
 * Lets Create's logistics network order items that the bridge's RS network can autocraft.
 * <p>
 * The bridge advertises every item RS has a pattern for as infinitely available (shown as ∞ in Stock Keepers),
 * so Create routes orders for them to the bridge's Packager. When an order arrives that RS can't fill from storage,
 * this starts an RS task for the shortfall and holds the Packager's requests until every task has finished or been
 * cancelled. Then it hands them back to the Packager, which packages whatever is in storage at that point.
 * <p>
 * The hooks into Create live in {@code PackagerBlockEntityMixin} and {@code PackagerLinkBlockEntityMixin}. Held
 * orders are kept in memory only; if the bridge unloads while an order is held, the order is dropped. Its RS tasks
 * keep running, and their output stays in the network.
 */
public class BridgeCrafting {
    private static final int CRAFTABLES_REFRESH_INTERVAL = 20;
    private static final int TASK_CHECK_INTERVAL = 10;

    private final Supplier<Network> networkSupplier;
    private final Supplier<Long> gameTimeSupplier;
    private final Actor actor;
    private final NetworkItemHandler itemHandler;

    private final List<HeldOrder> heldOrders = new ArrayList<>();
    private Set<ItemResource> craftables = Set.of();
    private int craftablesVersion;
    private boolean releasing;
    private int ticks;

    // Cache for withCraftables, keyed by the identity of the Packager's (itself cached) summary.
    @Nullable
    private InventorySummary cachedBase;
    private int cachedVersion = -1;
    @Nullable
    private InventorySummary cachedAugmented;

    BridgeCrafting(Supplier<Network> networkSupplier, Supplier<Long> gameTimeSupplier, Actor actor,
                   NetworkItemHandler itemHandler) {
        this.networkSupplier = networkSupplier;
        this.gameTimeSupplier = gameTimeSupplier;
        this.actor = actor;
        this.itemHandler = itemHandler;
    }

    /**
     * @return the crafting handler of the bridge this Packager targets, or {@code null} if it doesn't target one
     */
    @Nullable
    public static BridgeCrafting forPackager(@Nullable PackagerBlockEntity packager) {
        if (packager == null || packager.targetInventory == null) {
            return null;
        }
        return packager.targetInventory.getInventory() instanceof NetworkItemHandler handler
                ? handler.getCrafting()
                : null;
    }

    /**
     * @return {@code base} plus every craftable item at {@link BigItemStack#INF}. {@code base} itself is never
     * modified, because the Packager's restock gauges read it directly and must keep seeing real counts.
     */
    public InventorySummary withCraftables(InventorySummary base) {
        if (craftables.isEmpty()) {
            return base;
        }
        if (base == cachedBase && craftablesVersion == cachedVersion && cachedAugmented != null) {
            return cachedAugmented;
        }
        InventorySummary augmented = new InventorySummary();
        augmented.add(base);
        for (ItemResource craftable : craftables) {
            // Adding INF on top of a real count stays within int range and still reads as infinite.
            augmented.add(craftable.toItemStack(1), BigItemStack.INF);
        }
        cachedBase = base;
        cachedVersion = craftablesVersion;
        cachedAugmented = augmented;
        return augmented;
    }

    /**
     * Called before a Packager packages a batch of requests out of the bridge.
     *
     * @return {@code true} if RS tasks were started and the batch is now held; the caller must then not package it
     */
    public boolean holdIfCrafting(PackagerBlockEntity packager, List<PackagingRequest> requests) {
        if (releasing || requests.isEmpty() || !Config.BRIDGE_AUTOCRAFTING.getAsBoolean()) {
            return false;
        }
        Network network = networkSupplier.get();
        if (network == null) {
            return false;
        }
        AutocraftingNetworkComponent autocrafting = network.getComponent(AutocraftingNetworkComponent.class);
        StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);

        Map<ItemResource, Long> requested = new HashMap<>();
        for (PackagingRequest request : requests) {
            if (request.getCount() > 0) {
                requested.merge(ItemResource.ofItemStack(request.item()), (long) request.getCount(), Long::sum);
            }
        }

        Set<TaskId> tasks = new HashSet<>();
        Map<ItemResource, Long> fromStock = new HashMap<>();
        for (Map.Entry<ItemResource, Long> entry : requested.entrySet()) {
            ItemResource resource = entry.getKey();
            long amount = entry.getValue();
            // Stock that earlier held orders are counting on is not available to this one.
            long available = Math.max(0, storage.get(resource) - reservedFromStock(resource));
            long shortfall = amount - available;
            fromStock.put(resource, Math.min(amount, available));
            if (shortfall <= 0 || !craftables.contains(resource)) {
                continue;
            }
            autocrafting.startTask(resource, shortfall, actor, false, CancellationToken.NONE)
                    .ifPresent(tasks::add);
        }
        if (tasks.isEmpty()) {
            // Nothing to wait for; Create packages what is in stock, as it would for any inventory.
            return false;
        }
        heldOrders.add(new HeldOrder(packager, new ArrayList<>(requests), tasks, fromStock,
                gameTimeSupplier.get()));
        return true;
    }

    private long reservedFromStock(ItemResource resource) {
        long reserved = 0;
        for (HeldOrder order : heldOrders) {
            reserved += order.fromStock().getOrDefault(resource, 0L);
        }
        return reserved;
    }

    void tick() {
        ticks++;
        if (ticks % CRAFTABLES_REFRESH_INTERVAL == 0) {
            refreshCraftables();
        }
        if (!heldOrders.isEmpty() && ticks % TASK_CHECK_INTERVAL == 0) {
            releaseFinishedOrders();
        }
    }

    private void refreshCraftables() {
        Set<ItemResource> current = new HashSet<>();
        Network network = networkSupplier.get();
        if (network != null && Config.BRIDGE_AUTOCRAFTING.getAsBoolean()) {
            for (ResourceKey output : network.getComponent(AutocraftingNetworkComponent.class).getOutputs()) {
                if (output instanceof ItemResource item) {
                    current.add(item);
                }
            }
        }
        if (!current.equals(craftables)) {
            craftables = Set.copyOf(current);
            craftablesVersion++;
            // Make the Packager rebuild its summary so Stock Keepers pick up the change.
            itemHandler.invalidateSummary();
        }
    }

    private void releaseFinishedOrders() {
        Set<TaskId> running = new HashSet<>();
        Network network = networkSupplier.get();
        if (network != null) {
            for (TaskStatus status : network.getComponent(AutocraftingNetworkComponent.class).getStatuses()) {
                running.add(status.info().id());
            }
        }
        long now = gameTimeSupplier.get();
        long timeout = Config.BRIDGE_CRAFTING_TIMEOUT_TICKS.getAsInt();

        // Copy first: releasing an order may re-enter holdIfCrafting through the Packager.
        List<HeldOrder> toRelease = new ArrayList<>();
        Iterator<HeldOrder> iterator = heldOrders.iterator();
        while (iterator.hasNext()) {
            HeldOrder order = iterator.next();
            if (order.packager().isRemoved()) {
                iterator.remove();
                continue;
            }
            boolean done = order.tasks().stream().noneMatch(running::contains);
            boolean timedOut = now - order.heldSince() >= timeout;
            if (done || timedOut) {
                if (timedOut && !done) {
                    RefinedCreation.LOGGER.debug("Releasing a held order after {} ticks; RS tasks still running",
                            timeout);
                }
                iterator.remove();
                toRelease.add(order);
            }
        }
        for (HeldOrder order : toRelease) {
            release(order);
        }
    }

    private void release(HeldOrder order) {
        PackagerBlockEntity packager = order.packager();
        List<PackagingRequest> requests = order.requests();
        releasing = true;
        try {
            // Mirrors LogisticsManager.performPackageRequests.
            for (int i = 0; i < 100 && !requests.isEmpty(); i++) {
                packager.attemptToSend(requests);
            }
        } finally {
            releasing = false;
        }
        packager.triggerStockCheck();
        packager.notifyUpdate();
    }

    /**
     * Drops held orders. Called when the bridge is removed or unloaded.
     */
    void clear() {
        heldOrders.clear();
    }

    /**
     * @return how many orders are currently waiting on RS tasks (for tests and debugging)
     */
    public int getHeldOrderCount() {
        return heldOrders.size();
    }

    private record HeldOrder(PackagerBlockEntity packager, List<PackagingRequest> requests, Set<TaskId> tasks,
                             Map<ItemResource, Long> fromStock, long heldSince) {
    }
}
