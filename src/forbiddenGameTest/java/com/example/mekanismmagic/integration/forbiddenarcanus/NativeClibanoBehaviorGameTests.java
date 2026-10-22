package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Invokes the installed native machine's own consumeSoul/serverTick, not a reimplementation. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeClibanoBehaviorGameTests {
    private static final String NATIVE =
            "com.stal111.forbidden_arcanus.common.block.entity.clibano.ClibanoMainBlockEntity";

    private NativeClibanoBehaviorGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void nativeSoulAndEnchantedMediumAreConsumedWithOriginalDurations(GameTestHelper helper)
            throws ReflectiveOperationException {
        for (String mediumName : new String[]{"soul", "corrupt_soul", "enchanted_soul"}) {
            for (boolean crimson : new boolean[]{false, true}) {
                BlockEntity nativeMachine = createNative(helper);
                ItemStack medium = item(mediumName);
                medium.setCount(2);
                ItemStack enhancer = crimson ? item("crimson_stone") : ItemStack.EMPTY;
                setStack(nativeMachine, 0, enhancer);
                setStack(nativeMachine, 1, medium);
                Method consume = nativeMachine.getClass().getDeclaredMethod("consumeSoul", Level.class);
                consume.setAccessible(true);
                consume.invoke(nativeMachine, helper.getLevel());
                int nativeDuration = intField(nativeMachine, "soulTime");
                helper.assertTrue(nativeDuration == (crimson ? 3510 : 2700)
                                && nativeDuration == ForbiddenArcanus261Bridge.soulDuration(
                                helper.getLevel().registryAccess(), enhancer),
                        "Native consumeSoul and the bridge disagree on duration for " + mediumName);
                helper.assertTrue(stack(nativeMachine, 1).getCount() == 1
                                && stack(nativeMachine, 0).getCount() == enhancer.getCount(),
                        "Native consumeSoul must spend exactly one medium and preserve the enhancer");
                String expected = mediumName.equals("enchanted_soul") ? "ENCHANTED_FIRE" : "SOUL_FIRE";
                helper.assertTrue(field(nativeMachine, "fireType").toString().equals(expected),
                        "Native consumeSoul chose the wrong fire for " + mediumName);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void activeNativeFireCannotUpgradeMidBurnAndDecaysWhileIdle(GameTestHelper helper)
            throws ReflectiveOperationException {
        BlockEntity nativeMachine = createNative(helper);
        setStack(nativeMachine, 1, item("soul"));
        Method consume = nativeMachine.getClass().getDeclaredMethod("consumeSoul", Level.class);
        consume.setAccessible(true);
        consume.invoke(nativeMachine, helper.getLevel());
        setStack(nativeMachine, 1, item("enchanted_soul"));
        nativeMachine.getClass().getMethod("setSoulTime", int.class).invoke(nativeMachine, 2);
        Method tick = nativeMachine.getClass().getMethod("serverTick", Level.class, BlockPos.class,
                BlockState.class, nativeMachine.getClass());
        tick.invoke(null, helper.getLevel(), nativeMachine.getBlockPos(), nativeMachine.getBlockState(), nativeMachine);
        helper.assertTrue(intField(nativeMachine, "soulTime") == 1
                        && field(nativeMachine, "fireType").toString().equals("SOUL_FIRE")
                        && stack(nativeMachine, 1).getCount() == 1,
                "The native active soul fire upgraded or spent another medium during an idle tick");
        tick.invoke(null, helper.getLevel(), nativeMachine.getBlockPos(), nativeMachine.getBlockState(), nativeMachine);
        helper.assertTrue(intField(nativeMachine, "soulTime") == 0
                        && field(nativeMachine, "fireType").toString().equals("FIRE")
                        && stack(nativeMachine, 1).getCount() == 1,
                "Native fire did not expire while idle, or immediately consumed the waiting enchanted soul");
        helper.succeed();
    }

    private static BlockEntity createNative(GameTestHelper helper) throws ReflectiveOperationException {
        var block = BuiltInRegistries.BLOCK.get(id("clibano_main_part"));
        helper.assertTrue(block != Blocks.AIR, "The real native Clibano main block is missing");
        // A detached native BE has a real ServerLevel for recipe/registry calls without
        // scheduling a second independent native ticker in the test structure.
        BlockEntity blockEntity = (BlockEntity) Class.forName(NATIVE)
                .getConstructor(BlockPos.class, BlockState.class)
                .newInstance(helper.absolutePos(new BlockPos(1, 1, 1)), block.defaultBlockState());
        blockEntity.setLevel(helper.getLevel());
        return blockEntity;
    }

    private static void setStack(BlockEntity machine, int slot, ItemStack stack) throws ReflectiveOperationException {
        machine.getClass().getMethod("setStack", int.class, ItemStack.class).invoke(machine, slot, stack.copy());
        Method changed = machine.getClass().getDeclaredMethod("onSlotChanged", int.class);
        changed.setAccessible(true);
        changed.invoke(machine, slot);
    }

    private static ItemStack stack(BlockEntity machine, int slot) throws ReflectiveOperationException {
        return (ItemStack) machine.getClass().getMethod("getStack", int.class).invoke(machine, slot);
    }

    private static int intField(BlockEntity machine, String name) throws ReflectiveOperationException {
        return (Integer) field(machine, name);
    }

    private static Object field(BlockEntity machine, String name) throws ReflectiveOperationException {
        Field field = machine.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(machine);
    }

    private static ItemStack item(String path) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id(path)));
        if (stack.isEmpty()) {
            throw new IllegalStateException("Missing native Forbidden & Arcanus item " + path);
        }
        return stack;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", path);
    }
}
