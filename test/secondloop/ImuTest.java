package secondloop;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/** Checks the imu/* functions against a stand-in IMU. Run: java -cp build/classes secondloop.ImuTest */
public final class ImuTest {
    private static int failures;
    private static int count;

    /** Reports the angles a test sets; resetYaw makes the current heading 0. */
    static final class FakeImu implements IMU {
        RevHubOrientationOnRobot orientation;
        boolean initializes = true;
        double heading;
        double yawOffset;
        double pitch;
        double roll;

        @Override
        public boolean initialize(Parameters parameters) {
            orientation = (RevHubOrientationOnRobot) parameters.imuOrientationOnRobot;
            return initializes;
        }

        @Override
        public void resetYaw() {
            yawOffset = heading;
        }

        @Override
        public YawPitchRollAngles getRobotYawPitchRollAngles() {
            return new YawPitchRollAngles(AngleUnit.RADIANS, heading - yawOffset, pitch, roll, 0);
        }
    }

    private final Map<String, Object> devices = new HashMap<String, Object>();
    private final OpMode opMode = new OpMode() {
    };
    private final StringWriter buffer = new StringWriter();
    private final Repl repl;

    private ImuTest() {
        opMode.hardwareMap = new HardwareMap() {
            @Override
            public <T> T tryGet(Class<? extends T> type, String name) {
                Object device = devices.get(name);
                return type.isInstance(device) ? type.cast(device) : null;
            }
        };
        Env globals = Builtins.createGlobals();
        ImuModule.install(globals, opMode);
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
        ImuTest t = new ImuTest();
        String notSetUp = "Execution error: The IMU is not set up: first call imu/init with the way the hub is mounted, e.g. (imu/init :up :backward)";

        t.check("Execution error: No IMU named \"imu\" in the robot configuration", "(imu/init :up :backward)");
        FakeImu imu = new FakeImu();
        t.devices.put("imu", imu);
        t.check(notSetUp, "(imu/angles)");
        t.check(notSetUp, "(imu/reset-yaw)");
        t.check(notSetUp, "(imu/yaw)");

        t.check("Execution error: imu/init expects the logo direction as :up, :down, :left, :right, :forward or :backward, got: :top",
                "(imu/init :top :backward)");
        t.check("Execution error: imu/init expects the USB direction as :up, :down, :left, :right, :forward or :backward, got: \"backward\"",
                "(imu/init :up \"backward\")");
        t.check("Execution error: imu/init expects the logo and the USB port to face different axes, got: :up :down",
                "(imu/init :up :down)");
        t.check(notSetUp, "(imu/angles)");
        imu.initializes = false;
        t.check("Execution error: imu/init could not initialize the IMU", "(imu/init :up :backward)");
        t.check(notSetUp, "(imu/angles)");
        imu.initializes = true;
        t.check("nil", "(imu/init :left :forward)");
        check("LEFT FORWARD", imu.orientation.logo + " " + imu.orientation.usb, "orientation passed to the IMU");
        t.check("Execution error: Wrong number of args (1) passed to: imu/init", "(imu/init :up)");

        imu.heading = 0.5;
        imu.pitch = -0.25;
        imu.roll = 0.125;
        t.check("{:yaw 0.5, :pitch -0.25, :roll 0.125}", "(imu/angles)");
        t.check("0.5", "(:yaw (imu/angles))");
        t.check("0.5", "(imu/yaw)");
        t.check("-0.25", "(imu/pitch)");
        t.check("0.125", "(imu/roll)");
        t.check("Execution error: Wrong number of args (1) passed to: imu/roll", "(imu/roll 1)");
        t.check("nil", "(imu/reset-yaw)");
        t.check("{:yaw 0.0, :pitch -0.25, :roll 0.125}", "(imu/angles)");
        imu.heading = 0.5 + Math.PI / 2;
        t.check("1.5707963267948966", "(:yaw (imu/angles))");
        t.check("90.0", "(Math/toDegrees (:yaw (imu/angles)))");
        t.check("Execution error: Wrong number of args (1) passed to: imu/angles", "(imu/angles \"imu\")");

        // docs/robot/imu.md, Turn to a heading.
        imu.heading = 0.5 + 1.5;           // a yaw of 1.5
        t.check("#'user/turn-power", "(defn turn-power\n  \"Rotate power that turns the robot towards heading, in radians.\"\n  [heading]\n"
                + "  (* 0.6 (clamp (wrap-radians (- (:yaw (imu/angles)) heading)) -1 1)))");
        t.check("0.3", "(turn-power 1)");
        t.check("0.6", "(turn-power -1)");
        t.check("-0.6", "(turn-power -2.5)");     // the short way round is to the left
        t.check("0.3", "(turn-power (Math/toRadians 57.29577951308232))");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
