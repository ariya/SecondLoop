package secondloop;

/** The result of def, printed as #'user/name. */
public final class Var {
    public final Symbol symbol;

    public Var(Symbol symbol) {
        this.symbol = symbol;
    }

    /** The Printer shows this, since it does not know about Var. */
    @Override
    public String toString() {
        return "#'user/" + symbol.name;
    }
}
