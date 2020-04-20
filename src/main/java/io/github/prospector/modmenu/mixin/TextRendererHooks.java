package io.github.prospector.modmenu.mixin;

import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TextRenderer.class)
public interface TextRendererHooks {
	@Invoker("mirror")
	String mirror(String text);
}
