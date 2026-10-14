# Accessories and armour depth

Owner request (Drogashh): accessory slots left of the armour in the inventory (E), accessories visible on
the player for everyone, and worn SkyRPG armour that looks like its icons (raised plates, trims,
pauldrons, crests) instead of a flat skin-tight texture. Everything here is built in the `items` mod.

Every Minecraft/Fabric name below was checked with javap against the 26.3 merged jar and fabric-api
0.161.0+26.3 (rendering-v1 27.0.14, data-attachment 2.2.30). Re-check anything this doc doesn't name.

## 1. Slots

Four slots, one column, top to bottom (each row lines up with the armour row next to it):

| # | `AccessorySlot` | Shown on the body | Tag (`data/skyrpg_items/tags/item/...`) | Equip sound |
|---|---|---|---|---|
| 0 | `AMULET` "Amulet" | Cord round the neck, pendant on the upper chest | `skyrpg_items:accessory/amulet` | `ARMOR_EQUIP_CHAIN` |
| 1 | `CLOAK` "Cloak" | Shoulder mantle plus a cape hanging down the back, sways | `skyrpg_items:accessory/cloak` | `ARMOR_EQUIP_LEATHER` |
| 2 | `BELT` "Belt" | Band round the waist, front buckle, side pouch/tail | `skyrpg_items:accessory/belt` | `ARMOR_EQUIP_LEATHER` |
| 3 | `BRACERS` "Bracers" | Cuffs on both forearms, raised outer plate | `skyrpg_items:accessory/bracers` | `ARMOR_EQUIP_GENERIC` |

Hints for empty-slot tooltips: "Pendants and charms, worn at the neck", "Mantles and capes, worn on the
back", "Sashes and belts, worn at the waist", "Bracers and vambraces, worn on the forearms".
`skyrpg_items:accessories` is the union tag (`#skyrpg_items:accessory/amulet`, ...). **Tags are the rule**:
an item goes in a slot if it is in that slot's tag (tags sync to clients, so both sides agree). An item in
several tags uses the first one in the order above.

## 2. Storage

- Attachment `skyrpg_items:accessories`, value `AccessoryLoadout` (immutable record of 4 stacks, `EMPTY`
  for none). Builder: `.persistent(AccessoryLoadout.CODEC).copyOnDeath().syncWith(AccessoryLoadout.STREAM_CODEC, AttachmentSyncPredicate.allButTarget())`.
  No initializer; read with `getAttachedOrElse(LOADOUT, AccessoryLoadout.EMPTY)`.
- `CODEC` saves a map keyed by slot id (`{"amulet": <ItemStack.CODEC>, ...}`, empty slots left out), so
  slots can be added or reordered later. `STREAM_CODEC` is `ItemStack.OPTIONAL_LIST_STREAM_CODEC` mapped to the
  record, padded/trimmed to 4.
- `allButTarget()` syncs to everyone tracking the player, which is what the render layer needs. The player's own
  client gets its loadout only through inventory menu slots 46-49, like vanilla armour (equipment packets go to
  trackers only). The server resends that menu on join, respawn and dimension change, and every tick while no other
  container is open. A second copy through the attachment would arrive one round trip late and overwrite clicks the
  client had already predicted. In creative, the client then sends that stale stack back as a creative slot packet,
  so an accessory just taken off would come back while also being on the cursor.
- **Kept on death** and copied to the respawned player (`copyOnDeath`), like the purse and bank. They are
  not part of `Inventory`, so death drops, `/clear` and keepInventory never touch them. That is intended.
- Never mutate a stack inside the attachment. Every change is `setAttached(loadout.with(...))`. The
  client may set it too (Fabric only syncs from the server; the client copy is just prediction).
- `SkyRpgItems.normalizeInventory` also normalizes accessories: copy, `SkyItems.normalize(copy)`, and
  set it only if `!ItemStack.matches(copy, original)`.
- Rollout: new registered items mean every client needs the new jars (`tools/package_friend.py`) before
  the server restarts with them. New mixins and items need a restart, not a hot-swap.

## 3. Menu

`InventoryMenu` is built in the `Player` constructor on both sides. Vanilla indices: 0 result, 1-4 craft,
5-8 armour (x=8, y=8+18i), 9-35 inventory, 36-44 hotbar, 45 offhand.

- **`InventoryMenuMixin`** (main, `com.skyrpg.items.mixin`), `@Inject(method = "<init>", at = @At("TAIL"))`:
  `addSlot(new AccessoryMenuSlot(new AccessoryContainer(owner), slot, AccessoryMenuSlot.X, AccessoryMenuSlot.Y + 18 * i))`
  for each slot, giving **indices 46-49** (`46 + ordinal`). Item position x = **-21**, that is, left of the window.
- **`AccessoryContainer implements Container`**: a live view of the owner's attachment, size 4, max stack 1.
  It is created before attachments load, so it must read the attachment on every call. `getItem` returns the
  stored instance. `setItem` stores the stack as given. `removeItem` returns the stack and stores EMPTY (an
  over-full creative stack is split into copies, never shrunk in place). `setChanged()` re-sets the attachment
  with a loadout of **copies**, so in-place edits made by vanilla code still sync (the record compares stacks by
  identity, so the same instances would not count as a change), and on the server calls `Stats.refresh(player)`.
  `stillValid` is true.
- **`AccessoryMenuSlot extends Slot`**: `mayPlace` = `type.fits(stack)`. `getMaxStackSize()` = 1.
  `getNoItemIcon()` = `type.emptyIcon()`. `isActive()` = `!hidden`, where `setHidden` is client UI only.
  `setByPlayer(new, old)` plays `type.equipSound()` on the server when a different non-empty stack goes in.
- **Shift-click** (`@Inject` HEAD of `quickMoveStack`, cancellable): from an inventory or hotbar slot (9-44),
  an accessory whose slot is empty moves into it (`moveItemStackTo(stack, idx, idx + 1, false)` plus vanilla's
  bookkeeping), then return. Anything else falls through to vanilla. Shift-clicking out of 46-49 already works:
  vanilla's last branch moves it to 9-45.
- **Creative sync**: in the creative inventory tab, changes reach the server as `ServerboundSetCreativeModeSlotPacket`,
  and `ServerGamePacketListenerImpl.handleSetCreativeModeSlot` only accepts `1..45`.
  `ServerGamePacketListenerImplMixin`: `@ModifyConstant(method = "handleSetCreativeModeSlot", constant = @Constant(intValue = 45))`
  returns `AccessoryMenuSlot.END_INDEX - 1` (49). It is the only 45 in that method. Creative players are trusted
  here as everywhere: a non-fitting stack can land in a slot, so `Accessories.worn` filters it out for stats and rendering.
  Shift-clicking the creative trash slot sends EMPTY for every `inventoryMenu` slot, so it clears accessories too,
  the same as it clears armour.
- **Right-click to equip** (`UseItemCallback`, registered after `Abilities`): an accessory in either hand goes
  into its slot. If the slot is full, the two swap, like armour. The server does the work; the client returns
  SUCCESS for the swing.

## 4. Stats

`AccessoryStatSource implements Stats.Source`, `name()` = **"Accessories"**, registered in `Accessories.init()`.
For each slot it adds `SkyItems.definition(Accessories.worn(player, slot)).stats()`, player stats only, the
same as `ItemStatSource.add`. Also fix **`ItemStatSource`**: the held item must not count when it is an
accessory (`AccessorySlot.of(stack) != null`), or holding a pendant would give its stats. Set bonuses are
unchanged: `wornPieces` only reads the armour slots, and accessories have no `set`. The Stats page shows the
new "Accessories" source automatically.

## 5. Items

Registered like the others: `ModItems.Kind` gains `AMULET, CLOAK, BELT, BRACERS` after `WAND`, so
`isArmor()` is unchanged and `typeLabel` gives "Rare Amulet". The items are plain `register(id, kind, p -> p)`
(stack size 1, unbreakable and tooltip come from `register`). They have data JSONs in `skyrpg/items/`, use the
normal `ItemDefinition` format, have no `set`, and come with no recipes.

| id | Name | Slot | Rarity | Stats | Description | Curios price |
|---|---|---|---|---|---|---|
| `emberstone_pendant` | Emberstone Pendant | amulet | uncommon | health 20, defense 10 | A river stone that never quite cools, strung on waxed cord. | 900 |
| `starglass_amulet` | Starglass Amulet | amulet | rare | intelligence 40, crit_chance 3 | A chip of fallen sky set in pale silver. It hums when mana gathers. | 7500 |
| `wayfarer_mantle` | Wayfarer's Mantle | cloak | uncommon | speed 5, health 10 | Oiled wool that has walked every road out of Skyhaven. | 1200 |
| `duskwing_cloak` | Duskwing Cloak | cloak | epic | strength 20, crit_damage 15, speed 5 | Woven from the dark between two stars. It settles a moment after you do. | 24000 |
| `sunwoven_sash` | Sunwoven Sash | belt | uncommon | farming_fortune 10, farming_wisdom 3 | Wheat-gold linen, knotted the way the old harvesters knotted theirs. | 800 |
| `tinker_belt` | Tinker's Toolbelt | belt | uncommon | mining_speed 15, mining_fortune 5 | Loops for a hammer, a chisel and one good lamp. | 850 |
| `ironbark_bracers` | Ironbark Bracers | bracers | uncommon | foraging_fortune 8, strength 5 | Bark strips that set hard as iron. Good for felling, better for blocking. | 850 |
| `duelist_vambraces` | Duelist's Vambraces | bracers | rare | crit_chance 5, strength 10, defense 10 | Notched steel guards. Every nick is a parry that held. | 8000 |

All eight go into `economy/.../shops/curios.json` right after `mending_wand`. The lang file gets
`item.skyrpg_items.<id>` entries. `tools/art/generate_items.py` used to rewrite lang from `ICONS` only; its
`lang_names()` now adds every other definition in `ITEM_DATA` too: `list(ICONS)` first, then sorted extras, so the
existing order stays (other scripts can call `gi.lang_names()` instead of editing the lang file). Its docstring should also say that `generate_armor_depth.py` re-shades the worn layers and must run
after it, and that accessory art comes from `generate_accessories.py`.

## 6. Accessory rendering

- **State.** An `AvatarRendererMixin` (render mixins) with `@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))`
  sets `AccessoryRenderState.LOADOUT` (a `RenderStateDataKey`, stored through `EntityRenderState`'s
  `FabricRenderState.setData`) to `Accessories.loadout(player)` for a `Player`, and to `AccessoryLoadout.EMPTY`
  otherwise (mannequin NPCs share this renderer). It is EMPTY rather than null because `getDataOrDefault`
  returns a stored null instead of the default. It sets the value every time, so reused states never hold stale data.
  Fabric has no extraction event, so the mixin is required. The inventory preview goes through the same path,
  so accessories show in E automatically.
- **Layer.** Register it with `LivingEntityRenderLayerRegistrationCallback.EVENT` for `EntityTypes.PLAYER`.
  The constants live in **`EntityTypes`** in 26.x, not `EntityType`. `AccessoryLayer extends RenderLayer<AvatarRenderState, PlayerModel>`.
  It skips when `state.isInvisible` (like the cape); spectators already get no layers. For each slot with
  `type.fits(stack)` it calls `collector.submitModel(model, state, poseStack, RenderTypes.armorCutoutNoCull(texture), light, OverlayTexture.NO_OVERLAY, state.outlineColor)`.
- **Deferred-render trap.** `ModelFeatureRenderer` calls `model.setupAnim(state)` later, at draw time.
  Everything a model shows must therefore come from `state` inside `setupAnim`, and never from fields set before
  `submitModel`. The fix is one model instance per slot, plus a slim variant; never one model with parts toggled
  between submits.
- **Models.** Follow `PlayerCapeModel`: `AccessoryModel extends PlayerModel`, meshes from
  `PlayerModel.createMesh(CubeDeformation.NONE, slim).getRoot().clearRecursively()`, with cubes added under
  `body` / `right_arm` / `left_arm`. Register 4 slots × {wide, slim} layers with
  `ModelLayerRegistry.registerModelLayer(new ModelLayerLocation(SkyRpgItems.id("accessory_<slot>"), "wide"|"slim"), ...)`
  and bake them with `context.bakeLayer`. Pick the model by `state.skin.model() == PlayerModelType.SLIM`.
  In `setupAnim`, call `super.setupAnim(state)` first (it resets poses), then set the slot's own variants.
- **Fit variants.** Each slot bakes a `bare` and an `armored` variant of its cubes and toggles `visible` in
  `setupAnim`. Amulet, cloak and bracers use the armoured variant when `!state.chestEquipment.isEmpty()`. The belt
  uses it when chest armour is worn (leggings alone fit under the bare belt). See the lanes below.
- **Cloak.** Pivot at the top of the back. Sway comes from `state.capeFlap` / `capeLean` / `capeLean2` scaled
  to about 0.6, using the same math as `PlayerCapeModel.setupAnim`. These fields are filled for every player.
  The cloak hides when the chest slot has a wings equipment layer: check
  `EquipmentClientInfo.LayerType.WINGS` through `context.getEquipmentAssets()`, as `CapeLayer.hasLayer` does.
  While a cloak is worn, `LivingEntityFeatureRenderEvents.ALLOW_CAPE_RENDER` returns false.
  As built: a 10×16 cape, a collar roll, lapels with a 1×2 toggle clasp each, and shoulder drapes on the
  arms in the bare fit only (with armour the shoulders belong to the pauldrons).
- **Textures.** `skyrpg_items:textures/entity/accessory/<item path>.png`. All items of one slot share that
  slot's UV layout and texture size. Unused cubes are left transparent per item (cutout), which is how one
  geometry gives different shapes. Missing textures are skipped: check once with
  `Minecraft.getInstance().getResourceManager().getResource(id).isPresent()` and cache the result.
- **First person.** Bracers also show on your own hands. `@Inject` TAIL of `renderRightHand` / `renderLeftHand`
  (`(PoseStack, SubmitNodeCollector, int, Identifier, boolean)V`). Use a separate baked bracer arm part:
  `resetPose()`, `zRot = ±0.1f` as vanilla does, then
  `submitModelPart(part, poseStack, RenderTypes.armorCutoutNoCull(tex), light, OverlayTexture.NO_OVERLAY, null)`,
  using the bare variant and `Minecraft.getInstance().player`'s bracers.
- It is world rendering, so F1 doesn't change it. Other players see it through the synced attachment.

### Depth lanes (shared by both render builders)

Distances are outward from the skin surface, in model pixels. Vanilla: jacket 0.25, leggings 0.5, other armour 1.0.

| Where | Armour detail (§7) may use | Accessory bare / armored |
|---|---|---|
| Neck + upper chest centre (x -2..2, body y 0..5, front) | 1.0-1.3 | amulet 0.3-0.6 / 1.4-1.6 |
| Back face of the body | 1.0-1.3 | cloak 0.5 / 1.4 (plus the cape's own thickness) |
| Waist band (body y 9.5..12.5) | 1.0-1.1 | belt 0.55 / 1.2 |
| Forearms (arm y 4.5..9) | 1.0-1.2 | bracers 0.35 / 1.25 |
| Everywhere else (crests, pauldrons, knees, cuffs, toes) | 1.0-3.0 | none |

`generate_armor_depth.py`'s `check()` enforces the left column. It refuses a detail box that reaches into an
accessory lane, or that shares a face plane with the armour, the skin or another box, since the game would z-fight
there.

## 7. Armour depth

- Register `ArmorDetailLayer` through the same callback for `EntityTypes.PLAYER` only. Fabric's
  `createAvatarRenderers` hook reports every avatar renderer as the player type, mannequins included (NPCs may wear
  SkyRPG armour), so a separate `MANNEQUIN` check would never match. It reads `state.headEquipment / chestEquipment /
  legsEquipment / feetEquipment`. The set is the `Equippable` asset id: `stack.get(DataComponents.EQUIPPABLE).assetId()`
  is `skyrpg_items:<set>`, and only the 4 known sets render. **No invisibility check**: vanilla armour
  (`HumanoidArmorLayer`) stays visible on invisible players, and the plates must stay with it.
- Models: `ArmorDetailModel extends PlayerModel`, one LayerDefinition per set
  (`SkyRpgItems.id("armor_detail_<set>")`, variant "main"). There's no slim variant, because vanilla armour uses the
  same mesh on slim and wide players. The meshes are generated into `ArmorDetailMeshes.java` by
  `generate_armor_depth.py`; don't edit it by hand. Children are grouped by piece: `helmet` under `head`; `chest` under
  `body` and both arms; `legs` under `body` and both legs; `boots` under both legs. Bake one instance per
  (set, piece) and set that piece group's visibility **once in the constructor**. Constant visibility is
  safe with deferred rendering.
- Render type: `RenderTypes.armorCutoutNoCull(tex)`, or `armorCutoutNoCullGlint(tex)` when the stack `hasFoil()`
  (check what `EquipmentLayerRenderer` does and match it). Texture: `skyrpg_items:textures/entity/armor_detail/<set>.png`.
  An optional `<set>_glow.png` (Prospector's lamp) is drawn a second time with `RenderTypes.eyes`, so it glows in the dark.
  The detail only shows where `HumanoidArmorLayer.shouldRender(stack, slot)` would draw the armour.
- **Re-shaded worn layers**: `tools/art/generate_armor_depth.py` imports `generate_items as gi`. It wraps
  `gi.Sheet.render` to add a bevel tweak pass: lit top row and left edge on every side face, a darker bottom row,
  ±1 extra contrast where materials meet, and a dark seam under trims. It then calls `gi.ARMOR_SETS[set]()` and
  overwrites `textures/entity/equipment/humanoid{,_leggings}/<set>.png`. `generate_items.py` itself is not edited.

Per-set geometry (sizes in model pixels; match the existing palettes and icons):

| Set | Helmet | Chest (body + arms) | Legs | Boots |
|---|---|---|---|---|
| Harvester (straw, leaf, wheat) | Wide straw brim ring about 15×1×15 just above the eyes, raised leaf band, wheat sprig cross-plane on the left | Leaf collar ring, raised wheat-sheaf badge kept out of the amulet lane, rolled straw sleeve cuffs | Straw knee patches, belt ridge with buckle | Straw cuff ring, leather toe cap |
| Prospector (slate, copper, amber) | Copper ridge crest front to back, hard-hat brim all round with a deeper front peak, amber lamp box (optionally a second `RenderTypes.eyes` pass on the lamp pixels so it glows) | Raised copper suspenders and rivets, copper shoulder caps with one lame | Copper knee plates | Copper toe caps, cuff ring |
| Woodsman (oak, moss, hide, iron) | Thick moss fur roll round the cap, hanging ear flaps, oak-leaf sprig | Fur mantle collar over the shoulders, fur shoulder pads, laced front ridge, hide belt with iron buckle | Leather knee patches | Thick fur cuffs, crossed laces |
| Vanguard (steel, crimson, gold) | Crimson plume fin along the top, gold brow ridge, nose guard, cheek guards | Two-plate gold-trimmed pauldrons, centre keel and raised gold chevron (keel stays ≤1.3 in the amulet lane), no back cape (the cloak owns the back) | Knee cops with gold rivet, crimson tassets below the waist band | Layered sabaton toe plates, gold cuff |

## 8. Inventory UI

- **Panel.** Drawn by our code, not a texture. Survival `InventoryScreen`: rect `(leftPos-26, topPos+3)`, 28×80,
  where the rightmost 2 px tuck under the window. Draw it in `InventoryScreen.extractBackground` right before the
  window texture's `blit` (the only one in that method), so the window covers the tuck and the panel reads as
  attached. At HEAD, the darkened screen background would be drawn over it. Slot wells are 18×18 around each slot's x-1/y-1,
  with 4 px padding all round. The style copies the window: 1 px outline with cut corners, panel fill, and bevelled
  wells (shadow top-left, light bottom-right). Palette by `ThemePack.isActive()`:
  - themed: outline `0xFF2B3343`, fill `0xFF161B24`, well `0xFF0C0F15`, shadow `0xFF06080C`, light `0xFF232A37`
    (the theme pack's own inventory colours);
  - vanilla: outline `0xFF000000`, fill `0xFFC6C6C6`, bevel `0xFFFFFFFF`/`0xFF555555`, well `0xFF8B8B8B`,
    well bevel `0xFF373737`/`0xFFFFFFFF`.
  While the cursor carries an accessory, its slot's well gets `Theme.ACCENT_SOFT` fill and a 1 px `Theme.ACCENT` border.
- **Recipe book.** When it is open, the book sits where the panel would be. Each frame, call
  `setHidden(recipeBook.isVisible())` on the 4 slots and skip the panel. Read the book through an `@Accessor` on
  `AbstractRecipeBookScreen.recipeBookComponent`.
- **Clicking outside.** `AbstractContainerScreen.mouseClicked` / `mouseReleased` turn any click where
  `hasClickedOutside(...)` is true into slot -999 (a drop), **even on a slot**. Override `hasClickedOutside` so it
  returns false inside the panel rect. For the survival screen it is declared on `AbstractRecipeBookScreen`;
  `CreativeModeInventoryScreen` declares its own, so inject HEAD cancellable there.
- **Creative.** On the inventory tab, `selectTab` wraps every `player.inventoryMenu` slot in
  `CreativeModeInventoryScreen$SlotWrapper(Slot target, int index, int x, int y)`. Its fallback branch would put
  46-49 on top of the hotbar (y=112). Fix it with `@ModifyArg` on the `SlotWrapper.<init>(Lnet/minecraft/world/inventory/Slot;III)V`
  call in `selectTab`, indices 2 and 3, with the handler taking `(Slot, int, int, int)`. For
  `target instanceof AccessoryMenuSlot`, use the slot's own x and y: **-21** and **8 + 18·ordinal**, the same as
  in survival. (6 + 18·ordinal would put the top slot in the window texture's cut corner.) Also at TAIL,
  `setHidden(false)` on the 4 slots. The panel rect is `(leftPos-26, topPos+3)`, 28×80, drawn before the
  `extractBackground` blit only while `isInventoryOpen()`.
- **Un-hiding.** The survival screen also un-hides the slots when it closes (`ScreenEvents.remove`), so a menu left
  with the recipe book open never keeps hidden slots.
- **Empty-slot icons.** `skyrpg_items:container/slot/{amulet,cloak,belt,bracers}`, files under
  `assets/skyrpg_items/textures/gui/sprites/container/slot/` (the vanilla `gui` atlas loads `gui/sprites` from
  every namespace). They are 16×16 one-colour `#555555` outline silhouettes, the same convention as vanilla's armour
  silhouettes, which the theme leaves alone.
- **Tooltip.** Hovering an empty accessory slot with nothing carried shows `g.setComponentTooltipForNextFrame(font, lines, mx, my)`:
  the slot name in `Theme.ACCENT`, then its hint in grey. It uses vanilla tooltips (the theme pack styles them),
  not `Draw.queueTooltip`. Hover is the mouse over a slot's 18×18 well, not vanilla's hovered slot, because in creative
  that's a wrapper that doesn't say which accessory slot it wraps.

## 9. API contract

Foundation (`items`, main source set, package `com.skyrpg.items.accessory` unless noted). This is what was
built; it compiles.

```java
public enum AccessorySlot implements StringRepresentable {   // order = column order
    AMULET, CLOAK, BELT, BRACERS;
    public static final int COUNT = 4;                        // compile-time constant (= values().length)
    public static final Codec<AccessorySlot> CODEC;           // StringRepresentable.fromEnum
    public String id();                     // "amulet"  (= getSerializedName)
    public String displayName();            // "Amulet"
    public String hint();                   // "Pendants and charms, worn at the neck"
    public TagKey<Item> tag();              // skyrpg_items:accessory/amulet
    public Identifier emptyIcon();          // skyrpg_items:container/slot/amulet
    public Holder<SoundEvent> equipSound();
    public int menuIndex();                 // 46 + ordinal()
    public boolean fits(ItemStack stack);   // !stack.isEmpty() && stack.is(tag())
    public static AccessorySlot of(ItemStack stack);   // first slot it fits, else null
}

public record AccessoryLoadout(List<ItemStack> stacks) {   // always COUNT entries (padded/trimmed), immutable list
    public static final AccessoryLoadout EMPTY;
    public static final Codec<AccessoryLoadout> CODEC;        // {"amulet": ItemStack.CODEC, ...}, empty slots left out
    public static final StreamCodec<RegistryFriendlyByteBuf, AccessoryLoadout> STREAM_CODEC;   // OPTIONAL_LIST_STREAM_CODEC
    public ItemStack get(AccessorySlot slot);
    public AccessoryLoadout with(AccessorySlot slot, ItemStack stack);
    public boolean isEmpty();
    // equals() compares stacks by identity (ItemStack has no equals); that is what makes every new loadout sync.
}

public final class Accessories {
    public static final AttachmentType<AccessoryLoadout> LOADOUT;    // skyrpg_items:accessories
    public static final TagKey<Item> TAG;                            // skyrpg_items:accessories (union tag)
    public static void init();                                       // stat source, use-to-equip (called from SkyRpgItems)
    public static AccessoryLoadout loadout(Player player);           // both sides, any player, never null
    public static ItemStack get(Player player, AccessorySlot slot);  // stored stack (menu); don't mutate
    public static ItemStack worn(Player player, AccessorySlot slot); // get() if slot.fits(it), else EMPTY
    public static void set(Player player, AccessorySlot slot, ItemStack stack); // server: syncs + refreshes stats
    public static void normalize(ServerPlayer player);               // from SkyRpgItems.normalizeInventory
    static void playEquipSound(Player player, AccessorySlot slot);   // server only; used by the slot and right-click
}

public final class AccessoryContainer implements Container {
    public AccessoryContainer(Player owner);
    public Player owner();
}

public final class AccessoryMenuSlot extends Slot {
    public static final int FIRST_INDEX = 46, END_INDEX = 50;      // InventoryMenu slots 46..49 (compile-time constants)
    public static final int X = -21, Y = 8, SPACING = 18;          // survival positions (menu coordinates)
    public AccessoryMenuSlot(Container container, AccessorySlot type, int x, int y);
    public AccessorySlot type();
    public void setHidden(boolean hidden);                          // client UI only; isActive() = !hidden
}
// com.skyrpg.items.mixin: InventoryMenuMixin (slots + shift-click), ServerGamePacketListenerImplMixin (creative 45 -> 49)
// ModItems.Kind: + AMULET, CLOAK, BELT, BRACERS;  ModItems.EMBERSTONE_PENDANT, STARGLASS_AMULET, WAYFARER_MANTLE,
//   DUSKWING_CLOAK, SUNWOVEN_SASH, TINKER_BELT, IRONBARK_BRACERS, DUELIST_VAMBRACES
```

To find a slot on a menu: `player.inventoryMenu.getSlot(slot.menuIndex())`, or `menuSlot instanceof AccessoryMenuSlot a`
then `a.type()`. The client's `player.inventoryMenu` slots are long-lived (one menu per player), so UI code that hides
them must un-hide them again.

Client classes the builders own (the foundation creates the first three as `public static void init() {}` stubs
called from `SkyRpgItemsClient`, and `AccessoryRenderState` complete):

```java
com.skyrpg.items.client.accessory.AccessoryScreens.init()      // UI
com.skyrpg.items.client.render.AccessoryRendering.init()       // model layers, layer, cape event
com.skyrpg.items.client.armor.ArmorDepthRendering.init()       // model layers, layer
com.skyrpg.items.client.render.AccessoryRenderState            // RenderStateDataKey<AccessoryLoadout> LOADOUT;
                                                               // static AccessoryLoadout of(AvatarRenderState) -> EMPTY if unset
```

Mixin configs, all registered in `fabric.mod.json` (the two client ones with `"environment": "client"`):
`skyrpg_items.mixins.json` (`InventoryMenuMixin`, `ServerGamePacketListenerImplMixin`);
`items/src/client/resources/skyrpg_items.ui.mixins.json` (package `com.skyrpg.items.client.accessory.mixin`:
`AbstractRecipeBookScreenAccess`, `CreativeModeInventoryScreenMixin`, `InventoryScreenMixin`); and
`skyrpg_items.render.mixins.json` (package `com.skyrpg.items.client.render.mixin`: `AvatarRendererMixin`). The armour
depth registers through the layer callback and needs no mixin.

What each layer reads: the accessory layer reads `AccessoryRenderState.LOADOUT` plus vanilla `AvatarRenderState`
fields (`isInvisible`, `chestEquipment`, `skin.model()`, `capeFlap/capeLean/capeLean2`, `outlineColor`). The armour
layer reads only vanilla `HumanoidRenderState` equipment fields and `skin.model()`.

## 10. File ownership

Order: the **foundation** goes first and must compile with the stubs. Then the three builders work in parallel.
No file has two owners.

| Owner | Files |
|---|---|
| Foundation | `items/src/main/java/com/skyrpg/items/accessory/*` (AccessorySlot, AccessoryLoadout, Accessories, AccessoryContainer, AccessoryMenuSlot, AccessoryStatSource); `items/.../mixin/InventoryMenuMixin.java`, `ServerGamePacketListenerImplMixin.java`; `items/src/main/resources/skyrpg_items.mixins.json`; `fabric.mod.json` (adds `skyrpg_items.mixins.json` plus client configs `skyrpg_items.ui.mixins.json` and `skyrpg_items.render.mixins.json`); `ModItems`, `SkyRpgItems`, `ItemStatSource`; `SkyRpgItemsClient` (calls the 3 `init()`s); the 3 stub classes and both client mixin configs with empty `"client": []` (handed over below); data `skyrpg/items/<8>.json`, `tags/item/accessory/*.json`, `tags/item/accessories.json`; `lang/en_us.json`; `economy/.../shops/curios.json`; `tools/art/generate_items.py` (lang + docstring only); README (short Accessories note) |
| UI builder | `items/src/client/java/com/skyrpg/items/client/accessory/**` (AccessoryScreens, AccessoryPanel, `mixin/InventoryScreenMixin`, `mixin/CreativeModeInventoryScreenMixin`, `mixin/AbstractRecipeBookScreenAccess`, optional SlotWrapper invoker); `items/src/client/resources/skyrpg_items.ui.mixins.json`; `assets/skyrpg_items/textures/gui/sprites/container/slot/{amulet,cloak,belt,bracers}.png`; new `tools/art/generate_accessory_slots.py` |
| Accessory-render builder | `items/src/client/java/com/skyrpg/items/client/render/**` (AccessoryRendering, AccessoryRenderState, AccessoryLayer, AccessoryModel, `mixin/AvatarRendererMixin`); `items/src/client/resources/skyrpg_items.render.mixins.json`; new `tools/art/generate_accessories.py`; for the 8 accessories `textures/item/<id>.png`, `textures/entity/accessory/<id>.png`, `items/<id>.json`, `models/item/<id>.json` (`item/generated`) |
| Armour-depth builder | `items/src/client/java/com/skyrpg/items/client/armor/**` (ArmorDepthRendering, ArmorDetailLayer, ArmorDetailModel, per-set meshes); new `tools/art/generate_armor_depth.py`; `textures/entity/armor_detail/<set>.png`; the PNG outputs `textures/entity/equipment/humanoid{,_leggings}/<set>.png` |

Placeholders: so nothing shows a missing texture before the builders run, the foundation wrote temporary files the
builders overwrite: `textures/item/<id>.png` for the 8 accessories plus their `items/<id>.json` and
`models/item/<id>.json` (`item/generated`, `layer0` = `skyrpg_items:item/<id>`), and the 4 empty-slot silhouettes
under `textures/gui/sprites/container/slot/`. They belong to the builders named above.

Nobody touches `hub/`, `tools/builds/`, `tools/events/`, npcs data, `tools/theme/generate_theme.py` or the theme
pack, or git.

## 11. Art direction

Use the same engine as the items: `import generate_items as gi` and reuse `gi.render`, `gi.Material`, `gi.box`
and the palettes. Masks plus auto shading, with a 1 px coloured outline on icons. Worn textures use `faces=` so
shading stops at face seams. New palettes are fine (ember, starglass, silver, wool), five shades each, darkest first.

- Emberstone Pendant: waxed brown cord; a smooth dark-red stone with an amber core pixel.
- Starglass Amulet: pale silver chain; a faceted blue-violet gem with a white glint.
- Wayfarer's Mantle: muted teal-grey oiled wool, darker hem stripe, brass clasp, one patch.
- Duskwing Cloak: `VOID` violet-black with orchid/rift edge light, a jagged feather hem (cutout), silver clasp.
- Sunwoven Sash: wheat-gold linen, leaf-green trim, knotted tail at the hip (the belt's tail cube).
- Tinker's Toolbelt: dark leather, copper buckle, pouch with a hammer handle (Prospector palette).
- Ironbark Bracers: bark strips, iron rivets, moss binding (Woodsman palette).
- Duelist's Vambraces: polished steel guard, crimson lining, gold edge, notches (Vanguard palette).

All of this is original SkyRPG design, not copied from Hypixel.

## 12. Verification (compile only; people are on the dev server)

- `./gradlew :items:compileJava :items:compileClientJava :items:processResources :economy:processResources --continue`.
  That doesn't refresh `items/build/resources/client`, where the client mixin configs are. `:dev:runClient` builds it
  anyway, but anything that runs the class folders without Gradle needs `:items:processClientResources` first.
- Integration (2026-10-05): the full build passes; all six mixin targets apply under Mixin 0.17.4 offline and pass
  ASM's verifier. Every new item has its definition, item model, icon, worn texture, lang entry, slot tag and shop
  offer, and texture sizes match the Java meshes. Re-running the three art scripts reproduces the files in the repo
  byte for byte. A z-buffered preview of all four sets with their detail plus accessories (armoured and bare fits)
  showed no clipping.
- Art scripts accept `--preview DIR`; write to the scratchpad `.../scratchpad/accessories/` and look at it before
  committing to a look. Include a front/back/side figure with armour plus accessories, at 1× and 4×.
- Do not run `tools/mc.py`, RCON, `hotswap.py`, servers or clients. For the owner's later restart test: E and the
  creative inventory tab show the panel; shift-click works both ways; creative placement survives relog; stats show
  "Accessories"; a second player sees the accessories; the cloak sways and hides the vanilla cape and hides under
  elytra; invisibility hides accessories but not armour detail; bracers show in first person; the recipe book hides
  the panel; clicks on the panel never drop the carried item.
