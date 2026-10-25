package com.sb.peh.mixin;

import net.dries007.tfc.util.AxeLoggingHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TFC only fells a tree (AxeLoggingHelper.doLogging) when shouldLog says so.
 * Always answering "no" makes axes break logs one at a time, like normal blocks.
 */
@Mixin(AxeLoggingHelper.class)
public abstract class TfcAxeLoggingMixin {
    @Inject(method = "shouldLog", at = @At("HEAD"), cancellable = true, remap = false)
    private static void planetescapehandler$noTfcTreeFelling(
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
