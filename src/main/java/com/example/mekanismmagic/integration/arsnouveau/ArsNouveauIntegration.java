package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerAdapter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Optional Ars Nouveau integration that deliberately avoids hard-linking its
 * implementation classes. Filled mob jars store their captured entity in the
 * ars_nouveau:mob_jar data component as MobJarData#entityTag().
 */
public final class ArsNouveauIntegration implements EntityContainerAdapter {
    public static final ArsNouveauIntegration INSTANCE =
            new ArsNouveauIntegration();
    private static final ResourceLocation MOB_JAR_ITEM =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "mob_jar");
    private static final ResourceLocation MOB_JAR_COMPONENT =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "mob_jar");

    private ArsNouveauIntegration() {
    }

    @Override
    public String modId() {
        return ModCompatibility.ARS_NOUVEAU;
    }

    @Override
    public Optional<CapturedEntity> capturedEntity(ItemStack stack) {
        CompoundTag tag = entityTag(stack);
        if (tag == null) {
            return Optional.empty();
        }
        ResourceLocation entityId =
                ResourceLocation.tryParse(tag.getString("id"));
        return entityId == null ? Optional.empty()
                : Optional.of(new CapturedEntity(entityId, tag));
    }

    @Override
    public boolean empty(ItemStack stack) {
        return emptyMobJar(stack);
    }

    /**
     * Returns a defensive copy of the captured entity data, including
     * Occultism's optional spiritJob.factoryId field.
     */
    private static CompoundTag entityTag(ItemStack stack) {
        if (!isMobJar(stack)) {
            return null;
        }
        Object jarData = getMobJarData(stack);
        if (jarData == null) {
            return null;
        }
        Object entityTag = invokeNoArgs(jarData, "entityTag");
        if (entityTag instanceof Optional<?> optional) {
            entityTag = optional.orElse(null);
        }
        return entityTag instanceof CompoundTag tag ? tag.copy() : null;
    }

    /**
     * Removes only the captured mob component, leaving the empty jar item in
     * place for ritual sacrifice processing.
     */
    private static boolean emptyMobJar(ItemStack stack) {
        if (INSTANCE.capturedEntity(stack).isEmpty()) {
            return false;
        }
        DataComponentType<?> component = mobJarComponent();
        if (component == null) {
            return false;
        }
        stack.remove(component);
        return true;
    }

    private static boolean isMobJar(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return MOB_JAR_ITEM.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static Object getMobJarData(ItemStack stack) {
        DataComponentType<?> component = mobJarComponent();
        return component == null ? null : stack.get(component);
    }

    private static DataComponentType<?> mobJarComponent() {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.get(MOB_JAR_COMPONENT);
    }

    private static Object invokeNoArgs(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
