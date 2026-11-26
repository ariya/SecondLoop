package secondloop;

import java.io.PrintWriter;

/** The Clojure-like language: its reader, evaluator, and the Prelude written in it. */
public final class Clojure implements Language {
    @Override
    public Env createGlobals() {
        Env globals = Builtins.createGlobals();
        Prelude.load(globals);
        return globals;
    }

    @Override
    public Repl newRepl(Env globals, PrintWriter out) {
        return new ClojureRepl(globals, out);
    }
}
