package io.github.prospector.modmenu.gui;

import com.google.common.collect.Lists;
import io.github.prospector.modmenu.ModMenu;
import io.github.prospector.modmenu.config.ModMenuConfigManager;
import io.github.prospector.modmenu.gui.entries.ChildEntry;
import io.github.prospector.modmenu.gui.entries.IndependentEntry;
import io.github.prospector.modmenu.gui.entries.ParentEntry;
import io.github.prospector.modmenu.util.ModListSearch;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;

public class ModListWidget extends EntryListWidget implements AutoCloseable {
	private static final Logger LOGGER = LogManager.getLogger();
	public static final boolean DEBUG = Boolean.getBoolean("modmenu.debug");

	private final Map<Path, NativeImageBackedTexture> modIconsCache = new HashMap<>();
	private final ModsScreen parent;
	private List<ModContainer> modContainerList = null;
	private Set<ModContainer> addedMods = new HashSet<>();
	private String selectedModId = null;
	private boolean scrolling;

	private List<ModListEntry> entries = Lists.newArrayList();
	private ModListEntry selected = null;

	public ModListWidget(MinecraftClient client, int width, int height, int y1, int y2, int entryHeight, String searchTerm, ModListWidget list, ModsScreen parent) {
		super(client, width, height, y1, y2, entryHeight);
		this.parent = parent;
		if (list != null) {
			this.modContainerList = list.modContainerList;
		}
		this.filter(searchTerm, false);
		setScrollAmount(parent.getScrollPercent() * Math.max(0, this.getMaxPosition() - (this.yEnd - this.yStart - 4)));
	}

	public void setScrollAmount(double amount) {
		scrollAmount = (float) amount;
		capYPosition();
		int denominator = Math.max(0, this.getMaxPosition() - (this.yEnd - this.yStart - 4));
		if (denominator <= 0) {
			parent.updateScrollPercent(0);
		} else {
			parent.updateScrollPercent(getScrollAmount() / Math.max(0, this.getMaxPosition() - (this.yEnd - this.yStart - 4)));
		}
	}

	public void select(ModListEntry entry) {
		this.setSelected(entry);
	}

	public void setSelected(ModListEntry entry) {
		selected = entry;
		selectedModId = entry.getMetadata().getId();
		parent.updateSelectedEntry(getSelected());
	}

	@Override
	protected int getItemCount() {
		return entries.size();
	}

	@Override
	protected boolean isEntrySelected(int index) {
		ModListEntry selected = getSelected();
		return selected != null && selected.getMetadata().getId().equals(getEntry(index).getMetadata().getId());
	}

	@Override
	public ModListEntry getEntry(int i) {
		return entries.get(i);
	}

	public boolean addEntry(ModListEntry entry) {
		if (addedMods.contains(entry.container)) {
			return false;
		}
		addedMods.add(entry.container);
		boolean b = entries.add(entry);
		if (entry.getMetadata().getId().equals(selectedModId)) {
			setSelected(entry);
		}
		return b;
	}

	protected boolean removeEntry(ModListEntry entry) {
		addedMods.remove(entry.container);
		return entries.remove(entry);
	}

	protected ModListEntry remove(int index) {
		addedMods.remove(getEntry(index).container);
		return entries.remove(index);
	}

	public void reloadFilters() {
		filter(parent.getSearchInput(), true, false);
	}


	public void filter(String searchTerm, boolean refresh) {
		filter(searchTerm, refresh, true);
	}

	private void filter(String searchTerm, boolean refresh, boolean search) {
		this.entries.clear();
		addedMods.clear();
		Collection<ModContainer> mods = FabricLoader.getInstance().getAllMods();

		if (this.modContainerList == null || refresh) {
			this.modContainerList = new ArrayList<>();
			modContainerList.addAll(mods);
			this.modContainerList.sort(ModMenuConfigManager.getConfig().getSorting().getComparator());
		}

		boolean validSearch = ModListSearch.validSearchQuery(searchTerm);
		List<ModContainer> matched = ModListSearch.search(parent, searchTerm, modContainerList);

		for (ModContainer container : matched) {
			ModMetadata metadata = container.getMetadata();
			String modId = metadata.getId();
			boolean library = ModMenu.LIBRARY_MODS.contains(modId);

			//Hide parent lib mods when the config is set to hide
			if (library && !ModMenuConfigManager.getConfig().showLibraries()) {
				continue;
			}

			if (!ModMenu.PARENT_MAP.values().contains(container)) {
				if (ModMenu.PARENT_MAP.keySet().contains(container)) {
					//Add parent mods when not searching
					List<ModContainer> children = ModMenu.PARENT_MAP.get(container);
					children.sort(ModMenuConfigManager.getConfig().getSorting().getComparator());
					ParentEntry parent = new ParentEntry(container, children, this);
					this.addEntry(parent);
					//Add children if they are meant to be shown
					if (this.parent.showModChildren.contains(modId)) {
						List<ModContainer> validChildren = ModListSearch.search(this.parent, searchTerm, children);
						for (ModContainer child : validChildren) {
							this.addEntry(new ChildEntry(child, parent, this, validChildren.indexOf(child) == validChildren.size() - 1));
						}
					}
				} else {
					//A mod with no children
					this.addEntry(new IndependentEntry(container, this));
				}
			}
		}

		if (parent.getSelectedEntry() != null && !entries.isEmpty() || this.getSelected() != null && getSelected().getMetadata() != parent.getSelectedEntry().getMetadata()) {
			for (ModListEntry entry : entries) {
				if (entry.getMetadata().equals(parent.getSelectedEntry().getMetadata())) {
					setSelected(entry);
				}
			}
		} else {
			if (getSelected() == null && !entries.isEmpty() && getEntry(0) != null) {
				setSelected(getEntry(0));
			}
		}

		if (getScrollAmount() > Math.max(0, this.getMaxPosition() - (this.yEnd - this.yStart - 4))) {
			setScrollAmount(Math.max(0, this.getMaxPosition() - (this.yEnd - this.yStart - 4)));
		}
	}

	private ModListEntry getSelected() {
		return selected;
	}

	public final ModListEntry getEntryAtPos(double x, double y) {
		int int_5 = MathHelper.floor(y - (double) this.yStart) - this.headerHeight + (int) this.getScrollAmount() - 4;
		int index = int_5 / this.entryHeight;
		return x < (double) this.getScrollbarPosition() && x >= (double) getRowLeft() && x <= (double) (getRowLeft() + getRowWidth()) && index >= 0 && int_5 >= 0 && index < this.getItemCount() ? this.entries.get(index) : null;
	}

	@Override
	protected int getScrollbarPosition() {
		return this.width - 6;
	}

	@Override
	public int getRowWidth() {
		return this.width - (Math.max(0, this.getMaxPosition() - (this.yEnd - this.yStart - 4)) > 0 ? 18 : 12);
	}

	//	@Override
	protected int getRowLeft() {
		return xStart + 6;
	}

	public int getWidth() {
		return width;
	}

	public int getTop() {
		return this.yStart;
	}

	public ModsScreen getParent() {
		return parent;
	}

	@Override
	protected int getMaxPosition() {
		return super.getMaxPosition() + 4;
	}

	public int getDisplayedCountFor(Set<String> set) {
		int count = 0;
		for (ModListEntry c : entries) {
			if (set.contains(c.getMetadata().getId())) {
				count++;
			}
		}
		return count;
	}

	@Override
	public void close() {
		for (NativeImageBackedTexture tex : this.modIconsCache.values()) {
			tex.clearGlId();
		}
	}

	NativeImageBackedTexture getCachedModIcon(Path path) {
		return this.modIconsCache.get(path);
	}

	void cacheModIcon(Path path, NativeImageBackedTexture tex) {
		this.modIconsCache.put(path, tex);
	}

	public Set<ModContainer> getCurrentModSet() {
		return addedMods;
	}
}
