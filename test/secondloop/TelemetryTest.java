package secondloop;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/** Checks the telemetry functions against a recording Telemetry. Run: java -cp build/classes secondloop.TelemetryTest */
public final class TelemetryTest {
    private static int failures;
    private static int count;

    /** Records what render() adds, like one Driver Hub frame. */
    static final class RecordingTelemetry implements Telemetry {
        final List<String> lines = new ArrayList<String>();

        @Override
        public Item addData(String caption, Object value) {
            lines.add(caption + ": " + value);
            return null;
        }

        @Override
        public Line addLine(String lineCaption) {
            lines.add(lineCaption);
            return null;
        }

        @Override
        public boolean update() {
            return true;
        }
    }

    private final TelemetryModule module = new TelemetryModule();
    private final StringWriter buffer = new StringWriter();
    private final Repl repl;

    private TelemetryTest() {
        Env globals = Builtins.createGlobals();
        module.install(globals);
        repl = new Repl(globals, new PrintWriter(buffer));
    }

    private void feed(String expectedResult, String line) {
        buffer.getBuffer().setLength(0);
        repl.feed(line);
        check(expectedResult, buffer.toString().trim(), line);
    }

    /** The lines one redraw of the Driver Hub would show. */
    private void shows(String expected, String label) {
        RecordingTelemetry frame = new RecordingTelemetry();
        module.render(frame);
        StringBuilder sb = new StringBuilder();
        for (String line : frame.lines) {
            sb.append(line).append('|');
        }
        check(expected, sb.toString(), label);
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
        TelemetryTest t = new TelemetryTest();
        t.shows("", "nothing added yet");
        t.feed("nil", "(telemetry/add-line \"Hello\")");
        t.feed("nil", "(telemetry/add-line (format \"power %.2f\" 0.5))");
        t.shows("Hello|power 0.50|", "two lines");
        t.feed("nil", "(telemetry/add-line 3)");
        t.feed("nil", "(telemetry/add-line {:mode :fast})");
        t.shows("Hello|power 0.50|3.0|{:mode :fast}|", "values shown as println shows them");
        t.feed("nil", "(telemetry/clear)");
        t.feed("nil", "(telemetry/add-line \"power\" 0.54 \"heading\" 12.3)");
        t.feed("nil", "(telemetry/add-line \"mode\" :fast [\"a\" nil])");
        t.feed("nil", "(telemetry/add-line)");
        t.feed("nil", "(telemetry/add-line nil)");
        t.shows("power 0.54 heading 12.3|mode :fast [a nil]||nil|", "several values joined like println");
        t.feed("nil", "(telemetry/clear)");
        t.shows("", "after clear");
        t.feed("nil", "(dotimes [i 60] (telemetry/add-line (str \"line \" i)))");
        RecordingTelemetry frame = new RecordingTelemetry();
        t.module.render(frame);
        check("50 line 10.0 line 59.0", frame.lines.size() + " " + frame.lines.get(0) + " " + frame.lines.get(49), "keeps the newest 50 lines");
        t.feed("Execution error: Wrong number of args (1) passed to: telemetry/clear", "(telemetry/clear 1)");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
