package secondloop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Operations shared by the collection functions. Nothing is modified in place:
 * every operation that "changes" a collection returns a new copy.
 */
public final class Coll {
    private Coll() {
    }

    /** The elements of coll in order; a map gives [key value] vectors and nil gives nothing. */
    public static List<Object> items(Object coll, String fnName) {
        if (coll == null) {
            return new ArrayList<Object>();
        }
        if (coll instanceof FormMap) {
            List<Object> entries = new ArrayList<Object>();
            for (Map.Entry<Object, Object> e : ((FormMap) coll).entrySet()) {
                entries.add(entry(e.getKey(), e.getValue()));
            }
            return entries;
        }
        if (coll instanceof Collection) {
            return new ArrayList<Object>((Collection<?>) coll);
        }
        throw new ScriptError(fnName + " expects a collection, got: " + Printer.print(coll));
    }

    public static FormVector entry(Object key, Object value) {
        FormVector pair = new FormVector();
        pair.add(key);
        pair.add(value);
        return pair;
    }

    public static int count(Object coll, String fnName) {
        if (coll == null) {
            return 0;
        }
        if (coll instanceof String) {
            return ((String) coll).length();
        }
        if (coll instanceof Map) {
            return ((Map<?, ?>) coll).size();
        }
        if (coll instanceof Collection) {
            return ((Collection<?>) coll).size();
        }
        throw new ScriptError(fnName + " not supported on: " + Printer.print(coll));
    }

    public static int toIndex(Object x, String fnName) {
        double d = Builtins.toNumber(x, fnName);
        if (d != Math.floor(d) || Double.isInfinite(d)) {
            throw new ScriptError(fnName + " expects an integer index, got: " + Printer.print(x));
        }
        return (int) d;
    }

    public static Object conj(Object coll, Object x) {
        if (coll == null || coll instanceof FormList) {
            FormList result = new FormList();
            result.add(x);
            if (coll != null) {
                result.addAll((FormList) coll);
            }
            return result;
        }
        if (coll instanceof FormVector) {
            FormVector result = new FormVector((FormVector) coll);
            result.add(x);
            return result;
        }
        if (coll instanceof FormSet) {
            FormSet result = new FormSet((FormSet) coll);
            result.add(x);
            return result;
        }
        if (coll instanceof FormMap) {
            FormMap result = new FormMap((FormMap) coll);
            if (x instanceof FormMap) {
                result.putAll((FormMap) x);
            } else if (x instanceof FormVector && ((FormVector) x).size() == 2) {
                result.put(((FormVector) x).get(0), ((FormVector) x).get(1));
            } else {
                throw new ScriptError("Can only conj a [key value] vector or a map onto a map, got: " + Printer.print(x));
            }
            return result;
        }
        throw new ScriptError("conj expects a collection, got: " + Printer.print(coll));
    }

    public static Object get(Object coll, Object key, Object notFound) {
        if (coll instanceof FormMap) {
            FormMap map = (FormMap) coll;
            return map.containsKey(key) ? map.get(key) : notFound;
        }
        if (coll instanceof FormSet) {
            return ((FormSet) coll).contains(key) ? key : notFound;
        }
        if (coll instanceof FormVector && key instanceof Double) {
            FormVector vector = (FormVector) coll;
            double d = (Double) key;
            if (d == Math.floor(d) && d >= 0 && d < vector.size()) {
                return vector.get((int) d);
            }
        }
        return notFound;
    }

    public static boolean contains(Object coll, Object key) {
        if (coll == null) {
            return false;
        }
        if (coll instanceof FormMap) {
            return ((FormMap) coll).containsKey(key);
        }
        if (coll instanceof FormSet) {
            return ((FormSet) coll).contains(key);
        }
        if (coll instanceof FormVector) {
            Object missing = new Object();
            return get(coll, key, missing) != missing;
        }
        throw new ScriptError("contains? not supported on: " + Printer.print(coll));
    }

    public static Object nth(Object coll, int index) {
        if (!(coll instanceof List)) {
            throw new ScriptError("nth not supported on: " + Printer.print(coll));
        }
        List<?> items = (List<?>) coll;
        if (index < 0 || index >= items.size()) {
            throw new ScriptError("Index out of bounds: " + index);
        }
        return items.get(index);
    }

    public static Object assoc(Object coll, Object key, Object value) {
        if (coll == null || coll instanceof FormMap) {
            FormMap result = coll == null ? new FormMap() : new FormMap((FormMap) coll);
            result.put(key, value);
            return result;
        }
        if (coll instanceof FormVector) {
            FormVector result = new FormVector((FormVector) coll);
            int index = toIndex(key, "assoc");
            if (index == result.size()) {
                result.add(value);
            } else if (index >= 0 && index < result.size()) {
                result.set(index, value);
            } else {
                throw new ScriptError("Index out of bounds: " + index);
            }
            return result;
        }
        throw new ScriptError("assoc expects a map or a vector, got: " + Printer.print(coll));
    }

    /** Calling a keyword, map, set or vector as a function, e.g. (:speed config) or (v 0). */
    public static Object invoke(Object f, Object[] args) {
        int max = f instanceof Keyword || f instanceof FormMap ? 2 : 1;
        if (args.length < 1 || args.length > max) {
            throw new ScriptError("Wrong number of args (" + args.length + ") passed to: " + Printer.print(f));
        }
        Object notFound = args.length > 1 ? args[1] : null;
        if (f instanceof Keyword) {
            return get(args[0], f, notFound);
        }
        if (f instanceof FormVector) {
            return nth(f, toIndex(args[0], "nth"));
        }
        return get(f, args[0], notFound);
    }
}
