package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerRegistry;
import com.example.mekanismmagic.integration.common.inventory.StackedOutputHelper;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.hollingsworth.arsnouveau.api.ANFakePlayer;
import com.hollingsworth.arsnouveau.common.lib.EntityTags;
import com.hollingsworth.arsnouveau.setup.config.Config;
import com.hollingsworth.arsnouveau.common.items.data.MobJarData;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Simulates Drygmy loot production from captured entities without placing
 * those entities into the world.
 */
public final class DrygmySimulatorBlockEntity
        extends ArsSourceMachineBlockEntity {
    public static final int JAR_SLOT_START = 0;
    public static final int JAR_SLOT_COUNT = 9;
    public static final int OUTPUT_SLOT_START = 9;
    public static final int OUTPUT_SLOT_COUNT = 27;
    private static final int OUTPUT_LIMIT = 256;
    private static final ResourceLocation PROCESS_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "mekanism_magic", "drygmy_simulation");

    private List<BasicInventorySlot> jarSlots;
    private List<BasicInventorySlot> outputSlots;
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    private String pendingSignature = "";

    public DrygmySimulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        jarSlots = new ArrayList<>(JAR_SLOT_COUNT);
        outputSlots = new ArrayList<>(OUTPUT_SLOT_COUNT);
        IContentsListener inputListener = () -> {
            pendingOutputs.clear();
            pendingSignature = "";
            progress = 0;
            listener.onContentsChanged();
        };
        for (int index = 0; index < JAR_SLOT_COUNT; index++) {
            jarSlots.add(registerLogicalSlot(helper,
                    JAR_SLOT_START + index,
                    new PersistentJarSlot(inputListener,
                            24 + index * 18, 16)));
        }
        for (int index = 0; index < OUTPUT_SLOT_COUNT; index++) {
            int column = index % 9;
            int row = index / 9;
            BasicInventorySlot slot = registerLogicalSlot(helper,
                    OUTPUT_SLOT_START + index,
                    new DrygmyOutputSlot(listener,
                            24 + column * 18, 46 + row * 18));
            outputSlots.add(slot);
            if (index == 0) {
                outputSlot = slot;
            }
        }
        IInventorySlot module = addSourceConversionModuleSlot(
                helper, listener, 20, 106);
        setupArsItemIO(jarSlots, outputSlots, List.of(module));
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        return Optional.empty();
    }

    @Override
    protected int baseEnergyPerTick() {
        return 800;
    }

    @Override
    protected int energySlotX() {
        return 188;
    }

    @Override
    protected int energySlotY() {
        return 106;
    }

    @Override
    public boolean mekanismMagicSupportsPatternAutomation() {
        return false;
    }

    @Override
    public List<IInventorySlot> mekanismMagicPatternInputs() {
        return List.of();
    }

    @Override
    public List<IInventorySlot> mekanismMagicPersistentInputs() {
        List<IInventorySlot> persistent = new ArrayList<>(jarSlots);
        if (sourceConversionModuleSlot != null) {
            persistent.add(sourceConversionModuleSlot);
        }
        return List.copyOf(persistent);
    }

    @Override
    protected boolean onUpdateServer() {
        boolean changed = nativeBaseUpdate();
        tickEjectorAdditional(10);
        setActive(false);
        if (!(level instanceof ServerLevel serverLevel)) {
            return changed;
        }
        String signature = jarSignature();
        if (signature.isEmpty()) {
            resetProcess();
            return changed;
        }
        if (!signature.equals(pendingSignature)
                || pendingOutputs.isEmpty()) {
            prepareOutputs(serverLevel, signature);
        }
        if (pendingOutputs.isEmpty()
                || !canAccept(pendingOutputs)) {
            return changed;
        }
        MachineRecipeResult process = processResult();
        if (!hasRecipeResources(process)) {
            return changed;
        }
        long usage = energyUsagePerTick(process);
        if (energyContainer == null
                || energyContainer.getEnergy() < usage) {
            return changed;
        }
        setActive(true);
        energyContainer.extract(usage, Action.EXECUTE,
                AutomationType.INTERNAL);
        progress++;
        if (progress >= progressRequired) {
            if (!consumeRecipeResources(process)
                    || !insertOutputs(pendingOutputs)) {
                progress = 0;
                setActive(false);
                return changed;
            }
            progress = 0;
            pendingOutputs.clear();
            changed = true;
        }
        return changed;
    }

    private void prepareOutputs(ServerLevel serverLevel,
                                String signature) {
        pendingOutputs.clear();
        pendingSignature = signature;
        progress = 0;
        progressRequired = Math.max(1,
                mekanism.common.util.MekanismUtils.getTicks(this,
                        baseProcessDuration()));

        List<ItemStack> possibleLoot = new ArrayList<>();
        Set<EntityType<?>> uniqueTypes = new HashSet<>();
        int entityCount = 0;
        int experience = 0;
        for (BasicInventorySlot slot : jarSlots) {
            Optional<CapturedEntity> captured =
                    EntityContainerRegistry.capturedEntity(
                            slot.getStack());
            if (captured.isEmpty()) {
                continue;
            }
            LivingEntity entity =
                    createLivingEntity(serverLevel, captured.get());
            if (entity == null
                    || entity.getType().is(EntityTags.DRYGMY_BLACKLIST)) {
                if (entity != null) {
                    entity.discard();
                }
                continue;
            }
            entityCount++;
            uniqueTypes.add(entity.getType());
            possibleLoot.addAll(lootFor(serverLevel, entity));
            experience += Math.max(0,
                    entity.getExperienceReward(serverLevel,
                            ANFakePlayer.getPlayer(serverLevel)));
            entity.discard();
        }
        if (possibleLoot.isEmpty()) {
            return;
        }
        int bonus = uniqueTypes.size()
                * Config.DRYGMY_UNIQUE_BONUS.get()
                + Math.min(Config.DRYGMY_QUANTITY_CAP.get(),
                entityCount);
        int targetItems = Math.max(1,
                Config.DRYGMY_BASE_ITEM.get() + bonus);
        int produced = 0;
        while (produced < targetItems) {
            ItemStack selected = possibleLoot.get(
                    serverLevel.random.nextInt(possibleLoot.size()))
                    .copy();
            if (selected.isEmpty()) {
                continue;
            }
            pendingOutputs.add(selected);
            produced += selected.getCount();
        }
        addExperienceGems(experience / 4);
    }

    private List<ItemStack> lootFor(ServerLevel level,
                                    LivingEntity entity) {
        net.minecraft.world.entity.player.Player player =
                ANFakePlayer.getPlayer(level);
        net.minecraft.world.damagesource.DamageSource damage =
                level.damageSources().playerAttack(player);
        LootTable lootTable = level.getServer()
                .reloadableRegistries()
                .getLootTable(entity.getLootTable());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN,
                        entity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE,
                        damage)
                .withOptionalParameter(
                        LootContextParams.ATTACKING_ENTITY, player)
                .withOptionalParameter(
                        LootContextParams.DIRECT_ATTACKING_ENTITY,
                        damage.getDirectEntity())
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER,
                        player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.ENTITY);
        return List.copyOf(lootTable.getRandomItems(params));
    }

    private LivingEntity createLivingEntity(
            ServerLevel level, CapturedEntity captured) {
        CompoundTag tag = captured.entityData();
        tag.putString("id", captured.entityId().toString());
        Entity entity = EntityType.create(tag, level).orElse(null);
        if (!(entity instanceof LivingEntity living)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        living.setPos(worldPosition.getCenter());
        return living;
    }

    private void addExperienceGems(int experience) {
        if (experience <= 3) {
            return;
        }
        int greater = experience / 12;
        int remainder = experience - greater * 12;
        int normal = Math.ceilDiv(remainder, 3);
        if (greater > 0) {
            pendingOutputs.add(new ItemStack(
                    ItemsRegistry.GREATER_EXPERIENCE_GEM.get(),
                    greater));
        }
        if (normal > 0) {
            pendingOutputs.add(new ItemStack(
                    ItemsRegistry.EXPERIENCE_GEM.get(), normal));
        }
    }

    private MachineRecipeResult processResult() {
        return new MachineRecipeResult(
                PROCESS_ID, ItemStack.EMPTY, baseProcessDuration(),
                List.of(), -1, -1, null, null,
                Map.of(ArsNouveauMachineConfig.SOURCE_RESOURCE,
                        Math.max(0, Config.DRYGMY_MANA_COST.get())));
    }

    private static int baseProcessDuration() {
        return Math.max(100,
                Config.DRYGMY_MAX_PROGRESS.get() * 20);
    }

    private String jarSignature() {
        StringBuilder builder = new StringBuilder();
        for (BasicInventorySlot slot : jarSlots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()
                    && EntityContainerRegistry.isFilled(stack)) {
                builder.append(stack.getItemHolder())
                        .append(':')
                        .append(stack.getComponentsPatch())
                        .append('|');
            }
        }
        return builder.toString();
    }

    private boolean canAccept(List<ItemStack> stacks) {
        return StackedOutputHelper.canAccept(
                outputSlots, stacks, OUTPUT_LIMIT);
    }

    private boolean insertOutputs(List<ItemStack> stacks) {
        return StackedOutputHelper.insertAll(
                outputSlots, stacks, OUTPUT_LIMIT);
    }

    private void resetProcess() {
        pendingOutputs.clear();
        pendingSignature = "";
        progress = 0;
        progressRequired = 1;
    }

    int seedDevelopmentTest(ServerLevel serverLevel,
                            ResourceLocation entityId) {
        EntityType<?> type =
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getOptional(entityId)
                        .orElse(null);
        if (type == null) {
            return 0;
        }
        CompoundTag entityTag = new CompoundTag();
        entityTag.putString("id", entityId.toString());
        ItemStack jar = new ItemStack(BlockRegistry.MOB_JAR.get());
        jar.set(DataComponentRegistry.MOB_JAR.get(),
                new MobJarData(entityTag, null));
        jarSlots.getFirst().setStack(jar);
        setSource(getMaxSource());
        if (energyContainer != null) {
            energyContainer.setEnergy(energyContainer.getMaxEnergy());
        }
        prepareOutputs(serverLevel, jarSignature());
        if (pendingOutputs.isEmpty()) {
            return 0;
        }
        progress = Math.max(0, progressRequired - 1);
        return pendingOutputs.stream()
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    int developmentOutputCount() {
        return outputSlots.stream()
                .map(IInventorySlot::getStack)
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private static final class PersistentJarSlot
            extends BasicInventorySlot {
        private PersistentJarSlot(IContentsListener listener,
                                  int x, int y) {
            super((stack, automation) ->
                            automation == AutomationType.MANUAL,
                    (stack, automation) -> true,
                    EntityContainerRegistry::isFilled,
                    listener, x, y);
            setSlotType(ContainerSlotType.INPUT);
        }
    }

    private static final class DrygmyOutputSlot
            extends BasicInventorySlot {
        private DrygmyOutputSlot(IContentsListener listener,
                                 int x, int y) {
            super(OUTPUT_LIMIT,
                    (stack, automation) -> true,
                    (stack, automation) ->
                            automation == AutomationType.INTERNAL,
                    stack -> true, listener, x, y);
            obeyStackLimit = false;
            setSlotType(ContainerSlotType.OUTPUT);
        }
    }
}
