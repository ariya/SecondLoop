package secondloop;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Rejects recur outside a tail position before a top-level form is evaluated.
 * A tail position is one whose value becomes the value of the enclosing loop
 * or fn; only there can recur start the next round.
 */
public final class RecurCheck {
    private RecurCheck() {
    }

    public static void check(Object form) {
        visit(form, false);
    }

    private static void visit(Object form, boolean tail) {
        if (form instanceof FormList) {
            visitList((FormList) form, tail);
        } else if (form instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) form).entrySet()) {
                visit(e.getKey(), false);
                visit(e.getValue(), false);
            }
        } else if (form instanceof Collection) {
            for (Object item : (Collection<?>) form) {
                visit(item, false);
            }
        }
    }

    private static void visitList(FormList list, boolean tail) {
        if (list.isEmpty()) {
            return;
        }
        String head = list.get(0) instanceof Symbol ? ((Symbol) list.get(0)).name : "";
        switch (head) {
            case "quote":
                return;
            case "recur":
                if (!tail) {
                    throw new SyntaxError("Can only recur from tail position");
                }
                visitAll(list, 1, false);
                return;
            case "if":
            case "if-not":
                visitAll(list, 1, false, 2);
                visitAll(list, 2, tail);
                return;
            case "do":
            case "and":
            case "or":
                visitBody(list, 1, tail);
                return;
            case "when":
            case "when-not":
                visitAll(list, 1, false, 2);
                visitBody(list, 2, tail);
                return;
            case "if-let":
                visitBindings(list);
                visitAll(list, 2, tail);
                return;
            case "when-let":
                visitBindings(list);
                visitBody(list, 2, tail);
                return;
            case "case":
                visitCase(list, tail);
                return;
            case "->":
            case "->>":
                visit(Interp.thread(list), tail);
                return;
            case "cond":
                for (int i = 1; i < list.size(); i++) {
                    visit(list.get(i), tail && i % 2 == 0);
                }
                return;
            case "let":
                visitBindings(list);
                visitBody(list, 2, tail);
                return;
            case "loop":
                visitBindings(list);
                visitBody(list, 2, true);
                return;
            case "fn":
            case "defn":
                visitFnBody(list);
                return;
            default:
                // Function calls, def, while, dotimes, doseq: nothing is in tail position.
                visitAll(list, 0, false);
        }
    }

    private static void visitAll(List<Object> forms, int start, boolean tail) {
        visitAll(forms, start, tail, forms.size());
    }

    private static void visitAll(List<Object> forms, int start, boolean tail, int end) {
        for (int i = start; i < Math.min(end, forms.size()); i++) {
            visit(forms.get(i), tail);
        }
    }

    /** Only the last form can be in tail position, and only if the body is. */
    private static void visitBody(List<Object> forms, int start, boolean tail) {
        for (int i = start; i < forms.size(); i++) {
            visit(forms.get(i), tail && i == forms.size() - 1);
        }
    }

    private static void visitBindings(FormList list) {
        if (list.size() > 1 && list.get(1) instanceof FormVector) {
            FormVector bindings = (FormVector) list.get(1);
            for (int i = 1; i < bindings.size(); i += 2) {
                visit(bindings.get(i), false);
            }
        }
    }

    /** The tests of (case expr test result ... default?) are constants; the results and default are in tail position. */
    private static void visitCase(FormList list, boolean tail) {
        visitAll(list, 1, false, 2);
        for (int i = 3; i < list.size(); i += 2) {
            visit(list.get(i), tail);
        }
        if (list.size() > 2 && list.size() % 2 == 1) {
            visit(list.get(list.size() - 1), tail);
        }
    }

    /**
     * The body of (fn name? [params] body*) or (defn name doc? [params] body*) is a new recur target,
     * and so is each body of (fn name? ([params] body*)+).
     */
    private static void visitFnBody(FormList list) {
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i) instanceof FormVector) {
                visitBody(list, i + 1, true);
                return;
            }
            if (list.get(i) instanceof FormList) {
                FormList arity = (FormList) list.get(i);
                if (!arity.isEmpty() && arity.get(0) instanceof FormVector) {
                    visitBody(arity, 1, true);
                }
            }
        }
    }
}
