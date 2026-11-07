package secondloop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The value of (future body...): the body runs on its own thread, so a control
 * loop can run in the background while the REPL stays free. Every future that
 * is still running is cancelled when the OpMode stops.
 */
public final class Future {
    private static final Set<Future> RUNNING = new HashSet<Future>();
    private static final long CANCEL_WAIT_MILLIS = 1000;

    private final Thread thread;
    private boolean done;
    private boolean cancelled;
    private Object value;
    private RuntimeException error;

    public Future(final Interp interp, final List<Object> body, final Env env) {
        thread = new Thread(null, new Runnable() {
            @Override
            public void run() {
                execute(interp, body, env);
            }
        }, "SecondLoop-future", ReplServer.THREAD_STACK_SIZE);
        thread.setDaemon(true);
    }

    public Future start() {
        synchronized (RUNNING) {
            RUNNING.add(this);
        }
        thread.start();
        return this;
    }

    /** Cancels every running future; called when the OpMode stops. */
    public static void cancelAll() {
        List<Future> running;
        synchronized (RUNNING) {
            running = new ArrayList<Future>(RUNNING);
        }
        for (Future future : running) {
            future.cancel();
        }
    }

    private void execute(Interp interp, List<Object> body, Env env) {
        Object result = null;
        RuntimeException failure = null;
        try {
            result = interp.evalBody(body, 0, env);
        } catch (RuntimeException e) {
            failure = e;
        } catch (StackOverflowError e) {
            failure = new ScriptError("Stack overflow");
        }
        synchronized (RUNNING) {
            RUNNING.remove(this);
        }
        boolean reportFailure;
        synchronized (this) {
            reportFailure = failure != null && !cancelled;
            if (!done) {
                done = true;
                value = result;
                error = failure;
                notifyAll();
            }
        }
        if (reportFailure) {
            // Say so at once: a control loop that died silently is dangerous.
            interp.out.println();
            interp.out.println("Future failed. " + Repl.describe(failure));
            interp.out.flush();
        }
    }

    /**
     * Returns false if the future had already finished. Waits (up to a second)
     * for the thread to end, so once a loop is cancelled it cannot take one
     * more step, such as setting a motor's power.
     */
    public boolean cancel() {
        synchronized (this) {
            if (done) {
                return false;
            }
            done = true;
            cancelled = true;
            notifyAll();
        }
        thread.interrupt(); // ends a running loop or time/sleep
        if (Thread.currentThread() != thread) {
            try {
                thread.join(CANCEL_WAIT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return true;
    }

    public synchronized boolean isDone() {
        return done;
    }

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    /** Waits for the result: forever when timeoutMillis < 0, otherwise at most that long. */
    public synchronized Object deref(long timeoutMillis, Object timeoutValue) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!done) {
            long wait = timeoutMillis < 0 ? 0 : deadline - System.currentTimeMillis();
            if (timeoutMillis >= 0 && wait <= 0) {
                return timeoutValue;
            }
            try {
                wait(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Interrupted();
            }
        }
        if (cancelled) {
            throw new ScriptError("Future was cancelled");
        }
        if (error != null) {
            throw error;
        }
        return value;
    }

    /** For the printer: pending, cancelled or failed, or null once it has finished with a value. */
    public synchronized String status() {
        if (!done) {
            return "pending";
        }
        if (cancelled) {
            return "cancelled";
        }
        return error != null ? "failed" : null;
    }

    synchronized Object value() {
        return value;
    }
}
