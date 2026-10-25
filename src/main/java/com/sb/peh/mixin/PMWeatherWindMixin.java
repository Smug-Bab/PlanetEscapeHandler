package com.sb.peh.mixin;

import dev.protomanly.pmweather.weather.WindEngine;
import net.dries007.tfc.util.climate.Climate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WindEngine.class)
public abstract class PMWeatherWindMixin {
    @Inject(method = "getWind(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/Level;ZZZZZ)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void planetescapehandler$injectTfcWind(
            Vec3 position,
            Level level,
            boolean ignoreStorms,
            boolean ignoreTornadoes,
            boolean windCheck,
            boolean windAnyway,
            boolean forParticles,
            CallbackInfoReturnable<Vec3> cir) {
        Vec3 pmwWind = cir.getReturnValue();
        if (pmwWind == null) {
            return;
        }
        Vec2 tfc = Climate.get(level).getWind(level, new BlockPos((int) position.x, (int) position.y, (int) position.z));
        Vec3 tfcWind = new Vec3((double) tfc.x, 0.0D, (double) tfc.y);
        cir.setReturnValue(pmwWind.add(tfcWind.scale(0.2D)));
    }
}
