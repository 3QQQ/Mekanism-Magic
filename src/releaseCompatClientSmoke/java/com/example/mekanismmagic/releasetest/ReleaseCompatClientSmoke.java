package com.example.mekanismmagic.releasetest;

import com.example.mekanismmagic.MekanismMagic;
import com.google.gson.GsonBuilder;
import mekanism.client.gui.GuiMekanism;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Opt-in, hidden, real-world/real-container proof; never attached to the normal client or runtime JAR. */
@EventBusSubscriber(modid = MekanismMagic.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
@SuppressWarnings("removal")
public final class ReleaseCompatClientSmoke {
    private static final List<String> MACHINES = List.of("clibano_processor", "hephaestus_forge_processor", "essence_condenser");
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> GUI_CASES = new ArrayList<>();
    private static final long STARTED_AT = System.currentTimeMillis();
    private static boolean worldRequested, pending, capturing, stopping;
    private static int ticks, index, settle, tileReadyTicks, renderFrames;
    private static volatile BlockPos readyPos;
    private static BlockPos currentPos;

    private ReleaseCompatClientSmoke() { }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (!Boolean.getBoolean("mekanism_magic.release_compat_client")) return;
        NeoForge.EVENT_BUS.addListener(ReleaseCompatClientSmoke::tick);
        NeoForge.EVENT_BUS.addListener(ReleaseCompatClientSmoke::render);
        event.enqueueWork(() -> {
            try {
                var mc = Minecraft.getInstance();
                Path root = Path.of(System.getProperty("mekanism_magic.release_compat_isolated_root")).toRealPath();
                Path profile = mc.gameDirectory.toPath().toRealPath();
                require(!profile.equals(root) && profile.startsWith(root), "A dedicated build/ profile is mandatory");
                REPORT.put("gameDirectory", profile.toString());
                REPORT.put("isolatedBuildDirectory", true);
                REPORT.put("usesFakePlayer", false);
                REPORT.put("expectedMachines", MACHINES);
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false;
                mc.options.guiScale().set(2);
                mc.options.languageCode = "zh_cn";
                mc.getLanguageManager().setSelected("zh_cn");
                var packs = mc.getResourcePackRepository();
                packs.reload();
                packs.setSelected(packs.getSelectedIds().stream().filter(id -> !id.startsWith("file/")).toList());
                REPORT.put("selectedResourcePacks", List.copyOf(packs.getSelectedIds()));
            } catch (Throwable failure) { finish(failure); }
        });
    }

    private static void tick(ClientTickEvent.Post event) {
        if (stopping) return;
        try {
            var mc = Minecraft.getInstance();
            if (++ticks > 6000) throw new AssertionError("Release client acceptance timed out");
            if (mc.screen != null) {
                String screen = mc.screen.getClass().getSimpleName();
                if (screen.contains("Error") || screen.contains("Disconnected")) {
                    throw new AssertionError("Client startup/world failed: " + screen + " / " + mc.screen.getTitle().getString());
                }
            }
            if (!worldRequested && mc.screen != null && mc.getOverlay() == null) {
                // A fresh profile can show AccessibilityOnboardingScreen before TitleScreen.
                // This isolated fixture starts its own world without requiring user input there.
                REPORT.put("initialScreen", mc.screen.getClass().getName());
                verifyRuntime();
                worldRequested = true;
                REPORT.put("mainMenuReached", true);
                REPORT.put("phase", "creating isolated flat world");
                mc.reloadResourcePacks().whenComplete((nothing, failure) -> mc.execute(() -> {
                    if (failure != null) { finish(failure); return; }
                    try {
                        mc.createWorldOpenFlows().createFreshLevel("release-compat-" + System.currentTimeMillis(),
                                new net.minecraft.world.level.LevelSettings("Release compatibility acceptance",
                                        net.minecraft.world.level.GameType.CREATIVE, false,
                                        net.minecraft.world.Difficulty.PEACEFUL, true,
                                        new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                                new net.minecraft.world.level.levelgen.WorldOptions(20261110L, false, false),
                                access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                        .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT)
                                        .value().createWorldDimensions(), new TitleScreen());
                    } catch (Throwable createFailure) { finish(createFailure); }
                }));
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
            require(mc.player instanceof net.minecraft.client.player.LocalPlayer, "No real local player");
            REPORT.put("worldOpened", true);
            REPORT.put("worldCreatedFresh", true);
            REPORT.put("realLocalPlayer", true);
            if (pending && readyPos != null) {
                if (!(mc.level.getBlockEntity(readyPos) instanceof TileEntityMekanism) || ++tileReadyTicks < 5) return;
                BlockPos pos = readyPos; readyPos = null;
                var server = mc.getSingleplayerServer();
                var playerId = mc.player.getUUID();
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayer(playerId);
                        require(player != null, "Acceptance player missing on server");
                        var tile = player.serverLevel().getBlockEntity(pos);
                        require(tile instanceof TileEntityMekanism, "Actual server machine tile missing");
                        ((TileEntityMekanism) tile).openGui(player);
                    } catch (Throwable failure) { mc.execute(() -> finish(failure)); }
                });
                return;
            }
            if (pending || capturing || ++settle < 20) return;
            if (index == MACHINES.size()) { finish(null); return; }
            String id = MACHINES.get(index);
            REPORT.put("phase", "opening " + id);
            var blockId = ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, id);
            require(BuiltInRegistries.BLOCK.getOptional(blockId).isPresent(), "Original release machine is unregistered: " + id);
            pending = true; renderFrames = 0; tileReadyTicks = 0;
            var server = mc.getSingleplayerServer();
            var playerId = mc.player.getUUID();
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(playerId);
                    require(player != null, "Acceptance player missing on placement");
                    player.closeContainer();
                    BlockPos pos = player.blockPosition().offset(2, 0, 0);
                    var block = BuiltInRegistries.BLOCK.get(blockId);
                    player.serverLevel().setBlock(pos, block.defaultBlockState(), 3);
                    var tile = player.serverLevel().getBlockEntity(pos);
                    require(tile instanceof TileEntityMekanism, "Original machine did not create its real tile: " + id);
                    ((TileEntityMekanism) tile).getSecurity().setOwnerUUID(playerId);
                    mc.execute(() -> { currentPos = pos; readyPos = pos; });
                } catch (Throwable failure) { mc.execute(() -> finish(failure)); }
            });
        } catch (Throwable failure) { finish(failure); }
    }

    private static void verifyRuntime() {
        Map<String, String> loaded = new TreeMap<>();
        ModList.get().getMods().forEach(mod -> loaded.put(mod.getModId(), mod.getVersion().toString()));
        REPORT.put("loadedVersions", loaded);
        REPORT.put("actualForbiddenVersion", loaded.get("forbidden_arcanus"));
        REPORT.put("actualCoreVersion", loaded.get("valhelsia_core"));
        REPORT.put("actualAddonVersion", loaded.get(MekanismMagic.MOD_ID));
        require(System.getProperty("mekanism_magic.release_expected_addon", "").equals(loaded.get(MekanismMagic.MOD_ID)),
                "The loaded addon is not the checked-in release version");
        require(System.getProperty("mekanism_magic.release_expected_forbidden", "2.6.2")
                .equals(loaded.get("forbidden_arcanus")), "The actual loaded Forbidden version is not the supplied update");
        require(loaded.containsKey("valhelsia_core"), "Valhelsia Core was not loaded");
        var selected = Arrays.stream(System.getProperty("mekanism_magic.release_expected_mod_ids", "").split(","))
                .filter(id -> !id.isBlank()).toList();
        require(!selected.isEmpty() && selected.stream().allMatch(loaded::containsKey), "The complete selected original-release mod stack was not loaded");
        REPORT.put("expectedSelectedModIds", selected);
        REPORT.put("allSelectedModsLoaded", true);
        // Do not accidentally validate the unreleased three-mod development branch.
        require(List.of("malum", "goety", "astralsorcery").stream().noneMatch(loaded::containsKey),
                "Unreleased integration mods leaked into original-release acceptance");
    }

    private static void render(ScreenEvent.Render.Post event) {
        if (stopping || !pending || capturing || !(event.getScreen() instanceof GuiMekanism<?> gui) || ++renderFrames < 30) return;
        try {
            var mc = Minecraft.getInstance();
            String id = MACHINES.get(index);
            var menu = gui.getMenu();
            require(menu instanceof MekanismTileContainer<?>, "Screen is not a real native machine container");
            var tile = ((MekanismTileContainer<?>) menu).getTileEntity();
            require(tile.getBlockPos().equals(currentPos), "GUI opened for a different tile");
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, id));
            String title = gui.getTitle().getString();
            require(!title.startsWith("container.") && !title.startsWith("block."), "Untranslated native GUI title: " + id);
            require(title.equals(block.getName().getString()), "Native default title does not match original block: " + id);
            String filename = id + ".png";
            var entry = new LinkedHashMap<String, Object>();
            entry.put("id", id); entry.put("title", title); entry.put("titleMatchesBlock", true);
            entry.put("nativeScreen", gui.getClass().getName()); entry.put("tile", tile.getClass().getName());
            entry.put("width", gui.getXSize()); entry.put("height", gui.getYSize());
            entry.put("screenshot", "screenshots/" + filename);
            capturing = true;
            event.getGuiGraphics().flush();
            Screenshot.grab(mc.gameDirectory, filename, mc.getMainRenderTarget(), ignored -> mc.execute(() -> {
                try {
                    Path image = mc.gameDirectory.toPath().resolve("screenshots/" + filename);
                    require(Files.isRegularFile(image) && Files.size(image) > 32, "Actual screenshot was not saved: " + id);
                    GUI_CASES.add(entry);
                    mc.setScreen(null);
                    index++; settle = 0; pending = false; capturing = false;
                } catch (Throwable failure) { finish(failure); }
            }));
        } catch (Throwable failure) { finish(failure); }
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static void finish(Throwable failure) {
        if (stopping) return;
        stopping = true;
        var mc = Minecraft.getInstance();
        try { mc.setScreen(null); }
        catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
        if (failure == null && GUI_CASES.size() != MACHINES.size()) failure = new AssertionError("Incomplete native GUI coverage");
        REPORT.put("status", failure == null ? "PASS" : "FAIL");
        REPORT.put("guiCases", GUI_CASES);
        REPORT.put("clientTicks", ticks);
        REPORT.put("elapsedMillis", System.currentTimeMillis() - STARTED_AT);
        REPORT.put("autoStop", true);
        if (failure != null) REPORT.put("failure", failure.toString());
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("release-compat-client.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
        } catch (Exception writeFailure) { throw new IllegalStateException("Cannot write isolated client receipt", writeFailure); }
        if (failure != null) MekanismMagic.LOGGER.error("Original-release compatibility client failed", failure);
        mc.stop();
    }
}
