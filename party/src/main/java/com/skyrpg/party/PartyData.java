package com.skyrpg.party;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every party, in the world save (so it survives logouts and restarts). Parties are keyed by the players' UUIDs; a member
 * keeps their seat while offline, with their last health and place so the overlay and the K menu can still show them.
 */
public final class PartyData extends SavedData {
    /** One seat. {@code joined} is when they joined (the longest-standing member inherits leadership), the rest is "last seen". */
    public static final class Member {
        static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(m -> m.id),
                Codec.STRING.fieldOf("name").forGetter(m -> m.name),
                Codec.LONG.fieldOf("joined").forGetter(m -> m.joined),
                Codec.FLOAT.optionalFieldOf("hp", 0f).forGetter(m -> m.hp),
                Codec.FLOAT.optionalFieldOf("max_hp", 0f).forGetter(m -> m.maxHp),
                Codec.STRING.optionalFieldOf("dim", "").forGetter(m -> m.dimension),
                Codec.INT.optionalFieldOf("x", 0).forGetter(m -> m.x),
                Codec.INT.optionalFieldOf("y", 0).forGetter(m -> m.y),
                Codec.INT.optionalFieldOf("z", 0).forGetter(m -> m.z),
                Codec.LONG.optionalFieldOf("last_seen", 0L).forGetter(m -> m.lastSeen)).apply(i, Member::new));

        public final UUID id;
        public String name;
        public final long joined;
        public float hp;
        public float maxHp;
        public String dimension;
        public int x;
        public int y;
        public int z;
        public long lastSeen;

        Member(UUID id, String name, long joined, float hp, float maxHp, String dimension, int x, int y, int z, long lastSeen) {
            this.id = id;
            this.name = name;
            this.joined = joined;
            this.hp = hp;
            this.maxHp = maxHp;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastSeen = lastSeen;
        }
    }

    /** A party: the leader and the members in the order they joined (the first one is the longest-standing). */
    public static final class Party {
        static final Codec<Party> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(p -> p.id),
                UUIDUtil.CODEC.fieldOf("leader").forGetter(p -> p.leader),
                Member.CODEC.listOf().fieldOf("members").forGetter(p -> p.members)).apply(i, Party::new));

        public final UUID id;
        public UUID leader;
        public final List<Member> members;

        Party(UUID id, UUID leader, List<Member> members) {
            this.id = id;
            this.leader = leader;
            this.members = new ArrayList<>(members);
        }

        public Member member(UUID player) {
            for (Member member : members) {
                if (member.id.equals(player)) {
                    return member;
                }
            }
            return null;
        }

        public boolean isLeader(UUID player) {
            return leader.equals(player);
        }
    }

    static final Codec<PartyData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Party.CODEC.listOf().optionalFieldOf("parties", List.of()).forGetter(d -> List.copyOf(d.parties))).apply(i, PartyData::new));
    static final SavedDataType<PartyData> TYPE = new SavedDataType<>(SkyRpgParty.id("parties"), PartyData::new, CODEC, null);

    final List<Party> parties = new ArrayList<>();
    private final Map<UUID, Party> byPlayer = new HashMap<>();

    PartyData() {
    }

    PartyData(List<Party> parties) {
        this.parties.addAll(parties);
        reindex();
    }

    static PartyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private void reindex() {
        byPlayer.clear();
        for (Party party : parties) {
            for (Member member : party.members) {
                byPlayer.put(member.id, party);
            }
        }
    }

    Party of(UUID player) {
        return byPlayer.get(player);
    }

    void add(Party party) {
        parties.add(party);
        reindex();
        setDirty();
    }

    void remove(Party party) {
        parties.remove(party);
        reindex();
        setDirty();
    }

    /** Call after changing a party's members. */
    void changed() {
        reindex();
        setDirty();
    }
}
