package secondloop;

import java.util.concurrent.ConcurrentHashMap;

/** Interned, so symbols can be map keys and compared without equals(). */
public final class Symbol {
    private static final ConcurrentHashMap<String, Symbol> TABLE = new ConcurrentHashMap<String, Symbol>();

    public final String name;

    private Symbol(String name) {
        this.name = name;
    }

    public static Symbol of(String name) {
        Symbol existing = TABLE.get(name);
        if (existing != null) {
            return existing;
        }
        Symbol fresh = new Symbol(name);
        existing = TABLE.putIfAbsent(name, fresh);
        return existing != null ? existing : fresh;
    }

    @Override
    public String toString() {
        return name;
    }
}
