package secondloop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/** Checks the motor/* functions against stand-in motors. Run: java -cp build/classes secondloop.MotorTest */
public final class MotorTest {
    private static final String TEST_MOTOR =
            "(def motor-name \"motor\")\n" +
            "(defonce power (atom 0.1))         ; change it from the REPL too: (reset! power 0.15)\n" +
            "\n" +
            "(defn pressed?\n" +
            "  \"True when button k is down now but was up in the previous snapshot.\"\n" +
            "  [prev pad k]\n" +
            "  (and (k pad) (not (k prev))))\n" +
            "\n" +
            "(defn step [prev pad]\n" +
            "  (when (pressed? prev pad :a) (motor/set-direction motor-name :forward))\n" +
            "  (when (pressed? prev pad :b) (motor/set-direction motor-name :reverse))\n" +
            "  (when (pressed? prev pad :dpad-up) (swap! power (fn [p] (clamp (+ p 0.01) 0 0.2))))\n" +
            "  (when (pressed? prev pad :dpad-down) (swap! power (fn [p] (clamp (- p 0.01) 0 0.2))))\n" +
            "  (motor/set-power motor-name (if (> (:right-trigger pad) 0) @power 0))\n" +
            "  (telemetry/clear)\n" +
            "  (telemetry/add-line \"Motor\" motor-name)\n" +
            "  (telemetry/add-line \"Direction\" (motor/direction motor-name))\n" +
            "  (telemetry/add-line (format \"Power %6.2f\" @power)))\n" +
            "\n" +
            "(defn control-loop []\n" +
            "  (try\n" +
            "    (loop [prev (gamepad 1)]\n" +
            "      (let [pad (gamepad 1)]\n" +
            "        (step prev pad)\n" +
            "        (time/sleep 0.02)\n" +
            "        (recur pad)))\n" +
            "    (finally\n" +
            "      (motor/set-power motor-name 0))))\n";

    private static final String MECANUM_DRIVE =
            "(defn mecanum-drive\n" +
            "  \"Sets the four wheel powers and returns them. If the inputs add up to more\n" +
            "  than 1, the powers are scaled down together.\"\n" +
            "  [forward strafe rotate]\n" +
            "  (motor/set-direction \"front_left_drive\" :reverse)\n" +
            "  (motor/set-direction \"back_left_drive\" :reverse)\n" +
            "  (let [d           (max (+ (abs forward) (abs strafe) (abs rotate)) 1)\n" +
            "        front-left  (/ (- (+ forward strafe) rotate) d)\n" +
            "        front-right (/ (+ (- forward strafe) rotate) d)\n" +
            "        back-left   (/ (- forward strafe rotate) d)\n" +
            "        back-right  (/ (+ forward strafe rotate) d)]\n" +
            "    (motor/set-power \"front_left_drive\" front-left)\n" +
            "    (motor/set-power \"front_right_drive\" front-right)\n" +
            "    (motor/set-power \"back_left_drive\" back-left)\n" +
            "    (motor/set-power \"back_right_drive\" back-right)\n" +
            "    [front-left front-right back-left back-right]))\n" +
            "\n" +
            "(defn drive-with-gamepad []\n" +
            "  (loop []\n" +
            "    (let [pad     (gamepad 1)\n" +
            "          forward (- (:left-stick-y pad))\n" +
            "          strafe  (:left-stick-x pad)\n" +
            "          rotate  (:right-stick-x pad)]\n" +
            "      (mecanum-drive forward strafe rotate))\n" +
            "    (time/sleep 0.02)\n" +
            "    (recur)))\n";

    private static int failures;
    private static int count;

    /** Remembers what was set, like a motor would. */
    static final class FakeMotor implements DcMotorEx {
        Direction direction = Direction.FORWARD;
        RunMode mode = RunMode.RUN_USING_ENCODER;
        double power;
        double velocity;
        int modeChanges;

        @Override
        public void setVelocity(double velocity) {
            this.velocity = velocity;
        }

        @Override
        public double getVelocity() {
            return velocity;
        }

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

        @Override
        public void setMode(RunMode mode) {
            this.mode = mode;
            modeChanges++;
        }

        @Override
        public RunMode getMode() {
            return mode;
        }
    }

    private static String powers(FakeMotor... motors) {
        StringBuilder sb = new StringBuilder();
        for (FakeMotor m : motors) {
            sb.append(String.format("%.2f ", m.power));
        }
        return sb.toString().trim();
    }

    private final Map<String, Object> devices = new HashMap<String, Object>();
    private final OpMode opMode = new OpMode() {
    };
    private final StringWriter buffer = new StringWriter();
    private final Repl repl;

    private MotorTest() {
        opMode.hardwareMap = new HardwareMap() {
            @Override
            public <T> T tryGet(Class<? extends T> type, String name) {
                Object device = devices.get(name);
                return type.isInstance(device) ? type.cast(device) : null;
            }
        };
        Env globals = Builtins.createGlobals();
        MotorModule.install(globals, opMode);
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
        MotorTest t = new MotorTest();
        FakeMotor left = new FakeMotor();
        t.devices.put("left", left);
        t.devices.put("arm servo", "not a motor");

        t.check("0.0", "(motor/power \"left\")");
        t.check("0.5", "(motor/set-power \"left\" 0.5)");
        check("0.5 RUN_WITHOUT_ENCODER", left.power + " " + left.mode, "power set, encoder mode off");
        t.check("0.5", "(motor/power \"left\")");
        t.check("-1.0", "(motor/set-power \"left\" -1)");
        t.check("-1.0", "(motor/power \"left\")");
        check("1", String.valueOf(left.modeChanges), "mode is set only once");

        t.check("1200.0", "(motor/set-velocity \"left\" 1200)");
        check("1200.0 RUN_USING_ENCODER 2", left.velocity + " " + left.mode + " " + left.modeChanges, "velocity uses the encoder");
        t.check("1200.0", "(motor/velocity \"left\")");
        t.check("-300.0", "(motor/set-velocity \"left\" -300)");
        check("-300.0 2", left.velocity + " " + left.modeChanges, "mode is not set again for velocity");
        t.check("0.0", "(motor/set-power \"left\" 0)");
        check("0.0 RUN_WITHOUT_ENCODER 3", left.power + " " + left.mode + " " + left.modeChanges, "power switches the encoder off again");
        t.check("Execution error: motor/set-velocity expects a finite velocity, got: ##Inf",
                "(motor/set-velocity \"left\" (Math/exp 1000))");
        t.check("Execution error: motor/set-velocity expects numbers, got: nil",
                "(motor/set-velocity \"left\" nil)");
        t.check("Execution error: No motor named \"right\" in the robot configuration",
                "(motor/velocity \"right\")");

        t.check(":forward", "(motor/direction \"left\")");
        t.check(":reverse", "(motor/set-direction \"left\" :reverse)");
        t.check(":reverse", "(motor/direction \"left\")");
        t.check(":forward", "(motor/set-direction \"left\" :forward)");
        t.check(":forward", "(motor/direction \"left\")");

        t.check("Execution error: motor/set-power expects a power from -1.0 to 1.0, got: 1.5",
                "(motor/set-power \"left\" 1.5)");
        t.check("Execution error: motor/set-power expects a power from -1.0 to 1.0, got: ##NaN",
                "(motor/set-power \"left\" (Math/sqrt -1))");
        t.check("0.0", "(motor/power \"left\")");
        t.check("Execution error: motor/set-power expects numbers, got: \"fast\"",
                "(motor/set-power \"left\" \"fast\")");
        t.check("Execution error: motor/set-direction expects :forward or :reverse, got: :backward",
                "(motor/set-direction \"left\" :backward)");
        t.check("Execution error: No motor named \"right\" in the robot configuration",
                "(motor/set-power \"right\" 0.5)");
        t.check("Execution error: No motor named \"arm servo\" in the robot configuration",
                "(motor/power \"arm servo\")");
        t.check("Execution error: motor/power expects a motor name as a string, got: :left",
                "(motor/power :left)");
        t.check("Execution error: Wrong number of args (1) passed to: motor/set-power",
                "(motor/set-power \"left\")");

        // The motor test program from the manual, one round at a time.
        FakeMotor motor = new FakeMotor();
        t.devices.put("motor", motor);
        String example = TEST_MOTOR;
        t.check("#'user/motor-name\n#'user/power\n#'user/pressed?\n#'user/step\n#'user/control-loop", example);
        Gamepad prev = new Gamepad();
        Gamepad pad = new Gamepad();
        t.opMode.gamepad1 = prev;
        t.opMode.gamepad2 = pad;
        pad.dpad_up = true;
        pad.b = true;
        pad.right_trigger = 0.3f;
        t.check("nil", "(step (gamepad 1) (gamepad 2))");
        check("0.11 REVERSE", String.format("%.2f", motor.power) + " " + motor.direction, "example runs the motor");
        t.check("nil", "(step (gamepad 2) (gamepad 2))");
        check("0.11", String.format("%.2f", motor.power), "a held button counts once");
        pad.right_trigger = 0;
        t.check("nil", "(step (gamepad 2) (gamepad 2))");
        check("0.0", String.valueOf(motor.power), "releasing the trigger stops the motor");

        // Driver control from the Examples page.
        FakeMotor lf = new FakeMotor();
        FakeMotor rf = new FakeMotor();
        FakeMotor lb = new FakeMotor();
        FakeMotor rb = new FakeMotor();
        t.devices.put("front_left_drive", lf);
        t.devices.put("front_right_drive", rf);
        t.devices.put("back_left_drive", lb);
        t.devices.put("back_right_drive", rb);
        String drive = MECANUM_DRIVE;
        t.check("#'user/mecanum-drive\n#'user/drive-with-gamepad", drive);
        t.check("[0.3 0.3 0.3 0.3]", "(mecanum-drive 0.3 0 0)");
        check("REVERSE FORWARD REVERSE FORWARD", lf.direction + " " + rf.direction + " " + lb.direction + " " + rb.direction,
                "the left motors are reversed");
        check("0.30 0.30 0.30 0.30", powers(lf, rf, lb, rb), "forward");
        t.check("[0.3 -0.3 -0.3 0.3]", "(mecanum-drive 0 0.3 0)");
        check("0.30 -0.30 -0.30 0.30", powers(lf, rf, lb, rb), "strafe right");
        t.check("[-0.3 0.3 -0.3 0.3]", "(mecanum-drive 0 0 0.3)");
        check("-0.30 0.30 -0.30 0.30", powers(lf, rf, lb, rb), "turn right");
        t.check("[1.0 0.0 0.0 1.0]", "(mecanum-drive 1 1 0)");
        check("1.00 0.00 0.00 1.00", powers(lf, rf, lb, rb), "diagonal is scaled to 1");
        t.check("[0.0 0.0 0.0 0.0]", "(mecanum-drive 0 0 0)");
        check("0.00 0.00 0.00 0.00", powers(lf, rf, lb, rb), "stop");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
