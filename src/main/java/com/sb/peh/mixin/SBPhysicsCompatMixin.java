package com.sb.peh.mixin;

import com.sb.peh.compat.SBLoggerHandler;
import io.github.ferrinember.simpleblockphysics.utils.TickHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class SBPhysicsCompatMixin {

    @Mixin(FallingBlockEntity.class)
    public abstract static class FallingBlockMixin {
        @Inject(method = "tick",
                at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
                        shift = At.Shift.AFTER),
                cancellable = true, remap = false)
        private void planetescapehandler$failsafe(CallbackInfo ci) {
            FallingBlockEntity self = (FallingBlockEntity) (Object) this;
            if (SBLoggerHandler.resolve(self)) {
                ci.cancel();
            }
        }
    }

    @Mixin(TickHandler.class)
    public abstract static class TickHandlerMixin {
        @Redirect(method = "breakBlock",
                at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;fall(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/entity/item/FallingBlockEntity;"),
                remap = false)
        private static FallingBlockEntity planetescapehandler$fall(Level level, BlockPos pos, BlockState state) {
            FallingBlockEntity entity = FallingBlockEntity.fall(level, pos, SBLoggerHandler.layDown(level, pos, state));
            SBLoggerHandler.apply(level, pos, entity);
            return entity;
        }
    }
}