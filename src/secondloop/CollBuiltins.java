package secondloop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Functions on lists, vectors, maps and sets. They never modify their arguments. */
public final class CollBuiltins {
    private static final Object MISSING = new Object();

    private CollBuiltins() {
    }

    static Object assocIn(Object coll, List<Object> keys, int i, Object value) {
        Object key = keys.get(i);
        if (i == keys.size() - 1) {
            return Coll.assoc(coll, key, value);
        }
        return Coll.assoc(coll, key, assocIn(Coll.get(coll, key, null), keys, i + 1, value));
    }

    /** (f old extra...) as used by update and update-in. */
    static Object applyUpdate(Interp interp, Object f, Object old, Object[] args, int extraStart) {
        Object[] callArgs = new Object[1 + args.length - extraStart];
        callArgs[0] = old;
        System.arraycopy(args, extraStart, callArgs, 1, args.length - extraStart);
        return interp.apply(f, callArgs);
    }

    static Object updateIn(Interp interp, Object coll, List<Object> keys, int i, Object f, Object[] args) {
        Object key = keys.get(i);
        Object old = Coll.get(coll, key, null);
        Object updated = i == keys.size() - 1
                ? applyUpdate(interp, f, old, args, 3)
                : updateIn(interp, old, keys, i + 1, f, args);
        return Coll.assoc(coll, key, updated);
    }

    public static void install(Env env) {
        installConstructors(env);
        installAccessors(env);
        installUpdaters(env);
        installSequenceFns(env);
    }

    private static void installConstructors(Env env) {
        Builtins.define(env, new Builtin("list") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                return new FormList(Arrays.asList(args));
            }
        });
        Builtins.define(env, new Builtin("vector") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                return new FormVector(Arrays.asList(args));
            }
        });
        Builtins.define(env, new Builtin("hash-map") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                if (args.length % 2 != 0) {
                    throw new ScriptError("No value supplied for key: " + Printer.print(args[args.length - 1]));
                }
                FormMap result = new FormMap();
                for (int i = 0; i < args.length; i += 2) {
                    result.put(args[i], args[i + 1]);
                }
                return result;
            }
        });
        Builtins.define(env, new Builtin("hash-set") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                return new FormSet(Arrays.asList(args));
            }
        });
        Builtins.define(env, new Builtin("vec") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return new FormVector(Coll.items(args[0], "vec"));
            }
        });
        Builtins.define(env, new Builtin("set") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return new FormSet(Coll.items(args[0], "set"));
            }
        });
        Builtins.define(env, new Builtin("range") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 3);
                double start = args.length > 1 ? Builtins.toNumber(args[0], "range") : 0;
                double end = Builtins.toNumber(args[args.length > 1 ? 1 : 0], "range");
                double step = args.length > 2 ? Builtins.toNumber(args[2], "range") : 1;
                if (step == 0) {
                    throw new ScriptError("range step must not be zero");
                }
                FormList result = new FormList();
                for (double x = start; step > 0 ? x < end : x > end; x += step) {
                    result.add(x);
                }
                return result;
            }
        });

        Builtins.define(env, new TypePredicate("list?", FormList.class));
        Builtins.define(env, new TypePredicate("seq?", FormList.class));
        Builtins.define(env, new TypePredicate("vector?", FormVector.class));
        Builtins.define(env, new TypePredicate("map?", FormMap.class));
        Builtins.define(env, new TypePredicate("set?", FormSet.class));
        Builtins.define(env, new Builtin("coll?") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                Object x = args[0];
                return x instanceof FormList || x instanceof FormVector || x instanceof FormMap || x instanceof FormSet;
            }
        });
    }

    private static void installAccessors(Env env) {
        Builtins.define(env, new Builtin("count") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return (double) Coll.count(args[0], "count");
            }
        });
        Builtins.define(env, new Builtin("empty?") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return Coll.count(args[0], "empty?") == 0;
            }
        });
        Builtins.define(env, new Builtin("seq") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                List<Object> items = Coll.items(args[0], "seq");
                return items.isEmpty() ? null : new FormList(items);
            }
        });
        Builtins.define(env, new Builtin("first") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                List<Object> items = Coll.items(args[0], "first");
                return items.isEmpty() ? null : items.get(0);
            }
        });
        Builtins.define(env, new Builtin("second") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                List<Object> items = Coll.items(args[0], "second");
                return items.size() < 2 ? null : items.get(1);
            }
        });
        Builtins.define(env, new Builtin("last") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                List<Object> items = Coll.items(args[0], "last");
                return items.isEmpty() ? null : items.get(items.size() - 1);
            }
        });
        Builtins.define(env, new Builtin("rest") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                List<Object> items = Coll.items(args[0], "rest");
                return new FormList(items.isEmpty() ? items : items.subList(1, items.size()));
            }
        });
        Builtins.define(env, new Builtin("next") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                List<Object> items = Coll.items(args[0], "next");
                return items.size() < 2 ? null : new FormList(items.subList(1, items.size()));
            }
        });
        Builtins.define(env, new Builtin("nth") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 3);
                int index = Coll.toIndex(args[1], "nth");
                if (args.length == 3 && args[0] instanceof List
                        && (index < 0 || index >= ((List<?>) args[0]).size())) {
                    return args[2];
                }
                return Coll.nth(args[0], index);
            }
        });
        Builtins.define(env, new Builtin("get") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 3);
                return Coll.get(args[0], args[1], args.length == 3 ? args[2] : null);
            }
        });
        Builtins.define(env, new Builtin("get-in") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 3);
                Object current = args[0];
                for (Object key : Coll.items(args[1], "get-in")) {
                    current = Coll.get(current, key, MISSING);
                    if (current == MISSING) {
                        return args.length == 3 ? args[2] : null;
                    }
                }
                return current;
            }
        });
        Builtins.define(env, new Builtin("contains?") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                return Coll.contains(args[0], args[1]);
            }
        });
        Builtins.define(env, new Builtin("keys") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                if (args[0] != null && !(args[0] instanceof FormMap)) {
                    throw new ScriptError("keys expects a map, got: " + Printer.print(args[0]));
                }
                return args[0] == null || ((FormMap) args[0]).isEmpty() ? null : new FormList(((FormMap) args[0]).keySet());
            }
        });
        Builtins.define(env, new Builtin("vals") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                if (args[0] != null && !(args[0] instanceof FormMap)) {
                    throw new ScriptError("vals expects a map, got: " + Printer.print(args[0]));
                }
                return args[0] == null || ((FormMap) args[0]).isEmpty() ? null : new FormList(((FormMap) args[0]).values());
            }
        });
        Builtins.define(env, new Builtin("peek") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                Object coll = args[0];
                if (coll == null) {
                    return null;
                }
                if (!(coll instanceof FormList) && !(coll instanceof FormVector)) {
                    throw new ScriptError("peek expects a list or a vector, got: " + Printer.print(coll));
                }
                List<?> items = (List<?>) coll;
                if (items.isEmpty()) {
                    return null;
                }
                return coll instanceof FormList ? items.get(0) : items.get(items.size() - 1);
            }
        });
    }

    private static void installUpdaters(Env env) {
        Builtins.define(env, new Builtin("conj") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                if (args.length == 0) {
                    return new FormVector();
                }
                Object result = args[0];
                for (int i = 1; i < args.length; i++) {
                    result = Coll.conj(result, args[i]);
                }
                return result;
            }
        });
        Builtins.define(env, new Builtin("cons") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                FormList result = new FormList();
                result.add(args[0]);
                result.addAll(Coll.items(args[1], "cons"));
                return result;
            }
        });
        Builtins.define(env, new Builtin("pop") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                Object coll = args[0];
                if (coll == null) {
                    return null;
                }
                if (coll instanceof FormList) {
                    FormList list = (FormList) coll;
                    if (list.isEmpty()) {
                        throw new ScriptError("Can't pop empty list");
                    }
                    return new FormList(list.subList(1, list.size()));
                }
                if (coll instanceof FormVector) {
                    FormVector vector = (FormVector) coll;
                    if (vector.isEmpty()) {
                        throw new ScriptError("Can't pop empty vector");
                    }
                    return new FormVector(vector.subList(0, vector.size() - 1));
                }
                throw new ScriptError("pop expects a list or a vector, got: " + Printer.print(coll));
            }
        });
        Builtins.define(env, new Builtin("assoc") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 3, -1);
                if (args.length % 2 == 0) {
                    throw new ScriptError("assoc expects even number of arguments after map/vector");
                }
                Object result = args[0];
                for (int i = 1; i < args.length; i += 2) {
                    result = Coll.assoc(result, args[i], args[i + 1]);
                }
                return result;
            }
        });
        Builtins.define(env, new Builtin("assoc-in") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 3, 3);
                List<Object> keys = Coll.items(args[1], "assoc-in");
                if (keys.isEmpty()) {
                    throw new ScriptError("assoc-in expects at least one key");
                }
                return assocIn(args[0], keys, 0, args[2]);
            }
        });
        Builtins.define(env, new Builtin("dissoc") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, -1);
                if (args[0] == null) {
                    return null;
                }
                if (!(args[0] instanceof FormMap)) {
                    throw new ScriptError("dissoc expects a map, got: " + Printer.print(args[0]));
                }
                FormMap result = new FormMap((FormMap) args[0]);
                for (int i = 1; i < args.length; i++) {
                    result.remove(args[i]);
                }
                return result;
            }
        });
        Builtins.define(env, new Builtin("disj") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, -1);
                if (args[0] == null) {
                    return null;
                }
                if (!(args[0] instanceof FormSet)) {
                    throw new ScriptError("disj expects a set, got: " + Printer.print(args[0]));
                }
                FormSet result = new FormSet((FormSet) args[0]);
                for (int i = 1; i < args.length; i++) {
                    result.remove(args[i]);
                }
                return result;
            }
        });
        Builtins.define(env, new Builtin("update") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 3, -1);
                Object old = Coll.get(args[0], args[1], null);
                return Coll.assoc(args[0], args[1], applyUpdate(interp, args[2], old, args, 3));
            }
        });
        Builtins.define(env, new Builtin("update-in") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 3, -1);
                List<Object> keys = Coll.items(args[1], "update-in");
                if (keys.isEmpty()) {
                    throw new ScriptError("update-in expects at least one key");
                }
                return updateIn(interp, args[0], keys, 0, args[2], args);
            }
        });
        Builtins.define(env, new Builtin("into") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                Object result = args[0];
                for (Object x : Coll.items(args[1], "into")) {
                    result = Coll.conj(result, x);
                }
                return result;
            }
        });
        Builtins.define(env, new Builtin("merge") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                Object result = null;
                for (Object map : args) {
                    if (map == null) {
                        continue;
                    }
                    if (!(map instanceof FormMap)) {
                        throw new ScriptError("merge expects maps, got: " + Printer.print(map));
                    }
                    result = result == null ? map : Coll.conj(result, map);
                }
                return result;
            }
        });
    }

    private static void installSequenceFns(Env env) {
        Builtins.define(env, new Builtin("concat") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                FormList result = new FormList();
                for (Object coll : args) {
                    result.addAll(Coll.items(coll, "concat"));
                }
                return result;
            }
        });
        Builtins.define(env, new Builtin("reverse") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                List<Object> items = Coll.items(args[0], "reverse");
                FormList result = new FormList();
                for (int i = items.size() - 1; i >= 0; i--) {
                    result.add(items.get(i));
                }
                return result;
            }
        });
        Builtins.define(env, new Builtin("map") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, -1);
                List<List<Object>> colls = new ArrayList<List<Object>>();
                int length = Integer.MAX_VALUE;
                for (int i = 1; i < args.length; i++) {
                    List<Object> items = Coll.items(args[i], "map");
                    colls.add(items);
                    length = Math.min(length, items.size());
                }
                FormList result = new FormList();
                for (int i = 0; i < length; i++) {
                    Object[] callArgs = new Object[colls.size()];
                    for (int j = 0; j < colls.size(); j++) {
                        callArgs[j] = colls.get(j).get(i);
                    }
                    result.add(interp.apply(args[0], callArgs));
                }
                return result;
            }
        });
        Builtins.define(env, new Builtin("filter") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                FormList result = new FormList();
                for (Object x : Coll.items(args[1], "filter")) {
                    if (Interp.isTruthy(interp.apply(args[0], new Object[] {x}))) {
                        result.add(x);
                    }
                }
                return result;
            }
        });
        Builtins.define(env, new Builtin("some") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                for (Object x : Coll.items(args[1], "some")) {
                    Object result = interp.apply(args[0], new Object[] {x});
                    if (Interp.isTruthy(result)) {
                        return result;
                    }
                }
                return null;
            }
        });
        Builtins.define(env, new Builtin("every?") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                for (Object x : Coll.items(args[1], "every?")) {
                    if (!Interp.isTruthy(interp.apply(args[0], new Object[] {x}))) {
                        return false;
                    }
                }
                return true;
            }
        });
        Builtins.define(env, new Builtin("reduce") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 3);
                List<Object> items = Coll.items(args[args.length - 1], "reduce");
                int start = 0;
                Object acc;
                if (args.length == 3) {
                    acc = args[1];
                } else if (items.isEmpty()) {
                    return interp.apply(args[0], new Object[0]);
                } else {
                    acc = items.get(0);
                    start = 1;
                }
                for (int i = start; i < items.size(); i++) {
                    acc = interp.apply(args[0], new Object[] {acc, items.get(i)});
                }
                return acc;
            }
        });
        Builtins.define(env, new Builtin("apply") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, -1);
                List<Object> callArgs = new ArrayList<Object>(Arrays.asList(args).subList(1, args.length - 1));
                callArgs.addAll(Coll.items(args[args.length - 1], "apply"));
                return interp.apply(args[0], callArgs.toArray());
            }
        });
    }
}
