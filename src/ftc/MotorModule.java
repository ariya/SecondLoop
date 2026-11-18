package secondloop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * (motor/set-power "left" 0.5), (motor/power "left"),
 * (motor/set-velocity "flywheel" 1200), (motor/velocity "flywheel"),
 * (motor/set-direction "left" :reverse), (motor/direction "left"),
 * (motor/set-run-mode "left" :stop-and-reset-encoder), (motor/run-mode "left"),
 * (motor/set-target-position "arm" 300), (motor/target-position "arm"),
 * (motor/current-position "arm"),
 * (motor/set-pidf "flywheel" {:p 10 :f 14}), (motor/pidf "flywheel"),
 * (motor/set-zero-power-behavior "left" :brake) and
 * (motor/zero-power-behavior "left"): DC
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
        // Motors given a target during this OpMode run, guarded by modes.
        final Set<String> targeted = new HashSet<String>();

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
        Builtins.define(env, new Builtin("motor/set-run-mode") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                DcMotor motor = lookup(opMode, args[0], name());
                DcMotor.RunMode mode = null;
                for (DcMotor.RunMode m : DcMotor.RunMode.values()) {
                    if (runModeKeyword(m).equals(args[1])) {
                        mode = m;
                    }
                }
                if (mode == null) {
                    throw new ScriptError(name() + " expects :run-without-encoder, :run-using-encoder, "
                            + ":run-to-position or :stop-and-reset-encoder, got: " + Printer.print(args[1]));
                }
                // Always sent, even when unchanged: resetting the encoder
                // twice must reset it twice. The cache is kept in step so
                // that motor/set-power and motor/set-velocity still switch.
                synchronized (modes) {
                    // The SDK throws an exception that doesn't say what to do.
                    if (mode == DcMotor.RunMode.RUN_TO_POSITION && !targeted.contains(args[0])) {
                        throw new ScriptError(name() + " needs a target first: call motor/set-target-position");
                    }
                    modes.put((String) args[0], mode);
                    motor.setMode(mode);
                }
                return args[1];
            }
        });
        Builtins.define(env, new Builtin("motor/run-mode") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return runModeKeyword(lookup(opMode, args[0], name()).getMode());
            }
        });
        Builtins.define(env, new Builtin("motor/set-target-position") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                DcMotor motor = lookup(opMode, args[0], name());
                double position = Builtins.toNumber(args[1], name());
                // Also rejects NaN. The hub counts ticks in an int.
                if (!(position >= Integer.MIN_VALUE && position <= Integer.MAX_VALUE)) {
                    throw new ScriptError(name() + " expects a position in encoder ticks, got: "
                            + Printer.print(args[1]));
                }
                // Rounded, so that a computed target such as (* 1.5 537.7)
                // works; the result shows what was sent.
                int ticks = (int) Math.round(position);
                synchronized (modes) {
                    motor.setTargetPosition(ticks);
                    targeted.add((String) args[0]);
                }
                return (double) ticks;
            }
        });
        Builtins.define(env, new Builtin("motor/target-position") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return (double) lookup(opMode, args[0], name()).getTargetPosition();
            }
        });
        Builtins.define(env, new Builtin("motor/current-position") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return (double) lookup(opMode, args[0], name()).getCurrentPosition();
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
        Builtins.define(env, new Builtin("motor/set-zero-power-behavior") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                DcMotor motor = lookup(opMode, args[0], name());
                DcMotor.ZeroPowerBehavior behavior;
                if (Keyword.of("brake").equals(args[1])) {
                    behavior = DcMotor.ZeroPowerBehavior.BRAKE;
                } else if (Keyword.of("float").equals(args[1])) {
                    behavior = DcMotor.ZeroPowerBehavior.FLOAT;
                } else {
                    throw new ScriptError(name() + " expects :brake or :float, got: " + Printer.print(args[1]));
                }
                motor.setZeroPowerBehavior(behavior);
                return args[1];
            }
        });
        Builtins.define(env, new Builtin("motor/zero-power-behavior") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                // The SDK can also report UNKNOWN, which becomes :unknown.
                DcMotor.ZeroPowerBehavior behavior = lookup(opMode, args[0], name()).getZeroPowerBehavior();
                return Keyword.of(behavior.name().toLowerCase(java.util.Locale.ROOT));
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

    /** RUN_USING_ENCODER becomes :run-using-encoder. */
    private static Keyword runModeKeyword(DcMotor.RunMode mode) {
        return Keyword.of(mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
    }

    private static void switchMode(Map<String, DcMotor.RunMode> modes, String name, DcMotor motor, DcMotor.RunMode mode) {
        synchronized (modes) {
            DcMotor.RunMode current = modes.get(name);
            // On the way to a target, the power or velocity is the top speed,
            // so stay there until motor/set-run-mode leaves it.
            if (current != mode && current != DcMotor.RunMode.RUN_TO_POSITION) {
                modes.put(name, mode);
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
