package secondloop;

/** A runtime error, reported by the REPL as "Execution error". */
public class ScriptError extends RuntimeException {
    public ScriptError(String message) {
        super(message);
    }
}
