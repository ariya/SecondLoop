package secondloop;

/** True when the single argument is an instance of the given class. */
final class TypePredicate extends Builtin {
    private final Class<?> type;

    TypePredicate(String name, Class<?> type) {
        super(name);
        this.type = type;
    }

    @Override
    public Object invoke(Interp interp, Object[] args) {
        checkArity(args, 1, 1);
        return type.isInstance(args[0]);
    }
}
