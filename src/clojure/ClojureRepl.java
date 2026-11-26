package secondloop;

import java.io.PrintWriter;
import java.util.List;

/**
 * The Clojure REPL. A form may span several lines, and the prompt only
 * reappears once every form is complete.
 */
public final class ClojureRepl extends Repl {
    public static final String PROMPT = "user=> ";

    private final ClojureInterp interp;
    private final StringBuilder pending = new StringBuilder();

    public ClojureRepl(Env globals, PrintWriter out) {
        super(out);
        this.interp = new ClojureInterp(globals, out);
    }

    @Override
    public String prompt() {
        return PROMPT;
    }

    @Override
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
        out.println(interp.describe(e));
        out.flush();
    }
}
