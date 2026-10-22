package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.common.cleanup.RetiredEssenceMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loads a copied real chunk with old palette IDs; never opens or rewrites a player's world. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RetiredEssenceMachineChunkGameTests {
    private RetiredEssenceMachineChunkGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void chunkLoaderDropsOnlyOldThreeAndPreservesOtherPaletteEntries(GameTestHelper helper) {
        var level = helper.getLevel();
        var original = level.getChunkAt(helper.absolutePos(new BlockPos(1, 2, 1)));
        CompoundTag saved = ChunkSerializer.write(level, original).copy();
        // ChunkSerializer posts a real ChunkDataEvent.Load even for an
        // in-memory copy. Keep this fixture away from active test networks.
        ChunkPos target = new ChunkPos(original.getPos().x + 128, original.getPos().z + 128);
        saved.putInt("xPos", target.x);
        saved.putInt("zPos", target.z);
        int sectionY = Math.floorDiv(helper.absolutePos(new BlockPos(1, 2, 1)).getY(), 16);
        ListTag sections = saved.getList("sections", Tag.TAG_COMPOUND);
        CompoundTag section = null;
        for (Tag value : sections) {
            if (((CompoundTag) value).getByte("Y") == sectionY) section = (CompoundTag) value;
        }
        helper.assertTrue(section != null, "Real chunk fixture did not contain the test section");
        ListTag palette = new ListTag();
        String[] ids = {"minecraft:air", "mekanism_magic:soul_extractor_processor",
                "mekanism_magic:simulated_blood_extractor", "mekanism_magic:experience_bottler",
                "minecraft:stone", "minecraft:chest", "minecraft:gold_block"};
        for (String id : ids) {
            CompoundTag state = new CompoundTag();
            state.putString("Name", id);
            palette.add(state);
        }
        long[] packed = new long[256];
        for (int x = 1; x <= 6; x++) {
            int index = (1 << 8) | (1 << 4) | x;
            packed[index / 16] |= (long) x << ((index % 16) * 4);
        }
        CompoundTag states = new CompoundTag();
        states.put("palette", palette);
        states.putLongArray("data", packed);
        section.put("block_states", states);
        ListTag entities = new ListTag();
        for (int x = 1; x <= 3; x++) {
            CompoundTag old = new CompoundTag();
            old.putString("id", ids[x]);
            old.putInt("x", target.getMinBlockX() + x);
            old.putInt("y", sectionY * 16 + 1);
            old.putInt("z", target.getMinBlockZ() + 1);
            old.putIntArray("essences", new int[]{100, 200, 300, 400});
            entities.add(old);
        }
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.putInt("x", target.getMinBlockX() + 5);
        chest.putInt("y", sectionY * 16 + 1);
        chest.putInt("z", target.getMinBlockZ() + 1);
        chest.putString("CustomName", "\"unrelated chest survives\"");
        entities.add(chest.copy());
        saved.put("block_entities", entities);
        var loaded = ChunkSerializer.read(level, level.getPoiManager(),
                new RegionStorageInfo("retired-essence-test", level.dimension(), "chunk"), target, saved);
        for (int x = 1; x <= 3; x++) {
            var pos = new BlockPos(target.getMinBlockX() + x, sectionY * 16 + 1,
                    target.getMinBlockZ() + 1);
            helper.assertTrue(loaded.getBlockState(pos).isAir(), "An old essence machine survived chunk loading");
        }
        var kept = new BlockPos(target.getMinBlockX() + 4, sectionY * 16 + 1,
                target.getMinBlockZ() + 1);
        helper.assertTrue(loaded.getBlockState(kept).is(Blocks.STONE)
                        && loaded.getBlockState(kept.east()).is(Blocks.CHEST)
                        && loaded.getBlockState(kept.east(2)).is(Blocks.GOLD_BLOCK)
                        && saved.getList("block_entities", Tag.TAG_COMPOUND).size() == 1
                        && saved.getList("block_entities", Tag.TAG_COMPOUND).getCompound(0).equals(chest)
                        && saved.getBoolean("shouldSave"),
                "Retirement damaged unrelated palette/entity data or failed to persist the removal");
        CompoundTag once = saved.copy();
        helper.assertTrue(!RetiredEssenceMachines.clearFromChunk(saved) && once.equals(saved),
                "Retired cleanup was not idempotent");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void currentMachineNamesAndOtherModsAreNeverRetirementTargets(GameTestHelper helper) {
        CompoundTag chunk = new CompoundTag();
        ListTag entities = new ListTag();
        for (String id : new String[]{"mekanism_magic:essence_condenser", "mekanism_magic:hephaestus_forge_processor",
                "another_mod:experience_bottler", "minecraft:chest"}) {
            CompoundTag entity = new CompoundTag();
            entity.putString("id", id);
            entity.putString("owner", "preserve");
            entities.add(entity);
        }
        chunk.put("block_entities", entities);
        CompoundTag before = chunk.copy();
        helper.assertTrue(!RetiredEssenceMachines.clearFromChunk(chunk) && before.equals(chunk),
                "Retirement affected a current machine, another mod or unrelated inventory");
        helper.succeed();
    }
}
