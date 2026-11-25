package tech.chazwarp923.refinedcreation.workbench;

import java.util.List;
import java.util.function.Consumer;

import com.simibubi.create.content.logistics.BigItemStack;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import tech.chazwarp923.refinedcreation.RefinedCreation;
import tech.chazwarp923.refinedcreation.workbench.client.WorkbenchClientPackets;

/**
 * Network payloads for the Logistics Workbench GUI. Every payload carries the menu's container id. The receiver
 * ignores a payload unless that menu is still the open one.
 */
public final class WorkbenchPackets {
    private static final String VERSION = "1";

    private WorkbenchPackets() {
    }

    public enum Action {
        ORDER_PATTERN, CLEAR_GRID, SEND_RETURNS;

        static final StreamCodec<ByteBuf, Action> STREAM_CODEC =
                ByteBufCodecs.idMapper(i -> values()[i], Action::ordinal);
    }

    /**
     * Server → client: the tuned network's stock, sent periodically while the menu is open.
     */
    public record StockPayload(int containerId, boolean tuned, List<BigItemStack> stock)
            implements CustomPacketPayload {
        public static final Type<StockPayload> TYPE = new Type<>(id("workbench_stock"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StockPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StockPayload::containerId,
                ByteBufCodecs.BOOL, StockPayload::tuned,
                BigItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), StockPayload::stock,
                StockPayload::new);

        @Override
        public Type<StockPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: the outcome of an action, shown in the GUI.
     */
    public record StatusPayload(int containerId, Component message) implements CustomPacketPayload {
        public static final Type<StatusPayload> TYPE = new Type<>(id("workbench_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StatusPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StatusPayload::containerId,
                ComponentSerialization.STREAM_CODEC, StatusPayload::message,
                StatusPayload::new);

        @Override
        public Type<StatusPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: order an item from the network's stock.
     */
    public record RequestPayload(int containerId, ItemStack item, int count) implements CustomPacketPayload {
        public static final Type<RequestPayload> TYPE = new Type<>(id("workbench_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RequestPayload::containerId,
                ItemStack.STREAM_CODEC, RequestPayload::item,
                ByteBufCodecs.VAR_INT, RequestPayload::count,
                RequestPayload::new);

        @Override
        public Type<RequestPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: send the network stock now. The screen sends this as soon as it opens, so the list
     * appears at once instead of at the next periodic sync.
     */
    public record StockRequestPayload(int containerId) implements CustomPacketPayload {
        public static final Type<StockRequestPayload> TYPE = new Type<>(id("workbench_stock_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StockRequestPayload> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast()
                        .map(StockRequestPayload::new, StockRequestPayload::containerId);

        @Override
        public Type<StockRequestPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: press one of the GUI's buttons.
     */
    public record ActionPayload(int containerId, Action action, int amount) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(id("workbench_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ActionPayload::containerId,
                Action.STREAM_CODEC, ActionPayload::action,
                ByteBufCodecs.VAR_INT, ActionPayload::amount,
                ActionPayload::new);

        @Override
        public Type<ActionPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: edit the delivery or return address.
     */
    public record AddressPayload(int containerId, boolean isReturn, String address) implements CustomPacketPayload {
        public static final Type<AddressPayload> TYPE = new Type<>(id("workbench_address"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AddressPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AddressPayload::containerId,
                ByteBufCodecs.BOOL, AddressPayload::isReturn,
                ByteBufCodecs.stringUtf8(LogisticsWorkbenchBlockEntity.MAX_ADDRESS_LENGTH), AddressPayload::address,
                AddressPayload::new);

        @Override
        public Type<AddressPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client -> server: lay a recipe out in the grid (JEI recipe transfer). Holds the acceptable items for each
     * of the 9 grid slots, in grid order.
     */
    public record TransferPayload(int containerId, List<List<ItemStack>> options, boolean max, int crafts)
            implements CustomPacketPayload {
        /** Caps the alternatives per slot, so a huge tag can't produce an oversized packet. */
        public static final int MAX_OPTIONS_PER_SLOT = 64;

        public static final Type<TransferPayload> TYPE = new Type<>(id("workbench_transfer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TransferPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, TransferPayload::containerId,
                        ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OPTIONS_PER_SLOT))
                                .apply(ByteBufCodecs.list(LogisticsWorkbenchBlockEntity.GRID_SLOTS)),
                        TransferPayload::options,
                        ByteBufCodecs.BOOL, TransferPayload::max,
                        ByteBufCodecs.VAR_INT, TransferPayload::crafts,
                        TransferPayload::new);

        @Override
        public Type<TransferPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        // Lambdas rather than method references, so the client-only handler class is only loaded when a packet
        // is actually handled on the client.
        registrar.playToClient(StockPayload.TYPE, StockPayload.STREAM_CODEC,
                (payload, context) -> WorkbenchClientPackets.handleStock(payload));
        registrar.playToClient(StatusPayload.TYPE, StatusPayload.STREAM_CODEC,
                (payload, context) -> WorkbenchClientPackets.handleStatus(payload));
        registrar.playToServer(StockRequestPayload.TYPE, StockRequestPayload.STREAM_CODEC, (payload, context) ->
                withMenu(context, payload.containerId(), LogisticsWorkbenchMenu::sendStockNow));
        registrar.playToServer(RequestPayload.TYPE, RequestPayload.STREAM_CODEC, (payload, context) ->
                withMenu(context, payload.containerId(), menu -> menu.handleRequest(payload.item(), payload.count())));
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, (payload, context) ->
                withMenu(context, payload.containerId(),
                        menu -> menu.handleAction(payload.action(), payload.amount())));
        registrar.playToServer(TransferPayload.TYPE, TransferPayload.STREAM_CODEC, (payload, context) ->
                withMenu(context, payload.containerId(),
                        menu -> menu.handleTransfer(payload.options(), payload.max(), payload.crafts())));
        registrar.playToServer(AddressPayload.TYPE, AddressPayload.STREAM_CODEC, (payload, context) ->
                withMenu(context, payload.containerId(),
                        menu -> menu.handleAddress(payload.isReturn(), payload.address())));
    }

    private static void withMenu(IPayloadContext context, int containerId,
                                 Consumer<LogisticsWorkbenchMenu> action) {
        Player player = context.player();
        if (player.containerMenu instanceof LogisticsWorkbenchMenu menu && menu.containerId == containerId
                && menu.stillValid(player)) {
            action.accept(menu);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RefinedCreation.MODID, path);
    }
}
