package com.example.mekanismmagic.integration.mekenergistics;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.ClassReader;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Keeps the optional Mek Energistics bridge safe in release jars. The bridge
 * classes are compiled when the API jar is available, but are only applied
 * when the actual mod is present at runtime.
 */
public final class MekEnergisticsCompatMixinPlugin
        implements IMixinConfigPlugin {
    private static final String[] REQUIRED_ABI_CLASSES = {
            "com.beipuo.mekenergistics.api.upgrade."
                    + "IMePatternAutomationHost",
            "com.beipuo.mekenergistics.api.upgrade.MePatternAutomation",
            "com.beipuo.mekenergistics.blockentity.support."
                    + "AbstractMeAeSupport",
            "com.beipuo.mekenergistics.blockentity.support."
                    + "MeNetworkEnergyHelper",
            "com.beipuo.mekenergistics.upgrade."
                    + "MeUpgradeRecipeMachineRuntime",
            "com.beipuo.mekenergistics.blockentity.api."
                    + "MeAeSupportOwner",
            "com.beipuo.mekenergistics.blockentity.support."
                    + "AbstractMeAeSupport$1",
            "com.beipuo.mekenergistics.blockentity.support."
                    + "MeSmartPatternMultiplication",
            "com.beipuo.mekenergistics.blockentity.support."
                    + "MeSmartPatternMultiplication$PendingPattern",
            "com.beipuo.mekenergistics.blockentity.support.io."
                    + "MeInputLayout",
            "com.beipuo.mekenergistics.blockentity.support.io."
                    + "MeInputPort",
            "com.beipuo.mekenergistics.blockentity.api."
                    + "MePatternIoOwner",
            "com.beipuo.mekenergistics.common.machine.MeMekanismMachine",
            "com.beipuo.mekenergistics.item.MeTierInstallerItem",
            "com.beipuo.mekenergistics.item.MeInstallerUpgradeHandler",
            "com.beipuo.mekenergistics.item.MeInstallerTargetResolver",
            "com.beipuo.mekenergistics.upgrade."
                    + "MePatternAutomationProfiles",
            "com.beipuo.mekenergistics.upgrade.MeUpgradeMachineProfile",
            "com.beipuo.mekenergistics.upgrade."
                    + "MeUpgradeRecipeMachineAdapter",
            "com.beipuo.mekenergistics.upgrade.MeUpgradeType",
            "com.beipuo.mekenergistics.upgrade.MeUpgradeContainer",
            "com.beipuo.mekenergistics.upgrade.MeUpgradeStateOwner",
            "com.beipuo.mekenergistics.upgrade."
                    + "MeUpgradeRecipeMachineRuntime",
            "com.beipuo.mekenergistics.upgrade."
                    + "MeUpgradeRecipeMachineRuntime$1",
            "com.beipuo.mekenergistics.upgrade.MeMekanismUpgrades",
            "com.beipuo.mekenergistics.upgrade."
                    + "MeUpgradeConflictPolicy$Result"
    };
    private static volatile Boolean compatibleAbi;

    private static boolean loaded() {
        // Mixin plugins run before the normal FML ModList is ready. Querying
        // ModList here made every compatibility mixin silently opt out even
        // with Mek Energistics 3.0.6 installed. MixinService is the loader's
        // authoritative early resource view; this list is the exact ABI
        // surface referenced by the optional bridge mixins.
        for (String className : REQUIRED_ABI_CLASSES) {
            if (!classPresent(className)) {
                return false;
            }
        }
        return compatibleMemberAbi();
    }

    private static boolean compatibleMemberAbi() {
        Boolean cached = compatibleAbi;
        if (cached != null) {
            return cached;
        }
        synchronized (MekEnergisticsCompatMixinPlugin.class) {
            if (compatibleAbi == null) {
                String support = "com.beipuo.mekenergistics.blockentity."
                        + "support.AbstractMeAeSupport";
                String feeder = support + "$1";
                String inputLayout = "com.beipuo.mekenergistics."
                        + "blockentity.support.io.MeInputLayout";
                String smart = "com.beipuo.mekenergistics.blockentity."
                        + "support.MeSmartPatternMultiplication";
                String pending = "com.beipuo.mekenergistics.blockentity."
                        + "support.MeSmartPatternMultiplication$PendingPattern";
                String upgrades = "com.beipuo.mekenergistics.upgrade.";
                String container = upgrades + "MeUpgradeContainer";
                String owner = upgrades + "MeUpgradeStateOwner";
                String runtime = upgrades + "MeUpgradeRecipeMachineRuntime";
                String runtimeOwner = runtime + "$1";
                String result = upgrades + "MeUpgradeConflictPolicy$Result";
                String nativeUpgrade = "mekanism.common.tile.component."
                        + "TileComponentUpgrade";
                compatibleAbi = fieldPresent(feeder, "val$layout",
                        "Lcom/beipuo/mekenergistics/blockentity/support/io/"
                                + "MeInputLayout;")
                        && methodPresent(feeder, "feed",
                        "([Lappeng/api/stacks/KeyCounter;)Z")
                        && methodPresent(feeder, "maxAcceptedCopies",
                        "([Lappeng/api/stacks/KeyCounter;)J")
                        && methodPresent(inputLayout, "route",
                        "([Lappeng/api/stacks/KeyCounter;)Z")
                        && methodPresent(inputLayout, "maxAcceptedCopies",
                        "([Lappeng/api/stacks/KeyCounter;)J")
                        && methodPresent(inputLayout, "ports",
                        "()Ljava/util/List;")
                        && methodPresent(smart, "hasPendingWork", "()Z")
                        && fieldPresent(pending, "definition",
                        "Lappeng/api/stacks/AEKey;")
                        && methodPresent(pending, "toKeyCounters",
                        "(J)[Lappeng/api/stacks/KeyCounter;")
                        && fieldPresent(support, "ownerTile",
                        "Lmekanism/common/tile/base/TileEntityMekanism;")
                        && methodPresent(support, "drainOutputPorts",
                        "(Lcom/beipuo/mekenergistics/blockentity/api/AeOutputMode;Ljava/util/List;)Z")
                        && fieldPresent(support,
                        "smartPatternMultiplication",
                        "Lcom/beipuo/mekenergistics/blockentity/support/"
                                + "MeSmartPatternMultiplication;")
                        && methodPresent(support, "patternInputLayout",
                        "()Lcom/beipuo/mekenergistics/blockentity/support/io/"
                                + "MeInputLayout;")
                        && methodPresent(support, "routePatternInputs",
                        "([Lappeng/api/stacks/KeyCounter;)Z")
                        && methodPresent(support, "hasRegisteredPattern",
                        "(Lappeng/api/crafting/IPatternDetails;)Z")
                        && methodPresent(support, "maxAcceptedCopies",
                        "([Lappeng/api/stacks/KeyCounter;)J")
                        && methodPresent(support, "routeDataPatternInputs",
                        "([Lappeng/api/stacks/KeyCounter;)Z")
                        && methodPresent(support, "getAvailablePatterns",
                        "()Ljava/util/List;")
                        && methodPresent(support, "pushPatternWithAdapter",
                        "(Lappeng/api/crafting/IPatternDetails;"
                                + "[Lappeng/api/stacks/KeyCounter;)Z")
                        && methodPresent(support,
                        "dispatchWithSmartPatternFallback",
                        "(ZZLcom/beipuo/mekenergistics/blockentity/support/"
                                + "MeSmartPatternMultiplication;"
                                + "Lappeng/api/crafting/IPatternDetails;"
                                + "[Lappeng/api/stacks/KeyCounter;"
                                + "Ljava/lang/Runnable;"
                                + "Ljava/util/function/BooleanSupplier;)Z")
                        && methodPresent(support,
                        "processSmartPatternViaAdapter", "()Z")
                        && methodPresent(support, "enqueueSmartPattern",
                        "(Lappeng/api/crafting/IPatternDetails;"
                                + "[Lappeng/api/stacks/KeyCounter;)Z")
                        && methodPresent(support,
                        "processPassiveCrafting", "(Z)Z")
                        && methodPresent(support,
                        "isSmartPatternMultiplicationEnabled", "()Z")
                        && methodPresent(support, "updatePatterns", "()V")
                        && methodPresent(support,
                        "flushInterfaceRecovery", "()Z")
                        && methodPresent(support,
                        "hasInterfaceRecovery", "()Z")
                        && methodPresent(support, "alertAeTicker", "()V")
                        && methodPresent(support,
                        "refundToNetworkOrBuffer",
                        "(Lappeng/api/stacks/AEKey;J)V")
                        && methodPresent(
                        "com.beipuo.mekenergistics.blockentity.support."
                                + "MeNetworkEnergyHelper",
                        "extractNetworkFe",
                        "(Lappeng/api/networking/IGrid;"
                                + "Lappeng/api/networking/security/"
                                + "IActionSource;JLmekanism/api/Action;)J")
                        && fieldPresent(upgrades
                                + "MeUpgradeRecipeMachineRuntime",
                        "tile",
                        "Lmekanism/common/tile/base/TileEntityMekanism;")
                        && methodPresent(upgrades
                                + "MeUpgradeRecipeMachineRuntime",
                        "createNodeIfActive", "(Z)V")
                        && methodPresent(upgrades
                                + "MeUpgradeRecipeMachineRuntime",
                        "destroyNode", "()V")
                        && methodPresent(upgrades
                                + "MeUpgradeRecipeMachineRuntime",
                        "tick", "(ZZ)Z")
                        && methodPresent(
                        "com.beipuo.mekenergistics.api.upgrade."
                                + "MePatternAutomation",
                        "registeredBlockIds", "()Ljava/util/Set;")
                        && fieldPresent(container, "owner",
                        "Lcom/beipuo/mekenergistics/upgrade/"
                                + "MeUpgradeStateOwner;")
                        && fieldPresent(runtime, "tile",
                        "Lmekanism/common/tile/base/"
                                + "TileEntityMekanism;")
                        && fieldPresent(runtimeOwner, "this$0",
                        "Lcom/beipuo/mekenergistics/upgrade/"
                                + "MeUpgradeRecipeMachineRuntime;")
                        && methodPresent(container, "canInstall",
                        "(Lcom/beipuo/mekenergistics/upgrade/"
                                + "MeUpgradeType;I)Lcom/beipuo/"
                                + "mekenergistics/upgrade/"
                                + "MeUpgradeConflictPolicy$Result;")
                        && methodPresent(owner, "supportsUpgrade",
                        "(Lcom/beipuo/mekenergistics/upgrade/"
                                + "MeUpgradeType;)Z")
                        && methodPresent(upgrades + "MeMekanismUpgrades",
                        "toType", "(Lmekanism/api/Upgrade;)Lcom/"
                                + "beipuo/mekenergistics/upgrade/"
                                + "MeUpgradeType;")
                        && methodPresent(result, "unsupported",
                        "()Lcom/beipuo/mekenergistics/upgrade/"
                                + "MeUpgradeConflictPolicy$Result;")
                        && fieldPresent(nativeUpgrade, "tile",
                        "Lmekanism/common/tile/base/"
                                + "TileEntityMekanism;")
                        && methodPresent(nativeUpgrade, "supports",
                        "(Lmekanism/api/Upgrade;)Z")
                        && methodPresent(nativeUpgrade, "addUpgrades",
                        "(Lmekanism/api/Upgrade;I)I");
            }
            return compatibleAbi;
        }
    }

    private static boolean fieldPresent(
            String className, String fieldName, String descriptor) {
        ClassNode node = readClassNode(className);
        return node != null && node.fields.stream().anyMatch(field ->
                field.name.equals(fieldName)
                        && field.desc.equals(descriptor));
    }

    private static boolean methodPresent(
            String className, String methodName, String descriptor) {
        ClassNode node = readClassNode(className);
        return node != null && node.methods.stream().anyMatch(method ->
                method.name.equals(methodName)
                        && method.desc.equals(descriptor));
    }

    private static ClassNode readClassNode(String className) {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream stream = MixinService.getService()
                .getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node,
                    ClassReader.SKIP_CODE
                            | ClassReader.SKIP_DEBUG
                            | ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean classPresent(String className) {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream stream = MixinService.getService()
                .getResourceAsStream(resource)) {
            return stream != null;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName,
                                    String mixinClassName) {
        if (!loaded()) {
            return false;
        }
        if (targetClassName.endsWith("ExtraNatureAltarFactoryBlockEntity")
                && !classPresent("com.jerry.mekextras.common.tile.factory.TileEntityExtraItemToItemFactory")) {
            return false;
        }
        if (targetClassName.equals("com.jerry.mekextras.common.item.ItemExtraTierInstaller")
                && !classPresent(targetClassName)) {
            return false;
        }
        if (mixinClassName.endsWith(
                "MekEnergisticsNatureAltarPatternMixin")
                || mixinClassName.endsWith(
                "MekEnergisticsNaturePatternCacheMixin")
                || mixinClassName.endsWith(
                "MekEnergisticsNatureFactoryAutomationMixin")) {
            return classPresent(
                    "de.ellpeck.naturesaura.recipes.AltarRecipe")
                    && classPresent(
                    "com.example.mekanismmagic.integration.ae2."
                            + "Ae2NatureAltarPattern");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets,
                              Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
