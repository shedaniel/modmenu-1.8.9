package io.github.prospector.modmenu.mixin;

import io.github.prospector.modmenu.ModMenu;
import io.github.prospector.modmenu.gui.ModMenuButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class MixinTitleScreen extends Screen {
	@Shadow private ButtonWidget field_3272;

	@Inject(at = @At("RETURN"), method = "init()V")
	public void drawMenuButton(CallbackInfo info) {
		buttons.add(new ModMenuButtonWidget(123123, this.width / 2 - 100, this.height / 4 + 48 + 24 * 3, I18n.translate("modmenu.title") + " " + I18n.translate("modmenu.loaded", ModMenu.getDisplayedModCount()), this));
		for (ButtonWidget button : buttons) {
			if (button.y <= this.height / 4 + 48 + 24 * 3) {
				button.y -= 12;
			}
			if (button.y > this.height / 4 + 48 + 24 * 3) {
				button.y += 12;
			}
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
