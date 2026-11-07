package secondloop;

/** Anything that can be called: a builtin or a closure created by fn/defn. */
public interface Fn {
    String name();

    Object invoke(Interp interp, Object[] args);
}
