package io.github.prospector.modmenu.gui;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import io.github.prospector.modmenu.ModMenu;
import io.github.prospector.modmenu.config.ModMenuConfigManager;
import io.github.prospector.modmenu.util.BadgeRenderer;
import io.github.prospector.modmenu.util.HardcodedUtil;
import io.github.prospector.modmenu.util.RenderUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ConfirmChatLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.NumberFormat;
import java.util.*;

public class ModsScreen extends Screen {
	private static final Identifier FILTERS_BUTTON_LOCATION = new Identifier(ModMenu.MOD_ID, "textures/gui/filters_button.png");
	private static final Identifier CONFIGURE_BUTTON_LOCATION = new Identifier(ModMenu.MOD_ID, "textures/gui/configure_button.png");
	private final String textTitle;
	private TextFieldWidget searchBox;
	private DescriptionListWidget descriptionListWidget;
	private Screen parent;
	private ModListWidget modList;
	private String tooltip;
	private ModListEntry selected;
	private BadgeRenderer badgeRenderer;
	private double scrollPercent = 0;
	private boolean init = false;
	private boolean filterOptionsShown = false;
	private int paneY;
	private int paneWidth;
	private int rightPaneX;
	private int searchBoxX;
	private int filtersX;
	private int filtersWidth;
	private int searchRowWidth;
	public Set<String> showModChildren = new HashSet<>();

	public ModsScreen(Screen previousGui) {
		this.parent = previousGui;
		this.textTitle = I18n.translate("modmenu.title");
	}

	@Override
	public void tick() {
		this.searchBox.tick();
	}

	@Override
	public void init() {
		Keyboard.enableRepeatEvents(true);
		paneY = 48;
		paneWidth = this.width / 2 - 8;
		rightPaneX = width - paneWidth;

		int searchBoxWidth = paneWidth - 32 - 22;
		searchBoxX = paneWidth / 2 - searchBoxWidth / 2 - 22 / 2;
		String oldText = Optional.ofNullable(searchBox).map(TextFieldWidget::getText).orElse("");
		this.searchBox = new TextFieldWidget(420, this.textRenderer, searchBoxX, 22, searchBoxWidth, 20);
		this.searchBox.setText(oldText);
		this.modList = new ModListWidget(this.client, paneWidth, this.height, paneY + 19, this.height - 36, 36, this.searchBox.getText(), this.modList, this);
		this.modList.setXPos(0);
		this.descriptionListWidget = new DescriptionListWidget(this.client, paneWidth, this.height, paneY + 60, this.height - 36, textRenderer.fontHeight + 1, this);
		this.descriptionListWidget.setXPos(rightPaneX);
		ButtonWidget configureButton = new ModMenuTexturedButtonWidget(12341, width - 24, paneY, 20, 20, 0, 0, CONFIGURE_BUTTON_LOCATION, 32, 64,
				I18n.translate("modmenu.configure")) {
			@Override
			public void render(MinecraftClient client, int mouseX, int mouseY) {
				if (focused) {
					setTooltip(I18n.translate("modmenu.configure"));
				}
				if (selected != null) {
					String modid = selected.getMetadata().getId();
					active = ModMenu.hasConfigScreenFactory(modid) || ModMenu.hasLegacyConfigScreenTask(modid);
				} else {
					active = false;
				}
				visible = active;
				GlStateManager.color4f(1, 1, 1, 1f);
				super.render(client, mouseX, mouseY);
			}
		};
		int urlButtonWidths = paneWidth / 2 - 2;
		int cappedButtonWidth = Math.min(urlButtonWidths, 200);
		ButtonWidget websiteButton = new ButtonWidget(190, rightPaneX + (urlButtonWidths / 2) - (cappedButtonWidth / 2), paneY + 36, Math.min(urlButtonWidths, 200), 20,
				I18n.translate("modmenu.website")) {
			@Override
			public void render(MinecraftClient client, int var1, int var2) {
				visible = selected != null;
				active = visible && selected.getMetadata().getContact().get("homepage").isPresent();
				super.render(client, var1, var2);
			}
		};
		ButtonWidget issuesButton = new ButtonWidget(191, rightPaneX + urlButtonWidths + 4 + (urlButtonWidths / 2) - (cappedButtonWidth / 2), paneY + 36, Math.min(urlButtonWidths, 200), 20,
				I18n.translate("modmenu.issues")) {
			@Override
			public void render(MinecraftClient client, int var1, int var2) {
				visible = selected != null;
				active = visible && selected.getMetadata().getContact().get("issues").isPresent();
				super.render(client, var1, var2);
			}
		};
		this.buttons.add(new ModMenuTexturedButtonWidget(180, paneWidth / 2 + searchBoxWidth / 2 - 20 / 2 + 2, 22, 20, 20, 0, 0, FILTERS_BUTTON_LOCATION, 32, 64) {
			@Override
			public void render(MinecraftClient client, int var1, int var2) {
				super.render(client, var1, var2);
				if (focused) {
					setTooltip(I18n.translate("modmenu.toggleFilterOptions"));
				}
			}
		});
		String showLibrariesText = I18n.translate("modmenu.showLibraries", I18n.translate("modmenu.showLibraries." + ModMenuConfigManager.getConfig().showLibraries()));
		String sortingText = I18n.translate("modmenu.sorting", I18n.translate(ModMenuConfigManager.getConfig().getSorting().getTranslationKey()));
		int showLibrariesWidth = textRenderer.getStringWidth(showLibrariesText) + 20;
		int sortingWidth = textRenderer.getStringWidth(sortingText) + 20;
		filtersWidth = showLibrariesWidth + sortingWidth + 2;
		searchRowWidth = searchBoxX + searchBoxWidth + 22;
		updateFiltersX();
		this.buttons.add(new ButtonWidget(181, filtersX, 45, sortingWidth, 20, sortingText) {
			@Override
			public void render(MinecraftClient client, int var1, int var2) {
				GlStateManager.translated(0, 0, 1);
				visible = filterOptionsShown;
				message = I18n.translate("modmenu.sorting", I18n.translate(ModMenuConfigManager.getConfig().getSorting().getTranslationKey()));
				super.render(client, var1, var2);
			}
		});
		this.buttons.add(new ButtonWidget(182, filtersX + sortingWidth + 2, 45, showLibrariesWidth, 20, I18n.translate("modmenu.showLibraries", I18n.translate("modmenu.showLibraries." + ModMenuConfigManager.getConfig().showLibraries()))) {
			@Override
			public void render(MinecraftClient client, int var1, int var2) {
				GlStateManager.translated(0, 0, 1);
				visible = filterOptionsShown;
				message = I18n.translate("modmenu.showLibraries", I18n.translate("modmenu.showLibraries." + ModMenuConfigManager.getConfig().showLibraries()));
				super.render(client, var1, var2);
			}
		});
		this.buttons.add(configureButton);
		this.buttons.add(websiteButton);
		this.buttons.add(issuesButton);
		this.buttons.add(new ButtonWidget(201, this.width / 2 - 154, this.height - 28, 150, 20, I18n.translate("modmenu.modsFolder")));
		this.buttons.add(new ButtonWidget(202, this.width / 2 + 4, this.height - 28, 150, 20, I18n.translate("gui.done")));

		init = true;
	}

	@Override
	protected void buttonPressed(ButtonWidget button) {
		if (button.id == 12341) {
			final String modid = Objects.requireNonNull(selected).getMetadata().getId();
			final Screen screen = ModMenu.getConfigScreen(modid, this);
			if (screen != null) {
				client.openScreen(screen);
			} else {
				ModMenu.openConfigScreen(modid);
			}
		} else if (button.id == 190) {
			final ModMetadata metadata = Objects.requireNonNull(selected).getMetadata();
			this.client.openScreen(new ConfirmChatLinkScreen((bool, id) -> {
				if (bool) {
					try {
						Class<?> var3 = Class.forName("java.awt.Desktop");
						Object object = var3.getMethod("getDesktop").invoke(null);
						var3.getMethod("browse", URI.class).invoke(object, new URI(metadata.getContact().get("homepage").get()));
					} catch (Throwable var5) {
						LogManager.getLogger().error("Couldn't open link", var5);
					}
				}
				this.client.openScreen(this);
			}, metadata.getContact().get("homepage").get(), -1, true));
		} else if (button.id == 191) {
			final ModMetadata metadata = Objects.requireNonNull(selected).getMetadata();
			this.client.openScreen(new ConfirmChatLinkScreen((bool, id) -> {
				if (bool) {
					try {
						Class<?> var3 = Class.forName("java.awt.Desktop");
						Object object = var3.getMethod("getDesktop").invoke(null);
						var3.getMethod("browse", URI.class).invoke(object, new URI(metadata.getContact().get("issues").get()));
					} catch (Throwable var5) {
						LogManager.getLogger().error("Couldn't open link", var5);
					}
				}
				this.client.openScreen(this);
			}, metadata.getContact().get("issues").get(), -1, true));
		} else if (button.id == 180) {
			filterOptionsShown = !filterOptionsShown;
		} else if (button.id == 181) {
			ModMenuConfigManager.getConfig().toggleSortMode();
			modList.reloadFilters();
		} else if (button.id == 182) {
			ModMenuConfigManager.getConfig().toggleShowLibraries();
			modList.reloadFilters();
		} else if (button.id == 201) {
			File file = new File(FabricLoader.getInstance().getGameDirectory(), "mods");
			String string = file.getAbsolutePath();
			if (Util.getOperatingSystem() == Util.OperatingSystem.OSX) {
				try {
					LogManager.getLogger().info(string);
					Runtime.getRuntime().exec(new String[]{"/usr/bin/open", string});
					return;
				} catch (IOException var9) {
					LogManager.getLogger().error("Couldn't open file", var9);
				}
			} else if (Util.getOperatingSystem() == Util.OperatingSystem.WINDOWS) {
				String string2 = String.format("cmd.exe /C start \"Open file\" \"%s\"", string);

				try {
					Runtime.getRuntime().exec(string2);
					return;
				} catch (IOException var8) {
					LogManager.getLogger().error("Couldn't open file", var8);
				}
			}

			boolean bl = false;

			try {
				Class<?> var5 = Class.forName("java.awt.Desktop");
				Object object = var5.getMethod("getDesktop").invoke(null);
				var5.getMethod("browse", URI.class).invoke(object, file.toURI());
			} catch (Throwable var7) {
				LogManager.getLogger().error("Couldn't open link", var7);
				bl = true;
			}

			if (bl) {
				LogManager.getLogger().info("Opening via system class!");
				Sys.openURL("file://" + string);
			}
		} else if (button.id == 202) {
			client.openScreen(parent);
		}
	}

	@Override
	public void handleMouse() {
		super.handleMouse();
		modList.handleMouse();
	}

	@Override
	protected void keyPressed(char character, int code) {
		if (searchBox.keyPressed(character, code)) {
			modList.filter(searchBox.getText(), false);
		} else super.keyPressed(character, code);
	}

	@Override
	protected void mouseClicked(int i, int j, int k) {
		searchBox.mouseClicked(i, j, k);
		if (searchBox.isFocused())
			return;
		if (modList.mouseClicked(i, j, k))
			return;
		super.mouseClicked(i, j, k);
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		ModsScreen.overlayBackground(paneWidth, 0, rightPaneX, height, 64, 64, 64, 255, 255);
		this.tooltip = null;
		ModListEntry selectedEntry = selected;
		if (selectedEntry != null) {
			this.descriptionListWidget.render(mouseX, mouseY, delta);
		}
		this.modList.render(mouseX, mouseY, delta);
		this.searchBox.render();
		GlStateManager.disableBlend();
		this.drawCenteredString(this.textRenderer, this.textTitle, this.modList.getWidth() / 2, 8, 16777215);
		super.render(mouseX, mouseY, delta);
		String fullModCount = computeModCountText(true);
		if (updateFiltersX()) {
			if (filterOptionsShown) {
				if (!ModMenuConfigManager.getConfig().showLibraries() || textRenderer.getStringWidth(fullModCount) <= filtersX - 5) {
					textRenderer.draw(fullModCount, searchBoxX, 52, 0xFFFFFF);
				} else {
					textRenderer.draw(computeModCountText(false), searchBoxX, 46, 0xFFFFFF);
					textRenderer.draw(computeLibraryCountText(), searchBoxX, 57, 0xFFFFFF);
				}
			} else {
				if (!ModMenuConfigManager.getConfig().showLibraries() || textRenderer.getStringWidth(fullModCount) <= modList.getWidth() - 5) {
					drawCenteredString(textRenderer, fullModCount, this.modList.getWidth() / 2, 52, 0xFFFFFF);
				} else {
					drawCenteredString(textRenderer, computeModCountText(false), this.modList.getWidth() / 2, 46, 0xFFFFFF);
					drawCenteredString(textRenderer, computeLibraryCountText(), this.modList.getWidth() / 2, 57, 0xFFFFFF);
				}
			}
		}
		if (selectedEntry != null) {
			ModMetadata metadata = selectedEntry.getMetadata();
			int x = rightPaneX;
			GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
			this.selected.bindIconTexture();
			GlStateManager.enableBlend();
			method_2444(x, paneY, 0.0F, 0.0F, 32, 32, 32, 32);
			GlStateManager.disableBlend();
			int lineSpacing = textRenderer.fontHeight + 1;
			int imageOffset = 36;
			String name = metadata.getName();
			name = HardcodedUtil.formatFabricModuleName(name);
			String trimmedName = name;
			int maxNameWidth = this.width - (x + imageOffset);
			if (textRenderer.getStringWidth(name) > maxNameWidth) {
				trimmedName = textRenderer.trimToWidth(name, maxNameWidth - textRenderer.getStringWidth("...")) + "...";
			}
			textRenderer.draw(trimmedName, x + imageOffset, paneY + 1, 0xFFFFFF);
			if (mouseX > x + imageOffset && mouseY > paneY + 1 && mouseY < paneY + 1 + textRenderer.fontHeight && mouseX < x + imageOffset + textRenderer.getStringWidth(trimmedName)) {
				setTooltip(I18n.translate("modmenu.modIdToolTip", metadata.getId()));
			}
			if (init || badgeRenderer == null || badgeRenderer.getMetadata() != metadata) {
				badgeRenderer = new BadgeRenderer(x + imageOffset + Objects.requireNonNull(this.client).textRenderer.getStringWidth(trimmedName) + 2, paneY, width - 28, selectedEntry.container, this);
				init = false;
			}
			badgeRenderer.draw(mouseX, mouseY);
			textRenderer.draw("v" + metadata.getVersion().getFriendlyString(), x + imageOffset, paneY + 2 + lineSpacing, 0x808080);
			String authors;
			List<String> names = new ArrayList<>();

			metadata.getAuthors().stream()
					.filter(Objects::nonNull)
					.map(Person::getName)
					.filter(Objects::nonNull)
					.forEach(names::add);

			if (!names.isEmpty()) {
				if (names.size() > 1) {
					authors = Joiner.on(", ").join(names);
				} else {
					authors = names.get(0);
				}
				RenderUtils.drawWrappedString(I18n.translate("modmenu.authorPrefix", authors), x + imageOffset, paneY + 2 + lineSpacing * 2, paneWidth - imageOffset - 4, 1, 0x808080);
			}
			if (this.tooltip != null) {
				this.renderTooltip(Lists.newArrayList(Splitter.on("\n").split(this.tooltip)), mouseX, mouseY);
			}
		}

	}

	private String computeModCountText(boolean includeLibs) {
		int[] rootMods = formatModCount(ModMenu.ROOT_NONLIB_MODS);

		if (includeLibs && ModMenuConfigManager.getConfig().showLibraries()) {
			int[] rootLibs = formatModCount(ModMenu.ROOT_LIBRARIES);
			return translateNumeric("modmenu.showingModsLibraries", rootMods, rootLibs);
		} else {
			return translateNumeric("modmenu.showingMods", rootMods);
		}
	}

	private String computeLibraryCountText() {
		if (ModMenuConfigManager.getConfig().showLibraries()) {
			int[] rootLibs = formatModCount(ModMenu.ROOT_LIBRARIES);
			return translateNumeric("modmenu.showingLibraries", rootLibs);
		} else {
			return null;
		}
	}

	private static String translateNumeric(String key, int[]... args) {
		Object[] realArgs = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			NumberFormat nf = NumberFormat.getInstance();
			if (args[i].length == 1) {
				realArgs[i] = nf.format(args[i][0]);
			} else {
				assert args[i].length == 2;
				realArgs[i] = nf.format(args[i][0]) + "/" + nf.format(args[i][1]);
			}
		}

		int[] override = new int[args.length];
		Arrays.fill(override, -1);
		for (int i = 0; i < args.length; i++) {
			int[] arg = args[i];
			if (arg == null) {
				throw new NullPointerException("args[" + i + "]");
			}
			if (arg.length == 1) {
				override[i] = arg[0];
			}
		}

		String lastKey = key;
		for (int flags = (1 << args.length) - 1; flags >= 0; flags--) {
			StringBuilder fullKey = new StringBuilder(key);
			for (int i = 0; i < args.length; i++) {
				fullKey.append('.');
				if (((flags & (1 << i)) != 0) && override[i] != -1) {
					fullKey.append(override[i]);
				} else {
					fullKey.append('a');
				}
			}
			lastKey = fullKey.toString();
			if (I18n.field_5294.field_5304.containsKey(lastKey)) {
//				return lastKey + Arrays.toString(realArgs);
				return I18n.translate(lastKey, realArgs);
			}
		}
//		return lastKey + Arrays.toString(realArgs);
		return I18n.translate(lastKey, realArgs);
	}

	private int[] formatModCount(Set<String> set) {
		int visible = modList.getDisplayedCountFor(set);
		int total = set.size();
		if (visible == total) {
			return new int[]{total};
		}
		return new int[]{visible, total};
	}

	static void overlayBackground(int x1, int y1, int x2, int y2, int red, int green, int blue, int startAlpha, int endAlpha) {
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.getBuffer();
		Objects.requireNonNull(MinecraftClient.getInstance()).getTextureManager().bindTexture(DrawableHelper.BACKGROUND_TEXTURE);
		GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		buffer.begin(7, VertexFormats.POSITION_TEXTURE_COLOR);
		buffer.vertex(x1, y2, 0.0D).texture(x1 / 32.0F, y2 / 32.0F).color(red, green, blue, endAlpha).next();
		buffer.vertex(x2, y2, 0.0D).texture(x2 / 32.0F, y2 / 32.0F).color(red, green, blue, endAlpha).next();
		buffer.vertex(x2, y1, 0.0D).texture(x2 / 32.0F, y1 / 32.0F).color(red, green, blue, startAlpha).next();
		buffer.vertex(x1, y1, 0.0D).texture(x1 / 32.0F, y1 / 32.0F).color(red, green, blue, startAlpha).next();
		tessellator.draw();
	}

	@Override
	public void removed() {
		super.removed();
		this.modList.close();
	}

	private void setTooltip(String tooltip) {
		this.tooltip = tooltip;
	}

	ModListEntry getSelectedEntry() {
		return selected;
	}

	void updateSelectedEntry(ModListEntry entry) {
		if (entry != null) {
			this.selected = entry;
		}
	}

	double getScrollPercent() {
		return scrollPercent;
	}

	void updateScrollPercent(double scrollPercent) {
		this.scrollPercent = scrollPercent;
	}

	public String getSearchInput() {
		return searchBox.getText();
	}

	private boolean updateFiltersX() {
		if ((filtersWidth + textRenderer.getStringWidth(computeModCountText(true)) + 20) >= searchRowWidth && ((filtersWidth + textRenderer.getStringWidth(computeModCountText(false)) + 20) >= searchRowWidth || (filtersWidth + textRenderer.getStringWidth(computeLibraryCountText()) + 20) >= searchRowWidth)) {
			filtersX = paneWidth / 2 - filtersWidth / 2;
			return !filterOptionsShown;
		} else {
			filtersX = searchRowWidth - filtersWidth + 1;
			return true;
		}
	}
}
