package com.sb.peh.mixin;

import com.sb.peh.PlanetEscapeHandler;
import java.util.ArrayList;
import java.util.List;
import net.dries007.tfc.util.AxeLoggingHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two layers, so TFC can never fell more than the log you actually broke:
 *  1. shouldLog -> false: axes break logs one at a time like normal blocks.
 *  2. findLogs  -> only the broken log: if anything calls doLogging without asking
 *     shouldLog first, doLogging still only destroys that one log.
 */
@Mixin(AxeLoggingHelper.class)
public abstract class TfcAxeLoggingMixin {
    @Unique
    private static boolean planetescapehandler$announcedShouldLog;
    @Unique
    private static boolean planetescapehandler$announcedFindLogs;

    @Inject(method = "shouldLog", at = @At("HEAD"), cancellable = true, remap = false)
    private static void planetescapehandler$noTfcTreeFelling(
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        if (!planetescapehandler$announcedShouldLog) {
            planetescapehandler$announcedShouldLog = true;
            PlanetEscapeHandler.LOGGER.info("[felling] TFC axe logging refused (shouldLog -> false)");
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "findLogs", at = @At("HEAD"), cancellable = true, remap = false)
    private static void planetescapehandler$onlyTheBrokenLog(
            LevelAccessor level,
            BlockPos pos,
            CallbackInfoReturnable<List<BlockPos>> cir) {
        if (!planetescapehandler$announcedFindLogs) {
            planetescapehandler$announcedFindLogs = true;
            PlanetEscapeHandler.LOGGER.info("[felling] TFC doLogging was reached; limited to a single log");
        }
        cir.setReturnValue(new ArrayList<>(List.of(pos)));
    }
}