package secondloop;

import java.util.concurrent.ConcurrentHashMap;

/** An interned keyword such as :left. Keywords evaluate to themselves. */
public final class Keyword {
    private static final ConcurrentHashMap<String, Keyword> TABLE = new ConcurrentHashMap<String, Keyword>();

    public final String name;

    private Keyword(String name) {
        this.name = name;
    }

    public static Keyword of(String name) {
        Keyword existing = TABLE.get(name);
        if (existing != null) {
            return existing;
        }
        Keyword fresh = new Keyword(name);
        existing = TABLE.putIfAbsent(name, fresh);
        return existing != null ? existing : fresh;
    }

    @Override
    public String toString() {
        return ":" + name;
    }
}
