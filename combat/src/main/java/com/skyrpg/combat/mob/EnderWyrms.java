package com.skyrpg.combat.mob;

import com.skyrpg.core.world.MobLevelling;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The Ender Wyrm's summoning API, for the altar (docs/design/end_game.md) and /wyrm summon. */
public final class EnderWyrms {
    private EnderWyrms() {
    }

    /** Whether a Wyrm is already alive in this level. */
    public static boolean active(ServerLevel level) {
        return !level.getEntities(EndMobs.ENDER_WYRM, new AABB(-3.0E7, level.getMinY(), -3.0E7, 3.0E7, level.getMaxY(), 3.0E7), EnderWyrmEntity::isAlive).isEmpty();
    }

    /**
     * Summons the Ender Wyrm to fight over {@code arenaCentre} (the altar): it appears 28 blocks off and 18 above and
     * circles the centre. Returns null if one is already alive here.
     */
    public static EnderWyrmEntity summon(ServerLevel level, Vec3 arenaCentre) {
        if (active(level)) {
            return null;
        }
        EnderWyrmEntity wyrm = EndMobs.ENDER_WYRM.create(level, EntitySpawnReason.EVENT);
        if (wyrm == null) {
            return null;
        }
        wyrm.snapTo(arenaCentre.x + 28, arenaCentre.y + 18, arenaCentre.z, 90f, 0f);
        wyrm.setCenter(arenaCentre);
        wyrm.finalizeSpawn(level, level.getCurrentDifficultyAt(wyrm.blockPosition()), EntitySpawnReason.EVENT, null);
        wyrm.setPersistenceRequired();
        wyrm.setHealth(wyrm.getMaxHealth());
        // beyond vanilla's 1024 health cap it takes half damage instead (see MobLevelling.boss): about 2000 effective
        MobLevelling.boss(wyrm, 100, 2f);
        level.addFreshEntity(wyrm);
        wyrm.announce(level);
        return wyrm;
    }
}
