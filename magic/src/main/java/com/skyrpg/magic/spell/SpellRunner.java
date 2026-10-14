package com.skyrpg.magic.spell;

import com.skyrpg.core.text.Msg;
import com.skyrpg.magic.CastContext;
import com.skyrpg.magic.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;

/** Runs a spell by its id. Each spell reads its numbers from its data file (see {@code skyrpg/spells}); the effects are in the element classes. */
public final class SpellRunner {
    private SpellRunner() {
    }

    /** Casts the spell. Returns false if it fizzled (no valid target, no room): nothing is spent then. */
    public static boolean run(CastContext ctx) {
        return switch (ctx.def.id().getPath()) {
            case "firebolt" -> FireSpells.firebolt(ctx);
            case "fireball" -> FireSpells.fireball(ctx);
            case "meteor" -> FireSpells.meteor(ctx);
            case "frost_shard" -> FrostSpells.frostShard(ctx);
            case "blizzard" -> FrostSpells.blizzard(ctx);
            case "chain_lightning" -> LightningSpells.chainLightning(ctx);
            case "thunderclap" -> LightningSpells.thunderclap(ctx);
            case "arcane_missiles" -> ArcaneSpells.missiles(ctx);
            case "blink" -> ArcaneSpells.blink(ctx);
            case "mana_shield" -> ArcaneSpells.manaShield(ctx);
            case "healing_pulse" -> NatureSpells.healingPulse(ctx);
            case "entangle" -> NatureSpells.entangle(ctx);
            default -> false;
        };
    }

    /** The direction from the wand's tip to where the crosshair is pointing, so a bolt lands where you aim despite leaving from the hand. */
    static Vec3 aimDirection(CastContext ctx, double range) {
        Vec3 muzzle = ctx.muzzle();
        Vec3 target = Targeting.aim(ctx.caster, range).point();
        Vec3 direction = target.subtract(muzzle);
        return direction.lengthSqr() < 1.0 ? ctx.caster.getLookAngle() : direction.normalize();
    }

    static void tell(CastContext ctx, String text) {
        ctx.caster.sendOverlayMessage(Msg.text(text, ChatFormatting.GRAY));
    }
}
