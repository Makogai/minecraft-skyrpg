package com.skyrpg.wiki.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyrpg.core.client.SkyRpgCoreClient;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import java.lang.reflect.Field;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.inventory.Slot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The in-game wiki, client side only and optional: it registers no blocks, items, data components or networking, so players
 * without it can still join a server that has the other SkyRPG mods. It adds a page to the SkyRPG menu (K), the
 * {@code /wiki [page]} client command and the "Look it up" key (default H) over items in any container screen.
 */
public class SkyRpgWikiClient implements ClientModInitializer {
    public static final String MOD_ID = "skyrpg_wiki";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyMapping LOOKUP_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyrpg_wiki.lookup", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, SkyRpgCoreClient.CATEGORY));

    /** Opening a screen from inside a command or key handler can be undone by the screen that was closing, so it waits a tick. */
    private static boolean openNextTick;
    private static Field hoveredSlot;

    @Override
    public void onInitializeClient() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Identifier.fromNamespaceAndPath(MOD_ID, "wiki"),
                (ResourceManagerReloadListener) manager -> WikiData.invalidate());
        SkyRpgMenu.registerPage(WikiPage.instance());

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (openNextTick && minecraft.player != null) {
                openNextTick = false;
                SkyRpgMenu.open(WikiPage.ID);
            }
        });

        // Where a key press in a screen also counts as a key mapping click (and for the dev tools' "press"), use it too.
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            while (LOOKUP_KEY.consumeClick()) {
                if (!openNextTick && minecraft.gui.screen() instanceof AbstractContainerScreen<?> container) {
                    lookUpHovered(minecraft, container);
                }
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(ClientCommands.literal("wiki")
                .executes(ctx -> {
                    open(null, null);
                    return 1;
                })
                .then(ClientCommands.argument("page", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            String typed = builder.getRemainingLowerCase();
                            for (String id : WikiData.ids()) {
                                if (id.startsWith(typed)) {
                                    builder.suggest(id);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String text = StringArgumentType.getString(ctx, "page").trim();
                            String page = text;
                            String anchor = null;
                            int hash = text.indexOf('#');
                            if (hash >= 0) {
                                page = text.substring(0, hash);
                                anchor = text.substring(hash + 1);
                            }
                            if (WikiData.entry(page) == null) {
                                ctx.getSource().sendError(Component.literal("No wiki page called \"" + page + "\". Try /wiki and pick one."));
                                return 0;
                            }
                            open(page, anchor);
                            return 1;
                        }))));

        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (screen instanceof AbstractContainerScreen<?> container) {
                ScreenKeyboardEvents.allowKeyPress(screen).register((current, event) -> !lookUp(minecraft, container, event));
            }
        });
    }

    static void open(String page, String anchor) {
        WikiPage.request(page, anchor);
        openNextTick = true;
    }

    /** The look-it-up key over an item: true if the key was used. */
    private static boolean lookUp(Minecraft minecraft, AbstractContainerScreen<?> screen, net.minecraft.client.input.KeyEvent event) {
        if (!LOOKUP_KEY.matches(event) || screen.getFocused() instanceof EditBox) {
            return false;
        }
        return lookUpHovered(minecraft, screen);
    }

    private static boolean lookUpHovered(Minecraft minecraft, AbstractContainerScreen<?> screen) {
        Slot slot = hovered(screen);
        if (slot == null || !slot.hasItem()) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(slot.getItem().getItem());
        String page = WikiData.pageForItem(id.toString());
        if (page == null) {
            if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(Component.literal("No wiki entry for " + slot.getItem().getHoverName().getString()));
            }
            return true;
        }
        open(page, "item-" + id.getNamespace() + "-" + id.getPath());
        return true;
    }

    private static Slot hovered(AbstractContainerScreen<?> screen) {
        try {
            if (hoveredSlot == null) {
                hoveredSlot = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
                hoveredSlot.setAccessible(true);
            }
            return (Slot) hoveredSlot.get(screen);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Could not read the hovered slot: {}", e.toString());
            return null;
        }
    }
}
