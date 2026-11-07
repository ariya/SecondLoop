package secondloop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A function created by fn or defn, capturing the environment it was defined
 * in. It has one or more arities, e.g. (defn drive ([p] (drive p p)) ([l r] ...)).
 */
public final class Closure implements Fn {
    /** One parameter vector and its body. */
    public static final class Arity {
        final FormVector params;
        final int required;
        final boolean variadic;
        final List<Object> body;

        /** params is bound to the arguments like a sequential destructuring pattern. */
        public Arity(FormVector params, List<Object> body) {
            int ampersand = params.indexOf(Destructure.AMPERSAND);
            this.params = params;
            this.variadic = ampersand >= 0;
            this.required = variadic ? ampersand : params.size();
            this.body = body;
        }
    }

    private final Symbol name;
    private final String doc;
    private final List<Arity> arities;
    private final Env env;

    public Closure(Symbol name, String doc, List<Arity> arities, Env env) {
        this.name = name;
        this.doc = doc;
        this.arities = arities;
        this.env = env;
    }

    @Override
    public String name() {
        return name != null ? name.name : "fn";
    }

    /** The doc string given to defn, or null. */
    public String doc() {
        return doc;
    }

    /** The parameter vectors, for doc. */
    public List<FormVector> arglists() {
        List<FormVector> result = new ArrayList<FormVector>();
        for (Arity arity : arities) {
            result.add(arity.params);
        }
        return result;
    }

    @Override
    public Object invoke(Interp interp, Object[] args) {
        Arity arity = select(args.length);
        List<Object> argList = Arrays.asList(args);
        while (true) {
            Env local = new Env(env);
            if (name != null) {
                local.define(name, this);
            }
            Destructure.bind(interp, arity.params, new FormList(argList), local);
            Object result = interp.evalBody(arity.body, 0, local);
            if (!(result instanceof Recur)) {
                return result;
            }
            Interp.checkStop();
            argList = recurArgs(arity, ((Recur) result).args);
        }
    }

    /** A fixed arity that matches exactly wins over the variadic one. */
    private Arity select(int count) {
        Arity variadic = null;
        for (Arity arity : arities) {
            if (arity.variadic) {
                variadic = arity;
            } else if (arity.required == count) {
                return arity;
            }
        }
        if (variadic != null && count >= variadic.required) {
            return variadic;
        }
        throw new ScriptError("Wrong number of args (" + count + ") passed to: " + name());
    }

    /** recur passes one value per parameter; for (fn [a & more]) the last value is the whole rest list. */
    private static List<Object> recurArgs(Arity arity, Object[] values) {
        int expected = arity.required + (arity.variadic ? 1 : 0);
        if (values.length != expected) {
            throw new ScriptError("Mismatched argument count to recur, expected: " + expected + " args, got: " + values.length);
        }
        List<Object> result = new ArrayList<Object>(Arrays.asList(values).subList(0, arity.required));
        if (arity.variadic) {
            result.addAll(Coll.items(values[arity.required], "recur"));
        }
        return result;
    }
}
