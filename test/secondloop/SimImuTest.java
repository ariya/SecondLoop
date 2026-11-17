package secondloop;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Checks the imu/* functions against RobotSimulator's simulated IMU. Run: java -cp build/classes secondloop.SimImuTest */
public final class SimImuTest {
    private static int failures;
    private static int count;

    private final StringWriter buffer = new StringWriter();
    private final Repl repl;

    private SimImuTest() {
        Env globals = Builtins.createGlobals();
        ImuModule.install(globals, RobotSimulator.createOpMode());
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
        SimImuTest t = new SimImuTest();
        t.check("Execution error: The IMU is not set up: first call imu/init with the way the hub is mounted, e.g. (imu/init :up :backward)",
                "(imu/yaw)");
        t.check("nil", "(imu/init :up :backward)");
        t.check("0.0", "(imu/yaw)");
        t.check("{:yaw 0.0, :pitch 0.0, :roll 0.0}", "(imu/angles)");
        t.check("nil", "(imu/reset-yaw)");
        t.check("0.0", "(imu/yaw)");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
