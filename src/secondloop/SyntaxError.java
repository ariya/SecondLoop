package secondloop;

/** A malformed form or an unknown symbol, reported by the REPL as "Syntax error". */
public class SyntaxError extends ScriptError {
    public SyntaxError(String message) {
        super(message);
    }
}
