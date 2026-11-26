package secondloop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * One read-eval-print session, for an nc connection or a notebook tab. Each
 * language decides when its input is complete and how results print.
 */
public abstract class Repl {
    protected final PrintWriter out;

    protected Repl(PrintWriter out) {
        this.out = out;
    }

    public abstract String prompt();

    /**
     * Adds one line of input and evaluates everything that is complete.
     * Returns false when still waiting for the rest of an unfinished input.
     */
    public abstract boolean feed(String line);

    public void run(BufferedReader in) throws IOException {
        showPrompt();
        String line;
        while ((line = in.readLine()) != null) {
            if (feed(line)) {
                showPrompt();
            }
        }
    }

    private void showPrompt() {
        out.print(prompt());
        out.flush();
    }
}
