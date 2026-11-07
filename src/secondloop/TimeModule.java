package secondloop;

/**
 * (time/elapsed) and (time/sleep s), both in seconds, so a robot programmer
 * never converts milliseconds. time/elapsed counts from START, like the SDK's
 * ElapsedTime reset after waitForStart(); before START it counts from INIT, so
 * the clock is already useful while trying things out.
 */
public final class TimeModule {
    private TimeModule() {
    }

    /** Each globals gets its own clock, because OnBot Java keeps static fields between OpMode runs. */
    private static final class Clock extends Builtin {
        private volatile long startNanos = System.nanoTime();

        Clock() {
            super("time/elapsed");
        }

        @Override
        public Object invoke(Interp interp, Object[] args) {
            checkArity(args, 0, 0);
            return (System.nanoTime() - startNanos) / 1e9;
        }
    }

    public static void install(Env env) {
        Builtins.define(env, new Clock());
        Builtins.define(env, new Builtin("time/sleep") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                double seconds = Builtins.toNumber(args[0], "time/sleep");
                // A deadline that has already passed gives a negative time; that means no wait.
                if (seconds > 0) {
                    try {
                        Thread.sleep(Math.round(seconds * 1000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new Interrupted();
                    }
                }
                return null;
            }
        });
    }

    /** Starts counting from zero again; the OpMode calls it when START is pressed. */
    public static void restart(Env env) {
        Symbol name = Symbol.of("time/elapsed");
        Object fn = env.isDefined(name) ? env.lookup(name) : null;
        if (fn instanceof Clock) {
            ((Clock) fn).startNanos = System.nanoTime();
        }
    }
}
