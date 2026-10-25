package com.sb.peh.mixin;

import dev.protomanly.pmweather.block.ModBlocks;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class PMWeatherBaseFireBlockMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void planetescapehandler$preventPitKilnFire(
            BlockPos pos,
            BlockState state,
            int flags,
            CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (level == null) {
            return;
        }
        if (state.is((Block) ModBlocks.FIRE.get())
                && level.getBlockState(pos.below()).is((Block) TFCBlocks.PIT_KILN.get())) {
            cir.setReturnValue(false);
        }
    }
}
