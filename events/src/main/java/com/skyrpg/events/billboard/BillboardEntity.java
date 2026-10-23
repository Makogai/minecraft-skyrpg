package com.skyrpg.events.billboard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A billboard: a 16:9 screen in a cabinet, hung on a wall or standing on two posts, showing pictures from web links (a
 * slideshow). Its position is the bottom centre of the whole thing, in the middle of the cabinet's depth. The owner (or
 * an op) right-clicks it to set the pictures, size, look and timing, and breaks it with a hit, which gives the item back.
 * While it stands it lights the area in front of it with invisible light blocks, removed again when it goes.
 */
public final class BillboardEntity extends Entity {
    static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(BillboardEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> WIDTH = SynchedEntityData.defineId(BillboardEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Boolean> STAND = SynchedEntityData.defineId(BillboardEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Integer> THEME = SynchedEntityData.defineId(BillboardEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> SECONDS = SynchedEntityData.defineId(BillboardEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<String> IMAGES = SynchedEntityData.defineId(BillboardEntity.class, EntityDataSerializers.STRING);

    private static final int LIGHT_LEVEL = 13;

    private UUID owner;
    private String ownerName = "";
    /** Light blocks this billboard placed, to take away when it goes. */
    private final List<BlockPos> lights = new ArrayList<>();

    public BillboardEntity(EntityType<? extends BillboardEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FACING, Direction.SOUTH.get2DDataValue());
        builder.define(WIDTH, 6);
        builder.define(STAND, false);
        builder.define(THEME, 0);
        builder.define(SECONDS, 8);
        builder.define(IMAGES, "");
    }

    // ---------------------------------------------------------------- what it is

    public Direction facing() {
        return Direction.from2DDataValue(entityData.get(FACING));
    }

    public float screenWidth() {
        return entityData.get(WIDTH);
    }

    public float screenHeight() {
        return screenWidth() * 9 / 16;
    }

    public boolean standing() {
        return entityData.get(STAND);
    }

    public int theme() {
        return entityData.get(THEME);
    }

    public int seconds() {
        return entityData.get(SECONDS);
    }

    public List<String> images() {
        String joined = entityData.get(IMAGES);
        return joined.isEmpty() ? List.of() : List.of(joined.split("\n"));
    }

    /** Height of the screen's bottom edge above the entity's position. */
    public float screenBase() {
        return (standing() ? BillboardTheme.POST_HEIGHT : 0) + BillboardTheme.frame(screenWidth());
    }

    void setup(Direction facing, int width, boolean stand, ServerPlayer placer) {
        entityData.set(FACING, facing.get2DDataValue());
        entityData.set(WIDTH, width);
        entityData.set(STAND, stand);
        owner = placer.getUUID();
        ownerName = placer.getGameProfile().name();
        setYRot(facing.toYRot());
        refreshBox();
    }

    void configure(int width, int theme, int seconds, List<String> images) {
        entityData.set(WIDTH, width);
        entityData.set(THEME, theme);
        entityData.set(SECONDS, seconds);
        entityData.set(IMAGES, String.join("\n", images));
        refreshBox();
        if (level() instanceof ServerLevel server) {
            clearLights(server);
            placeLights(server);
        }
    }

    boolean mayEdit(Player player) {
        return player.isCreative() || owner == null || owner.equals(player.getUUID())
                || player instanceof ServerPlayer server
                && net.minecraft.commands.Commands.<net.minecraft.commands.CommandSourceStack>hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS)
                        .test(server.createCommandSourceStack());
    }

    // ---------------------------------------------------------------- size and shape

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == WIDTH || accessor == FACING || accessor == STAND) {
            refreshBox();
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        refreshBox();
    }

    private void refreshBox() {
        if (entityData == null) {
            return;
        }
        setBoundingBox(box(position()));
    }

    /** The cabinet (and posts) as a box: wide along the screen, thin across it. */
    AABB box(Vec3 at) {
        float w = screenWidth();
        float f = BillboardTheme.frame(w);
        float half = w / 2 + f;
        float depth = BillboardTheme.depth(w) / 2;
        float top = screenBase() + screenHeight() + f + 0.3f;
        Direction facing = facing();
        double hx = facing.getAxis() == Direction.Axis.Z ? half : depth;
        double hz = facing.getAxis() == Direction.Axis.Z ? depth : half;
        return new AABB(at.x - hx, at.y, at.z - hz, at.x + hx, at.y + top, at.z + hz);
    }

    @Override
    protected AABB makeBoundingBox(Vec3 at) {
        return entityData == null ? super.makeBoundingBox(at) : box(at);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Billboards.ITEM);
    }

    // ---------------------------------------------------------------- using it

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 at) {
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        if (!mayEdit(player)) {
            server.sendSystemMessage(Component.literal("Only " + (ownerName.isEmpty() ? "its owner" : ownerName) + " can change this billboard."));
            return InteractionResult.FAIL;
        }
        Billboards.openEditor(server, this);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !mayEdit(player) || isRemoved()) {
            return false;
        }
        playSound(SoundEvents.WOOD_BREAK, 1f, 0.8f);
        if (!player.isCreative()) {
            spawnAtLocation(level, new ItemStack(Billboards.ITEM));
        }
        discard();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server && lights.isEmpty() && tickCount == 20) {
            placeLights(server);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel server) {
            clearLights(server);
        }
        super.remove(reason);
    }

    // ---------------------------------------------------------------- light

    /** Invisible light blocks in the air in front of the screen, one every few blocks along it. */
    private void placeLights(ServerLevel level) {
        Direction facing = facing();
        float w = screenWidth();
        double y = getY() + screenBase() + screenHeight() / 2;
        int count = Math.max(1, Math.round(w / 4));
        Direction right = facing.getCounterClockWise();
        for (int i = 0; i < count; i++) {
            double along = count == 1 ? 0 : -w / 2 + w * (i + 0.5) / count;
            Vec3 spot = position().add(facing.getStepX() * (BillboardTheme.depth(w) / 2 + 1.2), 0, facing.getStepZ() * (BillboardTheme.depth(w) / 2 + 1.2))
                    .add(right.getStepX() * along, 0, right.getStepZ() * along);
            BlockPos pos = BlockPos.containing(spot.x, y, spot.z);
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL), 3);
                lights.add(pos);
            }
        }
    }

    private void clearLights(ServerLevel level) {
        for (BlockPos pos : lights) {
            if (level.getBlockState(pos).is(Blocks.LIGHT)) {
                level.removeBlock(pos, false);
            }
        }
        lights.clear();
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("facing", entityData.get(FACING));
        out.putInt("width", entityData.get(WIDTH));
        out.putBoolean("stand", entityData.get(STAND));
        out.putInt("theme", entityData.get(THEME));
        out.putInt("seconds", entityData.get(SECONDS));
        out.putString("images", entityData.get(IMAGES));
        if (owner != null) {
            out.putString("owner", owner.toString());
        }
        out.putString("owner_name", ownerName);
        out.putIntArray("lights", lights.stream().flatMapToInt(p -> java.util.stream.IntStream.of(p.getX(), p.getY(), p.getZ())).toArray());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(FACING, in.getIntOr("facing", Direction.SOUTH.get2DDataValue()));
        entityData.set(WIDTH, in.getIntOr("width", 6));
        entityData.set(STAND, in.getBooleanOr("stand", false));
        entityData.set(THEME, in.getIntOr("theme", 0));
        entityData.set(SECONDS, in.getIntOr("seconds", 8));
        entityData.set(IMAGES, in.getStringOr("images", ""));
        String id = in.getStringOr("owner", "");
        owner = id.isEmpty() ? null : UUID.fromString(id);
        ownerName = in.getStringOr("owner_name", "");
        lights.clear();
        in.getIntArray("lights").ifPresent(array -> {
            for (int i = 0; i + 2 < array.length; i += 3) {
                lights.add(new BlockPos(array[i], array[i + 1], array[i + 2]));
            }
        });
        refreshBox();
    }
}
