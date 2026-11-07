package secondloop;

/**
 * Raised in a loop or time/sleep when its thread is stopped: by
 * control/stop, future-cancel, or STOP on the Driver Hub. catch never
 * catches it, so a stopped loop cannot carry on; finally still runs.
 */
public final class Interrupted extends ScriptError {
    public Interrupted() {
        super("Interrupted");
    }
}
