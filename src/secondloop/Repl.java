package secondloop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * One read-eval-print session. Input is consumed line by line; a form may span
 * several lines, and the prompt only reappears once every form is complete.
 */
public final class Repl {
    public static final String PROMPT = "user=> ";

    private final Interp interp;
    private final PrintWriter out;
    private final StringBuilder pending = new StringBuilder();

    public Repl(Env globals, PrintWriter out) {
        this.interp = new Interp(globals, out);
        this.out = out;
    }

    public void run(BufferedReader in) throws IOException {
        prompt();
        String line;
        while ((line = in.readLine()) != null) {
            if (feed(line)) {
                prompt();
            }
        }
    }

    private void prompt() {
        out.print(PROMPT);
        out.flush();
    }

    /**
     * Adds one line of input and evaluates every complete form.
     * Returns false when still waiting for the rest of an unfinished form.
     */
    public boolean feed(String line) {
        pending.append(line).append('\n');
        List<Object> forms;
        try {
            forms = Reader.readAll(pending.toString());
        } catch (Incomplete e) {
            return false;
        } catch (SyntaxError e) {
            pending.setLength(0);
            report(e);
            return true;
        }
        pending.setLength(0);
        for (Object form : forms) {
            try {
                out.println(Printer.print(interp.evalTopLevel(form)));
            } catch (RuntimeException | StackOverflowError e) {
                report(e);
                break;
            }
        }
        out.flush();
        return true;
    }

    private void report(Throwable e) {
        out.println(describe(e));
        out.flush();
    }

    /** An error as the REPL shows it, e.g. "Execution error: Divide by zero". */
    public static String describe(Throwable e) {
        if (e instanceof SyntaxError) {
            return "Syntax error: " + e.getMessage();
        }
        if (e instanceof ScriptError) {
            return "Execution error: " + e.getMessage();
        }
        if (e.getMessage() != null) {
            return "Execution error (" + e.getClass().getSimpleName() + "): " + e.getMessage();
        }
        return "Execution error (" + e.getClass().getSimpleName() + ")";
    }
}
