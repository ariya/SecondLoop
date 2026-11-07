package secondloop;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The evaluator. One instance per REPL connection; the global environment is
 * shared, while output (println) goes to that connection.
 */
public final class Interp {
    public final Env globals;
    public final PrintWriter out;

    public Interp(Env globals, PrintWriter out) {
        this.globals = globals;
        this.out = out;
    }

    public static boolean isTruthy(Object x) {
        return x != null && !Boolean.FALSE.equals(x);
    }

    public Object eval(Object form, Env env) {
        if (form instanceof Symbol) {
            return env.lookup((Symbol) form);
        }
        if (form instanceof FormVector) {
            FormVector result = new FormVector();
            for (Object item : (FormVector) form) {
                result.add(eval(item, env));
            }
            return result;
        }
        if (form instanceof FormMap) {
            FormMap result = new FormMap();
            for (Map.Entry<Object, Object> e : ((FormMap) form).entrySet()) {
                result.put(eval(e.getKey(), env), eval(e.getValue(), env));
            }
            return result;
        }
        if (form instanceof FormSet) {
            FormSet result = new FormSet();
            for (Object item : (FormSet) form) {
                result.add(eval(item, env));
            }
            return result;
        }
        if (!(form instanceof FormList)) {
            return form;
        }
        FormList list = (FormList) form;
        if (list.isEmpty()) {
            return list;
        }
        Object head = list.get(0);
        if (head instanceof Symbol) {
            switch (((Symbol) head).name) {
                case "quote":
                    checkForm(list, 1, 1);
                    return list.get(1);
                case "if":
                    return evalIf(list, env);
                case "do":
                    return evalBody(list, 1, env);
                case "def":
                    return evalDef(list, env);
                case "defonce":
                    return evalDefonce(list, env);
                case "let":
                    return evalLet(list, env);
                case "fn":
                    return evalFn(list, env);
                case "defn":
                    return evalDefn(list, env);
                case "cond":
                    return evalCond(list, env);
                case "loop":
                    return evalLoop(list, env);
                case "recur":
                    return evalRecur(list, env);
                case "while":
                    return evalWhile(list, env);
                case "dotimes":
                    return evalDotimes(list, env);
                case "doseq":
                    return evalDoseq(list, env);
                case "try":
                    return evalTry(list, env);
                case "time":
                    return evalTime(list, env);
                case "future":
                    return new Future(this, new ArrayList<Object>(list.subList(1, list.size())), env).start();
                case "when":
                    checkForm(list, 1, -1);
                    return isTruthy(eval(list.get(1), env)) ? evalBody(list, 2, env) : null;
                case "when-not":
                    checkForm(list, 1, -1);
                    return isTruthy(eval(list.get(1), env)) ? null : evalBody(list, 2, env);
                case "if-not":
                    checkForm(list, 2, 3);
                    if (!isTruthy(eval(list.get(1), env))) {
                        return eval(list.get(2), env);
                    }
                    return list.size() > 3 ? eval(list.get(3), env) : null;
                case "if-let":
                    checkForm(list, 2, 3);
                    return evalIfLet(list, env, false);
                case "when-let":
                    checkForm(list, 1, -1);
                    return evalIfLet(list, env, true);
                case "case":
                    return evalCase(list, env);
                case "->":
                case "->>":
                    return eval(thread(list), env);
                case "doc":
                    return evalDoc(list);
                case "and":
                    return evalAndOr(list, env, true);
                case "or":
                    return evalAndOr(list, env, false);
                default:
                    break;
            }
        }
        Object f = eval(head, env);
        Object[] args = new Object[list.size() - 1];
        for (int i = 1; i < list.size(); i++) {
            args[i - 1] = eval(list.get(i), env);
        }
        return apply(f, args);
    }

    /** Evaluates a form typed at the REPL (or loaded at startup). */
    public Object evalTopLevel(Object form) {
        RecurCheck.check(form);
        return eval(form, globals);
    }

    /**
     * Called on every round of every loop. When the OpMode stops, the server
     * interrupts each session thread, so a running loop ends instead of
     * driving motors after STOP.
     */
    public static void checkStop() {
        if (Thread.currentThread().isInterrupted()) {
            throw new Interrupted();
        }
    }

    public Object apply(Object f, Object[] args) {
        if (f instanceof Fn) {
            return ((Fn) f).invoke(this, args);
        }
        if (f instanceof Keyword || f instanceof FormMap || f instanceof FormSet || f instanceof FormVector) {
            return Coll.invoke(f, args);
        }
        throw new ScriptError(Printer.print(f) + " is not a function");
    }

    /** Evaluates forms[start..] in order and returns the last value (nil if none). */
    public Object evalBody(List<Object> forms, int start, Env env) {
        Object result = null;
        for (int i = start; i < forms.size(); i++) {
            result = eval(forms.get(i), env);
        }
        return result;
    }

    /** Checks the number of arguments after the special form's name; max < 0 means no limit. */
    private static void checkForm(FormList list, int min, int max) {
        int count = list.size() - 1;
        String name = ((Symbol) list.get(0)).name;
        if (count < min) {
            throw new SyntaxError("Too few arguments to " + name);
        }
        if (max >= 0 && count > max) {
            throw new SyntaxError("Too many arguments to " + name);
        }
    }

    private Object evalIf(FormList list, Env env) {
        checkForm(list, 2, 3);
        if (isTruthy(eval(list.get(1), env))) {
            return eval(list.get(2), env);
        }
        return list.size() > 3 ? eval(list.get(3), env) : null;
    }

    /** (cond test expr ...) evaluates the expr of the first truthy test; :else is just a truthy test. */
    private Object evalCond(FormList list, Env env) {
        if (list.size() % 2 == 0) {
            throw new SyntaxError("cond requires an even number of forms");
        }
        for (int i = 1; i < list.size(); i += 2) {
            if (isTruthy(eval(list.get(i), env))) {
                return eval(list.get(i + 1), env);
            }
        }
        return null;
    }

    private Object evalDef(FormList list, Env env) {
        checkForm(list, 2, 2);
        if (!(list.get(1) instanceof Symbol)) {
            throw new SyntaxError("First argument to def must be a Symbol");
        }
        Symbol name = (Symbol) list.get(1);
        globals.define(name, eval(list.get(2), env));
        return new Var(name);
    }

    /**
     * (defonce name expr) is def, except that when name is already defined it
     * does nothing (and expr is not evaluated), returning nil. Re-sending a
     * file of code then keeps atoms that were tuned live.
     */
    private Object evalDefonce(FormList list, Env env) {
        checkForm(list, 2, 2);
        if (!(list.get(1) instanceof Symbol)) {
            throw new SyntaxError("First argument to defonce must be a Symbol");
        }
        Symbol name = (Symbol) list.get(1);
        if (globals.isDefined(name)) {
            return null;
        }
        return globals.defineIfAbsent(name, eval(list.get(2), env)) ? new Var(name) : null;
    }

    private Object evalLet(FormList list, Env env) {
        checkForm(list, 1, -1);
        FormVector bindings = bindingVector(list);
        Env local = new Env(env);
        for (int i = 0; i < bindings.size(); i += 2) {
            Destructure.bind(this, bindings.get(i), eval(bindings.get(i + 1), local), local);
        }
        return evalBody(list, 2, local);
    }

    /** The binding vector of let, loop, dotimes or doseq, after checking its shape and patterns. */
    private static FormVector bindingVector(FormList list) {
        String name = ((Symbol) list.get(0)).name;
        if (!(list.get(1) instanceof FormVector)) {
            throw new SyntaxError(name + " requires a vector for its binding");
        }
        FormVector bindings = (FormVector) list.get(1);
        if (bindings.size() % 2 != 0) {
            throw new SyntaxError(name + " requires an even number of forms in binding vector");
        }
        for (int i = 0; i < bindings.size(); i += 2) {
            Destructure.check(bindings.get(i));
        }
        return bindings;
    }

    /** (loop [pattern init ...] body*); (recur new-values...) in the body starts the next round. */
    private Object evalLoop(FormList list, Env env) {
        checkForm(list, 1, -1);
        FormVector bindings = bindingVector(list);
        Env local = new Env(env);
        for (int i = 0; i < bindings.size(); i += 2) {
            Destructure.bind(this, bindings.get(i), eval(bindings.get(i + 1), local), local);
        }
        while (true) {
            Object result = evalBody(list, 2, local);
            if (!(result instanceof Recur)) {
                return result;
            }
            checkStop();
            Object[] args = ((Recur) result).args;
            int expected = bindings.size() / 2;
            if (args.length != expected) {
                throw new ScriptError("Mismatched argument count to recur, expected: " + expected + " args, got: " + args.length);
            }
            local = new Env(env);
            for (int i = 0; i < expected; i++) {
                Destructure.bind(this, bindings.get(2 * i), args[i], local);
            }
        }
    }

    private Object evalRecur(FormList list, Env env) {
        Object[] args = new Object[list.size() - 1];
        for (int i = 1; i < list.size(); i++) {
            args[i - 1] = eval(list.get(i), env);
        }
        return new Recur(args);
    }

    /** (while test body*) repeats body while test is truthy. */
    private Object evalWhile(FormList list, Env env) {
        checkForm(list, 1, -1);
        while (isTruthy(eval(list.get(1), env))) {
            checkStop();
            evalBody(list, 2, env);
        }
        return null;
    }

    /** (dotimes [i n] body*) runs body with i = 0.0, 1.0, ... n-1. */
    private Object evalDotimes(FormList list, Env env) {
        checkForm(list, 1, -1);
        FormVector bindings = bindingVector(list);
        if (bindings.size() != 2 || !(bindings.get(0) instanceof Symbol)) {
            throw new SyntaxError("dotimes requires exactly one symbol and a count, e.g. [i 3]");
        }
        double n = Builtins.toNumber(eval(bindings.get(1), env), "dotimes");
        for (double i = 0; i < n; i++) {
            checkStop();
            Env local = new Env(env);
            local.define((Symbol) bindings.get(0), i);
            evalBody(list, 2, local);
        }
        return null;
    }

    /** (doseq [x xs y ys] body*) runs body for every x, and within that for every y. */
    private Object evalDoseq(FormList list, Env env) {
        checkForm(list, 1, -1);
        if (list.get(1) instanceof FormVector) {
            FormVector raw = (FormVector) list.get(1);
            for (int i = 0; i < raw.size(); i += 2) {
                if (raw.get(i) instanceof Keyword) {
                    throw new SyntaxError("Unsupported doseq modifier: " + Printer.print(raw.get(i)));
                }
            }
        }
        FormVector bindings = bindingVector(list);
        doseq(list, bindings, 0, env);
        return null;
    }

    private void doseq(FormList list, FormVector bindings, int index, Env env) {
        if (index == bindings.size()) {
            evalBody(list, 2, env);
            return;
        }
        for (Object item : Coll.items(eval(bindings.get(index + 1), env), "doseq")) {
            checkStop();
            Env local = new Env(env);
            Destructure.bind(this, bindings.get(index), item, local);
            doseq(list, bindings, index + 2, local);
        }
    }

    /**
     * (if-let [pattern expr] then else?) and (when-let [pattern expr] body*):
     * binds and runs then/body only when expr is truthy.
     */
    private Object evalIfLet(FormList list, Env env, boolean isWhen) {
        String name = ((Symbol) list.get(0)).name;
        FormVector bindings = bindingVector(list);
        if (bindings.size() != 2) {
            throw new SyntaxError(name + " requires exactly 2 forms in binding vector");
        }
        Object value = eval(bindings.get(1), env);
        if (isTruthy(value)) {
            Env local = new Env(env);
            Destructure.bind(this, bindings.get(0), value, local);
            return isWhen ? evalBody(list, 2, local) : eval(list.get(2), local);
        }
        return !isWhen && list.size() > 3 ? eval(list.get(3), env) : null;
    }

    /**
     * (case expr test result ... default?): the tests are constants, not evaluated;
     * a list of constants, e.g. (:left :right), matches any of them.
     */
    private Object evalCase(FormList list, Env env) {
        checkForm(list, 1, -1);
        List<Object> seen = new ArrayList<Object>();
        for (int i = 2; i + 1 < list.size(); i += 2) {
            for (Object constant : caseConstants(list.get(i))) {
                for (Object other : seen) {
                    if (Builtins.isEqual(constant, other)) {
                        throw new SyntaxError("Duplicate case test constant: " + Printer.print(constant));
                    }
                }
                seen.add(constant);
            }
        }
        Object value = eval(list.get(1), env);
        for (int i = 2; i + 1 < list.size(); i += 2) {
            for (Object constant : caseConstants(list.get(i))) {
                if (Builtins.isEqual(value, constant)) {
                    return eval(list.get(i + 1), env);
                }
            }
        }
        if (list.size() % 2 == 1) {
            return eval(list.get(list.size() - 1), env);
        }
        throw new ScriptError("No matching clause: " + Printer.print(value));
    }

    private static List<Object> caseConstants(Object test) {
        if (test instanceof FormList) {
            return (FormList) test;
        }
        List<Object> result = new ArrayList<Object>();
        result.add(test);
        return result;
    }

    /**
     * Rewrites (-> x (f a) g) as (g (f x a)), and (->> x (f a) g) as (g (f a x)).
     * RecurCheck uses it too, so recur is checked in the rewritten form.
     */
    static Object thread(FormList list) {
        checkForm(list, 1, -1);
        boolean last = ((Symbol) list.get(0)).name.equals("->>");
        Object result = list.get(1);
        for (int i = 2; i < list.size(); i++) {
            FormList step = new FormList();
            if (list.get(i) instanceof FormList) {
                step.addAll((FormList) list.get(i));
            } else {
                step.add(list.get(i));
            }
            if (step.isEmpty()) {
                throw new SyntaxError(((Symbol) list.get(0)).name + " requires a function or a call at each step, got: ()");
            }
            if (last) {
                step.add(result);
            } else {
                step.add(1, result);
            }
            result = step;
        }
        return result;
    }

    /** (doc name) prints the parameter vectors and doc string of a function. */
    private Object evalDoc(FormList list) {
        checkForm(list, 1, 1);
        if (!(list.get(1) instanceof Symbol)) {
            throw new SyntaxError("doc requires a name, e.g. (doc clamp)");
        }
        Symbol name = (Symbol) list.get(1);
        if (!globals.isDefined(name)) {
            return null;
        }
        Object value = globals.lookup(name);
        out.println("-------------------------");
        out.println(name.name);
        if (value instanceof Closure) {
            Closure closure = (Closure) value;
            StringBuilder arglists = new StringBuilder("(");
            for (FormVector params : closure.arglists()) {
                if (arglists.length() > 1) {
                    arglists.append(' ');
                }
                arglists.append(Printer.print(params));
            }
            out.println(arglists.append(')'));
            if (closure.doc() != null) {
                out.println("  " + closure.doc());
            }
        }
        out.flush();
        return null;
    }

    /**
     * (try body* (catch Exception e handler*)* (finally cleanup*)?)
     * The catch classes are Exception (any error), ExceptionInfo (from ex-info)
     * and Throwable (also stack overflow). Interrupted is never caught.
     */
    private Object evalTry(FormList list, Env env) {
        int bodyEnd = list.size();
        List<FormList> catches = new ArrayList<FormList>();
        FormList cleanup = null;
        for (int i = 1; i < list.size(); i++) {
            String clause = clauseName(list.get(i));
            if (clause == null) {
                if (bodyEnd < list.size()) {
                    throw new SyntaxError("Only catch or finally clause can follow catch in try expression");
                }
                continue;
            }
            bodyEnd = Math.min(bodyEnd, i);
            FormList form = (FormList) list.get(i);
            if (clause.equals("finally")) {
                if (i != list.size() - 1) {
                    throw new SyntaxError("finally clause must be last in try expression");
                }
                cleanup = form;
            } else {
                if (form.size() < 3 || !(form.get(1) instanceof Symbol) || !(form.get(2) instanceof Symbol)) {
                    throw new SyntaxError("catch requires a class and a symbol, e.g. (catch Exception e ...)");
                }
                catchMatches(((Symbol) form.get(1)).name, null);
                catches.add(form);
            }
        }
        try {
            try {
                return evalBody(list.subList(0, bodyEnd), 1, env);
            } catch (Interrupted e) {
                throw e;
            } catch (RuntimeException | StackOverflowError e) {
                for (FormList handler : catches) {
                    if (catchMatches(((Symbol) handler.get(1)).name, e)) {
                        Env local = new Env(env);
                        local.define((Symbol) handler.get(2), e);
                        return evalBody(handler, 3, local);
                    }
                }
                throw e;
            }
        } finally {
            if (cleanup != null) {
                runCleanup(cleanup, env);
            }
        }
    }

    private static String clauseName(Object form) {
        if (form instanceof FormList && !((FormList) form).isEmpty() && ((FormList) form).get(0) instanceof Symbol) {
            String name = ((Symbol) ((FormList) form).get(0)).name;
            if (name.equals("catch") || name.equals("finally")) {
                return name;
            }
        }
        return null;
    }

    /** Whether a catch clause for className catches e; with e null, only checks the class name. */
    private static boolean catchMatches(String className, Throwable e) {
        switch (className) {
            case "Throwable":
            case "java.lang.Throwable":
                return true;
            case "Exception":
            case "java.lang.Exception":
            case "RuntimeException":
            case "java.lang.RuntimeException":
                return e == null || e instanceof RuntimeException;
            case "ExceptionInfo":
                return e == null || e instanceof ExInfo;
            default:
                throw new SyntaxError("Unable to resolve classname: " + className);
        }
    }

    /**
     * Runs a finally clause to the end even when the thread is being stopped,
     * so cleanup such as stopping the motors is not cut short. The stop
     * request is restored afterwards.
     */
    private void runCleanup(FormList cleanup, Env env) {
        boolean stopping = Thread.interrupted();
        try {
            evalBody(cleanup, 1, env);
        } finally {
            if (stopping) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** (time expr) evaluates expr, prints how long it took, and returns its value. */
    private Object evalTime(FormList list, Env env) {
        checkForm(list, 1, 1);
        long start = System.nanoTime();
        Object result = eval(list.get(1), env);
        double millis = (System.nanoTime() - start) / 1000000.0;
        out.println(Printer.print("Elapsed time: " + millis + " msecs"));
        out.flush();
        return result;
    }

    /** (fn name? [params*] body*) or (fn name? ([params*] body*)+) */
    private Object evalFn(FormList list, Env env) {
        checkForm(list, 1, -1);
        Symbol name = null;
        int paramsIndex = 1;
        if (list.get(1) instanceof Symbol) {
            name = (Symbol) list.get(1);
            paramsIndex = 2;
        }
        return makeClosure(list, paramsIndex, name, null, env);
    }

    /** (defn name doc-string? [params*] body*) or (defn name doc-string? ([params*] body*)+) */
    private Object evalDefn(FormList list, Env env) {
        checkForm(list, 2, -1);
        if (!(list.get(1) instanceof Symbol)) {
            throw new SyntaxError("First argument to defn must be a Symbol");
        }
        Symbol name = (Symbol) list.get(1);
        String doc = list.get(2) instanceof String ? (String) list.get(2) : null;
        int paramsIndex = doc != null ? 3 : 2;
        globals.define(name, makeClosure(list, paramsIndex, name, doc, env));
        return new Var(name);
    }

    private Closure makeClosure(FormList list, int paramsIndex, Symbol name, String doc, Env env) {
        List<Closure.Arity> arities = new ArrayList<Closure.Arity>();
        if (paramsIndex < list.size() && list.get(paramsIndex) instanceof FormVector) {
            arities.add(makeArity(list, paramsIndex));
        } else {
            for (int i = paramsIndex; i < list.size(); i++) {
                if (!(list.get(i) instanceof FormList)) {
                    throw new SyntaxError("Parameter declaration missing");
                }
                arities.add(makeArity((FormList) list.get(i), 0));
            }
        }
        if (arities.isEmpty()) {
            throw new SyntaxError("Parameter declaration missing");
        }
        checkArities(arities);
        return new Closure(name, doc, arities, env);
    }

    /** The parameter vector at forms[paramsIndex], with the body after it. */
    private static Closure.Arity makeArity(List<Object> forms, int paramsIndex) {
        if (paramsIndex >= forms.size() || !(forms.get(paramsIndex) instanceof FormVector)) {
            throw new SyntaxError("Parameter declaration missing");
        }
        FormVector params = (FormVector) forms.get(paramsIndex);
        Destructure.check(params);
        if (params.contains(Keyword.of("as"))) {
            throw new SyntaxError("Unsupported parameter: :as");
        }
        return new Closure.Arity(params, new ArrayList<Object>(forms.subList(paramsIndex + 1, forms.size())));
    }

    private static void checkArities(List<Closure.Arity> arities) {
        Closure.Arity variadic = null;
        List<Integer> fixed = new ArrayList<Integer>();
        for (Closure.Arity arity : arities) {
            if (arity.variadic) {
                if (variadic != null) {
                    throw new SyntaxError("Can't have more than 1 variadic overload");
                }
                variadic = arity;
            } else if (fixed.contains(arity.required)) {
                throw new SyntaxError("Can't have 2 overloads with same arity");
            } else {
                fixed.add(arity.required);
            }
        }
        if (variadic != null) {
            for (int count : fixed) {
                if (count > variadic.required) {
                    throw new SyntaxError("Can't have fixed arity function with more params than variadic function");
                }
            }
        }
    }

    private Object evalAndOr(FormList list, Env env, boolean isAnd) {
        Object result = isAnd ? Boolean.TRUE : null;
        for (int i = 1; i < list.size(); i++) {
            result = eval(list.get(i), env);
            if (isTruthy(result) != isAnd) {
                return result;
            }
        }
        return result;
    }
}
