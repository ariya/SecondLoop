package secondloop;

/**
 * The value of (recur ...): the new bindings for the enclosing loop or fn.
 * RecurCheck guarantees it only travels through tail positions, so it is
 * always consumed by that loop or fn and never seen by other code.
 */
public final class Recur {
    public final Object[] args;

    public Recur(Object[] args) {
        this.args = args;
    }
}
