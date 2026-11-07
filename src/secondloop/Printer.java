package secondloop;

import java.util.Collection;
import java.util.Map;

/** Turns values back into text. */
public final class Printer {
    private Printer() {
    }

    /** Readable form, as pr-str shows it: strings are quoted. */
    public static String print(Object x) {
        StringBuilder sb = new StringBuilder();
        write(sb, x, true);
        return sb.toString();
    }

    /** Human form, as println shows it: strings are not quoted, even inside collections. */
    public static String display(Object x) {
        StringBuilder sb = new StringBuilder();
        write(sb, x, false);
        return sb.toString();
    }

    /** The str rule: nil is empty, a string is itself, anything else is printed readably. */
    public static String str(Object x) {
        if (x == null) {
            return "";
        }
        return x instanceof String ? (String) x : print(x);
    }

    private static void write(StringBuilder sb, Object x, boolean readably) {
        if (x == null) {
            sb.append("nil");
        } else if (x instanceof String) {
            if (readably) {
                writeString(sb, (String) x);
            } else {
                sb.append((String) x);
            }
        } else if (x instanceof Double) {
            sb.append(formatNumber((Double) x));
        } else if (x instanceof FormList) {
            writeSequence(sb, (FormList) x, "(", ")", readably);
        } else if (x instanceof FormVector) {
            writeSequence(sb, (FormVector) x, "[", "]", readably);
        } else if (x instanceof FormSet) {
            writeSequence(sb, (FormSet) x, "#{", "}", readably);
        } else if (x instanceof FormMap) {
            writeMap(sb, (FormMap) x, readably);
        } else if (x instanceof Atom) {
            sb.append("#<Atom ");
            write(sb, ((Atom) x).deref(), readably);
            sb.append('>');
        } else if (x instanceof Future) {
            Future future = (Future) x;
            String status = future.status();
            sb.append("#<Future ");
            if (status != null) {
                sb.append(status);
            } else {
                write(sb, future.value(), readably);
            }
            sb.append('>');
        } else if (x instanceof Throwable) {
            Throwable e = (Throwable) x;
            sb.append("#error {:message ");
            writeString(sb, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            if (e instanceof ExInfo) {
                sb.append(", :data ");
                write(sb, ((ExInfo) e).data, readably);
            }
            sb.append('}');
        } else if (x instanceof Var) {
            sb.append("#'user/").append(((Var) x).symbol.name);
        } else if (x instanceof Fn) {
            sb.append("#function[").append(((Fn) x).name()).append(']');
        } else {
            sb.append(x);
        }
    }

    private static String formatNumber(double d) {
        if (Double.isNaN(d)) {
            return "##NaN";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "##Inf" : "##-Inf";
        }
        return Double.toString(d);
    }

    private static void writeSequence(StringBuilder sb, Collection<Object> items, String open, String close, boolean readably) {
        sb.append(open);
        boolean first = true;
        for (Object item : items) {
            if (!first) {
                sb.append(' ');
            }
            first = false;
            write(sb, item, readably);
        }
        sb.append(close);
    }

    private static void writeMap(StringBuilder sb, FormMap map, boolean readably) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<Object, Object> e : map.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            write(sb, e.getKey(), readably);
            sb.append(' ');
            write(sb, e.getValue(), readably);
        }
        sb.append('}');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                case '\r': sb.append("\\r"); break;
                default: sb.append(c);
            }
        }
        sb.append('"');
    }
}
