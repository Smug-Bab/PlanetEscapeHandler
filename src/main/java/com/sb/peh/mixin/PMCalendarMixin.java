package com.sb.peh.mixin;

import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.seasons.SeasonHandler;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.ThermodynamicEngine.AtmosphericDataPoint;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.WindEngine;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.calendar.Month;
import net.dries007.tfc.util.climate.Climate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class PMCalendarMixin {

    @Mixin(WindEngine.class)
    public abstract static class WindMixin {
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

    @Mixin(SeasonHandler.class)
    public abstract static class SeasonMixin {
        @Inject(method = "getDay", at = @At("HEAD"), cancellable = true, remap = false)
        private static void planetescapehandler$getDay(Level level, CallbackInfoReturnable<Integer> cir) {
            ICalendar calendar = Calendars.get(level);
            cir.setReturnValue((int) calendar.getTotalCalendarDays());
        }

        @Inject(method = "getDayInMonth", at = @At("HEAD"), cancellable = true, remap = false)
        private static void planetescapehandler$getDayInMonth(Level level, CallbackInfoReturnable<Integer> cir) {
            ICalendar calendar = Calendars.get(level);
            cir.setReturnValue(calendar.getCalendarDayOfMonth());
        }

        @Inject(method = "getMonth", at = @At("HEAD"), cancellable = true, remap = false)
        private static void planetescapehandler$getMonth(Level level, CallbackInfoReturnable<Integer> cir) {
            ICalendar calendar = Calendars.get(level);
            Month month = ICalendar.getMonthOfYear(calendar.getCalendarTicks(), (long) calendar.getCalendarDaysInMonth());
            cir.setReturnValue(month.ordinal() + 1);
        }

        @Inject(method = "getYear", at = @At("HEAD"), cancellable = true, remap = false)
        private static void planetescapehandler$getYear(Level level, CallbackInfoReturnable<Integer> cir) {
            ICalendar calendar = Calendars.get(level);
            int year = (int) (calendar.getCalendarYear() - 1000L);
            cir.setReturnValue(Math.max(0, year));
        }

        @Inject(method = "getSeasonEffectSine", at = @At("HEAD"), cancellable = true, remap = false)
        private static void planetescapehandler$getSeasonEffectSine(Level level, float offset, CallbackInfoReturnable<Float> cir) {
            ICalendar calendar = Calendars.get(level);
            float yearFraction = calendar.getCalendarFractionOfYear();
            float month = yearFraction * 12.0F;
            float result = Mth.sin((float) Math.PI * (month - (3.5F + offset)) / 6.0F);
            cir.setReturnValue(result);
        }
    }

    @Mixin(ThermodynamicEngine.class)
    public abstract static class ThermoMixin {
        @Inject(method = "samplePoint", at = @At("RETURN"), cancellable = true, remap = false)
        private static void planetescapehandler$overrideSamplePoint(
                WeatherHandler weatherHandler,
                Vec3 pos,
                Level level,
                RadarBlockEntity radar,
                int advance,
                CallbackInfoReturnable<AtmosphericDataPoint> cir) {
            AtmosphericDataPoint original = cir.getReturnValue();
            if (original == null) {
                return;
            }
            BlockPos bp = new BlockPos((int) pos.x, (int) pos.y, (int) pos.z);
            ICalendar calendar = Calendars.get(level);
            float tfcTemp = Climate.getInstantTemperature(level, bp, calendar);
            float tfcRain = Climate.get(level).getAverageRainfall(level, bp);
            float pmwPressure = original.pressure();
            float pmwNoise = original.virtualTemperature() - original.temperature();
            float tempNoise = pmwNoise * 0.2F;
            float temperature = tfcTemp + tempNoise;
            float humidityApprox = Mth.clamp(tfcRain / 500.0F, 0.0F, 1.0F);
            humidityApprox = (float) Math.pow((double) humidityApprox, 1.1F);
            humidityApprox += pmwNoise * 0.12F;
            float heat = Mth.clamp((tfcTemp - 10.0F) / 25.0F, 0.0F, 1.0F);
            float cold = Mth.clamp((10.0F - tfcTemp) / 25.0F, 0.0F, 1.0F);
            float temperatureHumidityOffset = -heat * 0.35F + cold * 0.15F;
            humidityApprox += temperatureHumidityOffset;
            humidityApprox = Mth.clamp(humidityApprox, 0.02F, 1.0F);
            float maxDepression = 22.0F;
            float dewDepression = (float) Math.pow((double) (1.0F - humidityApprox), 1.1F) * maxDepression;
            float targetDew = temperature - dewDepression;
            float dew = Math.min(targetDew, temperature);
            float virtualTemp = ThermodynamicEngine.calcVTemp(temperature, dew, pmwPressure);
            AtmosphericDataPoint rebuilt = new AtmosphericDataPoint(temperature, dew, pmwPressure, virtualTemp);
            cir.setReturnValue(rebuilt);
        }
    }
}