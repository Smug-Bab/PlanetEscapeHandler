package com.sb.peh.mixin;

import dev.protomanly.pmweather.block.PMWFireBlock;
import net.dries007.tfc.common.TFCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PMWFireBlock.class)
public abstract class PMWeatherFireMixin {
    @Inject(method = "isGroundSuitable", at = @At("HEAD"), cancellable = true, remap = false)
    private static void planetescapehandler$addTfcGrounds(
            Level level,
            BlockState state,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        if (state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.FARMLAND)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.DIRT_PATH)
                || state.is(TFCTags.Blocks.GRASS)) {
            cir.setReturnValue(true);
        }
    }
}
