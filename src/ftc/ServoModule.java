package secondloop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.Servo;

/**
 * (servo/set-position "claw" 0.3), (servo/position "claw"),
 * (servo/set-direction "claw" :reverse) and (servo/direction "claw"):
 * standard servos by their name in the robot configuration.
 * (crservo/set-power "intake" 0.5), (crservo/power "intake"),
 * (crservo/set-direction "intake" :reverse) and (crservo/direction "intake"):
 * continuous rotation servos, which the SDK drives like a motor without an
 * encoder.
 * Like motor calls, servo calls can come from any thread: the SDK already
 * serialises every command it sends to a hub.
 */
public final class ServoModule {
    private ServoModule() {
    }

    public static void install(Env env, final OpMode opMode) {
        Builtins.define(env, new Builtin("servo/set-position") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                Servo servo = lookup(opMode, args[0], name());
                double position = Builtins.toNumber(args[1], name());
                // Also rejects NaN. The SDK would quietly clip an out-of-range
                // position, hiding a mistake in a formula.
                if (!(position >= 0 && position <= 1)) {
                    throw new ScriptError(name() + " expects a position from 0.0 to 1.0, got: " + Printer.print(args[1]));
                }
                servo.setPosition(position);
                return position;
            }
        });
        Builtins.define(env, new Builtin("servo/position") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return lookup(opMode, args[0], name()).getPosition();
            }
        });
        Builtins.define(env, new Builtin("servo/set-direction") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                Servo servo = lookup(opMode, args[0], name());
                Servo.Direction direction;
                if (Keyword.of("forward").equals(args[1])) {
                    direction = Servo.Direction.FORWARD;
                } else if (Keyword.of("reverse").equals(args[1])) {
                    direction = Servo.Direction.REVERSE;
                } else {
                    throw new ScriptError(name() + " expects :forward or :reverse, got: " + Printer.print(args[1]));
                }
                servo.setDirection(direction);
                return args[1];
            }
        });
        Builtins.define(env, new Builtin("servo/direction") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                Servo.Direction direction = lookup(opMode, args[0], name()).getDirection();
                return Keyword.of(direction == Servo.Direction.REVERSE ? "reverse" : "forward");
            }
        });

        Builtins.define(env, new Builtin("crservo/set-power") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                CRServo servo = lookupCr(opMode, args[0], name());
                double power = Builtins.toNumber(args[1], name());
                // Also rejects NaN, which must never reach a servo.
                if (!(power >= -1 && power <= 1)) {
                    throw new ScriptError(name() + " expects a power from -1.0 to 1.0, got: " + Printer.print(args[1]));
                }
                servo.setPower(power);
                return power;
            }
        });
        Builtins.define(env, new Builtin("crservo/power") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return lookupCr(opMode, args[0], name()).getPower();
            }
        });
        Builtins.define(env, new Builtin("crservo/set-direction") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                CRServo servo = lookupCr(opMode, args[0], name());
                DcMotorSimple.Direction direction;
                if (Keyword.of("forward").equals(args[1])) {
                    direction = DcMotorSimple.Direction.FORWARD;
                } else if (Keyword.of("reverse").equals(args[1])) {
                    direction = DcMotorSimple.Direction.REVERSE;
                } else {
                    throw new ScriptError(name() + " expects :forward or :reverse, got: " + Printer.print(args[1]));
                }
                servo.setDirection(direction);
                return args[1];
            }
        });
        Builtins.define(env, new Builtin("crservo/direction") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                DcMotorSimple.Direction direction = lookupCr(opMode, args[0], name()).getDirection();
                return Keyword.of(direction == DcMotorSimple.Direction.REVERSE ? "reverse" : "forward");
            }
        });
    }

    private static Servo lookup(OpMode opMode, Object name, String fnName) {
        return find(opMode, name, fnName, Servo.class, "servo");
    }

    private static CRServo lookupCr(OpMode opMode, Object name, String fnName) {
        return find(opMode, name, fnName, CRServo.class, "continuous rotation servo");
    }

    private static <T> T find(OpMode opMode, Object name, String fnName, Class<T> type, String kind) {
        if (!(name instanceof String)) {
            throw new ScriptError(fnName + " expects a servo name as a string, got: " + Printer.print(name));
        }
        T servo = opMode.hardwareMap == null ? null : opMode.hardwareMap.<T>tryGet(type, (String) name);
        if (servo == null) {
            throw new ScriptError("No " + kind + " named " + Printer.print(name) + " in the robot configuration");
        }
        return servo;
    }
}
