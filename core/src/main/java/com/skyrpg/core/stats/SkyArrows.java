package com.skyrpg.core.stats;

import com.skyrpg.core.SkyRpgCore;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * Arrows from SkyRPG bows. A bow tags the arrows it fires with the damage they should deal; the stats mod then turns that
 * into a hit the same way it does for melee (the shooter's Strength, then a Crit Chance roll for Crit Damage), instead of
 * vanilla's speed-based arrow damage. The tag lives on the arrow entity and is not saved: an arrow that outlives a restart
 * deals vanilla damage.
 */
public final class SkyArrows {
    /**
     * @param damage SkyRPG damage before Strength and crits: the bow's Damage stat scaled by how far it was drawn and by the
     *               arrow's share of the shot (a three-arrow bow splits its damage).
     */
    public record Shot(double damage) {
    }

    public static final AttachmentType<Shot> SHOT = AttachmentRegistry.create(SkyRpgCore.id("sky_arrow"));

    private SkyArrows() {
    }

    public static void tag(AbstractArrow arrow, double damage) {
        arrow.setAttached(SHOT, new Shot(damage));
    }

    /** The tag on this arrow, or null for a vanilla arrow. */
    public static Shot of(AbstractArrow arrow) {
        return arrow.getAttached(SHOT);
    }
}
