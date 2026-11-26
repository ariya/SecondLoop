package secondloop;

import java.io.PrintWriter;

/**
 * A language for the REPL. The servers, builtins, futures and hardware
 * modules are shared, so a second language only needs its own front end.
 */
public interface Language {
    /** A global environment with every builtin and the helpers written in the language. */
    Env createGlobals();

    /** A session for one connection or notebook tab. */
    Repl newRepl(Env globals, PrintWriter out);
}
