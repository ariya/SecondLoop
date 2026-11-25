package secondloop;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Reads RobotSimulator's simulated Pinpoint while the mecanum drivetrain moves,
 * with time stepped by hand. Run: java -cp build/classes secondloop.SimPinpointTest
 */
public final class SimPinpointTest {
    private static int failures;
    private static int count;

    private final SimMotorTest.StepClock clock = new SimMotorTest.StepClock();
    private final StringWriter out = new StringWriter();
    private final Repl repl;

    private SimPinpointTest() {
        SimRobot drivetrain = new SimRobot(new SimBattery(12, Double.POSITIVE_INFINITY, 0), clock); // no sag or drain, for numbers worked out by hand
        Env globals = Builtins.createGlobals();
        com.qualcomm.robotcore.eventloop.opmode.OpMode opMode = RobotSimulator.createOpMode(drivetrain);
        MotorModule.install(globals, opMode);
        PinpointModule.install(globals, opMode);
        repl = new Repl(globals, new PrintWriter(out));
        // Same powers as mecanum-drive in the docs, without the scaling.
        feed("(defn drive [f s r]"
                + "  (motor/set-direction \"front_left_drive\" :reverse)"
                + "  (motor/set-direction \"back_left_drive\" :reverse)"
                + "  (motor/set-power \"front_left_drive\" (+ f s r))"
                + "  (motor/set-power \"front_right_drive\" (- f s r))"
                + "  (motor/set-power \"back_left_drive\" (+ (- f s) r))"
                + "  (motor/set-power \"back_right_drive\" (- (+ f s) r)))");
        // Rounded, without the -0.0 a tiny negative leaves.
        feed("(defn r [x] (+ 0.0 (/ (Math/round (* 10 x)) 10)))");
        feed("(defn pose [] (let [p (pinpoint/pose)] [(r (:x p)) (r (:y p)) (r (Math/toDegrees (:heading p)))]))");
    }

    private String feed(String line) {
        out.getBuffer().setLength(0);
        repl.feed(line);
        return out.toString().trim();
    }

    private void check(String expected, String line) {
        check(expected, feed(line), line);
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
        SimPinpointTest t = new SimPinpointTest();
        t.check(":ready", "(pinpoint/status)");
        t.check(":ready", "(pinpoint/init :4-bar -3 -7)");
        t.check("[0.0 0.0 0.0]", "(pose)");

        // Power 0.3 rolls 20.1 in/s; 3 s of driving and the coast cover 60.2 in.
        t.feed("(drive 0.3 0 0)");
        t.clock.seconds(3);
        t.check("[20.1 0.0 0.0]", "(let [v (pinpoint/velocity)] [(r (:x v)) (r (:y v)) (r (:heading v))])");
        t.feed("(drive 0 0 0)");
        t.clock.seconds(3);
        t.check("[60.2 0.0 0.0]", "(pose)");
        t.check("[0.0 0.0 0.0]", "(let [v (pinpoint/velocity)] [(r (:x v)) (r (:y v)) (r (:heading v))])");

        // Strafing left is +y.
        t.feed("(drive 0 -0.3 0)");
        t.clock.seconds(3);
        t.feed("(drive 0 0 0)");
        t.clock.seconds(3);
        t.check("[60.2 60.2 0.0]", "(pose)");

        // A quarter turn left, then forward: now along +y. Each wheel rolls a
        // quarter of a circle of radius 13 in (7 + 6) at 0.3 * 5.2 turns/s.
        double quarterTurn = (SimRobot.HALF_TRACK + SimRobot.HALF_WHEELBASE) * Math.PI / 2
                / (0.3 * 312 / 60 * SimRobot.WHEEL_CIRCUMFERENCE);
        t.check(":ready", "(pinpoint/reset)");
        t.feed("(drive 0 0 -0.3)");
        t.clock.seconds(quarterTurn);
        t.feed("(drive 0 0 0)");
        t.clock.seconds(3);
        t.check("90.0", "(r (Math/toDegrees (:heading (pinpoint/pose))))");
        t.feed("(drive 0.3 0 0)");
        t.clock.seconds(3);
        t.feed("(drive 0 0 0)");
        t.clock.seconds(3);
        t.check("[0.0 60.2 90.0]", "(pose)");

        // A Y pod set the wrong way: strafing left reads as -y, as the docs describe.
        t.check(":ready", "(pinpoint/init :4-bar -3 -7 :forward :reverse)");
        t.feed("(drive 0 0 0.3)"); // back to facing along x
        t.clock.seconds(quarterTurn);
        t.feed("(drive 0 0 0)");
        t.clock.seconds(3);
        t.check(":ready", "(pinpoint/reset)");
        t.feed("(drive 0 -0.3 0)");
        t.clock.seconds(3);
        t.feed("(drive 0 0 0)");
        t.clock.seconds(3);
        t.check("[0.0 -60.2 0.0]", "(pose)");

        // Offsets of 0 track the point between the pods, 7 in behind and 3 in
        // to the right; a half turn on the spot moves it to twice that, ahead and left.
        t.check(":ready", "(pinpoint/init :4-bar 0 0)");
        t.feed("(drive 0 0 -0.3)");
        t.clock.seconds(2 * quarterTurn);
        t.feed("(drive 0 0 0)");
        t.clock.seconds(3);
        t.check("[14.0 6.0 180.0]", "(pose)");

        // The pods are 4-bar pods, so :swingarm counts them 1.5 times too far.
        t.check(":ready", "(pinpoint/init :swingarm -3 -7)");
        t.feed("(drive 0.3 0 0)");
        t.clock.seconds(3);
        t.feed("(drive 0 0 0)");
        t.clock.seconds(3);
        t.check("[90.3 0.0 0.0]", "(pose)");
        // The 4-bar resolution in ticks per inch counts right again.
        t.check(":ready", "(pinpoint/init 505.3169 -3 -7)");
        t.feed("(drive 0.3 0 0)");
        t.clock.seconds(3);
        t.feed("(drive 0 0 0)");
        t.clock.seconds(3);
        t.check("[60.2 0.0 0.0]", "(pose)");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
