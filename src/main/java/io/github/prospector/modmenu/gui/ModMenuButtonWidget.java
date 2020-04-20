package io.github.prospector.modmenu.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

public class ModMenuButtonWidget extends ButtonWidget {
	private Screen screen;
	
	public ModMenuButtonWidget(int id, int x, int y, String text, Screen screen) {
		super(id, x, y, text);
		this.screen = screen;
	}
	
	public void onClick() {
		MinecraftClient.getInstance().openScreen(new ModsScreen(screen));
	}
}
