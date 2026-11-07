package secondloop;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A changeable reference, shared safely between REPL connections and running
 * loops: (atom 0), @a, (reset! a 1), (swap! a inc).
 */
public final class Atom {
    private final AtomicReference<Object> value;

    public Atom(Object initial) {
        value = new AtomicReference<Object>(initial);
    }

    public Object deref() {
        return value.get();
    }

    public Object reset(Object newValue) {
        value.set(newValue);
        return newValue;
    }

    /**
     * Sets the value to (f old extra...). If another thread changes the atom
     * while f runs, f is called again with the newer value.
     */
    public Object swap(Interp interp, Object f, Object[] extra) {
        while (true) {
            Object old = value.get();
            Object[] args = new Object[extra.length + 1];
            args[0] = old;
            System.arraycopy(extra, 0, args, 1, extra.length);
            Object updated = interp.apply(f, args);
            if (value.compareAndSet(old, updated)) {
                return updated;
            }
        }
    }
}
