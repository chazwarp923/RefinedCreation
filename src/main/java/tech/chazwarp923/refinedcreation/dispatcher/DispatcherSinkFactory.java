package tech.chazwarp923.refinedcreation.dispatcher;

import com.refinedmods.refinedstorage.common.api.autocrafting.PatternProviderExternalPatternSinkFactory;
import com.refinedmods.refinedstorage.common.api.autocrafting.PlatformPatternProviderExternalPatternSink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Registered with RS so that every Autocrafter also gets a {@link DispatcherSink} for the block it faces. The sink
 * does nothing unless that block is (or later becomes) a Logistics Dispatcher.
 */
public enum DispatcherSinkFactory implements PatternProviderExternalPatternSinkFactory {
    INSTANCE;

    @Override
    public PlatformPatternProviderExternalPatternSink create(ServerLevel level, BlockPos pos, Direction direction) {
        return new DispatcherSink(level, pos);
    }
}
