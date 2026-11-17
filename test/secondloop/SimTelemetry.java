package secondloop;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The telemetry of RobotSimulator: a Telemetry that prints to stdout. Printing
 * every frame would flood the terminal, so update() prints only what changed:
 * new lines at the end, or "--------" and the whole frame when lines were
 * cleared. The web UI shows the whole frame, from lines().
 */
final class SimTelemetry implements Telemetry {
    static final String CLEAR = "--------";

    private final PrintStream out;
    private final List<String> frame = new ArrayList<String>();
    private List<String> shown = new ArrayList<String>();

    SimTelemetry(PrintStream out) {
        this.out = out;
    }

    @Override
    public synchronized Item addData(String caption, Object value) {
        frame.add(caption + ": " + value);
        return null;
    }

    @Override
    public synchronized Line addLine(String lineCaption) {
        frame.add(lineCaption);
        return null;
    }

    @Override
    public synchronized boolean update() {
        int start = shown.size();
        if (!startsWith(frame, shown)) {
            out.println(CLEAR);
            start = 0;
        }
        for (int i = start; i < frame.size(); i++) {
            out.println(frame.get(i));
        }
        shown = new ArrayList<String>(frame);
        frame.clear();
        return true;
    }

    /** The lines on screen since the last update(). */
    synchronized List<String> lines() {
        return new ArrayList<String>(shown);
    }

    private static boolean startsWith(List<String> list, List<String> prefix) {
        return list.size() >= prefix.size() && list.subList(0, prefix.size()).equals(prefix);
    }
}
