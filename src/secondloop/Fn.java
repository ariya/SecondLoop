package secondloop;

/** Anything that can be called: a builtin or a function written in the language. */
public interface Fn {
    String name();

    /** False for a function with no name of its own, such as (fn [] ...). */
    boolean named();

    Object invoke(Interp interp, Object[] args);
}
