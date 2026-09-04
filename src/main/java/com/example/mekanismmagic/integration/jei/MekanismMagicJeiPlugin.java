package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.recipe.UltimateMiniRitualRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

@JeiPlugin
public final class MekanismMagicJeiPlugin implements IModPlugin {
    public static final RecipeType<OccultismRecipeBridge.PentacleJeiData> MINI_RITUAL_TYPE =
            RecipeType.create(MekanismMagic.MOD_ID, "mini_ritual",
                    OccultismRecipeBridge.PentacleJeiData.class);
    public static final RecipeType<OccultismRecipeBridge.RitualJeiData> RITUAL_TYPE =
            RecipeType.create(MekanismMagic.MOD_ID, "ritual",
                    OccultismRecipeBridge.RitualJeiData.class);
    public static final RecipeType<OccultismRecipeBridge.SpiritJeiData> SPIRIT_TYPE =
            RecipeType.create(MekanismMagic.MOD_ID, "spirit",
                    OccultismRecipeBridge.SpiritJeiData.class);
    public static final RecipeType<OccultismRecipeBridge.MinerJeiData> MINER_TYPE =
            RecipeType.create(MekanismMagic.MOD_ID, "miner",
                    OccultismRecipeBridge.MinerJeiData.class);
    public static final RecipeType<UltimateMiniRitualRecipe>
            ULTIMATE_MINI_RITUAL_TYPE = RecipeType.create(
                    MekanismMagic.MOD_ID, "ultimate_mini_ritual",
                    UltimateMiniRitualRecipe.class);
    private static volatile List<OccultismRecipeBridge.RitualJeiData>
            registeredRituals = List.of();
    private static volatile List<OccultismRecipeBridge.PentacleJeiData>
            registeredPentacles = List.of();
    private static volatile List<OccultismRecipeBridge.SpiritJeiData>
            registeredSpirits = List.of();
    private static volatile List<OccultismRecipeBridge.MinerJeiData>
            registeredMiners = List.of();
    private static volatile IJeiRuntime runtime;
    private static volatile long displayedRecipeRevision = Long.MIN_VALUE;
    private static volatile long displayedSpiritRevision = Long.MIN_VALUE;
    private static final AtomicBoolean RUNTIME_REFRESH_QUEUED =
            new AtomicBoolean();

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(
                MekanismMagic.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        if (ModCompatibility.occultismLoaded()) {
            registration.registerSubtypeInterpreter(
                    MekanismMagic.MINI_RITUAL.get(),
                    MiniRitualSubtypeInterpreter.INSTANCE);
        }
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            invokeOptionalJeiSubtypeRegistration(registration);
        }
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        if (ModCompatibility.occultismLoaded()) {
            registration.addExtraItemStacks(
                    OccultismRecipeBridge.pentacleJeiRecipes(
                                    Minecraft.getInstance().level).stream()
                            .map(OccultismRecipeBridge.PentacleJeiData::output)
                            .map(net.minecraft.world.item.ItemStack::copy)
                            .toList());
        }
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            invokeOptionalJeiExtraRegistration(registration);
        }
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (ModCompatibility.occultismLoaded()) {
            registration.addRecipeCategories(new MiniRitualJeiCategory(
                            registration.getJeiHelpers().getGuiHelper()),
                    new RitualJeiCategory(
                            registration.getJeiHelpers().getGuiHelper()),
                    new SpiritJeiCategory(
                            registration.getJeiHelpers().getGuiHelper()),
                    new MinerJeiCategory(
                            registration.getJeiHelpers().getGuiHelper()),
                    new UltimateMiniRitualJeiCategory(
                            registration.getJeiHelpers().getGuiHelper()));
        }
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            invokeOptionalJeiCategoryRegistration(registration);
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            invokeOptionalJeiRecipeRegistration(registration);
        }
        if (!ModCompatibility.occultismLoaded()) {
            return;
        }
        if (Minecraft.getInstance().level != null) {
            List<OccultismRecipeBridge.PentacleJeiData> pentacles =
                    OccultismRecipeBridge.pentacleJeiRecipes(
                            Minecraft.getInstance().level);
            registration.addItemStackInfo(
                    pentacles.stream()
                            .map(OccultismRecipeBridge.PentacleJeiData::output)
                            .map(net.minecraft.world.item.ItemStack::copy)
                            .toList(),
                    net.minecraft.network.chat.Component.translatable(
                            "jei.mekanism_magic.mini_ritual.info"));
            registration.getIngredientManager().addIngredientsAtRuntime(
                    VanillaTypes.ITEM_STACK,
                    pentacles.stream()
                            .map(OccultismRecipeBridge.PentacleJeiData::output)
                            .map(net.minecraft.world.item.ItemStack::copy)
                            .toList());
            List<OccultismRecipeBridge.RitualJeiData> rituals =
                    OccultismRecipeBridge.ritualJeiRecipes(
                            Minecraft.getInstance().level);
            List<OccultismRecipeBridge.SpiritJeiData> spirits =
                    OccultismRecipeBridge.spiritJeiRecipes(
                            Minecraft.getInstance().level);
            List<OccultismRecipeBridge.MinerJeiData> miners =
                    OccultismRecipeBridge.minerJeiRecipes(
                            Minecraft.getInstance().level);
            registeredPentacles = List.copyOf(pentacles);
            registeredRituals = List.copyOf(rituals);
            registeredSpirits = List.copyOf(spirits);
            registeredMiners = List.copyOf(miners);
            registration.addRecipes(MINI_RITUAL_TYPE, pentacles);
            registration.addRecipes(RITUAL_TYPE, rituals);
            registration.addRecipes(SPIRIT_TYPE, spirits);
            registration.addRecipes(MINER_TYPE, miners);
            List<UltimateMiniRitualRecipe> ultimateRecipes =
                    Minecraft.getInstance().level.getRecipeManager()
                            .getAllRecipesFor(
                                    net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                            .stream()
                            .map(holder -> holder.value())
                            .filter(UltimateMiniRitualRecipe.class::isInstance)
                            .map(UltimateMiniRitualRecipe.class::cast)
                            .toList();
            registration.addRecipes(ULTIMATE_MINI_RITUAL_TYPE,
                    ultimateRecipes);
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        displayedRecipeRevision = OccultismRecipeBridge.recipeRevision();
        displayedSpiritRevision =
                OccultismRecipeBridge.spiritProcessingRevision();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        RUNTIME_REFRESH_QUEUED.set(false);
        registeredPentacles = List.of();
        registeredRituals = List.of();
        registeredSpirits = List.of();
        registeredMiners = List.of();
        displayedRecipeRevision = Long.MIN_VALUE;
        displayedSpiritRevision = Long.MIN_VALUE;
    }

    /**
     * Reconciles the active client JEI catalog after an Occultism config or
     * datapack reload. This entry point is invoked reflectively from the
     * common bridge so a dedicated server never links JEI/client classes.
     */
    public static void requestOccultismRuntimeRefresh() {
        if (runtime == null
                || !RUNTIME_REFRESH_QUEUED.compareAndSet(false, true)) {
            return;
        }
        Minecraft.getInstance().execute(() -> {
            try {
                refreshOccultismRuntime();
            } finally {
                RUNTIME_REFRESH_QUEUED.set(false);
                // A second reload can arrive while this pass is reconciling.
                // Requeue only when its revision was not observed, avoiding
                // both a lost update and a permanent client-tick poller.
                if (runtime != null && Minecraft.getInstance().level != null
                        && (displayedRecipeRevision
                        != OccultismRecipeBridge.recipeRevision()
                        || displayedSpiritRevision
                        != OccultismRecipeBridge
                        .spiritProcessingRevision())) {
                    requestOccultismRuntimeRefresh();
                }
            }
        });
    }

    private static void refreshOccultismRuntime() {
        IJeiRuntime activeRuntime = runtime;
        var level = Minecraft.getInstance().level;
        if (activeRuntime == null || level == null
                || !ModCompatibility.occultismLoaded()) {
            return;
        }
        long recipeRevision = OccultismRecipeBridge.recipeRevision();
        long spiritRevision =
                OccultismRecipeBridge.spiritProcessingRevision();
        boolean recipesChanged = recipeRevision
                != displayedRecipeRevision;
        boolean spiritsChanged = spiritRevision
                != displayedSpiritRevision;
        if (!recipesChanged && !spiritsChanged) {
            return;
        }

        IRecipeManager recipes = activeRuntime.getRecipeManager();
        if (recipesChanged) {
            List<OccultismRecipeBridge.PentacleJeiData> freshPentacles =
                    OccultismRecipeBridge.pentacleJeiRecipes(level);
            updatePentacleIngredients(activeRuntime,
                    registeredPentacles, freshPentacles);
            registeredPentacles = reconcileRecipes(recipes,
                    MINI_RITUAL_TYPE, registeredPentacles,
                    freshPentacles,
                    MekanismMagicJeiPlugin::samePentacleRecipe);
            registeredRituals = reconcileRecipes(recipes,
                    RITUAL_TYPE, registeredRituals,
                    OccultismRecipeBridge.ritualJeiRecipes(level),
                    MekanismMagicJeiPlugin::sameRitualRecipe);
            registeredMiners = reconcileRecipes(recipes,
                    MINER_TYPE, registeredMiners,
                    OccultismRecipeBridge.minerJeiRecipes(level),
                    MekanismMagicJeiPlugin::sameMinerRecipe);
            displayedRecipeRevision = recipeRevision;
        }
        if (recipesChanged || spiritsChanged) {
            registeredSpirits = reconcileRecipes(recipes,
                    SPIRIT_TYPE, registeredSpirits,
                    OccultismRecipeBridge.spiritJeiRecipes(level),
                    MekanismMagicJeiPlugin::sameSpiritRecipe);
            displayedSpiritRevision = spiritRevision;
        }
    }

    private static <T> List<T> reconcileRecipes(
            IRecipeManager manager, RecipeType<T> type,
            List<T> previous, List<T> fresh,
            BiPredicate<T, T> equivalent) {
        boolean[] reused = new boolean[previous.size()];
        List<T> reconciled = new ArrayList<>(fresh.size());
        List<T> additions = new ArrayList<>();
        for (T candidate : fresh) {
            int match = -1;
            for (int index = 0; index < previous.size(); index++) {
                if (!reused[index]
                        && equivalent.test(previous.get(index), candidate)) {
                    match = index;
                    break;
                }
            }
            if (match >= 0) {
                reused[match] = true;
                reconciled.add(previous.get(match));
            } else {
                reconciled.add(candidate);
                additions.add(candidate);
            }
        }
        List<T> retired = new ArrayList<>();
        for (int index = 0; index < previous.size(); index++) {
            if (!reused[index]) {
                retired.add(previous.get(index));
            }
        }
        if (!retired.isEmpty()) {
            manager.hideRecipes(type, retired);
        }
        if (!additions.isEmpty()) {
            manager.addRecipes(type, additions);
            manager.unhideRecipes(type, additions);
        }
        return List.copyOf(reconciled);
    }

    private static void updatePentacleIngredients(
            IJeiRuntime activeRuntime,
            List<OccultismRecipeBridge.PentacleJeiData> previous,
            List<OccultismRecipeBridge.PentacleJeiData> fresh) {
        List<ItemStack> oldStacks = previous.stream()
                .map(OccultismRecipeBridge.PentacleJeiData::output)
                .toList();
        List<ItemStack> newStacks = fresh.stream()
                .map(OccultismRecipeBridge.PentacleJeiData::output)
                .toList();
        List<ItemStack> removed = oldStacks.stream()
                .filter(old -> newStacks.stream().noneMatch(
                        current -> sameStack(old, current)))
                .toList();
        List<ItemStack> added = newStacks.stream()
                .filter(current -> oldStacks.stream().noneMatch(
                        old -> sameStack(old, current)))
                .map(ItemStack::copy)
                .toList();
        if (!removed.isEmpty()) {
            activeRuntime.getIngredientManager()
                    .removeIngredientsAtRuntime(
                            VanillaTypes.ITEM_STACK, removed);
        }
        if (!added.isEmpty()) {
            activeRuntime.getIngredientManager()
                    .addIngredientsAtRuntime(
                            VanillaTypes.ITEM_STACK, added);
        }
    }

    private static boolean samePentacleRecipe(
            OccultismRecipeBridge.PentacleJeiData first,
            OccultismRecipeBridge.PentacleJeiData second) {
        return first.pentacleId().equals(second.pentacleId())
                && first.chalkColors().equals(second.chalkColors())
                && sameStacks(first.materials(), second.materials())
                && sameStack(first.output(), second.output());
    }

    private static boolean sameRitualRecipe(
            OccultismRecipeBridge.RitualJeiData first,
            OccultismRecipeBridge.RitualJeiData second) {
        return first.recipeId().equals(second.recipeId())
                && first.pentacleId().equals(second.pentacleId())
                && sameIngredients(first.ingredients(), second.ingredients())
                && sameIngredient(first.activation(), second.activation())
                && sameStacks(first.sacrifices(), second.sacrifices())
                && sameStack(first.selector(), second.selector())
                && sameStack(first.output(), second.output());
    }

    private static boolean sameSpiritRecipe(
            OccultismRecipeBridge.SpiritJeiData first,
            OccultismRecipeBridge.SpiritJeiData second) {
        return first.recipeId().equals(second.recipeId())
                && first.recipeType().equals(second.recipeType())
                && first.weight() == second.weight()
                && sameIngredient(first.input(), second.input())
                && sameStack(first.spirit(), second.spirit())
                && sameStack(first.output(), second.output());
    }

    private static boolean sameMinerRecipe(
            OccultismRecipeBridge.MinerJeiData first,
            OccultismRecipeBridge.MinerJeiData second) {
        return first.recipeId().equals(second.recipeId())
                && first.weight() == second.weight()
                && sameIngredient(first.input(), second.input())
                && sameStack(first.output(), second.output());
    }

    private static boolean sameIngredients(
            List<Ingredient> first, List<Ingredient> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!sameIngredient(first.get(index), second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameIngredient(Ingredient first,
                                          Ingredient second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return sameStacks(List.of(first.getItems()),
                List.of(second.getItems()));
    }

    private static boolean sameStacks(List<ItemStack> first,
                                      List<ItemStack> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!sameStack(first.get(index), second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount()
                && ItemStack.isSameItemSameComponents(first, second);
    }

    static List<net.minecraft.world.item.ItemStack> boundRitualSelectors(
            ResourceLocation pentacleId) {
        return registeredRituals.stream()
                .filter(recipe -> recipe.pentacleId().equals(pentacleId))
                .map(OccultismRecipeBridge.RitualJeiData::selector)
                .map(net.minecraft.world.item.ItemStack::copy)
                .toList();
    }


    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (ModCompatibility.occultismLoaded()) {
            registerOccultismCatalysts(registration);
        }
        if (ModCompatibility.arsNouveauMachineContentEnabled()) {
            invokeOptionalJeiRegistration(registration,
                    "arsnouveau.client.ArsNouveauJeiIntegration");
        }
    }

    @Override
    public void registerRecipeTransferHandlers(
            IRecipeTransferRegistration registration) {
        if (ModCompatibility.arsNouveauMachineContentEnabled()
                && ModCompatibility.loaded("ae2")) {
            try {
                Class.forName("com.example.mekanismmagic.integration."
                                + "ae2.Ae2IdentifierImbuementJeiTransfer")
                        .getMethod("register",
                                IRecipeTransferRegistration.class)
                        .invoke(null, registration);
            } catch (ClassNotFoundException missing) {
                // AE2 is optional, but an installed AE2 with a build that
                // omitted the bridge must not fail silently as a seemingly
                // successful recipe transfer.
                if (net.neoforged.fml.ModList.get().isLoaded("ae2")) {
                    com.example.mekanismmagic.MekanismMagic.LOGGER.error(
                            "AE2 is loaded but the imbuement pattern transfer "
                                    + "bridge is missing", missing);
                }
            } catch (LinkageError incompatible) {
                com.example.mekanismmagic.MekanismMagic.LOGGER.error(
                        "AE2 imbuement pattern transfer is incompatible with "
                                + "the installed AE2/JEI version",
                        incompatible);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(
                        "Failed to register AE2 imbuement pattern transfer",
                        failure);
            }
        }
    }

    private static void registerOccultismCatalysts(
            IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.asItem(),
                MINI_RITUAL_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.RITUAL_BLOCK.asItem(),
                RITUAL_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.SPIRIT_BLOCK.asItem(),
                SPIRIT_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_BLOCK.asItem(),
                SPIRIT_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.ADVANCED_SPIRIT_FACTORY_BLOCK.asItem(),
                SPIRIT_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.ELITE_SPIRIT_FACTORY_BLOCK.asItem(),
                SPIRIT_TYPE);
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.ULTIMATE_SPIRIT_FACTORY_BLOCK.asItem(),
                SPIRIT_TYPE);
        addOptionalSpiritFactoryCatalyst(registration,
                "absolute_spirit_factory");
        addOptionalSpiritFactoryCatalyst(registration,
                "supreme_spirit_factory");
        addOptionalSpiritFactoryCatalyst(registration,
                "cosmic_spirit_factory");
        addOptionalSpiritFactoryCatalyst(registration,
                "infinite_spirit_factory");
        registration.addRecipeCatalyst(
                NativeMekanismRegistries.DIMENSION_MINER_BLOCK.asItem(),
                MINER_TYPE);
    }

    private static void invokeOptionalJeiRegistration(
            IRecipeCatalystRegistration registration, String className) {
        try {
            Class.forName("com.example.mekanismmagic.integration."
                            + className)
                    .getMethod("registerCatalysts",
                            IRecipeCatalystRegistration.class)
                    .invoke(null, registration);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register optional JEI catalysts", failure);
        }
    }

    private static void invokeOptionalJeiExtraRegistration(
            IExtraIngredientRegistration registration) {
        try {
            Class.forName("com.example.mekanismmagic.integration."
                            + "arsnouveau.client.ArsNouveauJeiIntegration")
                    .getMethod("registerExtraIngredients",
                            IExtraIngredientRegistration.class)
                    .invoke(null, registration);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register optional Ars JEI ingredients",
                    failure);
        }
    }

    private static void invokeOptionalJeiSubtypeRegistration(
            ISubtypeRegistration registration) {
        try {
            Class.forName("com.example.mekanismmagic.integration."
                            + "arsnouveau.client.ArsNouveauJeiIntegration")
                    .getMethod("registerSubtypes",
                            ISubtypeRegistration.class)
                    .invoke(null, registration);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register optional Ars JEI subtypes",
                    failure);
        }
    }

    private static void invokeOptionalJeiCategoryRegistration(
            IRecipeCategoryRegistration registration) {
        try {
            Class.forName("com.example.mekanismmagic.integration."
                            + "arsnouveau.client.ArsNouveauJeiIntegration")
                    .getMethod("registerCategories",
                            IRecipeCategoryRegistration.class)
                    .invoke(null, registration);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register optional Ars JEI categories",
                    failure);
        }
    }

    private static void invokeOptionalJeiRecipeRegistration(
            IRecipeRegistration registration) {
        try {
            Class.forName("com.example.mekanismmagic.integration."
                            + "arsnouveau.client.ArsNouveauJeiIntegration")
                    .getMethod("registerRecipes",
                            IRecipeRegistration.class)
                    .invoke(null, registration);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Failed to register optional Ars JEI recipes", failure);
        }
    }

    private static void addOptionalSpiritFactoryCatalyst(
            IRecipeCatalystRegistration registration, String path) {
        net.minecraft.world.item.Item item =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath(
                                MekanismMagic.MOD_ID, path));
        if (item != net.minecraft.world.item.Items.AIR) {
            registration.addRecipeCatalyst(item, SPIRIT_TYPE);
        }
    }
}
