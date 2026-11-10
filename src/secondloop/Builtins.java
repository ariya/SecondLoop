package secondloop;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** The core functions on numbers, strings, booleans, keywords and symbols. All numbers are doubles. */
public final class Builtins {
    private static final int LT = 0;
    private static final int GT = 1;
    private static final int LE = 2;
    private static final int GE = 3;

    private Builtins() {
    }

    /** A fresh global environment containing every builtin. */
    public static Env createGlobals() {
        Env globals = new Env(null);
        install(globals);
        CollBuiltins.install(globals);
        MathBuiltins.install(globals);
        StringBuiltins.install(globals);
        TimeModule.install(globals);
        Version.install(globals);
        Prelude.load(globals);
        return globals;
    }

    public static void define(Env env, Builtin fn) {
        env.define(Symbol.of(fn.name()), fn);
    }

    static double toNumber(Object x, String fnName) {
        if (x instanceof Double) {
            return (Double) x;
        }
        throw new ScriptError(fnName + " expects numbers, got: " + Printer.print(x));
    }

    static Atom toAtom(Object x, String fnName) {
        if (x instanceof Atom) {
            return (Atom) x;
        }
        throw new ScriptError(fnName + " expects an atom, got: " + Printer.print(x));
    }

    static Future toFuture(Object x, String fnName) {
        if (x instanceof Future) {
            return (Future) x;
        }
        throw new ScriptError(fnName + " expects a future, got: " + Printer.print(x));
    }

    static double divide(double a, double b) {
        if (b == 0) {
            throw new ScriptError("Divide by zero");
        }
        return a / b;
    }

    /** Equality: numbers by value, lists and vectors element by element. */
    static boolean isEqual(Object a, Object b) {
        if (a instanceof Double && b instanceof Double) {
            return ((Double) a).doubleValue() == ((Double) b).doubleValue();
        }
        if (a instanceof List && b instanceof List) {
            List<?> x = (List<?>) a;
            List<?> y = (List<?>) b;
            if (x.size() != y.size()) {
                return false;
            }
            for (int i = 0; i < x.size(); i++) {
                if (!isEqual(x.get(i), y.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof Map && b instanceof Map || a instanceof Set && b instanceof Set) {
            return a.equals(b);
        }
        return a == null ? b == null : a.equals(b);
    }

    static boolean compareChain(String fnName, Object[] args, int op) {
        for (int i = 0; i + 1 < args.length; i++) {
            double a = toNumber(args[i], fnName);
            double b = toNumber(args[i + 1], fnName);
            boolean ok;
            switch (op) {
                case LT: ok = a < b; break;
                case GT: ok = a > b; break;
                case LE: ok = a <= b; break;
                default: ok = a >= b; break;
            }
            if (!ok) {
                return false;
            }
        }
        if (args.length == 1) {
            toNumber(args[0], fnName);
        }
        return true;
    }

    private static void install(Env env) {
        define(env, new Builtin("+") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                double result = 0;
                for (Object x : args) {
                    result += toNumber(x, "+");
                }
                return result;
            }
        });
        define(env, new Builtin("-") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, -1);
                double result = toNumber(args[0], "-");
                if (args.length == 1) {
                    return -result;
                }
                for (int i = 1; i < args.length; i++) {
                    result -= toNumber(args[i], "-");
                }
                return result;
            }
        });
        define(env, new Builtin("*") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                double result = 1;
                for (Object x : args) {
                    result *= toNumber(x, "*");
                }
                return result;
            }
        });
        define(env, new Builtin("/") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, -1);
                double result = toNumber(args[0], "/");
                if (args.length == 1) {
                    return divide(1, result);
                }
                for (int i = 1; i < args.length; i++) {
                    result = divide(result, toNumber(args[i], "/"));
                }
                return result;
            }
        });
        define(env, new Builtin("inc") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return toNumber(args[0], "inc") + 1;
            }
        });
        define(env, new Builtin("dec") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return toNumber(args[0], "dec") - 1;
            }
        });

        define(env, new Builtin("=") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, -1);
                for (int i = 0; i + 1 < args.length; i++) {
                    if (!isEqual(args[i], args[i + 1])) {
                        return false;
                    }
                }
                return true;
            }
        });
        define(env, new Builtin("not=") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, -1);
                for (int i = 0; i + 1 < args.length; i++) {
                    if (!isEqual(args[i], args[i + 1])) {
                        return true;
                    }
                }
                return false;
            }
        });
        define(env, new Builtin("<") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, -1);
                return compareChain("<", args, LT);
            }
        });
        define(env, new Builtin(">") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, -1);
                return compareChain(">", args, GT);
            }
        });
        define(env, new Builtin("<=") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, -1);
                return compareChain("<=", args, LE);
            }
        });
        define(env, new Builtin(">=") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, -1);
                return compareChain(">=", args, GE);
            }
        });
        define(env, new Builtin("not") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return !Interp.isTruthy(args[0]);
            }
        });

        define(env, new Builtin("str") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                StringBuilder sb = new StringBuilder();
                for (Object x : args) {
                    sb.append(Printer.str(x));
                }
                return sb.toString();
            }
        });
        define(env, new Builtin("println") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) {
                        sb.append(' ');
                    }
                    sb.append(Printer.display(args[i]));
                }
                interp.out.println(sb);
                interp.out.flush();
                return null;
            }
        });
        define(env, new Builtin("atom") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return new Atom(args[0]);
            }
        });
        define(env, new Builtin("deref") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 3);
                if (args[0] instanceof Future) {
                    if (args.length == 2) {
                        throw new ScriptError("deref takes a future alone, or with a timeout and a timeout value");
                    }
                    long timeout = args.length == 3 ? (long) toNumber(args[1], "deref") : -1;
                    return ((Future) args[0]).deref(timeout, args.length == 3 ? args[2] : null);
                }
                checkArity(args, 1, 1);
                return toAtom(args[0], "deref").deref();
            }
        });
        define(env, new Builtin("reset!") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                return toAtom(args[0], "reset!").reset(args[1]);
            }
        });
        define(env, new Builtin("swap!") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, -1);
                Object[] extra = new Object[args.length - 2];
                System.arraycopy(args, 2, extra, 0, extra.length);
                return toAtom(args[0], "swap!").swap(interp, args[1], extra);
            }
        });
        define(env, new Builtin("future-cancel") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return toFuture(args[0], "future-cancel").cancel();
            }
        });
        define(env, new Builtin("future-done?") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return toFuture(args[0], "future-done?").isDone();
            }
        });
        define(env, new Builtin("future-cancelled?") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return toFuture(args[0], "future-cancelled?").isCancelled();
            }
        });
        define(env, new TypePredicate("future?", Future.class));
        define(env, new Builtin("throw") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                if (args[0] instanceof RuntimeException) {
                    throw (RuntimeException) args[0];
                }
                if (args[0] instanceof Error) {
                    throw (Error) args[0];
                }
                throw new ScriptError("throw expects an exception, e.g. (ex-info \"message\" {}), got: " + Printer.print(args[0]));
            }
        });
        define(env, new Builtin("ex-info") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                if (!(args[0] instanceof String)) {
                    throw new ScriptError("ex-info expects a message string, got: " + Printer.print(args[0]));
                }
                if (args[1] != null && !(args[1] instanceof FormMap)) {
                    throw new ScriptError("ex-info expects a map of data, got: " + Printer.print(args[1]));
                }
                return new ExInfo((String) args[0], args[1]);
            }
        });
        define(env, new Builtin("ex-message") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return args[0] instanceof Throwable ? ((Throwable) args[0]).getMessage() : null;
            }
        });
        define(env, new Builtin("ex-data") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return args[0] instanceof ExInfo ? ((ExInfo) args[0]).data : null;
            }
        });
        define(env, new Builtin("nil?") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return args[0] == null;
            }
        });
        define(env, new Builtin("some?") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return args[0] != null;
            }
        });
        define(env, new Builtin("true?") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return Boolean.TRUE.equals(args[0]);
            }
        });
        define(env, new Builtin("false?") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return Boolean.FALSE.equals(args[0]);
            }
        });
        define(env, new Builtin("boolean") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                return Interp.isTruthy(args[0]);
            }
        });
        define(env, new TypePredicate("boolean?", Boolean.class));
        define(env, new TypePredicate("number?", Double.class));
        define(env, new TypePredicate("string?", String.class));
        define(env, new TypePredicate("keyword?", Keyword.class));
        define(env, new TypePredicate("symbol?", Symbol.class));
        define(env, new TypePredicate("fn?", Fn.class));
        define(env, new Builtin("keyword") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                Object x = args[0];
                if (x == null || x instanceof Keyword) {
                    return x;
                }
                if (x instanceof String) {
                    return Keyword.of((String) x);
                }
                if (x instanceof Symbol) {
                    return Keyword.of(((Symbol) x).name);
                }
                throw new ScriptError("keyword expects a string, got: " + Printer.print(x));
            }
        });
        define(env, new Builtin("symbol") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                Object x = args[0];
                if (x instanceof Symbol) {
                    return x;
                }
                if (x instanceof String) {
                    return Symbol.of((String) x);
                }
                if (x instanceof Keyword) {
                    return Symbol.of(((Keyword) x).name);
                }
                throw new ScriptError("symbol expects a string, got: " + Printer.print(x));
            }
        });
        define(env, new Builtin("name") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 1);
                Object x = args[0];
                if (x instanceof String) {
                    return x;
                }
                if (x instanceof Keyword) {
                    return ((Keyword) x).name;
                }
                if (x instanceof Symbol) {
                    return ((Symbol) x).name;
                }
                throw new ScriptError("name expects a string, keyword or symbol, got: " + Printer.print(x));
            }
        });
    }
}
