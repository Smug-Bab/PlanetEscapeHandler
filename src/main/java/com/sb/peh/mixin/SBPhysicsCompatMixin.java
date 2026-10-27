package com.sb.peh.mixin;

import com.sb.peh.compat.SBLoggerHandler;
import io.github.ferrinember.simpleblockphysics.utils.TickHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

public class SBPhysicsCompatMixin {

    @Mixin(TickHandler.class)
    public abstract static class TickHandlerMixin {

        @Redirect(
                method = "breakBlock",
                at = @At(
                        value = "INVOKE",
                        target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;fall(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/entity/item/FallingBlockEntity;"),
                remap = false)
        private static FallingBlockEntity planetescapehandler$fall(
                Level level,
                BlockPos pos,
                BlockState state) {

            BlockState adjustedState =
                    SBLoggerHandler.layDown(level, pos, state);

            FallingBlockEntity entity =
                    FallingBlockEntity.fall(level, pos, adjustedState);

            SBLoggerHandler.apply(level, pos, entity);

            return entity;
        }
    }
}