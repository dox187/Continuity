package me.pepperbell.continuity.client.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BlockStateModel;

@Mixin(BlockModels.class)
public interface BlockModelsAccessor {
	@Accessor("models")
	Map<BlockState, BlockStateModel> getModels();
}
