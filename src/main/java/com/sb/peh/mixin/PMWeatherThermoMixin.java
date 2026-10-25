package com.sb.peh.mixin;

import dev.protomanly.pmweather.block.entity.RadarBlockEntity;
import dev.protomanly.pmweather.weather.ThermodynamicEngine;
import dev.protomanly.pmweather.weather.WeatherHandler;
import dev.protomanly.pmweather.weather.ThermodynamicEngine.AtmosphericDataPoint;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.climate.Climate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ThermodynamicEngine.class)
public abstract class PMWeatherThermoMixin {
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
