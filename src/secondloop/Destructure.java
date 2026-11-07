package secondloop;

import java.util.List;
import java.util.Map;

/**
 * Binds a pattern to a value, as let and fn parameters do:
 *   x                                  the whole value
 *   [a b & more :as all]               items of a list or vector
 *   {:keys [a b] c :c :or {b 0} :as m} entries of a map
 * Patterns nest, e.g. [[x y] {:keys [angle]}].
 */
public final class Destructure {
    static final Symbol AMPERSAND = Symbol.of("&");
    private static final Keyword AS = Keyword.of("as");
    private static final Keyword KEYS = Keyword.of("keys");
    private static final Keyword OR = Keyword.of("or");

    private Destructure() {
    }

    /** Throws a SyntaxError if the pattern is malformed, so mistakes show up before any value is bound. */
    public static void check(Object pattern) {
        if (pattern instanceof Symbol) {
            if (pattern == AMPERSAND) {
                throw new SyntaxError("& must be followed by exactly one binding form");
            }
        } else if (pattern instanceof FormVector) {
            checkSequential((FormVector) pattern);
        } else if (pattern instanceof FormMap) {
            checkMap((FormMap) pattern);
        } else {
            throw new SyntaxError("Unsupported binding form: " + Printer.print(pattern));
        }
    }

    private static void checkSequential(FormVector pattern) {
        for (int i = 0; i < pattern.size(); i++) {
            Object p = pattern.get(i);
            if (p == AMPERSAND) {
                if (i + 1 >= pattern.size()) {
                    throw new SyntaxError("& must be followed by exactly one binding form");
                }
                check(pattern.get(i + 1));
                i++;
                if (i + 1 < pattern.size() && pattern.get(i + 1) != AS) {
                    throw new SyntaxError("Only :as can follow the & binding form");
                }
            } else if (p == AS) {
                if (i != pattern.size() - 2 || !(pattern.get(i + 1) instanceof Symbol)) {
                    throw new SyntaxError(":as must be followed by a symbol, at the end of the binding vector");
                }
                return;
            } else {
                check(p);
            }
        }
    }

    private static void checkMap(FormMap pattern) {
        for (Map.Entry<Object, Object> e : pattern.entrySet()) {
            Object key = e.getKey();
            Object value = e.getValue();
            if (key == KEYS) {
                if (!(value instanceof FormVector)) {
                    throw new SyntaxError(":keys must be followed by a vector of symbols");
                }
                for (Object name : (FormVector) value) {
                    if (!(name instanceof Symbol) && !(name instanceof Keyword)) {
                        throw new SyntaxError(":keys must be followed by a vector of symbols");
                    }
                }
            } else if (key == OR) {
                if (!(value instanceof FormMap)) {
                    throw new SyntaxError(":or must be followed by a map");
                }
            } else if (key == AS) {
                if (!(value instanceof Symbol)) {
                    throw new SyntaxError(":as must be followed by a symbol");
                }
            } else if (key instanceof Keyword) {
                throw new SyntaxError("Unsupported map binding option: " + Printer.print(key));
            } else {
                check(key);
            }
        }
    }

    /** Binds a pattern that has already passed check(). */
    public static void bind(Interp interp, Object pattern, Object value, Env env) {
        if (pattern instanceof Symbol) {
            env.define((Symbol) pattern, value);
        } else if (pattern instanceof FormVector) {
            bindSequential(interp, (FormVector) pattern, value, env);
        } else {
            bindMap(interp, (FormMap) pattern, value, env);
        }
    }

    private static void bindSequential(Interp interp, FormVector pattern, Object value, Env env) {
        if (value != null && !(value instanceof FormList) && !(value instanceof FormVector)) {
            throw new ScriptError("Cannot destructure as a sequence: " + Printer.print(value));
        }
        List<Object> items = Coll.items(value, "destructure");
        int position = 0;
        for (int i = 0; i < pattern.size(); i++) {
            Object p = pattern.get(i);
            if (p == AMPERSAND) {
                Object rest = position < items.size() ? new FormList(items.subList(position, items.size())) : null;
                bind(interp, pattern.get(i + 1), rest, env);
                i++;
            } else if (p == AS) {
                env.define((Symbol) pattern.get(i + 1), value);
                i++;
            } else {
                bind(interp, p, position < items.size() ? items.get(position) : null, env);
                position++;
            }
        }
    }

    private static void bindMap(Interp interp, FormMap pattern, Object value, Env env) {
        FormMap map = toMap(value);
        Object defaults = pattern.get(OR);
        for (Map.Entry<Object, Object> e : pattern.entrySet()) {
            Object key = e.getKey();
            if (key == AS) {
                env.define((Symbol) e.getValue(), value);
            } else if (key == KEYS) {
                for (Object name : (FormVector) e.getValue()) {
                    String text = name instanceof Symbol ? ((Symbol) name).name : ((Keyword) name).name;
                    Symbol symbol = Symbol.of(text);
                    env.define(symbol, lookup(interp, map, Keyword.of(text), symbol, defaults, env));
                }
            } else if (key != OR) {
                bind(interp, key, lookup(interp, map, e.getValue(), key, defaults, env), env);
            }
        }
    }

    /** The value for key, or the :or default when the map has no such key. */
    private static Object lookup(Interp interp, FormMap map, Object key, Object pattern, Object defaults, Env env) {
        if (map.containsKey(key)) {
            return map.get(key);
        }
        if (defaults != null && pattern instanceof Symbol && ((FormMap) defaults).containsKey(pattern)) {
            return interp.eval(((FormMap) defaults).get(pattern), env);
        }
        return null;
    }

    /** A map to destructure. A list of alternating keys and values also works, as for (f & {:keys [a]}). */
    private static FormMap toMap(Object value) {
        if (value == null) {
            return new FormMap();
        }
        if (value instanceof FormMap) {
            return (FormMap) value;
        }
        if (value instanceof FormList && ((FormList) value).size() % 2 == 0) {
            FormList pairs = (FormList) value;
            FormMap map = new FormMap();
            for (int i = 0; i < pairs.size(); i += 2) {
                map.put(pairs.get(i), pairs.get(i + 1));
            }
            return map;
        }
        throw new ScriptError("Cannot destructure as a map: " + Printer.print(value));
    }
}
