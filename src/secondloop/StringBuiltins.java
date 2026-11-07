package secondloop;

import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * String functions: format, pr-str, prn and subs, plus str/ helpers such as
 * str/join, str/split and str/replace.
 * There are no regexes: str/split and str/replace match plain text.
 */
public final class StringBuiltins {
    private StringBuiltins() {
    }

    static String toStr(Object x, String fnName) {
        if (x instanceof String) {
            return (String) x;
        }
        throw new ScriptError(fnName + " expects a string, got: " + Printer.print(x));
    }

    /** A function of one string. */
    static final class Unary extends Builtin {
        Unary(String name) {
            super(name);
        }

        @Override
        public Object invoke(Interp interp, Object[] args) {
            checkArity(args, 1, 1);
            if (name().equals("str/blank?") && args[0] == null) {
                return true;
            }
            String s = toStr(args[0], name());
            switch (name()) {
                case "str/upper-case": return s.toUpperCase(Locale.ROOT);
                case "str/lower-case": return s.toLowerCase(Locale.ROOT);
                case "str/trim": return s.trim();
                case "str/blank?": return s.trim().isEmpty();
                case "parse-double": return parseDouble(s);
                default: throw new IllegalStateException("No implementation for " + name());
            }
        }
    }

    /** A test of one string against another, e.g. (str/includes? s "x"). */
    static final class Search extends Builtin {
        Search(String name) {
            super(name);
        }

        @Override
        public Object invoke(Interp interp, Object[] args) {
            checkArity(args, 2, 2);
            String s = toStr(args[0], name());
            String part = toStr(args[1], name());
            switch (name()) {
                case "str/includes?": return s.contains(part);
                case "str/starts-with?": return s.startsWith(part);
                case "str/ends-with?": return s.endsWith(part);
                default: throw new IllegalStateException("No implementation for " + name());
            }
        }
    }

    static Object parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String joinReadably(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(Printer.print(args[i]));
        }
        return sb.toString();
    }

    public static void install(Env env) {
        for (String name : new String[] {"str/upper-case", "str/lower-case", "str/trim", "str/blank?", "parse-double"}) {
            Builtins.define(env, new Unary(name));
        }
        for (String name : new String[] {"str/includes?", "str/starts-with?", "str/ends-with?"}) {
            Builtins.define(env, new Search(name));
        }

        Builtins.define(env, new Builtin("format") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, -1);
                String pattern = toStr(args[0], "format");
                Object[] values = new Object[args.length - 1];
                for (int i = 1; i < args.length; i++) {
                    Object x = args[i];
                    boolean isJavaValue = x == null || x instanceof Double || x instanceof String || x instanceof Boolean;
                    values[i - 1] = isJavaValue ? x : Printer.print(x);
                }
                try {
                    return String.format(Locale.ROOT, pattern, values);
                } catch (IllegalFormatException e) {
                    throw new ScriptError("format: " + e.getMessage() + " (all numbers are doubles, so use %.0f instead of %d)");
                }
            }
        });
        Builtins.define(env, new Builtin("pr-str") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                return joinReadably(args);
            }
        });
        Builtins.define(env, new Builtin("prn") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                interp.out.println(joinReadably(args));
                interp.out.flush();
                return null;
            }
        });
        Builtins.define(env, new Builtin("subs") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 3);
                String s = toStr(args[0], "subs");
                int start = Coll.toIndex(args[1], "subs");
                int end = args.length == 3 ? Coll.toIndex(args[2], "subs") : s.length();
                if (start < 0 || end > s.length() || start > end) {
                    throw new ScriptError("String index out of range: begin " + start + ", end " + end + ", length " + s.length());
                }
                return s.substring(start, end);
            }
        });
        Builtins.define(env, new Builtin("str/join") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, 2);
                String separator = args.length == 2 ? Printer.str(args[0]) : "";
                List<Object> items = Coll.items(args[args.length - 1], "str/join");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) {
                        sb.append(separator);
                    }
                    sb.append(Printer.str(items.get(i)));
                }
                return sb.toString();
            }
        });
        Builtins.define(env, new Builtin("str/replace") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 3, 3);
                return toStr(args[0], "str/replace").replace(toStr(args[1], "str/replace"), toStr(args[2], "str/replace"));
            }
        });
        Builtins.define(env, new Builtin("str/split") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                String s = toStr(args[0], "str/split");
                String separator = toStr(args[1], "str/split");
                FormVector result = new FormVector();
                for (String part : s.split(Pattern.quote(separator))) {
                    result.add(part);
                }
                return result;
            }
        });
    }
}
