package com.sb.peh.mixin;

import com.sb.peh.PlanetEscapeHandler;
import com.sb.peh.compat.SBLoggerHandler;
import ht.treechop.api.ChopEvent.BeforeFellEvent;
import ht.treechop.api.TreeDataImmutable;
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
        private static boolean planetescapehandler$announced;

        @Inject(
                method = "shouldLog",
                at = @At("HEAD"),
                cancellable = true,
                remap = false)
        private static void planetescapehandler$disableTfcFelling(
                LevelAccessor level,
                BlockPos pos,
                BlockState state,
                ItemStack stack,
                CallbackInfoReturnable<Boolean> cir) {

            if (!planetescapehandler$announced) {
                planetescapehandler$announced = true;
                PlanetEscapeHandler.LOGGER.info(
                        "[felling] Disabled TFC axe felling");
            }

            cir.setReturnValue(false);
        }
    }

    @Mixin(BeforeFellEvent.class)
    public abstract static class TreeChopMixin {

        @Unique
        private static boolean planetescapehandler$announced;

        @Inject(
                method = "<init>",
                at = @At("RETURN"),
                remap = false)
        private void planetescapehandler$interceptFelling(CallbackInfo ci) {
            BeforeFellEvent event = (BeforeFellEvent) (Object) this;

            // Prevent TreeChop from performing its normal tree conversion.
            event.setCanceled(true);

            Player player = event.getPlayer();

            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            BlockPos choppedPos = event.getChoppedBlockPos();
            if (choppedPos == null) {
                return;
            }

            // Record the player's direction for subsequent physics events.
            SBLoggerHandler.record(serverPlayer, choppedPos);

            TreeDataImmutable tree = event.getFellData().getTree();

            List<BlockPos> logs = tree == null
                    ? List.of(choppedPos.immutable())
                    : tree.streamLogs()
                            .map(BlockPos::immutable)
                            .distinct()
                            .toList();

            if (logs.isEmpty()) {
                logs = List.of(choppedPos.immutable());
            }

            // Break TreeChop's selected logs normally. Their removal updates
            // neighboring blocks, allowing Simple Block Physics to handle
            // the unsupported remainder of the structure.
            for (BlockPos pos : logs) {
                if (!serverPlayer.level().isClientSide()) {
                    serverPlayer.level().destroyBlock(pos, true, serverPlayer);
                }
            }

            if (!planetescapehandler$announced) {
                planetescapehandler$announced = true;
                PlanetEscapeHandler.LOGGER.info(
                        "[felling] Intercepted TreeChop; broke {} selected logs",
                        logs.size());
            }
        }
    }
}