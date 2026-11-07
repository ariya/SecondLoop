package secondloop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.VoltageSensor;

/**
 * (battery/voltage): the battery voltage in volts, as the hubs measure it.
 * Each Control or Expansion Hub has its own voltage sensor. The lowest reading
 * is the one that matters, since that hub is the first to brown out; a sensor
 * that reads 0 is not connected and is skipped.
 */
public final class BatteryModule {
    private BatteryModule() {
    }

    public static void install(Env env, final OpMode opMode) {
        Builtins.define(env, new Builtin("battery/voltage") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 0, 0);
                double min = Double.POSITIVE_INFINITY;
                if (opMode.hardwareMap != null) {
                    for (VoltageSensor sensor : opMode.hardwareMap.voltageSensor) {
                        double v = sensor.getVoltage();
                        if (v > 0) {
                            min = Math.min(min, v);
                        }
                    }
                }
                return min == Double.POSITIVE_INFINITY ? 0.0 : min;
            }
        });
    }
}
