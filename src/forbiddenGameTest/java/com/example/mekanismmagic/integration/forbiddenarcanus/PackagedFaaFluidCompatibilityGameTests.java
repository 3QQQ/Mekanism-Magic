package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.stal111.forbidden_arcanus.common.block.entity.EssenceUtremJarBlockEntity;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceType;
import com.stal111.forbidden_arcanus.common.block.properties.ModBlockStateProperties;
import com.stal111.forbidden_arcanus.core.init.ModBlocks;
import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ABI and unit-conservation checks against the published PackagedFAA mod. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PackagedFaaFluidCompatibilityGameTests {
    private static final BlockPos JAR = new BlockPos(2, 2, 2);
    private static final BlockPos FORGE = new BlockPos(2, 1, 2);
    private PackagedFaaFluidCompatibilityGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void publishedFluidIdsTagsAndJarCapability(
            GameTestHelper helper) {
        if (!ModCompatibility.loaded("packaged_faa")) {
            MekanismMagic.LOGGER.info(
                    "SKIP PackagedFAA fluid ABI test: optional mod absent");
            helper.succeed();
            return;
        }
        EssenceType[] types = {EssenceType.AUREAL,
                EssenceType.SOULS, EssenceType.BLOOD,
                EssenceType.EXPERIENCE};
        String[] names = {"aureal", "souls", "blood", "experience"};
        for (int index = 0; index < types.length; index++) {
            EssenceType type = types[index];
            String name = names[index];
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    "packaged_faa", "fluid_" + name);
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            helper.assertTrue(fluid != null && id.equals(
                            BuiltInRegistries.FLUID.getKey(fluid)),
                    "Published PackagedFAA fluid is missing: " + id);
            TagKey<Fluid> tag = TagKey.create(Registries.FLUID,
                    ResourceLocation.fromNamespaceAndPath("c", name));
            helper.assertTrue(new FluidStack(fluid, 1).is(tag),
                    "Published fluid is missing its common tag: " + name);

            helper.setBlock(JAR, Blocks.AIR);
            helper.setBlock(JAR, ModBlocks.ESSENCE_UTREM_JAR.get()
                    .defaultBlockState().setValue(
                            ModBlockStateProperties.ESSENCE_TYPE, type));
            EssenceUtremJarBlockEntity jar = helper.getBlockEntity(JAR);
            IFluidHandler handler = helper.getLevel().getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    helper.absolutePos(JAR), Direction.UP);
            helper.assertTrue(handler != null && handler.getTanks() == 1
                            && handler.getTankCapacity(0) == jar.getLimit(),
                    "Published jar fluid capability/capacity is absent");
            FluidStack offered = new FluidStack(fluid, 37);
            helper.assertTrue(handler.fill(offered,
                            IFluidHandler.FluidAction.SIMULATE) == 37
                            && jar.getAmount() == 0,
                    "Simulated jar fill consumed fluid or essence");
            helper.assertTrue(handler.fill(offered,
                            IFluidHandler.FluidAction.EXECUTE) == 37
                            && jar.getAmount() == 37
                            && handler.getFluidInTank(0).getAmount() == 37,
                    "Jar did not convert 1 fluid mB to 1 essence unit");
            helper.assertTrue(handler.drain(11,
                            IFluidHandler.FluidAction.SIMULATE)
                            .getAmount() == 11 && jar.getAmount() == 37,
                    "Simulated jar drain consumed essence");
            helper.assertTrue(handler.drain(11,
                            IFluidHandler.FluidAction.EXECUTE)
                            .getAmount() == 11 && jar.getAmount() == 26,
                    "Jar did not drain fluid and essence at 1:1");
            Fluid other = BuiltInRegistries.FLUID.get(
                    ResourceLocation.fromNamespaceAndPath("packaged_faa",
                            "fluid_" + names[(index + 1) % names.length]));
            helper.assertTrue(handler.fill(new FluidStack(other, 1),
                            IFluidHandler.FluidAction.EXECUTE) == 0
                            && jar.getAmount() == 26,
                    "Nonempty jar silently changed its essence type");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void actualJarFluidTransfersIntoFourForgeBanks(
            GameTestHelper helper) {
        if (!ModCompatibility.loaded("packaged_faa")) {
            MekanismMagic.LOGGER.info(
                    "SKIP PackagedFAA forge-fluid test: optional mod absent");
            helper.succeed();
            return;
        }
        helper.setBlock(FORGE,
                ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get());
        HephaestusForgeProcessorBlockEntity forge =
                helper.getBlockEntity(FORGE);
        setInternalTier(helper, forge, 2);
        forge.onUpdateServer();
        IFluidHandler input = helper.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(FORGE), Direction.UP);
        helper.assertTrue(input != null && input.getTanks() == 4,
                "Forge did not expose its four native essence banks on top");
        IFluidHandler[] closedSides = new IFluidHandler[Direction.values().length];
        for (Direction side : Direction.values()) {
            if (side != Direction.UP) {
                closedSides[side.ordinal()] = helper.getLevel()
                        .getCapability(Capabilities.FluidHandler.BLOCK,
                                helper.absolutePos(FORGE), side);
            }
        }
        String[] names = {"aureal", "souls", "blood", "experience"};
        EssenceType[] types = {EssenceType.AUREAL,
                EssenceType.SOULS, EssenceType.BLOOD,
                EssenceType.EXPERIENCE};
        for (int channel = 0; channel < 4; channel++) {
            helper.assertTrue(forge.getEssenceCapacity(channel) >= 29,
                    "Tier-two forge bank has no fluid capacity");
            helper.setBlock(JAR, Blocks.AIR);
            helper.setBlock(JAR, ModBlocks.ESSENCE_UTREM_JAR.get()
                    .defaultBlockState().setValue(
                            ModBlockStateProperties.ESSENCE_TYPE,
                            types[channel]));
            EssenceUtremJarBlockEntity jar = helper.getBlockEntity(JAR);
            IFluidHandler source = helper.getLevel().getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    helper.absolutePos(JAR), Direction.DOWN);
            Fluid fluid = BuiltInRegistries.FLUID.get(
                    ResourceLocation.fromNamespaceAndPath("packaged_faa",
                            "fluid_" + names[channel]));
            FluidStack stack = new FluidStack(fluid, 29);
            helper.assertTrue(source != null
                            && source.fill(stack,
                            IFluidHandler.FluidAction.EXECUTE) == 29,
                    "Published Utrem jar did not store the source essence");
            int before = forge.getStoredEssence(channel);
            FluidStack preview = source.drain(29,
                    IFluidHandler.FluidAction.SIMULATE);
            helper.assertTrue(preview.getAmount() == 29
                            && input.fill(preview,
                            IFluidHandler.FluidAction.SIMULATE) == 29
                            && jar.getAmount() == 29
                            && forge.getStoredEssence(channel) == before,
                    "Simulated jar-to-forge transfer changed a balance");
            FluidStack removed = source.drain(29,
                    IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(removed.getAmount() == 29
                            && input.fill(removed,
                            IFluidHandler.FluidAction.EXECUTE) == 29
                            && jar.getAmount() == 0
                            && forge.getStoredEssence(channel) == before + 29,
                    "Jar-to-forge fluid transfer was not conserved 1:1");
            for (Direction side : Direction.values()) {
                IFluidHandler closed = closedSides[side.ordinal()];
                if (closed != null) {
                    helper.assertTrue(closed.fill(stack,
                                    IFluidHandler.FluidAction.EXECUTE) == 0,
                            "Forge accepted essence through disabled " + side);
                }
            }
            helper.assertTrue(input.drain(1,
                            IFluidHandler.FluidAction.EXECUTE).isEmpty()
                            && forge.getStoredEssence(channel) == before + 29,
                    "Input-only forge leaked a stored essence as output fluid");
            for (int other = 0; other < 4; other++) {
                int expected = other <= channel ? 29 : 0;
                helper.assertTrue(forge.getStoredEssence(other) == expected,
                        "Cross-channel fluid transfer changed bank " + other);
            }
        }
        Fluid flowingAureal = BuiltInRegistries.FLUID.get(
                ResourceLocation.fromNamespaceAndPath("packaged_faa",
                        "fluid_aurealflowing"));
        helper.assertTrue(BuiltInRegistries.FLUID.getKey(flowingAureal)
                        .toString().equals("packaged_faa:fluid_aurealflowing")
                        && input.fill(new FluidStack(flowingAureal, 1),
                        IFluidHandler.FluidAction.SIMULATE) == 1
                        && forge.getStoredEssence(0) == 29,
                "Published flowing variant was not normalized without mutation");
        for (int channel = 0; channel < 4; channel++) {
            int capacity = forge.getEssenceCapacity(channel);
            int room = capacity - forge.getStoredEssence(channel);
            Fluid fluid = BuiltInRegistries.FLUID.get(
                    ResourceLocation.fromNamespaceAndPath("packaged_faa",
                            "fluid_" + names[channel]));
            FluidStack overflow = new FluidStack(fluid, capacity + 100);
            helper.assertTrue(input.fill(overflow,
                            IFluidHandler.FluidAction.SIMULATE) == room
                            && forge.getStoredEssence(channel) == 29,
                    "Simulated overfill changed forge bank " + channel);
            helper.assertTrue(input.fill(overflow,
                            IFluidHandler.FluidAction.EXECUTE) == room
                            && forge.getStoredEssence(channel) == capacity
                            && input.fill(new FluidStack(fluid, 1),
                            IFluidHandler.FluidAction.EXECUTE) == 0,
                    "Forge bank exceeded its native capacity " + channel);
        }
        var config = forge.getConfig().getConfig(TransmissionType.FLUID);
        helper.assertTrue(config != null
                        && config.getDataType(RelativeSide.TOP)
                        == DataType.INPUT
                        && config.getDataType(RelativeSide.FRONT)
                        == DataType.NONE,
                "Forge fluid sides did not default to TOP-only input");
        config.setDataType(DataType.NONE, RelativeSide.TOP);
        forge.getConfig().sideChanged(TransmissionType.FLUID,
                RelativeSide.TOP);
        IFluidHandler disabled = helper.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(FORGE), Direction.UP);
        if (disabled != null) {
            helper.assertTrue(disabled.getTanks() == 0
                            || disabled.fill(new FluidStack(
                            BuiltInRegistries.FLUID.get(
                                    ResourceLocation.fromNamespaceAndPath(
                                            "packaged_faa", "fluid_aureal")),
                            1), IFluidHandler.FluidAction.SIMULATE) == 0,
                    "Disabled forge TOP side still accepts fluid");
        }
        config.setDataType(DataType.INPUT, RelativeSide.FRONT);
        forge.getConfig().sideChanged(TransmissionType.FLUID,
                RelativeSide.FRONT);
        Direction front = RelativeSide.FRONT.getDirection(
                forge.getDirection());
        IFluidHandler frontHandler = helper.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(FORGE), front);
        helper.assertTrue(frontHandler != null
                        && frontHandler.getTanks() == 4,
                "Configurable physical FRONT side did not expose four banks");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void forgeFluidSimulationNeverMutatesTierOrBanks(
            GameTestHelper helper) {
        if (!ModCompatibility.loaded("packaged_faa")) {
            MekanismMagic.LOGGER.info(
                    "SKIP PackagedFAA forge SIM test: optional mod absent");
            helper.succeed();
            return;
        }
        helper.setBlock(FORGE,
                ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get());
        HephaestusForgeProcessorBlockEntity forge =
                helper.getBlockEntity(FORGE);
        helper.assertTrue(forge.getForgeTier() == 1,
                "New forge did not start at native level one");
        IFluidHandler input = helper.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(FORGE), Direction.UP);
        Fluid aureal = BuiltInRegistries.FLUID.get(
                ResourceLocation.fromNamespaceAndPath("packaged_faa",
                        "fluid_aureal"));
        FluidStack offer = new FluidStack(aureal, 17);
        helper.assertTrue(input != null
                        && forge.getEssenceFluidCapacity(0) >= 17,
                "Internal level did not supply a fluid capacity");
        int tierBefore = forge.getForgeTier();
        int shownCapacityBefore = forge.getEssenceCapacity(0);
        CompoundTag savedBefore = forge.saveWithoutMetadata(
                helper.getLevel().registryAccess());
        helper.assertTrue(input.fill(offer,
                        IFluidHandler.FluidAction.SIMULATE) == 17
                        && forge.getStoredEssence(0) == 0
                        && forge.getForgeTier() == tierBefore
                        && forge.getEssenceCapacity(0)
                        == shownCapacityBefore
                        && savedBefore.equals(forge.saveWithoutMetadata(
                        helper.getLevel().registryAccess())),
                "Fluid SIM unexpectedly refreshed tier/capacity or NBT");
        setInternalTier(helper, forge, 2);
        helper.assertTrue(forge.getForgeTier() == 2
                        && forge.getEssenceFluidCapacity(0)
                        == HephaestusForge261Bridge.capacities(2)[0]
                        && forge.getStoredEssence(0) == 0,
                "Internal tier change did not update fluid capacity");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 160)
    public static void nativeFluidEjectorTransfersExperienceToOfficialJar(
            GameTestHelper helper) {
        if (!ModCompatibility.loaded("packaged_faa")) {
            MekanismMagic.LOGGER.info(
                    "SKIP PackagedFAA auto-eject test: optional mod absent");
            helper.succeed();
            return;
        }
        helper.setBlock(FORGE,
                ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.get());
        EssenceCondenserBlockEntity producer =
                helper.getBlockEntity(FORGE);
        CompoundTag saved = producer.saveWithoutMetadata(
                helper.getLevel().registryAccess());
        saved.getCompound(EssenceCondenserBlockEntity.STATE_TAG)
                .putIntArray("banks", new int[]{0, 0, 0, 50});
        producer.loadWithComponents(saved,
                helper.getLevel().registryAccess());
        helper.setBlock(JAR, ModBlocks.ESSENCE_UTREM_JAR.get()
                .defaultBlockState().setValue(
                        ModBlockStateProperties.ESSENCE_TYPE,
                        EssenceType.EXPERIENCE));
        EssenceUtremJarBlockEntity jar = helper.getBlockEntity(JAR);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                jar.getAmount() == 50
                        && producer.getStoredEssence(3) == 0,
                "Native fluid ejector did not move exact XP essence to jar"))
                .thenIdle(10).thenExecute(() -> {
                    helper.assertTrue(jar.getAmount() == 50
                                    && producer.getStoredEssence(3) == 0,
                            "Repeated ejection duplicated XP essence");
                }).thenSucceed();
    }

    private static void setNativeInt(GameTestHelper helper,
                                     BlockEntity tile, String stateTag,
                                     String key, int value) {
        CompoundTag saved = tile.saveWithoutMetadata(
                helper.getLevel().registryAccess());
        CompoundTag state = saved.getCompound(stateTag);
        state.putInt(key, value);
        saved.put(stateTag, state);
        tile.loadWithComponents(saved, helper.getLevel().registryAccess());
    }

    private static void setInternalTier(GameTestHelper helper,
                                        HephaestusForgeProcessorBlockEntity tile,
                                        int tier) {
        setNativeInt(helper, tile,
                HephaestusForgeProcessorBlockEntity.STATE_TAG,
                "internal_tier", tier);
    }
}
