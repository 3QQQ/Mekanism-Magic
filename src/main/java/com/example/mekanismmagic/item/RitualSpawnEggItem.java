package com.example.mekanismmagic.item;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.CustomData;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Spawn egg fallback for Occultism entities without a registered SpawnEggItem,
 * and for ritual recipes whose target is an entity tag.
 */
public final class RitualSpawnEggItem extends Item {
    public static final String ENTITY_TAG = "ritual_entity_tag";
    private static final String LEGACY_ASSIGN_OWNER =
            "ritual_assign_owner";
    private static final String OWNER_MODE = "ritual_owner_mode";
    private static final String CLEAR_MOTION = "ritual_clear_motion";
    private static final String GENERATE_SPIRIT_NAME =
            "ritual_generate_spirit_name";
    private static final String CALLING_BOOK =
            "ritual_calling_book";
    private static final String INITIALIZE_SPIRIT_JOB =
            "ritual_initialize_spirit_job";
    private static final int OWNER_NONE = 0;
    private static final int OWNER_TAME = 1;
    private static final int OWNER_FAMILIAR_ONLY = 2;
    private static final String CHICKEN_FALLBACK_DENOMINATOR =
            "ritual_chicken_fallback_denominator";

    public RitualSpawnEggItem(Properties properties) {
        super(properties);
    }

    public static ItemStack forEntity(EntityType<?> entity, CompoundTag data) {
        ItemStack stack = new ItemStack(com.example.mekanismmagic.MekanismMagic.RITUAL_SPAWN_EGG.get());
        CompoundTag tag = data == null ? new CompoundTag() : data.copy();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity);
        if (id != null) {
            tag.putString("id", id.toString());
        }
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(tag));
        return stack;
    }

    public static ItemStack forOwnedEntity(
            EntityType<?> entity, CompoundTag data) {
        ItemStack stack = forEntity(entity, data);
        updateEntityData(stack, tag -> tag.putInt(
                OWNER_MODE, OWNER_TAME));
        return stack;
    }

    public static ItemStack forResurrectedEntity(
            EntityType<?> entity, CompoundTag data) {
        ItemStack stack = forEntity(entity, data);
        updateEntityData(stack, tag -> {
            tag.putInt(OWNER_MODE, OWNER_FAMILIAR_ONLY);
            tag.putBoolean(CLEAR_MOTION, true);
        });
        return stack;
    }

    public static ItemStack forOwnedEntityWithChickenFallback(
            EntityType<?> entity, CompoundTag data,
            int targetChanceDenominator) {
        ItemStack stack = forOwnedEntity(entity, data);
        int denominator = Math.max(1, targetChanceDenominator);
        updateEntityData(stack, tag -> tag.putInt(
                CHICKEN_FALLBACK_DENOMINATOR, denominator));
        return stack;
    }

    public static ItemStack withDeferredSpiritName(ItemStack stack) {
        updateEntityData(stack, tag -> tag.putBoolean(
                GENERATE_SPIRIT_NAME, true));
        return stack;
    }

    public static ItemStack withCallingBook(
            ItemStack stack, ItemStack callingBook,
            HolderLookup.Provider registries) {
        if (!callingBook.isEmpty()) {
            Tag saved = callingBook.copyWithCount(1)
                    .saveOptional(registries);
            updateEntityData(stack, tag -> tag.put(
                    CALLING_BOOK, saved));
        }
        return stack;
    }

    public static ItemStack withJobInitialization(
            ItemStack stack, ResourceLocation jobId) {
        if (jobId != null) {
            updateEntityData(stack, tag -> tag.putString(
                    INITIALIZE_SPIRIT_JOB, jobId.toString()));
        }
        return stack;
    }

    public static ItemStack forTag(TagKey<EntityType<?>> entityTag, CompoundTag data) {
        ItemStack stack = new ItemStack(com.example.mekanismmagic.MekanismMagic.RITUAL_SPAWN_EGG.get());
        CompoundTag tag = data == null ? new CompoundTag() : data.copy();
        tag.putString(ENTITY_TAG, entityTag.location().toString());
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(tag));
        return stack;
    }

    public static ItemStack forOwnedTag(
            TagKey<EntityType<?>> entityTag, CompoundTag data) {
        ItemStack stack = forTag(entityTag, data);
        updateEntityData(stack, tag -> tag.putInt(
                OWNER_MODE, OWNER_TAME));
        return stack;
    }

    public static ResourceLocation entityId(ItemStack stack) {
        CompoundTag data = data(stack);
        return ResourceLocation.tryParse(data.getString("id"));
    }

    public static ResourceLocation entityTag(ItemStack stack) {
        CompoundTag data = data(stack);
        return ResourceLocation.tryParse(data.getString(ENTITY_TAG));
    }

    private static CompoundTag data(ItemStack stack) {
        CustomData data = stack.get(DataComponents.ENTITY_DATA);
        return data == null || data.isEmpty() ? new CompoundTag() : data.copyTag();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ResourceLocation entityId = entityId(stack);
        if (entityId != null) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                    .getOptional(entityId).orElse(null);
            Component name = type == null
                    ? Component.literal(entityId.toString())
                    : type.getDescription();
            tooltip.add(Component.translatable(
                    "tooltip.mekanism_magic.ritual_spawn_egg.entity",
                    name).withStyle(ChatFormatting.GRAY));
        }
        ResourceLocation tag = entityTag(stack);
        if (tag != null) {
            tooltip.add(Component.translatable(
                    "tooltip.mekanism_magic.ritual_spawn_egg.entity_tag",
                    Component.literal("#" + tag))
                    .withStyle(ChatFormatting.GRAY));
        }
        CompoundTag entityData = data(stack);
        ResourceLocation job = ResourceLocation.tryParse(
                entityData.getString(INITIALIZE_SPIRIT_JOB));
        if (job != null) {
            Component jobName = "occultism".equals(job.getNamespace())
                    ? Component.translatable(
                    "job.occultism." + job.getPath())
                    : Component.literal(job.toString());
            tooltip.add(Component.translatable(
                    "tooltip.mekanism_magic.ritual_spawn_egg.job",
                    jobName).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        int ownerMode = entityData.contains(OWNER_MODE)
                ? entityData.getInt(OWNER_MODE)
                : entityData.getBoolean(LEGACY_ASSIGN_OWNER)
                ? OWNER_TAME : OWNER_NONE;
        if (ownerMode != OWNER_NONE) {
            tooltip.add(Component.translatable(ownerMode == OWNER_TAME
                            ? "tooltip.mekanism_magic.ritual_spawn_egg.owned"
                            : "tooltip.mekanism_magic.ritual_spawn_egg.familiar")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (entityData.contains(CALLING_BOOK, Tag.TAG_COMPOUND)) {
            tooltip.add(Component.translatable(
                            "tooltip.mekanism_magic.ritual_spawn_egg.calling_book")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = context.getItemInHand();
        EntityType<?> type = resolveEntity(held, level);
        if (type == null) {
            return InteractionResult.FAIL;
        }
        CompoundTag sourceData = data(held);
        int ownerMode = sourceData.contains(OWNER_MODE)
                ? sourceData.getInt(OWNER_MODE)
                : sourceData.getBoolean(LEGACY_ASSIGN_OWNER)
                ? OWNER_TAME : OWNER_NONE;
        boolean clearMotion = sourceData.getBoolean(CLEAR_MOTION);
        boolean generateSpiritName = sourceData.getBoolean(
                GENERATE_SPIRIT_NAME);
        boolean hasCallingBook = sourceData.contains(
                CALLING_BOOK, Tag.TAG_COMPOUND);
        ItemStack callingBook = hasCallingBook
                ? ItemStack.parseOptional(level.registryAccess(),
                sourceData.getCompound(CALLING_BOOK))
                : ItemStack.EMPTY;
        if (hasCallingBook && callingBook.isEmpty()) {
            return InteractionResult.FAIL;
        }
        boolean initializeJob = sourceData.contains(
                INITIALIZE_SPIRIT_JOB, Tag.TAG_STRING);
        ResourceLocation spiritJobId = initializeJob
                ? ResourceLocation.tryParse(sourceData.getString(
                INITIALIZE_SPIRIT_JOB)) : null;
        if (initializeJob && spiritJobId == null) {
            return InteractionResult.FAIL;
        }
        sourceData.remove(ENTITY_TAG);
        sourceData.remove(LEGACY_ASSIGN_OWNER);
        sourceData.remove(OWNER_MODE);
        sourceData.remove(CLEAR_MOTION);
        sourceData.remove(GENERATE_SPIRIT_NAME);
        sourceData.remove(CHICKEN_FALLBACK_DENOMINATOR);
        sourceData.remove(CALLING_BOOK);
        sourceData.remove(INITIALIZE_SPIRIT_JOB);
        if (initializeJob) {
            // SummonSpiritWithJobRitual creates and initializes a fresh job;
            // loading the serialized factory id first would create an
            // uninitialized placeholder and can leave worker caches empty.
            sourceData.remove("spiritJob");
        }
        ResourceLocation resolvedId =
                BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (resolvedId != null) {
            sourceData.putString("id", resolvedId.toString());
        }
        ItemStack spawnContext = held.copyWithCount(1);
        spawnContext.set(DataComponents.ENTITY_DATA,
                CustomData.of(sourceData));
        Entity spawned = type.spawn(level, spawnContext,
                context.getPlayer(),
                context.getClickedPos().relative(context.getClickedFace()),
                MobSpawnType.SPAWN_EGG, true, false);
        if (spawned == null || !spawned.isAddedToLevel()) {
            if (spawned != null) {
                spawned.discard();
            }
            return InteractionResult.FAIL;
        }
        if (clearMotion) {
            spawned.setDeltaMovement(Vec3.ZERO);
        }
        if (generateSpiritName) {
            String generatedName = generateSpiritName(held);
            if (!generatedName.isBlank()) {
                spawned.setCustomName(Component.literal(generatedName));
            }
        }
        if (ownerMode != OWNER_NONE) {
            assignOwner(spawned, context.getPlayer(), ownerMode);
        }
        if (initializeJob
                && !initializeSpiritJob(spawned, spiritJobId)) {
            spawned.discard();
            return InteractionResult.FAIL;
        }
        if (!callingBook.isEmpty()) {
            if (!isOccultismSpirit(spawned)
                    || !bindCallingBook(callingBook, spawned)) {
                spawned.discard();
                return InteractionResult.FAIL;
            }
            if (!giveCallingBook(level, spawned,
                    context.getPlayer(), callingBook)) {
                spawned.discard();
                return InteractionResult.FAIL;
            }
        }
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            held.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    private static EntityType<?> resolveEntity(ItemStack stack, ServerLevel level) {
        CompoundTag data = data(stack);
        int fallbackDenominator = data.getInt(
                CHICKEN_FALLBACK_DENOMINATOR);
        if (fallbackDenominator > 1
                && level.random.nextInt(fallbackDenominator) != 0) {
            return EntityType.CHICKEN;
        }
        ResourceLocation tagId = entityTag(stack);
        if (tagId != null) {
            TagKey<EntityType<?>> tag = TagKey.create(
                    net.minecraft.core.registries.Registries.ENTITY_TYPE, tagId);
            List<EntityType<?>> candidates = BuiltInRegistries.ENTITY_TYPE.getTag(tag)
                    .stream().flatMap(named -> named.stream())
                    .map(Holder::value).toList();
            if (!candidates.isEmpty()) {
                return candidates.get(level.random.nextInt(candidates.size()));
            }
        }
        ResourceLocation id = entityId(stack);
        return id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
    }

    private static void updateEntityData(
            ItemStack stack,
            java.util.function.Consumer<CompoundTag> update) {
        CompoundTag tag = data(stack);
        update.accept(tag);
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(tag));
    }

    private static void assignOwner(
            Entity entity, Player player, int ownerMode) {
        if (entity == null || player == null) {
            return;
        }
        if (ownerMode == OWNER_TAME
                && entity instanceof TamableAnimal tamable) {
            tamable.tame(player);
        }
        invokeOwnerSetter(entity, "setFamiliarOwner", player);
        if (entity.getClass().getName().equals(
                "com.klikli_dev.occultism.common.entity.spirit."
                        + "demonicpartner.DemonicPartner")) {
            invokeOwnerSetter(entity, "setOwnerUUID", player.getUUID());
        }
    }

    private static void invokeOwnerSetter(
            Entity entity, String name, Object owner) {
        for (Method method : entity.getClass().getMethods()) {
            if (!method.getName().equals(name)
                    || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0]
                    .isInstance(owner)) {
                continue;
            }
            try {
                method.invoke(entity, owner);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Ownership is best-effort for optional familiar types.
            }
            return;
        }
    }

    private static boolean bindCallingBook(
            ItemStack callingBook, Entity spirit) {
        try {
            Class<?> utility = Class.forName(
                    "com.klikli_dev.occultism.util.ItemNBTUtil");
            Method uuidSetter = utility.getMethod(
                    "setSpiritEntityUUID", ItemStack.class,
                    java.util.UUID.class);
            Method nameSetter = utility.getMethod(
                    "setBoundSpiritName", ItemStack.class,
                    String.class);
            uuidSetter.invoke(null, callingBook, spirit.getUUID());
            nameSetter.invoke(null, callingBook,
                    spirit.getName().getString());
            return true;
        } catch (ReflectiveOperationException | RuntimeException
                 | LinkageError ignored) {
            return false;
        }
    }

    private static boolean initializeSpiritJob(
            Entity spirit, ResourceLocation jobId) {
        if (!isOccultismSpirit(spirit) || jobId == null) {
            return false;
        }
        try {
            Class<?> jobs = Class.forName(
                    "com.klikli_dev.occultism.registry."
                            + "OccultismSpiritJobs");
            Object registry = jobs.getField("REGISTRY").get(null);
            Method registryGet = compatibleOneArgumentMethod(
                    registry.getClass(), "get", jobId);
            Object factory = registryGet == null ? null
                    : registryGet.invoke(registry, jobId);
            if (factory == null) {
                return false;
            }
            Method create = compatibleOneArgumentMethod(
                    factory.getClass(), "create", spirit);
            Object job = create == null ? null
                    : create.invoke(factory, spirit);
            if (job == null) {
                return false;
            }
            job.getClass().getMethod("init").invoke(job);
            Method setJob = compatibleOneArgumentMethod(
                    spirit.getClass(), "setJob", job);
            if (setJob == null) {
                return false;
            }
            setJob.invoke(spirit, job);
            return true;
        } catch (ReflectiveOperationException | RuntimeException
                 | LinkageError ignored) {
            return false;
        }
    }

    private static Method compatibleOneArgumentMethod(
            Class<?> type, String name, Object argument) {
        if (type == null || argument == null) {
            return null;
        }
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0]
                    .isInstance(argument)) {
                return method;
            }
        }
        return null;
    }

    private static boolean isOccultismSpirit(Entity entity) {
        for (Class<?> type = entity == null ? null
                : entity.getClass(); type != null;
             type = type.getSuperclass()) {
            if (type.getName().equals("com.klikli_dev.occultism.common."
                    + "entity.spirit.SpiritEntity")) {
                return true;
            }
        }
        return false;
    }

    private static boolean giveCallingBook(
            ServerLevel level, Entity spirit,
            Player player, ItemStack callingBook) {
        if (player != null) {
            if (player.addItem(callingBook)) {
                return true;
            }
            ItemEntity dropped = player.drop(callingBook, false);
            return dropped != null && dropped.isAddedToLevel();
        }
        return level.addFreshEntity(new ItemEntity(level,
                spirit.getX(), spirit.getY(), spirit.getZ(),
                callingBook));
    }

    private static String generateSpiritName(ItemStack source) {
        try {
            Class<?> utility = Class.forName(
                    "com.klikli_dev.occultism.util.ItemNBTUtil");
            Method getter = utility.getMethod(
                    "getBoundSpiritName", ItemStack.class);
            Object value = getter.invoke(null, source.copyWithCount(1));
            return value instanceof String name ? name : "";
        } catch (ReflectiveOperationException | RuntimeException
                 | LinkageError ignored) {
            return "";
        }
    }
}
