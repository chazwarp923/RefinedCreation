package tech.chazwarp923.refinedcreation.bridge;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.node.SimpleNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.NetworkNodeActor;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.AbstractNetworkNodeContainerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import tech.chazwarp923.refinedcreation.Config;
import tech.chazwarp923.refinedcreation.registry.ModBlockEntities;

/**
 * The Logistics Bridge: a Refined Storage network node that exposes its network's items via
 * {@link NetworkItemHandler}.
 */
public class LogisticsBridgeBlockEntity extends AbstractNetworkNodeContainerBlockEntity<SimpleNetworkNode> {
    private static final int ACTIVENESS_CHECK_INTERVAL = 20;

    private final NetworkItemHandler itemHandler;
    private int ticks;

    public LogisticsBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGISTICS_BRIDGE.get(), pos, state, new SimpleNetworkNode(0));
        this.itemHandler = new NetworkItemHandler(
                this::getNetworkStorage,
                () -> level == null ? 0L : level.getGameTime(),
                new NetworkNodeActor(mainNetworkNode));
    }

    public NetworkItemHandler getItemHandler() {
        return itemHandler;
    }

    /**
     * @return the network's storage, or {@code null} if the bridge is not connected or not powered
     */
    @Nullable
    private StorageNetworkComponent getNetworkStorage() {
        Network network = mainNetworkNode.getNetwork();
        if (network == null || !mainNetworkNode.isActive()) {
            return null;
        }
        return network.getComponent(StorageNetworkComponent.class);
    }

    void serverTick(BlockState state) {
        if (ticks++ % ACTIVENESS_CHECK_INTERVAL == 0) {
            updateActiveness(state);
        }
        mainNetworkNode.doWork();
        itemHandler.syncNetwork();
    }

    private void updateActiveness(BlockState state) {
        mainNetworkNode.setEnergyUsage(Config.BRIDGE_ENERGY_USAGE.getAsLong());

        boolean active = calculateActive();
        if (active != mainNetworkNode.isActive()) {
            mainNetworkNode.setActive(active);
        }
        if (level != null && state.getValue(LogisticsBridgeBlock.ACTIVE) != active) {
            level.setBlockAndUpdate(worldPosition, state.setValue(LogisticsBridgeBlock.ACTIVE, active));
        }
    }

    private boolean calculateActive() {
        if (level == null || !level.isLoaded(worldPosition)) {
            return false;
        }
        Network network = mainNetworkNode.getNetwork();
        if (network == null) {
            return false;
        }
        if (!RefinedStorageApi.INSTANCE.isEnergyRequired()) {
            return true;
        }
        return network.getComponent(EnergyNetworkComponent.class).getStored() >= mainNetworkNode.getEnergyUsage();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        itemHandler.detach();
    }
}
