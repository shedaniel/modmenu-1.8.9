package io.github.prospector.modmenu.mixin;

import io.github.prospector.modmenu.ModMenu;
import io.github.prospector.modmenu.gui.ModMenuButtonWidget;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public class MixinGameMenuScreen extends Screen {
	@Inject(at = @At("RETURN"), method = "init()V")
	public void drawMenuButton(CallbackInfo info) {
		buttons.add(new ModMenuButtonWidget(313, this.width / 2 - 100, this.height / 4 + 8 + 24 * 3, I18n.translate("modmenu.title") + " " + I18n.translate("modmenu.loaded", ModMenu.getDisplayedModCount()), this));
		for (ButtonWidget button : buttons) {
			if (button.y >= this.height / 4 - 16 + 24 * 4 - 1 && !(button instanceof ModMenuButtonWidget)) {
				button.y += 24;
			}
			button.y -= 12;
		}
	}

	@Inject(at = @At("HEAD"), method = "buttonPressed", cancellable = true)
	public void buttonPressed(ButtonWidget button, CallbackInfo ci) {
		if (button instanceof ModMenuButtonWidget) {
			((ModMenuButtonWidget) button).onClick();
			ci.cancel();
		}
	}
}
