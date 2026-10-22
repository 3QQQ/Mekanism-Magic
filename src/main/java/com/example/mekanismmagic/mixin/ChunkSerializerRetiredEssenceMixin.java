package com.example.mekanismmagic.mixin;

import com.example.mekanismmagic.integration.common.cleanup.RetiredEssenceMachines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removes only the explicitly retired three IDs before vanilla reads any block palette or inventory. */
@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerRetiredEssenceMixin {
    @Inject(method = "read", at = @At("HEAD"))
    private static void mekanismMagic$discardRetiredEssenceMachines(ServerLevel level, PoiManager poi,
            RegionStorageInfo storage, ChunkPos pos, CompoundTag data,
            CallbackInfoReturnable<ProtoChunk> callback) {
        RetiredEssenceMachines.clearFromChunk(data);
    }
}
