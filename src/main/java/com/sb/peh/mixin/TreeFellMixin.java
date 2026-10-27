package com.sb.peh.mixin;

import com.sb.peh.PlanetEscapeHandler;
import com.sb.peh.compat.SBLoggerHandler;
import ht.treechop.api.ChopEvent.BeforeFellEvent;
import java.util.ArrayList;
import java.util.List;
import net.dries007.tfc.util.AxeLoggingHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class TreeFellMixin {

    @Mixin(AxeLoggingHelper.class)
    public abstract static class TFCLoggingMixin {
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
                PlanetEscapeHandler.LOGGER.info(
                        "[felling] TFC axe logging refused (shouldLog -> false)");
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
                PlanetEscapeHandler.LOGGER.info(
                        "[felling] TFC doLogging was reached; limited to a single log");
            }

            cir.setReturnValue(new ArrayList<>(List.of(pos)));
        }
    }

    @Mixin(BeforeFellEvent.class)
    public abstract static class TreeChopMixin {
        @Unique
        private static boolean planetescapehandler$announcedCancel;

        @Inject(method = "<init>", at = @At("RETURN"), remap = false)
        private void planetescapehandler$cancelAndRecord(CallbackInfo ci) {
            BeforeFellEvent event = (BeforeFellEvent) (Object) this;
            event.setCanceled(true);

            Player player = event.getPlayer();
            BlockPos pos = event.getChoppedBlockPos();

            if (player instanceof ServerPlayer serverPlayer && pos != null) {
                SBLoggerHandler.record(serverPlayer, pos);
            }

            if (!planetescapehandler$announcedCancel) {
                planetescapehandler$announcedCancel = true;
                PlanetEscapeHandler.LOGGER.info(
                        "[felling] Cancelled a TreeChop BeforeFellEvent via mixin");
            }
        }
    }
}