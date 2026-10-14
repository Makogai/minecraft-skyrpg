package com.skyrpg.skills;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sends the server's skill definitions to a client, so menus can show names, icons and level
 * progress. Sent on join and after every /reload. Player XP itself syncs through {@link PlayerSkills#XP}.
 */
public record SkillDefinitionsPayload(List<Entry> skills) implements CustomPacketPayload {
    public static final Type<SkillDefinitionsPayload> TYPE = new Type<>(SkyRpgSkills.id("definitions"));

    public record Entry(Identifier id, String name, int color, Identifier icon, String description, int order, List<Long> levels,
                        Map<String, Double> rewards) {
        static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Entry::id,
                ByteBufCodecs.STRING_UTF8, Entry::name,
                ByteBufCodecs.INT, Entry::color,
                Identifier.STREAM_CODEC, Entry::icon,
                ByteBufCodecs.STRING_UTF8, Entry::description,
                ByteBufCodecs.VAR_INT, Entry::order,
                ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), Entry::levels,
                ByteBufCodecs.<ByteBuf, String, Double, Map<String, Double>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.DOUBLE), Entry::rewards,
                Entry::new);

        public Skill toSkill() {
            return new Skill(id, name, TextColor.fromRgb(color), order, levels, icon, description, rewards);
        }
    }

    public static final StreamCodec<ByteBuf, SkillDefinitionsPayload> CODEC =
            Entry.CODEC.apply(ByteBufCodecs.list()).map(SkillDefinitionsPayload::new, SkillDefinitionsPayload::skills);

    /** The definitions currently loaded on the server. */
    public static SkillDefinitionsPayload current() {
        List<Entry> entries = new ArrayList<>();
        for (Skill skill : Skills.all()) {
            entries.add(new Entry(skill.id(), skill.name(), skill.color().getValue(), skill.icon(), skill.description(), skill.order(),
                    skill.levels(), skill.rewardsPerLevel()));
        }
        return new SkillDefinitionsPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
