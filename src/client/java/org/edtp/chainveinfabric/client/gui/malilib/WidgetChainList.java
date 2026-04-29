package org.edtp.chainveinfabric.client.gui.malilib;

import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import net.minecraft.world.item.ItemStack;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class WidgetChainList extends WidgetListBase<ItemStack, WidgetChainListEntry> {
    private final boolean isWhitelist;
    private final ListProvider provider;
    private final GuiChainVein parentScreen;

    public interface ListProvider {
        List<ItemStack> getItems();
    }

    public WidgetChainList(int x, int y, int width, int height, ISelectionListener<ItemStack> selectionListener, boolean isWhitelist, ListProvider provider, GuiChainVein parent) {
        super(x, y, width, height, selectionListener);
        this.isWhitelist = isWhitelist;
        this.provider = provider;
        this.parentScreen = parent;
        this.browserEntryHeight = 24;
    }

    public void bindSearchBar(WidgetSearchBar searchBar) {
        this.widgetSearchBar = searchBar;
    }

    @Override
    protected Collection<ItemStack> getAllEntries() {
        String filterChars = this.widgetSearchBar != null ? this.widgetSearchBar.getFilter() : "";
        List<ItemStack> all = this.provider.getItems();

        if (filterChars.isEmpty()) {
            return all;
        } else {
            String lowerFilter = filterChars.toLowerCase();
            return all.stream().filter(stack -> {
                return stack.getHoverName().getString().toLowerCase().contains(lowerFilter);
            }).collect(Collectors.toList());
        }
    }

    @Override
    protected WidgetChainListEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, ItemStack entry) {
        return new WidgetChainListEntry(x, y, this.browserEntryWidth, this.getBrowserEntryHeightFor(entry), isOdd, entry, listIndex, this, this.isWhitelist);
    }

    public void onButtonAction(ItemStack stack, boolean removing) {
        net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == net.minecraft.core.registries.BuiltInRegistries.ITEM.getDefaultKey()) {
            id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(net.minecraft.world.level.block.Block.byItem(stack.getItem()));
        }

        String key = id.toString();
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;

        java.util.Set<String> targetSet;
        if (config.mode == ChainVeinConfig.ChainMode.CHAIN_MINE) targetSet = config.whitelistedBlocks;
        else if (config.mode == ChainVeinConfig.ChainMode.CHAIN_PLANT) targetSet = config.whitelistedCrops;
        else targetSet = config.whitelistedUtilityBlocks;

        if (removing) {
            targetSet.remove(key);
        } else {
            targetSet.add(key);
        }
        config.save();

        this.parentScreen.refreshLists();
    }
}
