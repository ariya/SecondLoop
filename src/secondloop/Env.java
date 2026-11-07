package secondloop;

import java.util.HashMap;

/**
 * A scope of bindings. The global scope is shared by every REPL connection,
 * so access is synchronized.
 */
public final class Env {
    private final Env parent;
    private final HashMap<Symbol, Object> bindings = new HashMap<Symbol, Object>();

    public Env(Env parent) {
        this.parent = parent;
    }

    public synchronized void define(Symbol symbol, Object value) {
        bindings.put(symbol, value);
    }

    /** Checks and defines in one step, so two connections running the same defonce cannot both define it. */
    public synchronized boolean defineIfAbsent(Symbol symbol, Object value) {
        if (bindings.containsKey(symbol)) {
            return false;
        }
        bindings.put(symbol, value);
        return true;
    }

    public synchronized boolean isDefined(Symbol symbol) {
        return bindings.containsKey(symbol);
    }

    public Object lookup(Symbol symbol) {
        for (Env env = this; env != null; env = env.parent) {
            synchronized (env) {
                if (env.bindings.containsKey(symbol)) {
                    return env.bindings.get(symbol);
                }
            }
        }
        throw new SyntaxError("Unable to resolve symbol: " + symbol.name + " in this context");
    }
}
