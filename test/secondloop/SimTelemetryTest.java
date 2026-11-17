package secondloop;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/** Checks what RobotSimulator prints for each telemetry frame. Run: java -cp build/classes secondloop.SimTelemetryTest */
public final class SimTelemetryTest {
    private static int failures;
    private static int count;

    private final ByteArrayOutputStream printed = new ByteArrayOutputStream();
    private final SimTelemetry telemetry;

    private SimTelemetryTest() throws Exception {
        telemetry = new SimTelemetry(new PrintStream(printed, true, "UTF-8"));
    }

    /** Draws one frame of lines, then checks what was printed (lines joined by '|'). */
    private void frame(String expected, String label, String... lines) throws Exception {
        printed.reset();
        for (String line : lines) {
            telemetry.addLine(line);
        }
        telemetry.update();
        check(expected, printed.toString("UTF-8").replace(System.lineSeparator(), "|"), label);
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
        SimTelemetryTest t = new SimTelemetryTest();
        t.frame("", "empty frame prints nothing");
        t.frame("power 0.5|", "first line", "power 0.5");
        t.frame("", "same frame prints nothing", "power 0.5");
        t.frame("a|b|", "only the added lines", "power 0.5", "a", "b");
        t.frame("--------|", "cleared");
        t.frame("x|", "after clear", "x");
        t.frame("--------|y|z|", "changed lines redraw the frame", "y", "z");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
