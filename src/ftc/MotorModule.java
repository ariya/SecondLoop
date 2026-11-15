package secondloop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (motor/set-power "left" 0.5), (motor/power "left"),
 * (motor/set-velocity "flywheel" 1200), (motor/velocity "flywheel"),
 * (motor/set-direction "left" :reverse), (motor/direction "left"),
 * (motor/set-pidf "flywheel" {:p 10 :f 14}) and (motor/pidf "flywheel"): DC
 * motors by their name in the robot configuration.
 * Unlike Telemetry, motor calls can come from any thread: the SDK already
 * serialises every command it sends to a hub.
 */
public final class MotorModule {
    private MotorModule() {
    }

    public static void install(Env env, final OpMode opMode) {
        // The mode each motor was last switched to during this OpMode run, so
        // setMode, which talks to the hub, is only called when it changes.
        final Map<String, DcMotor.RunMode> modes = new HashMap<String, DcMotor.RunMode>();

        Builtins.define(env, new Builtin("motor/set-power") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                DcMotor motor = lookup(opMode, args[0], name());
                double power = Builtins.toNumber(args[1], name());
                // Also rejects NaN, which must never reach a motor.
                if (!(power >= -1 && power <= 1)) {
                    throw new ScriptError(name() + " expects a power from -1.0 to 1.0, got: " + Printer.print(args[1]));
                }
                // Power is a fraction of battery voltage, as in the SDK
                // examples. With RUN_USING_ENCODER and no encoder plugged in,
                // the motor would run at full power.
                switchMode(modes, (String) args[0], motor, DcMotor.RunMode.RUN_WITHOUT_ENCODER);
                motor.setPower(power);
                return power;
            }
        });
        Builtins.define(env, new Builtin("motor/set-velocity") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                DcMotorEx motor = lookupEx(opMode, args[0], name());
                double velocity = Builtins.toNumber(args[1], name());
                if (Double.isNaN(velocity) || Double.isInfinite(velocity)) {
                    throw new ScriptError(name() + " expects a finite velocity, got: " + Printer.print(args[1]));
                }
                // The hub's speed controller reads the encoder to hold the
                // velocity, whatever the load or battery voltage.
                switchMode(modes, (String) args[0], motor, DcMotor.RunMode.RUN_USING_ENCODER);
                motor.setVelocity(velocity);
                return velocity;
            }
        });
        Builtins.define(env, new Builtin("motor/set-pidf") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                DcMotorEx motor = lookupEx(opMode, args[0], name());
                if (!(args[1] instanceof Map)) {
                    throw new ScriptError(name() + " expects a map such as {:p 10 :i 3 :d 0 :f 14}, got: "
                            + Printer.print(args[1]));
                }
                Map<?, ?> given = (Map<?, ?>) args[1];
                for (Object key : given.keySet()) {
                    if (!PIDF.contains(key)) {
                        throw new ScriptError(name() + " expects the keys :p :i :d :f, got: " + Printer.print(key));
                    }
                }
                double[] k = new double[4];
                for (int n = 0; n < 4; n++) {
                    Object v = given.get(PIDF.get(n));
                    k[n] = given.containsKey(PIDF.get(n)) ? Builtins.toNumber(v, name()) : 0;
                    // The hub stores them as fixed point; a bad value would make the motor run away.
                    if (!(k[n] >= 0) || Double.isInfinite(k[n])) {
                        throw new ScriptError(name() + " expects coefficients of 0 or more, got: " + Printer.print(v));
                    }
                }
                // Only the velocity loop: motor/set-velocity is the one function that uses it.
                motor.setPIDFCoefficients(DcMotor.RunMode.RUN_USING_ENCODER,
                        new PIDFCoefficients(k[0], k[1], k[2], k[3]));
                return pidfMap(k[0], k[1], k[2], k[3]);
            }
        });
        Builtins.define(env, new Builtin("motor/pidf") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                PIDFCoefficients c = lookupEx(opMode, args[0], name())
                        .getPIDFCoefficients(DcMotor.RunMode.RUN_USING_ENCODER);
                return pidfMap(c.p, c.i, c.d, c.f);
            }
        });
        Builtins.define(env, new Builtin("motor/velocity") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return lookupEx(opMode, args[0], name()).getVelocity();
            }
        });
        Builtins.define(env, new Builtin("motor/power") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return lookup(opMode, args[0], name()).getPower();
            }
        });
        Builtins.define(env, new Builtin("motor/set-direction") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                DcMotor motor = lookup(opMode, args[0], name());
                DcMotorSimple.Direction direction;
                if (Keyword.of("forward").equals(args[1])) {
                    direction = DcMotorSimple.Direction.FORWARD;
                } else if (Keyword.of("reverse").equals(args[1])) {
                    direction = DcMotorSimple.Direction.REVERSE;
                } else {
                    throw new ScriptError(name() + " expects :forward or :reverse, got: " + Printer.print(args[1]));
                }
                motor.setDirection(direction);
                return args[1];
            }
        });
        Builtins.define(env, new Builtin("motor/direction") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                DcMotorSimple.Direction direction = lookup(opMode, args[0], name()).getDirection();
                return Keyword.of(direction == DcMotorSimple.Direction.REVERSE ? "reverse" : "forward");
            }
        });
    }

    private static final List<Keyword> PIDF = Arrays.asList(
            Keyword.of("p"), Keyword.of("i"), Keyword.of("d"), Keyword.of("f"));

    private static FormMap pidfMap(double p, double i, double d, double f) {
        FormMap m = new FormMap();
        m.put(PIDF.get(0), p);
        m.put(PIDF.get(1), i);
        m.put(PIDF.get(2), d);
        m.put(PIDF.get(3), f);
        return m;
    }

    private static void switchMode(Map<String, DcMotor.RunMode> modes, String name, DcMotor motor, DcMotor.RunMode mode) {
        synchronized (modes) {
            if (modes.put(name, mode) != mode) {
                motor.setMode(mode);
            }
        }
    }

    private static DcMotor lookup(OpMode opMode, Object name, String fnName) {
        return find(opMode, name, fnName, DcMotor.class);
    }

    /** Velocity needs DcMotorEx, which every motor on a Control or Expansion Hub is. */
    private static DcMotorEx lookupEx(OpMode opMode, Object name, String fnName) {
        return find(opMode, name, fnName, DcMotorEx.class);
    }

    private static <T> T find(OpMode opMode, Object name, String fnName, Class<T> type) {
        if (!(name instanceof String)) {
            throw new ScriptError(fnName + " expects a motor name as a string, got: " + Printer.print(name));
        }
        T motor = opMode.hardwareMap == null ? null : opMode.hardwareMap.<T>tryGet(type, (String) name);
        if (motor == null) {
            throw new ScriptError("No motor named " + Printer.print(name) + " in the robot configuration");
        }
        return motor;
    }
}
