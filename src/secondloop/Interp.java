package secondloop;

import java.io.PrintWriter;

/**
 * The interpreter as builtins, futures and modules see it. Each language
 * extends it with its own syntax and evaluation, so the rest of the runtime
 * works with any of them. One instance per REPL session: the globals are
 * shared, while output (println) goes to that session.
 */
public abstract class Interp {
    public final Env globals;
    public final PrintWriter out;

    protected Interp(Env globals, PrintWriter out) {
        this.globals = globals;
        this.out = out;
    }

    /** Only nil and false are false, as in both Clojure and Lua. */
    public static boolean isTruthy(Object x) {
        return x != null && !Boolean.FALSE.equals(x);
    }

    /**
     * Called on every round of every loop. When the OpMode stops, the server
     * interrupts each session thread, so a running loop ends instead of
     * driving motors after STOP.
     */
    public static void checkStop() {
        if (Thread.currentThread().isInterrupted()) {
            throw new Interrupted();
        }
    }

    /** Calls f. A language may override this to let other values be called too. */
    public Object apply(Object f, Object[] args) {
        if (f instanceof Fn) {
            return ((Fn) f).invoke(this, args);
        }
        throw new ScriptError(Printer.print(f) + " is not a function");
    }

    /** An error as the REPL shows it, in the style of the language. */
    public abstract String describe(Throwable e);
}
