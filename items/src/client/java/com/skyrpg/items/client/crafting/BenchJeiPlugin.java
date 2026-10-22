package com.skyrpg.items.client.crafting;

import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.items.SkyRpgItems;
import com.skyrpg.items.crafting.Bench;
import com.skyrpg.items.crafting.BenchPayloads;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * JEI support (only loaded when JEI is installed): an "Artisan Bench" recipe category, so looking up an item's recipe or
 * uses in JEI shows the bench recipes too. Bench recipes are server data, so they're added once the server has sent
 * the bench catalog after joining ({@link BenchCatalog}); vanilla-style recipes JEI finds by itself.
 */
public final class BenchJeiPlugin implements IModPlugin {
    static final IRecipeType<BenchPayloads.Entry> TYPE = IRecipeType.create(SkyRpgItems.id("artisan_bench"), BenchPayloads.Entry.class);
    private static final int PER_ROW = 6;
    private static final int SLOT = 18;

    private static IJeiRuntime runtime;
    private static boolean added;

    @Override
    public Identifier getPluginUid() {
        return SkyRpgItems.id("bench");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(TYPE, Bench.ITEM);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime available) {
        runtime = available;
        added = false;
        addRecipes();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        added = false;
    }

    /** Called when the catalog arrives, and when JEI starts: whichever comes second adds the recipes. */
    static void addRecipes() {
        List<BenchPayloads.Entry> entries = BenchCatalog.entries();
        if (runtime != null && !added && !entries.isEmpty()) {
            added = true;
            runtime.getRecipeManager().addRecipes(TYPE, entries);
        }
    }

    private static final class Category implements IRecipeCategory<BenchPayloads.Entry> {
        private final IDrawable icon;
        private final IDrawableStatic arrow;

        Category(IGuiHelper gui) {
            icon = gui.createDrawableItemLike(Bench.ITEM);
            arrow = gui.getRecipeArrow();
        }

        @Override
        public IRecipeType<BenchPayloads.Entry> getRecipeType() {
            return TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.literal("Artisan Bench");
        }

        @Override
        public int getWidth() {
            return PER_ROW * SLOT + 30 + 26;
        }

        @Override
        public int getHeight() {
            return 3 * SLOT + 12;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, BenchPayloads.Entry entry, IFocusGroup focuses) {
            for (int i = 0; i < entry.ingredients().size(); i++) {
                builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % PER_ROW) * SLOT, 1 + (i / PER_ROW) * SLOT)
                        .setStandardSlotBackground()
                        .add(entry.ingredients().get(i));
            }
            builder.addSlot(RecipeIngredientRole.OUTPUT, PER_ROW * SLOT + 34, SLOT + 1)
                    .setOutputSlotBackground()
                    .add(entry.result());
        }

        @Override
        public void draw(BenchPayloads.Entry entry, IRecipeSlotsView slots, GuiGraphicsExtractor g, double mouseX, double mouseY) {
            arrow.draw(g, PER_ROW * SLOT + 6, SLOT + 1);
            if (!entry.requirement().isEmpty()) {
                g.text(Minecraft.getInstance().font, "Needs " + entry.requirement(), 0, 3 * SLOT + 3, Theme.MUTED, false);
            }
        }
    }
}
