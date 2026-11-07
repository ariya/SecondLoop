package secondloop;

/**
 * Number functions beyond arithmetic: mod, rem, quot, abs, min, max and the
 * number predicates, plus java.lang.Math under Math/ names, e.g. (Math/sin x)
 * and Math/PI.
 */
public final class MathBuiltins {
    private static final String[] UNARY = {
        "abs", "zero?", "pos?", "neg?", "even?", "odd?",
        "Math/abs", "Math/sin", "Math/cos", "Math/tan", "Math/asin", "Math/acos", "Math/atan",
        "Math/sqrt", "Math/cbrt", "Math/exp", "Math/log", "Math/log10",
        "Math/floor", "Math/ceil", "Math/round", "Math/signum",
        "Math/toRadians", "Math/toDegrees",
    };

    private static final String[] BINARY = {
        "mod", "rem", "quot", "Math/pow", "Math/atan2", "Math/hypot", "Math/min", "Math/max",
    };

    private MathBuiltins() {
    }

    /** A function of one number. */
    static final class Unary extends Builtin {
        Unary(String name) {
            super(name);
        }

        @Override
        public Object invoke(Interp interp, Object[] args) {
            checkArity(args, 1, 1);
            double x = Builtins.toNumber(args[0], name());
            switch (name()) {
                case "abs":
                case "Math/abs": return Math.abs(x);
                case "zero?": return x == 0;
                case "pos?": return x > 0;
                case "neg?": return x < 0;
                case "even?": return toInteger(x, args[0]) % 2 == 0;
                case "odd?": return toInteger(x, args[0]) % 2 != 0;
                case "Math/sin": return Math.sin(x);
                case "Math/cos": return Math.cos(x);
                case "Math/tan": return Math.tan(x);
                case "Math/asin": return Math.asin(x);
                case "Math/acos": return Math.acos(x);
                case "Math/atan": return Math.atan(x);
                case "Math/sqrt": return Math.sqrt(x);
                case "Math/cbrt": return Math.cbrt(x);
                case "Math/exp": return Math.exp(x);
                case "Math/log": return Math.log(x);
                case "Math/log10": return Math.log10(x);
                case "Math/floor": return Math.floor(x);
                case "Math/ceil": return Math.ceil(x);
                case "Math/round": return (double) Math.round(x);
                case "Math/signum": return Math.signum(x);
                case "Math/toRadians": return Math.toRadians(x);
                case "Math/toDegrees": return Math.toDegrees(x);
                default: throw new IllegalStateException("No implementation for " + name());
            }
        }
    }

    /** A function of two numbers. */
    static final class Binary extends Builtin {
        Binary(String name) {
            super(name);
        }

        @Override
        public Object invoke(Interp interp, Object[] args) {
            checkArity(args, 2, 2);
            double a = Builtins.toNumber(args[0], name());
            double b = Builtins.toNumber(args[1], name());
            switch (name()) {
                case "mod": {
                    // Floored: the result has the sign of b, so (mod -1 360) is 359.
                    double m = a % checkDivisor(b);
                    return m != 0 && (m < 0) != (b < 0) ? m + b : m;
                }
                case "rem":
                    // Truncated: the result has the sign of a, so (rem -1 360) is -1.
                    return a % checkDivisor(b);
                case "quot": {
                    double q = a / checkDivisor(b);
                    return q < 0 ? Math.ceil(q) : Math.floor(q);
                }
                case "Math/pow": return Math.pow(a, b);
                case "Math/atan2": return Math.atan2(a, b);
                case "Math/hypot": return Math.hypot(a, b);
                case "Math/min": return Math.min(a, b);
                case "Math/max": return Math.max(a, b);
                default: throw new IllegalStateException("No implementation for " + name());
            }
        }
    }

    static double checkDivisor(double b) {
        if (b == 0) {
            throw new ScriptError("Divide by zero");
        }
        return b;
    }

    static double toInteger(double x, Object original) {
        if (x != Math.floor(x) || Double.isInfinite(x)) {
            throw new ScriptError("Argument must be an integer: " + Printer.print(original));
        }
        return x;
    }

    public static void install(Env env) {
        for (String name : UNARY) {
            Builtins.define(env, new Unary(name));
        }
        for (String name : BINARY) {
            Builtins.define(env, new Binary(name));
        }
        Builtins.define(env, new Builtin("min") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, -1);
                double result = Builtins.toNumber(args[0], "min");
                for (int i = 1; i < args.length; i++) {
                    result = Math.min(result, Builtins.toNumber(args[i], "min"));
                }
                return result;
            }
        });
        Builtins.define(env, new Builtin("max") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 1, -1);
                double result = Builtins.toNumber(args[0], "max");
                for (int i = 1; i < args.length; i++) {
                    result = Math.max(result, Builtins.toNumber(args[i], "max"));
                }
                return result;
            }
        });
        env.define(Symbol.of("Math/PI"), Math.PI);
        env.define(Symbol.of("Math/E"), Math.E);
    }
}
