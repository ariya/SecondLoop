package secondloop;

/** The input ended in the middle of a form; the REPL waits for more lines. */
public final class Incomplete extends SyntaxError {
    public Incomplete() {
        super("EOF while reading");
    }
}
