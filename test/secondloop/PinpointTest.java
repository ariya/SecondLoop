package secondloop;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.HardwareMap;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/** Checks the pinpoint/* functions against a stand-in Pinpoint. Run: java -cp build/classes secondloop.PinpointTest */
public final class PinpointTest {
    private static int failures;
    private static int count;

    /**
     * Keeps the pose in inches and radians, and only answers in those units, so
     * a wrong unit shows up as a failed check. update() copies the live values,
     * as the real driver reads them from the device; each update moves x by step
     * and the heading by turnStep.
     */
    static final class FakePinpoint extends GoBildaPinpointDriver {
        String setup = "";
        int resets;
        int updates;
        int calibratingUpdates;      // how many updates after a reset report CALIBRATING
        private int calibrating;
        DeviceStatus status = DeviceStatus.READY;
        double x, y, heading, velX, velY, velHeading, step, turnStep;
        private double readX, readY, readHeading, readVelX, readVelY, readVelHeading;
        private DeviceStatus readStatus = DeviceStatus.NOT_READY;

        @Override
        public void update() {
            updates++;
            x += step;
            heading += turnStep;
            readX = x;
            readY = y;
            readHeading = heading;
            readVelX = velX;
            readVelY = velY;
            readVelHeading = velHeading;
            readStatus = calibrating > 0 ? DeviceStatus.CALIBRATING : status;
            if (calibrating > 0) {
                calibrating--;
            }
        }

        @Override
        public void setOffsets(double xOffset, double yOffset, DistanceUnit unit) {
            setup += "offsets " + xOffset + " " + yOffset + " " + unit + "; ";
        }

        @Override
        public void setEncoderResolution(GoBildaOdometryPods pods) {
            setup += "pods " + pods + "; ";
        }

        @Override
        public void setEncoderResolution(double ticksPerUnit, DistanceUnit unit) {
            setup += "resolution " + ticksPerUnit + " " + unit + "; ";
        }

        @Override
        public void setEncoderDirections(EncoderDirection xEncoder, EncoderDirection yEncoder) {
            setup += "directions " + xEncoder + " " + yEncoder + "; ";
        }

        @Override
        public void resetPosAndIMU() {
            resets++;
            calibrating = calibratingUpdates;
            x = 0;
            y = 0;
            heading = 0;
        }

        @Override
        public DeviceStatus getDeviceStatus() {
            return readStatus;
        }

        @Override
        public double getPosX(DistanceUnit unit) {
            return inches(readX, unit);
        }

        @Override
        public double getPosY(DistanceUnit unit) {
            return inches(readY, unit);
        }

        @Override
        public double getHeading(AngleUnit unit) {
            return unit == AngleUnit.RADIANS ? readHeading : Double.NaN;
        }

        @Override
        public double getVelX(DistanceUnit unit) {
            return inches(readVelX, unit);
        }

        @Override
        public double getVelY(DistanceUnit unit) {
            return inches(readVelY, unit);
        }

        @Override
        public double getHeadingVelocity(UnnormalizedAngleUnit unit) {
            return unit == UnnormalizedAngleUnit.RADIANS ? readVelHeading : Double.NaN;
        }

        private static double inches(double value, DistanceUnit unit) {
            return unit == DistanceUnit.INCH ? value : Double.NaN;
        }
    }

    private final Map<String, Object> devices = new HashMap<String, Object>();
    private final OpMode opMode = new OpMode() {
    };
    private final StringWriter buffer = new StringWriter();
    private final Repl repl;

    private PinpointTest() {
        opMode.hardwareMap = new HardwareMap() {
            @Override
            public <T> T tryGet(Class<? extends T> type, String name) {
                Object device = devices.get(name);
                return type.isInstance(device) ? type.cast(device) : null;
            }
        };
        Env globals = Builtins.createGlobals();
        PinpointModule.install(globals, opMode);
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
        PinpointTest t = new PinpointTest();
        String notSetUp = "Execution error: The Pinpoint is not set up: first call pinpoint/init";
        String noDevice = "Execution error: No Pinpoint named \"pinpoint\" in the robot configuration";

        t.check(":not-configured", "(pinpoint/init :swingarm -3.3 -6.6)");
        t.check(":not-configured", "(pinpoint/status)");
        // Even after pinpoint/init, reading names the missing device.
        t.check(noDevice, "(pinpoint/pose)");
        t.check(noDevice, "(pinpoint/velocity)");
        t.check(noDevice, "(pinpoint/reset)");
        FakePinpoint p = new FakePinpoint();
        t.devices.put("pinpoint", p);
        t.check(notSetUp, "(pinpoint/pose)");
        t.check(notSetUp, "(pinpoint/velocity)");
        t.check(notSetUp, "(pinpoint/reset)");

        // Status works before pinpoint/init, to check the wiring.
        t.check(":ready", "(pinpoint/status)");
        p.status = GoBildaPinpointDriver.DeviceStatus.FAULT_X_POD_NOT_DETECTED;
        t.check(":fault-x-pod-not-detected", "(pinpoint/status)");
        p.status = GoBildaPinpointDriver.DeviceStatus.READY;
        t.check("Execution error: Wrong number of args (1) passed to: pinpoint/status", "(pinpoint/status 1)");

        t.check("Execution error: pinpoint/init expects the pods as :swingarm, :4-bar or encoder ticks per inch, got: :odometry",
                "(pinpoint/init :odometry -3.3 -6.6)");
        t.check("Execution error: pinpoint/init expects the pods as :swingarm, :4-bar or encoder ticks per inch, got: -500.0",
                "(pinpoint/init -500 -3.3 -6.6)");
        t.check("Execution error: pinpoint/init expects numbers, got: \"3\"", "(pinpoint/init :swingarm \"3\" -6.6)");
        t.check("Execution error: pinpoint/init expects a finite Y offset, got: ##NaN", "(pinpoint/init :swingarm -3.3 (Math/sqrt -1))");
        t.check("Execution error: pinpoint/init expects a pod direction as :forward or :reverse, got: :reversed",
                "(pinpoint/init :swingarm -3.3 -6.6 :reversed :forward)");
        t.check("Execution error: pinpoint/init expects both the X and the Y pod direction, or neither",
                "(pinpoint/init :swingarm -3.3 -6.6 :reverse)");
        t.check("Execution error: Wrong number of args (2) passed to: pinpoint/init", "(pinpoint/init :swingarm -3.3)");
        check("", p.setup, "nothing sent to the Pinpoint after a bad pinpoint/init");
        t.check(notSetUp, "(pinpoint/pose)");

        p.x = 5;
        t.check(":ready", "(pinpoint/init :swingarm -3.3 -6.6)");
        check("offsets -3.3 -6.6 INCH; pods goBILDA_SWINGARM_POD; directions FORWARD FORWARD; ", p.setup, "swingarm setup");
        check("1", String.valueOf(p.resets), "pinpoint/init resets the pose");
        t.check("{:x 0.0, :y 0.0, :heading 0.0}", "(pinpoint/pose)");

        p.setup = "";
        t.check(":ready", "(pinpoint/init :4-bar 2 0 :reverse :forward)");
        check("offsets 2.0 0.0 INCH; pods goBILDA_4_BAR_POD; directions REVERSED FORWARD; ", p.setup, "4-bar setup");
        p.setup = "";
        t.check(":ready", "(pinpoint/init 508 0 0 :forward :reverse)");
        check("offsets 0.0 0.0 INCH; resolution 20.0 MM; directions FORWARD REVERSED; ", p.setup, "custom pods, in ticks per mm");

        // pinpoint/init waits for the IMU calibration, then returns the status.
        p.calibratingUpdates = 3;
        int before = p.updates;
        t.check(":ready", "(pinpoint/init :swingarm -3.3 -6.6)");
        check("4", String.valueOf(p.updates - before), "pinpoint/init polls until the calibration is done");
        p.status = GoBildaPinpointDriver.DeviceStatus.FAULT_Y_POD_NOT_DETECTED;
        t.check(":fault-y-pod-not-detected", "(pinpoint/init :swingarm -3.3 -6.6)");
        p.status = GoBildaPinpointDriver.DeviceStatus.READY;
        // A calibration that never ends gives up after the timeout.
        p.calibratingUpdates = Integer.MAX_VALUE;
        long started = System.nanoTime();
        t.check(":calibrating", "(pinpoint/init :swingarm -3.3 -6.6)");
        long waited = (System.nanoTime() - started) / 1000000;
        check("true", String.valueOf(waited >= PinpointModule.CALIBRATION_TIMEOUT_MILLIS && waited < 3000),
                "pinpoint/init gives up after the timeout: " + waited + " ms");
        p.calibratingUpdates = 0;
        t.check(":ready", "(pinpoint/init :swingarm -3.3 -6.6)");

        p.x = 12.5;
        p.y = -3.25;
        p.heading = 0.5;
        t.check("{:x 12.5, :y -3.25, :heading 0.5}", "(pinpoint/pose)");
        t.check("12.5", "(:x (pinpoint/pose))");
        t.check("Execution error: Wrong number of args (1) passed to: pinpoint/pose", "(pinpoint/pose 1)");

        p.velX = 30;
        p.velY = -2;
        p.velHeading = 7;      // faster than half a turn a second: not wrapped
        t.check("{:x 30.0, :y -2.0, :heading 7.0}", "(pinpoint/velocity)");

        int resets = p.resets;
        t.check(":ready", "(pinpoint/reset)");
        check(String.valueOf(resets + 1), String.valueOf(p.resets), "pinpoint/reset");
        // Like pinpoint/init, it waits for the IMU calibration, then returns the status.
        p.calibratingUpdates = 3;
        int beforeReset = p.updates;
        t.check(":ready", "(pinpoint/reset)");
        check("4", String.valueOf(p.updates - beforeReset), "pinpoint/reset polls until the calibration is done");
        p.calibratingUpdates = 0;
        p.status = GoBildaPinpointDriver.DeviceStatus.FAULT_X_POD_NOT_DETECTED;
        t.check(":fault-x-pod-not-detected", "(pinpoint/reset)");
        p.status = GoBildaPinpointDriver.DeviceStatus.READY;
        t.check("{:x 0.0, :y 0.0, :heading 0.0}", "(pinpoint/pose)");

        // docs/robot/pinpoint.md, Distance to a point.
        p.x = 10;
        p.y = 20;
        t.check("#'user/distance-to", "(defn distance-to\n  \"How far the robot is from the point x, y on the field, in inches.\"\n  [x y]\n"
                + "  (let [pose (pinpoint/pose)]\n    (Math/hypot (- x (:x pose)) (- y (:y pose)))))");
        t.check("5.0", "(distance-to 13 24)");

        // docs/robot/pinpoint.md, Driving a distance; each pose read moves the robot 5 inches.
        t.check("#'user/mecanum-drive", "(defn mecanum-drive [forward strafe rotate] [forward strafe rotate])");
        t.check("#'user/drive-forward", "(defn drive-forward\n  \"Drives forward until the robot has gone inches.\"\n  [inches]\n"
                + "  (let [start (pinpoint/pose)]\n    (loop []\n      (when (< (distance-to (:x start) (:y start)) inches)\n"
                + "        (mecanum-drive 0.3 0 0)\n        (time/sleep 0.02)\n        (recur))))\n  (mecanum-drive 0 0 0))");
        p.step = 5;
        int updates = p.updates;
        t.check("[0.0 0.0 0.0]", "(drive-forward 24)");
        check("6", String.valueOf(p.updates - updates), "drive-forward reads the start, then the pose until it passes 24 inches");
        p.step = 0;

        // docs/robot/pinpoint.md, Setup: print-pose prints until control/stop.
        t.check("#'user/print-pose", "(defn print-pose []\n  (loop []\n    (println (pinpoint/pose))\n    (time/sleep 0.1)\n    (recur)))");
        p.x = 1.5;
        p.y = 0;
        p.heading = 0;
        t.buffer.getBuffer().setLength(0);
        t.repl.feed("(do (control/start print-pose) (time/sleep 0.25) (control/stop))");
        String printed = t.buffer.toString().trim();
        check("true", String.valueOf(printed.startsWith("{:x 1.5, :y 0.0, :heading 0.0}\n{:x 1.5, :y 0.0, :heading 0.0}\n")
                && printed.endsWith("\ntrue")), "print-pose prints the pose until control/stop: " + printed);
        int stopped = p.updates;
        Thread.sleep(250);
        check(String.valueOf(stopped), String.valueOf(p.updates), "print-pose reads nothing after control/stop");

        // docs/robot/pinpoint.md, Checking the offsets; between pose reads, the robot turns 1 radian.
        t.check("#'user/check-offsets", "(defn check-offsets []\n  (loop []\n    (let [p (pinpoint/pose)]\n"
                + "      (println (format \"x %.2f  y %.2f  heading %.0f°\"\n"
                + "                       (:x p) (:y p) (Math/toDegrees (:heading p))))\n"
                + "      (when (< (abs (:heading p)) 3.1)     ; until about 178° (π is 180°)\n"
                + "        (time/sleep 0.1)\n        (recur)))))");
        p.x = 0.5;
        p.y = -0.25;
        p.heading = 0;
        p.turnStep = 1;
        t.check("x 0.50  y -0.25  heading 57°\nx 0.50  y -0.25  heading 115°\n"
                + "x 0.50  y -0.25  heading 172°\nx 0.50  y -0.25  heading 229°\nnil", "(check-offsets)");
        p.turnStep = 0;

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
