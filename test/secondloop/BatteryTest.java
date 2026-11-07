package secondloop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.VoltageSensor;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Checks battery/voltage against stand-in voltage sensors. Run: java -cp build/classes secondloop.BatteryTest */
public final class BatteryTest {
    private static int failures;
    private static int count;

    /** Reads a fixed voltage, like a hub's sensor would. */
    static final class FakeSensor implements VoltageSensor {
        final double voltage;

        FakeSensor(double voltage) {
            this.voltage = voltage;
        }

        @Override
        public double getVoltage() {
            return voltage;
        }
    }

    private final OpMode opMode = new OpMode() {
    };
    private final StringWriter buffer = new StringWriter();
    private final Repl repl;

    private BatteryTest() {
        Env globals = Builtins.createGlobals();
        BatteryModule.install(globals, opMode);
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
        BatteryTest t = new BatteryTest();
        t.check("0.0", "(battery/voltage)");
        t.opMode.hardwareMap = new HardwareMap();
        t.check("0.0", "(battery/voltage)");
        t.opMode.hardwareMap.voltageSensor.put("Control Hub", new FakeSensor(12.84));
        t.check("12.84", "(battery/voltage)");
        t.opMode.hardwareMap.voltageSensor.put("Expansion Hub 2", new FakeSensor(12.5));
        t.opMode.hardwareMap.voltageSensor.put("Expansion Hub 3", new FakeSensor(0));
        t.check("12.5", "(battery/voltage)");
        t.check("Execution error: Wrong number of args (1) passed to: battery/voltage",
                "(battery/voltage \"Control Hub\")");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
