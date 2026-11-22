package secondloop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;

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
            "        front-left  (/ (+ forward strafe rotate) d)\n" +
            "        front-right (/ (- forward strafe rotate) d)\n" +
            "        back-left   (/ (+ (- forward strafe) rotate) d)\n" +
            "        back-right  (/ (- (+ forward strafe) rotate) d)]\n" +
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
        ZeroPowerBehavior zeroPowerBehavior = ZeroPowerBehavior.BRAKE;
        double power;
        double velocity;
        int modeChanges;
        int targetPosition;
        int currentPosition;
        RunMode pidfMode;
        PIDFCoefficients pidf = new PIDFCoefficients(10, 3, 0, 0);

        @Override
        public void setPIDFCoefficients(RunMode mode, PIDFCoefficients pidf) {
            this.pidfMode = mode;
            this.pidf = pidf;
        }

        @Override
        public PIDFCoefficients getPIDFCoefficients(RunMode mode) {
            return pidf;
        }

        @Override
        public void setVelocity(double velocity) {
            this.velocity = velocity;
        }

        @Override
        public double getVelocity() {
            return velocity;
        }

        @Override
        public void setZeroPowerBehavior(ZeroPowerBehavior zeroPowerBehavior) {
            this.zeroPowerBehavior = zeroPowerBehavior;
        }

        @Override
        public ZeroPowerBehavior getZeroPowerBehavior() {
            return zeroPowerBehavior;
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

        @Override
        public void setTargetPosition(int position) {
            this.targetPosition = position;
        }

        @Override
        public int getTargetPosition() {
            return targetPosition;
        }

        @Override
        public int getCurrentPosition() {
            return currentPosition;
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

        t.check("{:p 10.0, :i 3.0, :d 0.0, :f 0.0}", "(motor/pidf \"left\")");
        t.check("{:p 20.0, :i 0.0, :d 0.0, :f 14.2}", "(motor/set-pidf \"left\" {:f 14.2 :p 20})");
        check("20.0 0.0 0.0 14.2 RUN_USING_ENCODER", left.pidf.p + " " + left.pidf.i + " " + left.pidf.d + " "
                + left.pidf.f + " " + left.pidfMode, "pidf is set on the velocity loop");
        t.check("{:p 20.0, :i 0.0, :d 0.0, :f 14.2}", "(motor/pidf \"left\")");
        t.check("Execution error: motor/set-pidf expects the keys :p :i :d :f, got: :kp",
                "(motor/set-pidf \"left\" {:kp 1})");
        t.check("Execution error: motor/set-pidf expects coefficients of 0 or more, got: -1.0",
                "(motor/set-pidf \"left\" {:p -1})");
        t.check("Execution error: motor/set-pidf expects coefficients of 0 or more, got: ##NaN",
                "(motor/set-pidf \"left\" {:p (Math/sqrt -1)})");
        t.check("Execution error: motor/set-pidf expects numbers, got: nil",
                "(motor/set-pidf \"left\" {:p nil})");
        t.check("Execution error: motor/set-pidf expects a map such as {:p 10 :i 3 :d 0 :f 14}, got: [1.0 2.0 3.0 4.0]",
                "(motor/set-pidf \"left\" [1 2 3 4])");
        check("20.0", String.valueOf(left.pidf.p), "a rejected pidf changes nothing");

        left.mode = DcMotor.RunMode.RUN_USING_ENCODER;
        left.modeChanges = 0;
        t.check(":run-using-encoder", "(motor/run-mode \"left\")");
        t.check(":stop-and-reset-encoder", "(motor/set-run-mode \"left\" :stop-and-reset-encoder)");
        check("STOP_AND_RESET_ENCODER 1", left.mode + " " + left.modeChanges, "run mode is set");
        t.check(":stop-and-reset-encoder", "(motor/run-mode \"left\")");
        t.check(":stop-and-reset-encoder", "(motor/set-run-mode \"left\" :stop-and-reset-encoder)");
        check("2", String.valueOf(left.modeChanges), "the same run mode is sent again");
        t.check("0.2", "(motor/set-power \"left\" 0.2)");
        check("RUN_WITHOUT_ENCODER 3", left.mode + " " + left.modeChanges, "power switches out of a reset");
        t.check(":run-without-encoder", "(motor/run-mode \"left\")");
        t.check(":run-using-encoder", "(motor/set-run-mode \"left\" :run-using-encoder)");
        t.check("0.0", "(motor/set-power \"left\" 0)");
        check("RUN_WITHOUT_ENCODER 5", left.mode + " " + left.modeChanges, "power switches after set-run-mode");
        t.check(":run-without-encoder", "(motor/set-run-mode \"left\" :run-without-encoder)");
        t.check("0.0", "(motor/set-power \"left\" 0)");
        check("6", String.valueOf(left.modeChanges), "set-run-mode keeps the cache in step");
        left.mode = DcMotor.RunMode.RUN_TO_POSITION;
        t.check(":run-to-position", "(motor/run-mode \"left\")");
        t.check("Execution error: motor/set-run-mode needs a target first: call motor/set-target-position",
                "(motor/set-run-mode \"left\" :run-to-position)");
        t.check("Execution error: motor/set-run-mode expects :run-without-encoder, :run-using-encoder, "
                + ":run-to-position or :stop-and-reset-encoder, got: \"RUN_USING_ENCODER\"",
                "(motor/set-run-mode \"left\" \"RUN_USING_ENCODER\")");
        check("RUN_TO_POSITION", String.valueOf(left.mode), "a rejected run mode changes nothing");
        t.check("Execution error: No motor named \"right\" in the robot configuration",
                "(motor/run-mode \"right\")");

        t.check("0.0", "(motor/target-position \"left\")");
        t.check("300.0", "(motor/set-target-position \"left\" 300)");
        check("300", String.valueOf(left.targetPosition), "target position is set");
        t.check("300.0", "(motor/target-position \"left\")");
        t.check("-807.0", "(motor/set-target-position \"left\" (* -1.5 537.7))");
        check("-807", String.valueOf(left.targetPosition), "target position is rounded");
        t.check("Execution error: motor/set-target-position expects a position in encoder ticks, got: ##NaN",
                "(motor/set-target-position \"left\" (Math/sqrt -1))");
        t.check("Execution error: motor/set-target-position expects a position in encoder ticks, got: 1.0E10",
                "(motor/set-target-position \"left\" 1e10)");
        t.check("Execution error: motor/set-target-position expects numbers, got: nil",
                "(motor/set-target-position \"left\" nil)");
        check("-807", String.valueOf(left.targetPosition), "a rejected target position changes nothing");
        left.currentPosition = 1234;
        t.check("1234.0", "(motor/current-position \"left\")");
        t.check("Execution error: No motor named \"right\" in the robot configuration",
                "(motor/current-position \"right\")");
        t.check("Execution error: No motor named \"right\" in the robot configuration",
                "(motor/set-target-position \"right\" 10)");

        left.modeChanges = 0;
        t.check(":run-to-position", "(motor/set-run-mode \"left\" :run-to-position)");
        check("RUN_TO_POSITION 1", left.mode + " " + left.modeChanges, "run to position after a target");
        t.check("0.3", "(motor/set-power \"left\" 0.3)");
        t.check("400.0", "(motor/set-velocity \"left\" 400)");
        check("RUN_TO_POSITION 1 0.30 400.0", left.mode + " " + left.modeChanges + " " + powers(left)
                + " " + left.velocity, "power and velocity keep run to position");
        t.check(":run-using-encoder", "(motor/set-run-mode \"left\" :run-using-encoder)");
        t.check("0.0", "(motor/set-power \"left\" 0)");
        check("RUN_WITHOUT_ENCODER 3", left.mode + " " + left.modeChanges, "power switches again after leaving it");

        t.check(":forward", "(motor/direction \"left\")");
        t.check(":reverse", "(motor/set-direction \"left\" :reverse)");
        t.check(":reverse", "(motor/direction \"left\")");
        t.check(":forward", "(motor/set-direction \"left\" :forward)");
        t.check(":forward", "(motor/direction \"left\")");

        t.check(":brake", "(motor/zero-power-behavior \"left\")");
        t.check(":float", "(motor/set-zero-power-behavior \"left\" :float)");
        check("FLOAT", String.valueOf(left.zeroPowerBehavior), "zero power behavior is set");
        t.check(":float", "(motor/zero-power-behavior \"left\")");
        t.check(":brake", "(motor/set-zero-power-behavior \"left\" :brake)");
        t.check(":brake", "(motor/zero-power-behavior \"left\")");
        t.check("Execution error: motor/set-zero-power-behavior expects :brake or :float, got: :coast",
                "(motor/set-zero-power-behavior \"left\" :coast)");
        check("BRAKE", String.valueOf(left.zeroPowerBehavior), "a rejected zero power behavior changes nothing");
        left.zeroPowerBehavior = DcMotor.ZeroPowerBehavior.UNKNOWN;
        t.check(":unknown", "(motor/zero-power-behavior \"left\")");
        t.check("Execution error: No motor named \"right\" in the robot configuration",
                "(motor/set-zero-power-behavior \"right\" :brake)");

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
        lf.zeroPowerBehavior = DcMotor.ZeroPowerBehavior.FLOAT;
        rb.zeroPowerBehavior = DcMotor.ZeroPowerBehavior.FLOAT;
        t.check(":brake\n:brake\n:brake\n:brake",
                "(motor/set-zero-power-behavior \"front_left_drive\" :brake)\n"
                + "(motor/set-zero-power-behavior \"front_right_drive\" :brake)\n"
                + "(motor/set-zero-power-behavior \"back_left_drive\" :brake)\n"
                + "(motor/set-zero-power-behavior \"back_right_drive\" :brake)");
        check("BRAKE BRAKE BRAKE BRAKE", lf.zeroPowerBehavior + " " + rf.zeroPowerBehavior + " " + lb.zeroPowerBehavior
                + " " + rb.zeroPowerBehavior, "all four wheels brake");
        t.check("[0.3 0.3 0.3 0.3]", "(mecanum-drive 0.3 0 0)");
        check("REVERSE FORWARD REVERSE FORWARD", lf.direction + " " + rf.direction + " " + lb.direction + " " + rb.direction,
                "the left motors are reversed");
        check("0.30 0.30 0.30 0.30", powers(lf, rf, lb, rb), "forward");
        t.check("[0.3 -0.3 -0.3 0.3]", "(mecanum-drive 0 0.3 0)");
        check("0.30 -0.30 -0.30 0.30", powers(lf, rf, lb, rb), "strafe right");
        t.check("[0.3 -0.3 0.3 -0.3]", "(mecanum-drive 0 0 0.3)");
        check("0.30 -0.30 0.30 -0.30", powers(lf, rf, lb, rb), "turn right");
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
