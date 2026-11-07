package secondloop;

/** A function implemented in Java. */
public abstract class Builtin implements Fn {
    private final String name;

    protected Builtin(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    protected void checkArity(Object[] args, int min, int max) {
        if (args.length < min || (max >= 0 && args.length > max)) {
            throw new ScriptError("Wrong number of args (" + args.length + ") passed to: " + name);
        }
    }
}
