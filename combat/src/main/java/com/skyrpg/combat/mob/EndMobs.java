package com.skyrpg.combat.mob;

import com.skyrpg.combat.SkyRpgCombat;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The End's monsters (docs/design/end_game.md): the Ender Wyrm ({@code skyrpg_combat:ender_wyrm}), the final boss, a
 * flying serpent with a segmented model (see EnderWyrmEntity and the client's EndMobRenderers), plus its drops
 * {@code wyrm_scale} and {@code void_heart} (loot table {@code data/skyrpg_combat/loot_table/entities/ender_wyrm.json}) and
 * its spawn egg. {@code /wyrm summon} (ops) summons one at the executor; {@link EnderWyrms#summon} is the API for the altar.
 */
public final class EndMobs {
    public static final EntityType<EnderWyrmEntity> ENDER_WYRM = register("ender_wyrm",
            EntityType.Builder.of(EnderWyrmEntity::new, MobCategory.MONSTER).fireImmune().sized(3.2f, 3.0f).eyeHeight(1.5f)
                    .clientTrackingRange(16).updateInterval(1));
    public static final Item WYRM_SCALE = item("wyrm_scale", Rarity.RARE);
    public static final Item VOID_HEART = item("void_heart", Rarity.EPIC);

    private EndMobs() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(ENDER_WYRM, EnderWyrmEntity.attributes());
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgCombat.id("ender_wyrm_spawn_egg"));
        Item egg = Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(ENDER_WYRM)));
        ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));
        CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> output.accept(egg));
        // /wyrm summon: op-only, for testing
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("wyrm")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("summon").executes(ctx -> {
                    var source = ctx.getSource();
                    EnderWyrmEntity wyrm = EnderWyrms.summon(source.getLevel(), source.getPosition());
                    if (wyrm == null) {
                        source.sendFailure(Component.literal("An Ender Wyrm is already alive here"));
                        return 0;
                    }
                    source.sendSuccess(() -> Component.literal("The Ender Wyrm stirs"), true);
                    return 1;
                }))
                .then(Commands.literal("kill").executes(ctx -> {
                    var source = ctx.getSource();
                    int n = 0;
                    for (EnderWyrmEntity wyrm : source.getLevel().getEntities(ENDER_WYRM,
                            new net.minecraft.world.phys.AABB(-3.0E7, source.getLevel().getMinY(), -3.0E7, 3.0E7, source.getLevel().getMaxY(), 3.0E7), e -> true)) {
                        wyrm.discard();
                        n++;
                    }
                    int count = n;
                    source.sendSuccess(() -> Component.literal("Removed " + count + " Ender Wyrm(s)"), true);
                    return n;
                }))));
    }

    private static Item item(String name, Rarity rarity) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgCombat.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).rarity(rarity)));
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, SkyRpgCombat.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}
