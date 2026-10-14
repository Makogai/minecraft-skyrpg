package com.skyrpg.vfx.data;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/**
 * A parsed binding ({@code data/<ns>/skyrpg/vfx_bindings/<path>.json}): plays an effect on matching entities near the
 * camera at {@code rate} plays per tick. Every filter that is present must match (an entity filter, an armour filter,
 * a held-item filter, conditions).
 */
public final class BindingDef {
    public enum Hand {
        MAIN, OFF, EITHER
    }

    public final Identifier id;
    public Identifier effectId;
    public EffectDef effect;
    /** Expected plays per tick (0.25 = about every 4 ticks). */
    public float rate = 0.25f;
    /** Only entities this close to the camera, in blocks. */
    public float range = 32;
    /** Also on the camera's own entity (you). */
    public boolean self = true;
    public int required;
    public int forbidden;

    // Entity filter (null = any entity)
    public List<EntityType<?>> entityTypes;
    public List<TagKey<EntityType<?>>> entityTags;

    // Armour filter (null = none): any of these equipment asset ids, on at least this many armour pieces
    public List<Identifier> armorAssets;
    public int armorPieces = 4;

    // Held-item filter (null = none)
    public Hand hand;
    public List<Item> items;
    public List<TagKey<Item>> itemTags;
    /** Lower-case rarity names ("legendary"); null = any. */
    public List<String> rarities;
    public List<Item> exceptItems;
    public List<TagKey<Item>> exceptTags;

    public BindingDef(Identifier id) {
        this.id = id;
    }

    public boolean hasEntityFilter() {
        return entityTypes != null || entityTags != null;
    }

    public boolean hasItemFilter() {
        return items != null || itemTags != null;
    }

    /** Short "what it matches" text for /vfx list. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        if (hasEntityFilter()) {
            text.append("mobs");
        }
        if (armorAssets != null) {
            text.append(text.isEmpty() ? "" : " + ").append(armorPieces).append("pc ").append(armorAssets.getFirst());
        }
        if (hand != null) {
            text.append(text.isEmpty() ? "" : " + ").append("held");
            if (rarities != null) {
                text.append(' ').append(String.join("/", rarities));
            }
        }
        return text.toString();
    }
}
