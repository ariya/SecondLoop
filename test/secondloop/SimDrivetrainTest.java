package secondloop;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Drives RobotSimulator's mecanum drivetrain with the mecanum-drive function from
 * the docs, with time stepped by hand. Run: java -cp build/classes secondloop.SimDrivetrainTest
 */
public final class SimDrivetrainTest {
    private static int failures;
    private static int count;

    // From docs/examples/mecanum-drive.md.
    private static final String MECANUM_DRIVE = "(defn mecanum-drive [forward strafe rotate]"
            + "  (motor/set-direction \"front_left_drive\" :reverse)"
            + "  (motor/set-direction \"back_left_drive\" :reverse)"
            + "  (let [d           (max (+ (abs forward) (abs strafe) (abs rotate)) 1)"
            + "        front-left  (/ (- (+ forward strafe) rotate) d)"
            + "        front-right (/ (+ (- forward strafe) rotate) d)"
            + "        back-left   (/ (- forward strafe rotate) d)"
            + "        back-right  (/ (+ forward strafe rotate) d)]"
            + "    (motor/set-power \"front_left_drive\" front-left)"
            + "    (motor/set-power \"front_right_drive\" front-right)"
            + "    (motor/set-power \"back_left_drive\" back-left)"
            + "    (motor/set-power \"back_right_drive\" back-right)"
            + "    [front-left front-right back-left back-right]))";

    private final SimMotorTest.StepClock clock = new SimMotorTest.StepClock();
    private final SimRobot drivetrain;
    private final StringWriter out = new StringWriter();
    private final Repl repl;

    private SimDrivetrainTest() {
        drivetrain = new SimRobot(new SimBattery(12, Double.POSITIVE_INFINITY, 0), clock); // no sag or drain, for numbers worked out by hand
        Env globals = Builtins.createGlobals();
        com.qualcomm.robotcore.eventloop.opmode.OpMode opMode = RobotSimulator.createOpMode(drivetrain);
        MotorModule.install(globals, opMode);
        ImuModule.install(globals, opMode);
        repl = new Repl(globals, new PrintWriter(out));
        repl.feed(MECANUM_DRIVE);
        repl.feed("(imu/init :up :backward)");
    }

    private String feed(String line) {
        out.getBuffer().setLength(0);
        repl.feed(line);
        return out.toString().trim();
    }

    /** Runs a move for some seconds, stops, and lets the robot coast to rest. */
    private void move(String forwardStrafeRotate, double seconds) {
        feed("(mecanum-drive " + forwardStrafeRotate + ")");
        clock.seconds(seconds);
        feed("(mecanum-drive 0 0 0)");
        clock.seconds(3);
    }

    /** The pose as "x y heading°", rounded, with -0 shown as 0. */
    private String pose() {
        double[] p = drivetrain.pose();
        return round(p[0], 10) + " " + round(p[1], 10) + " " + (long) round(Math.toDegrees(p[2]), 1);
    }

    /** Rounded to 1/scale, without the -0.0 a tiny negative leaves. */
    private static double round(double x, double scale) {
        return Math.round(x * scale) / scale + 0.0;
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

    public static void main(String[] args) {
        SimDrivetrainTest t = new SimDrivetrainTest();
        // At power 0.3 a wheel rolls 0.3 * 5.2 turns/s * 12.87 in = 20.1 in/s.
        // With tau = 0.2 s it lags 0.2 s behind when starting and coasts 0.2 s
        // further when stopped, so 3 s of driving covers 3 s worth: 60.2 in.
        t.move("0.3 0 0", 3);
        check("60.2 0.0 0", t.pose(), "forward");
        check("0.0", t.feed("(imu/yaw)"), "driving straight does not turn");
        t.move("0 0.3 0", 3);
        check("60.2 -60.2 0", t.pose(), "strafe right");
        t.move("-0.3 0 0", 3);
        t.move("0 -0.3 0", 3);
        check("0.0 0.0 0", t.pose(), "a square ends where it started");

        // TODO: the example says a positive rotate turns right, but in the
        // simulator it turns counterclockwise, so the heading goes up. Settle
        // which is right on real hardware. The wheels roll 60.2 in on a circle
        // of radius 13 in (7 + 6), so 265 degrees.
        t.move("0 0 0.3", 3);
        check("0.0 0.0 265", t.pose(), "turn on the spot");
        check("-95.0", t.feed("(Math/round (Math/toDegrees (imu/yaw)))"), "imu/yaw wraps to -180..180");
        check("nil", t.feed("(imu/reset-yaw)"), "reset yaw");
        check("0.0", t.feed("(imu/yaw)"), "yaw is 0 after reset");

        check("0.0", t.feed("(motor/velocity \"front_left_drive\")"), "no encoder: velocity reads 0");
        t.feed("(motor/set-power \"front_left_drive\" 0.5)");
        t.clock.seconds(1);
        check("0.0", t.feed("(motor/current-position \"front_left_drive\")"), "no encoder: position reads 0");
        check("0.5", t.feed("(motor/power \"front_left_drive\")"), "power still reads back");
        t.feed("(motor/set-power \"front_left_drive\" 0)");

        // Floating, the wheels coast 0.5 s further instead of 0.2 s: 6 in more.
        t.clock.seconds(3);
        double[] start = t.drivetrain.pose();
        t.feed("(motor/set-zero-power-behavior \"front_left_drive\" :float)"
                + "(motor/set-zero-power-behavior \"front_right_drive\" :float)"
                + "(motor/set-zero-power-behavior \"back_left_drive\" :float)"
                + "(motor/set-zero-power-behavior \"back_right_drive\" :float)");
        t.move("0.3 0 0", 3);
        double[] end = t.drivetrain.pose();
        check("66.2", String.valueOf(round(Math.hypot(end[0] - start[0], end[1] - start[1]), 10)), "floats further");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
