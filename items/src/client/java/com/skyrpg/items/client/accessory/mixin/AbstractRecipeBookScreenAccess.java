package com.skyrpg.items.client.accessory.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The recipe book is private; the survival inventory needs to know when it's open, as it covers the accessory panel. */
@Mixin(AbstractRecipeBookScreen.class)
public interface AbstractRecipeBookScreenAccess {
    @Accessor("recipeBookComponent")
    RecipeBookComponent<?> skyrpg$recipeBook();
}
