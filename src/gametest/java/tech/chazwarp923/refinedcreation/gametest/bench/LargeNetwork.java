package tech.chazwarp923.refinedcreation.gametest.bench;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ServerLevelData;
import tech.chazwarp923.refinedcreation.bridge.LogisticsBridgeBlockEntity;
import tech.chazwarp923.refinedcreation.bridge.NetworkItemHandler;
import tech.chazwarp923.refinedcreation.gametest.RsTestSupport;
import tech.chazwarp923.refinedcreation.registry.ModBlocks;
import tech.chazwarp923.refinedcreation.workbench.LogisticsWorkbenchBlockEntity;

/**
 * A large Refined Storage network, as in a late-game base, connected to Create through every block of the mod.
 * <p>
 * The RS side: a creative controller, 32 storage blocks (28 64k, 4 creative) in a cluster, and 34 Autocrafters
 * on top of it with 9 processing patterns each. The storage holds about {@value #TARGET_TYPES} distinct item
 * types: every registered item, plus named variants of common items, with counts from 1 to 100,000.
 * <p>
 * The Create side: a Logistics Bridge with its Packager and Stock Link, a Logistics Dispatcher fed by its own
 * Autocrafter, and a Logistics Workbench tuned to the Stock Link.
 */
public final class LargeNetwork {
    public static final int TARGET_TYPES = 10_000;
    public static final int STORAGE_BLOCKS = 32;
    public static final int CREATIVE_STORAGE_BLOCKS = 4;
    public static final int PATTERN_AUTOCRAFTERS = 34;
    public static final int PATTERNS_PER_AUTOCRAFTER = 9;
    public static final long DIAMONDS = 1_000_000;
    public static final String FACTORY_ADDRESS = "factory";
    public static final String WORKBENCH_ADDRESS = "workbench";

    static final BlockPos CONTROLLER = new BlockPos(0, 1, 0);
    // Storage cluster: x 1..4, y 1..2, z 0..3. Pattern Autocrafters: y 3, x 0..5, z 0..5, facing up into air.
    static final BlockPos BRIDGE = new BlockPos(5, 1, 0);
    static final BlockPos BRIDGE_PACKAGER = new BlockPos(6, 1, 0);
    static final BlockPos STOCK_LINK = new BlockPos(6, 2, 0);
    static final BlockPos DISPATCH_AUTOCRAFTER = new BlockPos(5, 1, 2);
    static final BlockPos DISPATCHER = new BlockPos(6, 1, 2);
    static final BlockPos DISPATCHER_PACKAGER = new BlockPos(7, 1, 2);
    static final BlockPos DISPATCHER_SIGN = new BlockPos(7, 2, 2);
    static final BlockPos WORKBENCH = new BlockPos(9, 1, 6);
    static final BlockPos WORKBENCH_PACKAGER = new BlockPos(10, 1, 6);

    /**
     * The common items that dispatcher, workbench and pattern ingredients are made of.
     */
    static final List<Item> INGREDIENTS = List.of(Items.COBBLESTONE, Items.IRON_INGOT, Items.OAK_PLANKS,
            Items.STICK, Items.REDSTONE, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.STRING, Items.GLASS);

    private final GameTestHelper helper;
    private final List<BlockPos> storagePositions = new ArrayList<>();
    private final List<BlockPos> autocrafterPositions = new ArrayList<>();
    private final List<ItemResource> patternOutputs = new ArrayList<>();
    private final List<ItemResource> stockedTypes = new ArrayList<>();
    private ItemResource dispatchOutput;
    private String mix = "";

    LargeNetwork(GameTestHelper helper) {
        this.helper = helper;
    }

    // --- Building ---------------------------------------------------------------------------------------------

    /**
     * Places every block. The network needs a few ticks to form before {@link #fill} can run.
     */
    void place() {
        helper.setBlock(CONTROLLER, RsTestSupport.rsBlock("creative_controller"));
        for (int y = 1; y <= 2; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 1; x <= 4; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean creative = storagePositions.size() >= STORAGE_BLOCKS - CREATIVE_STORAGE_BLOCKS;
                    helper.setBlock(pos, RsTestSupport.rsBlock(creative ? "creative_storage_block"
                            : "64k_storage_block"));
                    storagePositions.add(pos);
                }
            }
        }
        for (int z = 0; z < 6 && autocrafterPositions.size() < PATTERN_AUTOCRAFTERS; z++) {
            for (int x = 0; x < 6 && autocrafterPositions.size() < PATTERN_AUTOCRAFTERS; x++) {
                BlockPos pos = new BlockPos(x, 3, z);
                helper.setBlock(pos, RsTestSupport.withDirection(
                        RsTestSupport.rsBlock("autocrafter").defaultBlockState(), Direction.UP));
                autocrafterPositions.add(pos);
            }
        }

        helper.setBlock(BRIDGE, ModBlocks.LOGISTICS_BRIDGE.get());
        helper.setBlock(BRIDGE_PACKAGER, packagerFacingEast());
        helper.setBlock(STOCK_LINK, AllBlocks.STOCK_LINK.getDefaultState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));

        helper.setBlock(DISPATCH_AUTOCRAFTER, RsTestSupport.withDirection(
                RsTestSupport.rsBlock("autocrafter").defaultBlockState(), Direction.EAST));
        helper.setBlock(DISPATCHER, ModBlocks.LOGISTICS_DISPATCHER.get());
        helper.setBlock(DISPATCHER_PACKAGER, packagerFacingEast());
        helper.setBlock(DISPATCHER_SIGN, Blocks.OAK_SIGN);
        SignBlockEntity sign = helper.getBlockEntity(DISPATCHER_SIGN);
        sign.setText(new SignText().setMessage(0, Component.literal(FACTORY_ADDRESS)), true);

        helper.setBlock(WORKBENCH, ModBlocks.LOGISTICS_WORKBENCH.get());
        helper.setBlock(WORKBENCH_PACKAGER, packagerFacingEast());
    }

    /**
     * @return how many storage sources the network has; {@link #STORAGE_BLOCKS} once every block has joined
     */
    int storageSources() {
        AtomicInteger sources = new AtomicInteger();
        storage().hasSource(source -> {
            sources.incrementAndGet();
            return false;
        });
        return sources.get();
    }

    /**
     * Stocks the network and loads the patterns.
     */
    void fill() {
        Random random = new Random(923);
        StorageNetworkComponent storage = storage();
        Set<ItemResource> types = new LinkedHashSet<>();

        List<Item> registryItems = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                registryItems.add(item);
            }
        }
        registryItems.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        for (Item item : registryItems) {
            types.add(new ItemResource(item));
        }
        // Variants that RS stores as separate types, as a real hoard has them: every enchanted book, every potion
        // in every form, worn tools and armour, then named items spread over many base items.
        level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders().forEach(enchantment -> {
            for (int lvl = 1; lvl <= enchantment.value().getMaxLevel(); lvl++) {
                types.add(ItemResource.ofItemStack(EnchantedBookItem.createForEnchantment(
                        new EnchantmentInstance(enchantment, lvl))));
            }
        });
        BuiltInRegistries.POTION.holders().forEach(potion -> {
            for (Item form : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW)) {
                types.add(ItemResource.ofItemStack(PotionContents.createItemStack(form, potion)));
            }
        });
        int damageable = 0;
        for (Item item : registryItems) {
            ItemStack stack = new ItemStack(item);
            if (!stack.isDamageableItem() || damageable++ >= 60) {
                continue;
            }
            for (int wear = 1; wear <= 20; wear++) {
                ItemStack worn = stack.copy();
                worn.setDamageValue(Math.max(1, stack.getMaxDamage() * wear / 21));
                types.add(ItemResource.ofItemStack(worn));
            }
        }
        List<Item> variantBases = new ArrayList<>();
        for (int i = 0; i < registryItems.size() && variantBases.size() < 600; i += 3) {
            Item item = registryItems.get(i);
            if (item.getDefaultMaxStackSize() > 1) {
                variantBases.add(item);
            }
        }
        int beforeNamed = types.size();
        for (int n = 0; types.size() < TARGET_TYPES; n++) {
            ItemStack stack = new ItemStack(variantBases.get(n % variantBases.size()));
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Batch " + n));
            types.add(ItemResource.ofItemStack(stack));
        }
        int maxVariants = 0;
        java.util.Map<Item, Integer> perItem = new java.util.HashMap<>();
        for (ItemResource type : types) {
            maxVariants = Math.max(maxVariants, perItem.merge(type.item(), 1, Integer::sum));
        }
        mix = String.format("%,d registry items, %,d enchanted book/potion/worn-tool variants, %,d named variants"
                        + " over %d base items; at most %d variants of one item", registryItems.size(),
                beforeNamed - registryItems.size(), types.size() - beforeNamed, variantBases.size(), maxVariants);

        for (ItemResource type : types) {
            long amount = count(random);
            insert(storage, type, amount);
            stockedTypes.add(type);
        }
        insert(storage, new ItemResource(Items.DIAMOND), DIAMONDS);
        for (Item ingredient : INGREDIENTS) {
            insert(storage, new ItemResource(ingredient), 1_000_000);
        }

        // Pattern outputs are registry items, as in a real base where most craftables are also kept in stock.
        int output = 0;
        for (BlockPos pos : autocrafterPositions) {
            Container patterns = RsTestSupport.patternContainer(helper.getBlockEntity(pos));
            for (int slot = 0; slot < PATTERNS_PER_AUTOCRAFTER; slot++) {
                ItemResource result = new ItemResource(registryItems.get(output++ * 7 % registryItems.size()));
                if (patternOutputs.contains(result) || result.item() == Items.DIAMOND
                        || INGREDIENTS.contains(result.item())) {
                    slot--;
                    continue;
                }
                List<ResourceAmount> inputs = new ArrayList<>();
                int inputCount = 1 + random.nextInt(3);
                for (int i = 0; i < inputCount; i++) {
                    inputs.add(new ResourceAmount(new ItemResource(INGREDIENTS.get((output + i) % INGREDIENTS.size())),
                            1 + random.nextInt(4)));
                }
                patterns.setItem(slot, RsTestSupport.processingPattern(inputs, new ResourceAmount(result, 1)));
                patternOutputs.add(result);
            }
        }

        // The dispatcher's pattern takes nine different ingredients, so each step fills a whole package.
        ItemStack widget = new ItemStack(Items.STICK);
        widget.set(DataComponents.CUSTOM_NAME, Component.literal("Dispatch Widget"));
        dispatchOutput = ItemResource.ofItemStack(widget);
        List<ResourceAmount> inputs = new ArrayList<>();
        for (Item ingredient : INGREDIENTS) {
            inputs.add(new ResourceAmount(new ItemResource(ingredient), 4));
        }
        RsTestSupport.patternContainer(helper.getBlockEntity(DISPATCH_AUTOCRAFTER))
                .setItem(0, RsTestSupport.processingPattern(inputs, new ResourceAmount(dispatchOutput, 1)));

        LogisticsWorkbenchBlockEntity workbench = workbench();
        workbench.setFrequency(frequency());
        workbench.setDeliveryAddress(WORKBENCH_ADDRESS);
    }

    /**
     * Mostly small stacks with a long tail of large ones, from 1 to 100,000.
     */
    private static long count(Random random) {
        double roll = random.nextDouble();
        if (roll < 0.70) {
            return 1 + random.nextInt(256);
        }
        if (roll < 0.95) {
            return 256 + random.nextInt(3840);
        }
        return 4096 + random.nextInt(95_905);
    }

    private void insert(StorageNetworkComponent storage, ItemResource resource, long amount) {
        long inserted = storage.insert(resource, amount, Action.EXECUTE, Actor.EMPTY);
        helper.assertTrue(inserted == amount, "Network took only " + inserted + " of " + amount + " " + resource);
    }

    /**
     * @return {@code null} once the network is ready, or what it is still waiting for
     */
    String readiness() {
        if (!bridgePackager().targetInventory.hasInventory()) {
            return "Bridge Packager has not found the bridge";
        }
        if (!autocrafting().getOutputs().contains(dispatchOutput)) {
            return "Network does not know the dispatcher pattern";
        }
        int known = 0;
        for (ItemResource output : patternOutputs) {
            if (autocrafting().getOutputs().contains(output)) {
                known++;
            }
        }
        if (known < patternOutputs.size()) {
            return "Network knows " + known + " of " + patternOutputs.size() + " patterns";
        }
        InventorySummary summary = LogisticsManager.getSummaryOfNetwork(frequency(), true);
        for (ItemResource output : patternOutputs) {
            if (summary.getCountOf(output.toItemStack()) < BigItemStack.INF) {
                return "Stock Link does not list " + output + " as craftable yet";
            }
        }
        return null;
    }

    void describe(Bench bench) {
        bench.note("Item types in network", storage().getAll().size());
        bench.note("Item mix", mix);
        bench.note("Items stored", String.format("%,d", storage().getStored()));
        bench.note("Storage sources", storageSources() + " (" + (STORAGE_BLOCKS - CREATIVE_STORAGE_BLOCKS)
                + " 64k, " + CREATIVE_STORAGE_BLOCKS + " creative)");
        bench.note("Patterns", autocrafting().getPatterns().size() + " (" + (PATTERN_AUTOCRAFTERS + 1)
                + " Autocrafters)");
        bench.note("Bridge slots", itemHandler().getSlots());
        bench.note("Stock Link summary entries", LogisticsManager.getSummaryOfNetwork(frequency(), true)
                .getStacks().size());
        bench.note("Java", System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        bench.note("CPU threads", Runtime.getRuntime().availableProcessors());
    }

    // --- Access -----------------------------------------------------------------------------------------------

    /**
     * Moves the game on by one tick for the code under test: game time advances, and Create's one-tick cache of
     * accurate network summaries expires. Create's other tick-based caches are left alone: they include the
     * registry of present Stock Links, which would time out without the links' own ticks. Only call this inside
     * {@link #rewinding}.
     */
    void nextTick() {
        ServerLevel level = level();
        ((ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 1);
        LogisticsManager.ACCURATE_SUMMARIES.invalidateAll();
    }

    /**
     * Runs {@code benchmarks}, then sets game time back to what it was. Game tests count their ticks in game
     * time, so without this the {@link #nextTick} jumps would make the test's later waits end early.
     */
    void rewinding(Runnable benchmarks) {
        ServerLevel level = level();
        long time = level.getGameTime();
        try {
            benchmarks.run();
        } finally {
            ((ServerLevelData) level.getLevelData()).setGameTime(time);
        }
    }

    /**
     * Changes the network's contents without changing what it holds overall, as any insert or extract would.
     */
    void touchNetwork() {
        ItemResource cobble = new ItemResource(Items.COBBLESTONE);
        storage().insert(cobble, 1, Action.EXECUTE, Actor.EMPTY);
        storage().extract(cobble, 1, Action.EXECUTE, Actor.EMPTY);
    }

    GameTestHelper helper() {
        return helper;
    }

    ServerLevel level() {
        return helper.getLevel();
    }

    LogisticsBridgeBlockEntity bridge() {
        return helper.getBlockEntity(BRIDGE);
    }

    NetworkItemHandler itemHandler() {
        return bridge().getItemHandler();
    }

    Network network() {
        Network network = bridge().getNetwork();
        helper.assertTrue(network != null, "Bridge has no network");
        return network;
    }

    StorageNetworkComponent storage() {
        return network().getComponent(StorageNetworkComponent.class);
    }

    AutocraftingNetworkComponent autocrafting() {
        return network().getComponent(AutocraftingNetworkComponent.class);
    }

    PackagerBlockEntity bridgePackager() {
        return helper.getBlockEntity(BRIDGE_PACKAGER);
    }

    PackagerBlockEntity dispatcherPackager() {
        return helper.getBlockEntity(DISPATCHER_PACKAGER);
    }

    PackagerBlockEntity workbenchPackager() {
        return helper.getBlockEntity(WORKBENCH_PACKAGER);
    }

    LogisticsWorkbenchBlockEntity workbench() {
        return helper.getBlockEntity(WORKBENCH);
    }

    UUID frequency() {
        PackagerLinkBlockEntity link = helper.getBlockEntity(STOCK_LINK);
        return link.behaviour.freqId;
    }

    BlockPos dispatcherAbsolute() {
        return helper.absolutePos(DISPATCHER);
    }

    List<ItemResource> patternOutputs() {
        return patternOutputs;
    }

    List<ItemResource> stockedTypes() {
        return stockedTypes;
    }

    ItemResource dispatchOutput() {
        return dispatchOutput;
    }

    /**
     * Empties a Packager's outgoing packages and ends its animation, standing in for the conveyor that would carry
     * packages off and for the ticks a real delivery waits for the Packager to be free.
     */
    static void clearOutgoing(PackagerBlockEntity packager) {
        packager.heldBox = ItemStack.EMPTY;
        packager.queuedExitingPackages.clear();
        packager.animationTicks = 0;
    }

    private static net.minecraft.world.level.block.state.BlockState packagerFacingEast() {
        return AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, Direction.EAST);
    }
}
