package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.client.gui.MachineSettingsSections;
import com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerProcessorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerProcessorContainer;
import com.example.mekanismmagic.integration.naturesaura.AuraFieldInterfaceBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.AuraFieldInterfaceContainer;
import com.example.mekanismmagic.integration.naturesaura.NatureAltarFactoryBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.NatureAltarFactoryContainer;
import com.example.mekanismmagic.integration.naturesaura.NatureAltarProcessorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.NatureAltarProcessorContainer;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraGeneratorBlockEntity;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** Optional Nature controls in the shared settings window; all mutations retain their existing server routes. */
public final class NatureMachineSettingsSection implements MachineSettingsSections.Section {
    private static final NatureMachineSettingsSection INSTANCE = new NatureMachineSettingsSection();
    private static final Map<Class<?>, AltarControl> ALTARS = new LinkedHashMap<>();

    private NatureMachineSettingsSection() { }

    public static void register() {
        registerAltar(NatureAltarProcessorBlockEntity.class, NatureAltarProcessorBlockEntity::getMatchingRecipeCount,
                NatureAltarProcessorBlockEntity::getSelectedRecipeOutput, NatureAltarProcessorContainer.SELECT_NEXT_RECIPE);
        registerAltar(NatureAltarFactoryBlockEntity.class, NatureAltarFactoryBlockEntity::getMatchingRecipeCount,
                NatureAltarFactoryBlockEntity::getSelectedRecipeOutput, NatureAltarFactoryContainer.SELECT_NEXT_RECIPE);
        MachineSettingsSections.register(INSTANCE);
    }

    /** Extras calls this from its own screen, so this optional Nature entry never links its absent classes. */
    public static <T extends TileEntityMekanism> void registerAltar(Class<T> type, ToIntFunction<T> matches,
                                                                  Function<T, ItemStack> output, int buttonId) {
        ALTARS.putIfAbsent(type, new AltarControl(tile -> matches.applyAsInt(type.cast(tile)),
                tile -> output.apply(type.cast(tile)), buttonId));
    }

    @Override public String id() { return "work"; }
    @Override public Component title() { return Component.translatable("gui.mekanism_magic.settings.work"); }
    @Override public boolean supports(TileEntityMekanism tile) {
        return tile instanceof NatureAuraGeneratorBlockEntity || tile instanceof AuraFieldInterfaceBlockEntity
                || tile instanceof AnimalSpawnerProcessorBlockEntity || tile != null && ALTARS.containsKey(tile.getClass());
    }
    @Override public int preferredHeight(TileEntityMekanism tile) { return 22; }

    @Override public void build(MachineSettingsSections.Context context) {
        var tile = context.tile();
        if (tile instanceof NatureAuraGeneratorBlockEntity generator) {
            context.add(new ControlButton(context, NatureAuraGeneratorBlockEntity.CYCLE_AURA_TYPE_BUTTON_ID,
                    generator::canCycleAuraTypeMode,
                    () -> Component.translatable("gui.mekanism_magic.aura_mode.title").copy().append(": ")
                            .append(auraTypeLabel(generator.getSelectedAuraTypeMode())),
                    () -> Component.translatable(generator.canCycleAuraTypeMode()
                            ? "gui.mekanism_magic.aura_mode.cycle" : "gui.mekanism_magic.aura_mode.locked")));
        } else if (tile instanceof AuraFieldInterfaceBlockEntity field) {
            context.add(new ControlButton(context, AuraFieldInterfaceContainer.CYCLE_MODE_BUTTON, () -> true,
                    () -> Component.translatable("gui.mekanism_magic.aura_mode", Component.translatable(
                            field.getFieldMode().translationKey())),
                    () -> Component.translatable("gui.mekanism_magic.aura_mode", Component.translatable(
                            field.getFieldMode().translationKey()))));
        } else if (tile instanceof AnimalSpawnerProcessorBlockEntity animal) {
            context.add(new ControlButton(context, AnimalSpawnerProcessorContainer.SELECT_NEXT_RECIPE,
                    () -> animal.getMatchingRecipeCount() > 1,
                    () -> Component.translatable("gui.mekanism_magic.animal_spawner.target", targetName(animal)),
                    () -> Component.translatable("gui.mekanism_magic.animal_spawner.select")));
        } else {
            AltarControl altar = ALTARS.get(tile.getClass());
            if (altar == null) return;
            Supplier<Component> result = () -> recipeName(altar.output().apply(tile));
            context.add(new ControlButton(context, altar.buttonId(), () -> altar.matches().applyAsInt(tile) > 1,
                    () -> Component.translatable("gui.mekanism_magic.settings.recipe", result.get()),
                    () -> Component.translatable("tooltip.mekanism_magic.nature_altar.choose_recipe", result.get())));
        }
    }

    public static Component auraTypeLabel(int mode) {
        return Component.translatable("gui.mekanism_magic.aura_mode." + switch (mode) {
            case 1 -> "overworld";
            case 2 -> "nether";
            case 3 -> "end";
            default -> "auto";
        });
    }

    public static Component targetName(AnimalSpawnerProcessorBlockEntity tile) {
        var id = tile.getSelectedEntityId();
        return id == null ? Component.translatable("gui.mekanism_magic.panel.none")
                : BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(type -> type.getDescription())
                        .orElseGet(() -> Component.literal(id.toString()));
    }

    public static Component recipeName(ItemStack output) {
        return output == null || output.isEmpty() ? Component.translatable("gui.mekanism_magic.nature_altar.no_recipe")
                : output.getHoverName();
    }

    private record AltarControl(ToIntFunction<TileEntityMekanism> matches,
                                Function<TileEntityMekanism, ItemStack> output, int buttonId) { }

    private static final class ControlButton extends MekanismButton {
        private final int actionId;
        private final BooleanSupplier allowed;
        private final Supplier<Component> label;
        private final Supplier<Component> explanation;
        private Component previousLabel;
        private Component previousTooltip;

        private ControlButton(MachineSettingsSections.Context context, int actionId, BooleanSupplier allowed,
                              Supplier<Component> label, Supplier<Component> explanation) {
            super(context.gui(), context.x(), context.y() + 2, context.width(),
                    Math.min(14, Math.max(1, context.height() - 2)), Component.empty(),
                    (element, mouseX, mouseY) -> {
                        if (!allowed.getAsBoolean() || Minecraft.getInstance().gameMode == null) return false;
                        Minecraft.getInstance().gameMode.handleInventoryButtonClick(context.gui().getMenu().containerId, actionId);
                        return true;
                    }, null);
            this.actionId = actionId;
            this.allowed = allowed;
            this.label = label;
            this.explanation = explanation;
            refresh();
        }

        private void refresh() {
            active = allowed.getAsBoolean();
            Component nextLabel = label.get();
            Component nextTooltip = nextLabel.copy().append("\n").append(explanation.get());
            if (!nextLabel.equals(previousLabel)) setMessage(nextLabel);
            if (!nextTooltip.equals(previousTooltip)) setTooltip(Tooltip.create(nextTooltip));
            previousLabel = nextLabel;
            previousTooltip = nextTooltip;
        }
        @Override public void tick() { super.tick(); refresh(); }
        @Override public boolean isMouseOver(double mouseX, double mouseY) {
            return visible && mouseX >= getX() && mouseX < getRight() && mouseY >= getY() && mouseY < getBottom();
        }
    }
}
