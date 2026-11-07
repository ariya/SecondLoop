package secondloop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/** Checks the servo/* functions against stand-in servos. Run: java -cp build/classes secondloop.ServoTest */
public final class ServoTest {
    private static final String TEST_SERVO =
            "(def servo-name \"servo\")\n" +
            "(defonce position (atom 0.5))      ; change it from the REPL too: (reset! position 0.25)\n" +
            "\n" +
            "(defn pressed?\n" +
            "  \"True when button k is down now but was up in the previous snapshot.\"\n" +
            "  [prev pad k]\n" +
            "  (and (k pad) (not (k prev))))\n" +
            "\n" +
            "(defn nudge [d]\n" +
            "  (swap! position (fn [p] (clamp (+ p d) 0 1))))\n" +
            "\n" +
            "(defn step [prev pad]\n" +
            "  (when (pressed? prev pad :a) (servo/set-direction servo-name :forward))\n" +
            "  (when (pressed? prev pad :b) (servo/set-direction servo-name :reverse))\n" +
            "  (when (pressed? prev pad :dpad-up) (nudge 0.01))\n" +
            "  (when (pressed? prev pad :dpad-down) (nudge -0.01))\n" +
            "  (when (pressed? prev pad :right-bumper) (nudge 0.1))\n" +
            "  (when (pressed? prev pad :left-bumper) (nudge -0.1))\n" +
            "  (servo/set-position servo-name @position)\n" +
            "  (telemetry/clear)\n" +
            "  (telemetry/add-line \"Servo\" servo-name)\n" +
            "  (telemetry/add-line \"Direction\" (servo/direction servo-name))\n" +
            "  (telemetry/add-line (format \"Position %5.2f\" @position)))\n" +
            "\n" +
            "(defn control-loop []\n" +
            "  (loop [prev (gamepad 1)]\n" +
            "    (let [pad (gamepad 1)]\n" +
            "      (step prev pad)\n" +
            "      (time/sleep 0.02)\n" +
            "      (recur pad))))\n";

    private static int failures;
    private static int count;

    /** Remembers what was set, like a servo would. */
    static final class FakeServo implements Servo {
        Direction direction = Direction.FORWARD;
        double position;

        @Override
        public void setDirection(Direction direction) {
            this.direction = direction;
        }

        @Override
        public Direction getDirection() {
            return direction;
        }

        @Override
        public void setPosition(double position) {
            this.position = position;
        }

        @Override
        public double getPosition() {
            return position;
        }
    }

    /** Remembers what was set, like a continuous rotation servo would. */
    static final class FakeCRServo implements CRServo {
        Direction direction = Direction.FORWARD;
        double power;

        @Override
        public void setDirection(Direction direction) {
            this.direction = direction;
        }

        @Override
        public Direction getDirection() {
            return direction;
        }

        @Override
        public void setPower(double power) {
            this.power = power;
        }

        @Override
        public double getPower() {
            return power;
        }
    }

    private final Map<String, Object> devices = new HashMap<String, Object>();
    private final OpMode opMode = new OpMode() {
    };
    private final StringWriter buffer = new StringWriter();
    private final Repl repl;

    private ServoTest() {
        opMode.hardwareMap = new HardwareMap() {
            @Override
            public <T> T tryGet(Class<? extends T> type, String name) {
                Object device = devices.get(name);
                return type.isInstance(device) ? type.cast(device) : null;
            }
        };
        Env globals = Builtins.createGlobals();
        ServoModule.install(globals, opMode);
        GamepadModule.install(globals, opMode);
        new TelemetryModule().install(globals);
        repl = new Repl(globals, new PrintWriter(buffer));
    }

    private void check(String expected, String line) {
        buffer.getBuffer().setLength(0);
        repl.feed(line);
        check(expected, buffer.toString().trim(), line);
    }

    private static void check(String expected, String actual, String label) {
        count++;
        if (!actual.equals(expected)) {
            failures++;
            System.out.println("FAIL: " + label);
            System.out.println("  expected: " + expected);
            System.out.println("  actual:   " + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        ServoTest t = new ServoTest();
        FakeServo claw = new FakeServo();
        t.devices.put("claw", claw);
        t.devices.put("left", "not a servo");

        t.check("0.0", "(servo/position \"claw\")");
        t.check("0.3", "(servo/set-position \"claw\" 0.3)");
        check("0.3", String.valueOf(claw.position), "position set");
        t.check("0.3", "(servo/position \"claw\")");
        t.check("1.0", "(servo/set-position \"claw\" 1)");
        t.check("1.0", "(servo/position \"claw\")");
        t.check("0.0", "(servo/set-position \"claw\" 0)");
        t.check("0.0", "(servo/position \"claw\")");

        t.check(":forward", "(servo/direction \"claw\")");
        t.check(":reverse", "(servo/set-direction \"claw\" :reverse)");
        t.check(":reverse", "(servo/direction \"claw\")");
        check("REVERSE", String.valueOf(claw.direction), "direction set");
        t.check(":forward", "(servo/set-direction \"claw\" :forward)");
        t.check(":forward", "(servo/direction \"claw\")");

        t.check("Execution error: servo/set-position expects a position from 0.0 to 1.0, got: 1.5",
                "(servo/set-position \"claw\" 1.5)");
        t.check("Execution error: servo/set-position expects a position from 0.0 to 1.0, got: -0.1",
                "(servo/set-position \"claw\" -0.1)");
        t.check("Execution error: servo/set-position expects a position from 0.0 to 1.0, got: ##NaN",
                "(servo/set-position \"claw\" (Math/sqrt -1))");
        t.check("0.0", "(servo/position \"claw\")");
        t.check("Execution error: servo/set-position expects numbers, got: \"open\"",
                "(servo/set-position \"claw\" \"open\")");
        t.check("Execution error: servo/set-direction expects :forward or :reverse, got: :backward",
                "(servo/set-direction \"claw\" :backward)");
        t.check("Execution error: No servo named \"clow\" in the robot configuration",
                "(servo/set-position \"clow\" 0.5)");
        t.check("Execution error: No servo named \"left\" in the robot configuration",
                "(servo/position \"left\")");
        t.check("Execution error: servo/position expects a servo name as a string, got: :claw",
                "(servo/position :claw)");
        t.check("Execution error: Wrong number of args (1) passed to: servo/set-position",
                "(servo/set-position \"claw\")");

        FakeCRServo intake = new FakeCRServo();
        t.devices.put("intake", intake);
        t.check("0.0", "(crservo/power \"intake\")");
        t.check("0.5", "(crservo/set-power \"intake\" 0.5)");
        check("0.5", String.valueOf(intake.power), "crservo power set");
        t.check("0.5", "(crservo/power \"intake\")");
        t.check("-1.0", "(crservo/set-power \"intake\" -1)");
        t.check("-1.0", "(crservo/power \"intake\")");
        t.check(":forward", "(crservo/direction \"intake\")");
        t.check(":reverse", "(crservo/set-direction \"intake\" :reverse)");
        t.check(":reverse", "(crservo/direction \"intake\")");
        check("REVERSE", String.valueOf(intake.direction), "crservo direction set");
        t.check("Execution error: crservo/set-power expects a power from -1.0 to 1.0, got: 1.5",
                "(crservo/set-power \"intake\" 1.5)");
        t.check("Execution error: crservo/set-power expects a power from -1.0 to 1.0, got: ##NaN",
                "(crservo/set-power \"intake\" (Math/sqrt -1))");
        t.check("-1.0", "(crservo/power \"intake\")");
        t.check("Execution error: crservo/set-direction expects :forward or :reverse, got: :up",
                "(crservo/set-direction \"intake\" :up)");
        t.check("Execution error: No continuous rotation servo named \"claw\" in the robot configuration",
                "(crservo/set-power \"claw\" 0.5)");
        t.check("Execution error: No servo named \"intake\" in the robot configuration",
                "(servo/set-position \"intake\" 0.5)");
        t.check("Execution error: crservo/power expects a servo name as a string, got: nil",
                "(crservo/power nil)");
        t.check("Execution error: Wrong number of args (3) passed to: crservo/set-power",
                "(crservo/set-power \"intake\" 0.5 1)");

        // The servo test program from the manual, one round at a time.
        FakeServo servo = new FakeServo();
        t.devices.put("servo", servo);
        String example = TEST_SERVO;
        t.check("#'user/servo-name\n#'user/position\n#'user/pressed?\n#'user/nudge\n#'user/step\n#'user/control-loop", example);
        Gamepad prev = new Gamepad();
        Gamepad pad = new Gamepad();
        t.opMode.gamepad1 = prev;
        t.opMode.gamepad2 = pad;
        t.check("nil", "(step (gamepad 1) (gamepad 2))");
        check("0.50 FORWARD", String.format("%.2f", servo.position) + " " + servo.direction, "example holds the start position");
        pad.dpad_up = true;
        pad.b = true;
        t.check("nil", "(step (gamepad 1) (gamepad 2))");
        check("0.51 REVERSE", String.format("%.2f", servo.position) + " " + servo.direction, "example nudges the servo");
        t.check("nil", "(step (gamepad 2) (gamepad 2))");
        check("0.51", String.format("%.2f", servo.position), "a held button counts once");
        pad.dpad_up = false;
        pad.right_bumper = true;
        for (int i = 0; i < 6; i++) {
            t.check("nil", "(step (gamepad 1) (gamepad 2))");
        }
        check("1.00", String.format("%.2f", servo.position), "the position stops at 1.0");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
