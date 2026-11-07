package secondloop;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.ArrayList;
import java.util.List;

/**
 * (telemetry/add-line "power" 0.5) and (telemetry/clear): lines shown on the
 * Driver Hub. The SDK's Telemetry is not thread-safe and REPL code runs
 * on other threads, so the functions only edit this list; the OpMode thread
 * draws it with render() on every status update.
 */
public final class TelemetryModule {
    /** Oldest lines are dropped beyond this, so a loop that never clears cannot fill memory. */
    static final int MAX_LINES = 50;

    private final List<String> lines = new ArrayList<String>();

    public void install(Env env) {
        Builtins.define(env, new Builtin("telemetry/add-line") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                // Same as println, so a line reads the same on the Driver Hub as in the REPL.
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) {
                        sb.append(' ');
                    }
                    sb.append(Printer.display(args[i]));
                }
                addLine(sb.toString());
                return null;
            }
        });
        Builtins.define(env, new Builtin("telemetry/clear") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 0, 0);
                clear();
                return null;
            }
        });
    }

    public synchronized void addLine(String line) {
        lines.add(line);
        if (lines.size() > MAX_LINES) {
            lines.remove(0);
        }
    }

    public synchronized void clear() {
        lines.clear();
    }

    /** Adds the current lines to telemetry; the caller then calls telemetry.update(). */
    public void render(Telemetry telemetry) {
        List<String> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<String>(lines);
        }
        for (String line : snapshot) {
            telemetry.addLine(line);
        }
    }
}
