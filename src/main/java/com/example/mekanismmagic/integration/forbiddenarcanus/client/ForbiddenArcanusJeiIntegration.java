package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRegistries;
import com.example.mekanismmagic.integration.forbiddenarcanus.EssencePreparationDisplay;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog.ClibanoDisplay;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog.DisplaySnapshot;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog.RitualDisplay;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Function;

/** Client-only JEI adapter. The server processing catalog and machine state are never consulted. */
public final class ForbiddenArcanusJeiIntegration {
    public static final RecipeType<ClibanoDisplay> CLIBANO_TYPE = RecipeType.create(
            MekanismMagic.MOD_ID, "clibano_processing", ClibanoDisplay.class);
    public static final RecipeType<RitualDisplay> HEPHAESTUS_FORGE_TYPE = RecipeType.create(
            MekanismMagic.MOD_ID, "hephaestus_forge_processing", RitualDisplay.class);
    public static final RecipeType<EssencePreparationDisplay> ESSENCE_CONDENSER_TYPE = RecipeType.create(
            MekanismMagic.MOD_ID, "essence_condensing", EssencePreparationDisplay.class);

    private static IJeiRuntime runtime;
    private static RecipeManager latestSyncedManager;
    private static RecipeManager cachedManager;
    private static RegistryAccess cachedRegistries;
    private static RecipeManager publishedManager;
    private static RegistryAccess publishedRegistries;
    private static DisplaySnapshot cachedSnapshot;
    private static DisplaySnapshot publishedSnapshot;
    private static List<ClibanoDisplay> registeredClibano = List.of();
    private static List<RitualDisplay> registeredRituals = List.of();
    private static boolean refreshQueued;
    private static long lifecycleGeneration;

    private ForbiddenArcanusJeiIntegration() {
    }

    public static void registerCategories(IRecipeCategoryRegistration registration) {
        if (!enabled()) return;
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new ClibanoJeiCategory(gui), new HephaestusForgeJeiCategory(gui));
        registration.addRecipeCategories(new EssencePreparationJeiCategory(gui));
    }

    public static void registerCatalysts(IRecipeCatalystRegistration registration) {
        if (!enabled()) return;
        registration.addRecipeCatalyst(ForbiddenArcanusRegistries.CLIBANO_PROCESSOR_BLOCK.asItem(), CLIBANO_TYPE);
        registration.addRecipeCatalyst(ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.asItem(), HEPHAESTUS_FORGE_TYPE);
        registration.addRecipeCatalyst(ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.asItem(), ESSENCE_CONDENSER_TYPE);
    }

    public static synchronized void registerRecipes(IRecipeRegistration registration) {
        if (!enabled()) return;
        registration.addRecipes(ESSENCE_CONDENSER_TYPE, EssencePreparationDisplay.recipes());
        DisplaySnapshot snapshot = currentSnapshot();
        if (snapshot == null) return;
        registeredClibano = List.copyOf(snapshot.clibano());
        registeredRituals = List.copyOf(snapshot.rituals());
        if (!registeredClibano.isEmpty()) registration.addRecipes(CLIBANO_TYPE, registeredClibano);
        if (!registeredRituals.isEmpty()) registration.addRecipes(HEPHAESTUS_FORGE_TYPE, registeredRituals);
        publishedSnapshot = snapshot;
        publishedManager = cachedManager;
        publishedRegistries = cachedRegistries;
    }

    public static synchronized void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        if (!enabled()) return;
        runtime = jeiRuntime;
        // Covers JEI starting after the initial recipe sync, and a recipe
        // update arriving between registerRecipes and runtime availability.
        requestRuntimeRefresh();
    }

    public static synchronized void onRuntimeUnavailable() {
        clearClientState();
    }

    public static synchronized void onClientDisconnected() {
        // Drop client registry holders and invalidate already queued callbacks.
        // JEI owns/disposes its old runtime; do not mutate it during teardown.
        clearClientState();
    }

    public static synchronized void onRecipesUpdated(RecipeManager manager) {
        if (!enabled()) return;
        latestSyncedManager = manager;
        cachedManager = null;
        cachedRegistries = null;
        cachedSnapshot = null;
        requestRuntimeRefresh();
    }

    /** No client tick hook: at most one queued reconciliation per recipe update burst. */
    private static void requestRuntimeRefresh() {
        if (runtime == null || refreshQueued) return;
        refreshQueued = true;
        long generation = lifecycleGeneration;
        Minecraft.getInstance().execute(() -> {
            synchronized (ForbiddenArcanusJeiIntegration.class) {
                if (generation != lifecycleGeneration) return;
                refreshQueued = false;
                refreshRuntime();
            }
        });
    }

    private static void refreshRuntime() {
        if (runtime == null || !enabled()) return;
        DisplaySnapshot snapshot = currentSnapshot();
        if (snapshot == null || snapshot == publishedSnapshot) return;
        IRecipeManager recipes = runtime.getRecipeManager();
        // Item component values can contain registry holders. Equivalent
        // serialized content must still be rebound when the owner changes.
        boolean sameOwner = publishedManager == cachedManager && publishedRegistries == cachedRegistries;
        registeredClibano = reconcile(recipes, CLIBANO_TYPE, registeredClibano, snapshot.clibano(),
                display -> display.recipe().id(), ClibanoDisplay::signature, sameOwner);
        registeredRituals = reconcile(recipes, HEPHAESTUS_FORGE_TYPE, registeredRituals, snapshot.rituals(),
                display -> display.recipe().id(), RitualDisplay::signature, sameOwner);
        publishedSnapshot = snapshot;
        publishedManager = cachedManager;
        publishedRegistries = cachedRegistries;
    }

    private static <T> List<T> reconcile(IRecipeManager manager, RecipeType<T> type,
                                         List<T> previous, List<T> fresh,
                                         Function<T, ResourceLocation> id, Function<T, String> signature,
                                         boolean sameOwner) {
        var changes = sameOwner ? ForbiddenJeiRecipeReconciler.reconcile(previous, fresh, id, signature)
                : new ForbiddenJeiRecipeReconciler.Change<>(fresh, fresh, previous);
        if (!changes.removed().isEmpty()) manager.hideRecipes(type, changes.removed());
        if (!changes.added().isEmpty()) {
            manager.addRecipes(type, changes.added());
            manager.unhideRecipes(type, changes.added());
        }
        return changes.registered();
    }

    private static DisplaySnapshot currentSnapshot() {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        RecipeManager manager = level.getRecipeManager();
        // A sync can precede installation of the new client world. Never
        // combine its RecipeManager with the previous world's registries.
        if (latestSyncedManager != null && manager != latestSyncedManager) return null;
        RegistryAccess registries = level.registryAccess();
        if (cachedSnapshot == null || cachedManager != manager || cachedRegistries != registries) {
            DisplaySnapshot fresh = ForbiddenRecipeDisplayCatalog.read(manager, registries);
            cachedManager = manager;
            cachedRegistries = registries;
            cachedSnapshot = fresh;
        }
        return cachedSnapshot;
    }

    private static void clearClientState() {
        runtime = null;
        latestSyncedManager = null;
        cachedManager = null;
        cachedRegistries = null;
        publishedManager = null;
        publishedRegistries = null;
        cachedSnapshot = null;
        publishedSnapshot = null;
        registeredClibano = List.of();
        registeredRituals = List.of();
        refreshQueued = false;
        lifecycleGeneration++;
    }

    private static boolean enabled() {
        return ModCompatibility.forbiddenArcanusIntegrationEnabled() && ModCompatibility.loaded("jei");
    }

    static boolean isItemVisible(ItemStack stack) {
        return runtime == null || runtime.getJeiHelpers().getIngredientVisibility().isIngredientVisible(VanillaTypes.ITEM_STACK, stack);
    }

}
