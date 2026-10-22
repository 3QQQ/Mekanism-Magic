package com.example.mekanismmagic.integration;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.common.entity.EntityContainerRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForbiddenAbsentGameTests {
    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void baseLoadsWithoutForbiddenOrValhelsia(GameTestHelper helper) {
        helper.assertTrue(!ModCompatibility.forbiddenArcanusLoaded()
                && !ModCompatibility.loaded(ModCompatibility.VALHELSIA_CORE)
                && !ModCompatibility.forbiddenArcanusIntegrationEnabled(), "This matrix must omit both optional mods");
        helper.assertTrue(EntityContainerRegistry.capturedEntity(new ItemStack(Items.STICK)).isEmpty(),
                "Unrelated items must not be captured containers");
        for (String machine : new String[]{"clibano_processor", "hephaestus_forge_processor",
                "essence_condenser", "soul_extractor_processor", "simulated_blood_extractor", "experience_bottler"}) {
            var machineId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mekanism_magic", machine);
            helper.assertTrue(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(machineId)
                        && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(machineId),
                    "Forbidden machine registered without its verified dependencies: " + machine);
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(machineId).isEmpty(),
                    "Machine recipe loaded although the version-gated registry is absent: " + machine);
        }
        helper.assertTrue(!new com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusEnabledCondition()
                        .test(net.neoforged.neoforge.common.conditions.ICondition.IContext.EMPTY),
                "Resource condition does not use the same dependency gate as the registry");
        for (String name : new String[]{"com.stal111.forbidden_arcanus.ForbiddenArcanus",
                "net.valhelsia.valhelsia_core.ValhelsiaCore"}) {
            try {
                Class.forName(name, false, ForbiddenAbsentGameTests.class.getClassLoader());
                throw new AssertionError("Optional runtime JAR leaked into absent matrix: " + name);
            } catch (ClassNotFoundException expected) {
                // A clean dedicated-server startup proves the gated module stayed dormant.
            }
        }
        helper.succeed();
    }
}
