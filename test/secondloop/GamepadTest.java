package secondloop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.Gamepad;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Checks (gamepad n) against stand-in gamepads. Run: java -cp build/classes secondloop.GamepadTest */
public final class GamepadTest {
    private static int failures;
    private static int count;

    private final OpMode opMode = new OpMode() {
    };
    private final StringWriter buffer = new StringWriter();
    private final Repl repl;

    private GamepadTest() {
        Env globals = Builtins.createGlobals();
        GamepadModule.install(globals, opMode);
        repl = new Repl(globals, new PrintWriter(buffer));
    }

    private void check(String expected, String line) {
        buffer.getBuffer().setLength(0);
        repl.feed(line);
        String actual = buffer.toString().trim();
        count++;
        if (!actual.equals(expected)) {
            failures++;
            System.out.println("FAIL: " + line);
            System.out.println("  expected: " + expected);
            System.out.println("  actual:   " + actual);
        }
    }

    public static void main(String[] args) {
        GamepadTest t = new GamepadTest();
        t.check("Execution error: Gamepad 1.0 is not available yet", "(gamepad 1)");

        Gamepad pad1 = new Gamepad();
        Gamepad pad2 = new Gamepad();
        t.opMode.gamepad1 = pad1;
        t.opMode.gamepad2 = pad2;
        t.check("{:left-stick-x 0.0, :left-stick-y 0.0, :right-stick-x 0.0, :right-stick-y 0.0, "
                + ":left-trigger 0.0, :right-trigger 0.0, :a false, :b false, :x false, :y false, "
                + ":left-bumper false, :right-bumper false, :dpad-up false, :dpad-down false, "
                + ":dpad-left false, :dpad-right false, :left-stick-button false, "
                + ":right-stick-button false, :start false, :back false, :guide false}", "(gamepad 1)");

        pad1.right_trigger = 0.8f;
        pad1.left_stick_y = -0.35f;
        pad1.a = true;
        pad1.left_bumper = true;
        pad2.dpad_up = true;
        t.check("0.8", "(:right-trigger (gamepad 1))");
        t.check("-0.35", "(:left-stick-y (gamepad 1))");
        t.check("[true true false]", "(let [{:keys [a left-bumper b]} (gamepad 1)] [a left-bumper b])");
        t.check("true", "(:dpad-up (gamepad 2))");
        t.check("false", "(:dpad-up (gamepad 1))");

        // A snapshot does not change when the gamepad does.
        t.check("#'user/before", "(def before (gamepad 1))");
        pad1.a = false;
        t.check("[true false]", "[(:a before) (:a (gamepad 1))]");

        t.check("Execution error: gamepad expects 1 or 2, got: 3.0", "(gamepad 3)");
        t.check("Execution error: Wrong number of args (0) passed to: gamepad", "(gamepad)");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
