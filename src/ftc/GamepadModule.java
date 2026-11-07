package secondloop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.Gamepad;

/**
 * (gamepad 1) and (gamepad 2): a snapshot of a controller as a map, with the
 * FTC field names as kebab-case keywords, e.g. left_trigger becomes
 * :left-trigger.
 * Values are not adjusted: as in FTC, pushing a stick forward gives a
 * negative :left-stick-y.
 */
public final class GamepadModule {
    private GamepadModule() {
    }

    /** The gamepad fields are read on every call, so each snapshot is current. */
    public static void install(Env env, final OpMode opMode) {
        Builtins.define(env, new Builtin("gamepad") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                Gamepad pad;
                if (Builtins.isEqual(args[0], 1.0)) {
                    pad = opMode.gamepad1;
                } else if (Builtins.isEqual(args[0], 2.0)) {
                    pad = opMode.gamepad2;
                } else {
                    throw new ScriptError("gamepad expects 1 or 2, got: " + Printer.print(args[0]));
                }
                if (pad == null) {
                    throw new ScriptError("Gamepad " + Printer.print(args[0]) + " is not available yet");
                }
                return snapshot(pad);
            }
        });
    }

    public static FormMap snapshot(Gamepad g) {
        FormMap m = new FormMap();
        put(m, "left-stick-x", g.left_stick_x);
        put(m, "left-stick-y", g.left_stick_y);
        put(m, "right-stick-x", g.right_stick_x);
        put(m, "right-stick-y", g.right_stick_y);
        put(m, "left-trigger", g.left_trigger);
        put(m, "right-trigger", g.right_trigger);
        put(m, "a", g.a);
        put(m, "b", g.b);
        put(m, "x", g.x);
        put(m, "y", g.y);
        put(m, "left-bumper", g.left_bumper);
        put(m, "right-bumper", g.right_bumper);
        put(m, "dpad-up", g.dpad_up);
        put(m, "dpad-down", g.dpad_down);
        put(m, "dpad-left", g.dpad_left);
        put(m, "dpad-right", g.dpad_right);
        put(m, "left-stick-button", g.left_stick_button);
        put(m, "right-stick-button", g.right_stick_button);
        put(m, "start", g.start);
        put(m, "back", g.back);
        put(m, "guide", g.guide);
        return m;
    }

    /** Converts through the float's shortest text, so 0.8f shows as 0.8 rather than 0.800000011920929. */
    private static void put(FormMap m, String key, float value) {
        m.put(Keyword.of(key), Double.parseDouble(Float.toString(value)));
    }

    private static void put(FormMap m, String key, boolean value) {
        m.put(Keyword.of(key), value);
    }
}
