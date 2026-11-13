package secondloop;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Feeds lines into a REPL session and checks what it prints. Run: java -cp build/classes secondloop.ReplTest */
public final class ReplTest {
    private static int failures;
    private static int count;

    private final StringWriter buffer = new StringWriter();
    private final Repl repl = new Repl(Builtins.createGlobals(), new PrintWriter(buffer));

    /** Feeds each line and expects the output of the last one. */
    private void check(String expected, String... lines) {
        for (String line : lines) {
            buffer.getBuffer().setLength(0);
            repl.feed(line);
        }
        String actual = buffer.toString().trim();
        count++;
        if (!actual.equals(expected)) {
            failures++;
            System.out.println("FAIL: " + String.valueOf(lines.length == 1 ? lines[0] : java.util.Arrays.asList(lines)));
            System.out.println("  expected: " + expected);
            System.out.println("  actual:   " + actual);
        }
    }

    /** Stopping the OpMode cancels every running future, even one whose connection is gone. */
    private static void checkCancelAll() {
        ReplTest t = new ReplTest();
        t.repl.feed("(def bg (future (while true (time/sleep 0.001))))");
        Future.cancelAll();
        t.buffer.getBuffer().setLength(0);
        t.repl.feed("[(future-done? bg) (future-cancelled? bg)]");
        count++;
        if (!t.buffer.toString().trim().equals("[true true]")) {
            failures++;
            System.out.println("FAIL (cancelAll): " + t.buffer.toString().trim());
        }
    }

    /** restart() (START on the robot) brings time/elapsed back to zero. */
    private static void checkTimeRestart() {
        ReplTest t = new ReplTest();
        Env globals = Builtins.createGlobals();
        Repl repl = new Repl(globals, new PrintWriter(t.buffer));
        repl.feed("(time/sleep 0.1)");
        TimeModule.restart(globals);
        t.buffer.getBuffer().setLength(0);
        repl.feed("(< (time/elapsed) 0.05)");
        count++;
        if (!t.buffer.toString().trim().equals("true")) {
            failures++;
            System.out.println("FAIL (time restart): " + t.buffer.toString().trim());
        }
    }

    /** The Driver Hub status line, and which address it picks. */
    private static void checkStatus() {
        String[][] cases = {
            {"Ready at 192.168.43.1:9001 (0 clients)", ReplServer.status("192.168.43.1", 9001, 0)},
            {"Ready at 192.168.43.1:9001 (1 client)", ReplServer.status("192.168.43.1", 9001, 1)},
            {"Ready at 192.168.49.1:9001 (2 clients)", ReplServer.status("192.168.49.1", 9001, 2)},
            {"Ready on port 9001 (0 clients)", ReplServer.status(null, 9001, 0)},
            {"192.168.43.1", ReplServer.preferredAddress(java.util.Arrays.asList("10.0.0.5", "192.168.43.1"))},
            {"192.168.49.1", ReplServer.preferredAddress(java.util.Arrays.asList("172.17.0.1", "192.168.49.1"))},
            {"10.0.0.5", ReplServer.preferredAddress(java.util.Arrays.asList("10.0.0.5", "172.17.0.1"))},
            {"null", String.valueOf(ReplServer.preferredAddress(new java.util.ArrayList<String>()))},
            {"Second Loop version 1.1.9", Version.greeting("main")},
            {"Second Loop version 1.1.9 (commit ab123)", Version.greeting("ab123")},
        };
        for (String[] c : cases) {
            count++;
            if (!c[0].equals(c[1])) {
                failures++;
                System.out.println("FAIL (status): expected " + c[0] + ", got " + c[1]);
            }
        }
    }

    /** Two sessions sharing globals each run swap! 10000 times; no update may be lost. */
    private static void checkConcurrentSwaps() {
        Env globals = Builtins.createGlobals();
        final Repl setup = new Repl(globals, new PrintWriter(new StringWriter()));
        setup.feed("(def counter (atom 0))");
        Thread[] sessions = new Thread[2];
        for (int i = 0; i < sessions.length; i++) {
            final Repl session = new Repl(globals, new PrintWriter(new StringWriter()));
            sessions[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    session.feed("(dotimes [i 10000] (swap! counter inc))");
                }
            });
            sessions[i].start();
        }
        try {
            for (Thread session : sessions) {
                session.join();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        StringWriter result = new StringWriter();
        new Repl(globals, new PrintWriter(result)).feed("@counter");
        count++;
        if (!result.toString().trim().equals("20000.0")) {
            failures++;
            System.out.println("FAIL (concurrent swap!): expected 20000.0, got " + result.toString().trim());
        }
    }

    /** Starts src on its own thread, interrupts it as ReplServer.stop() does, and expects it to end. */
    private static void checkInterrupted(final String src) {
        final ReplTest t = new ReplTest();
        Thread session = new Thread(new Runnable() {
            @Override
            public void run() {
                t.repl.feed(src);
            }
        });
        session.start();
        try {
            Thread.sleep(100);
            session.interrupt();
            session.join(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        count++;
        String actual = t.buffer.toString().trim();
        if (session.isAlive() || !actual.equals("Execution error: Interrupted")) {
            failures++;
            System.out.println("FAIL (interrupt): " + src);
            System.out.println("  still running: " + session.isAlive() + ", output: " + actual);
        }
    }

    public static void main(String[] args) {
        ReplTest t = new ReplTest();

        // Arithmetic
        t.check("6.0", "(+ 1 2 3)");
        t.check("6.0", "(- 10 4)");
        t.check("24.0", "(* 2 3 4)");
        t.check("25.0", "(/ 100 4)");
        t.check("0.0", "(+)");
        t.check("1.0", "(*)");
        t.check("-5.0", "(- 5)");
        t.check("0.25", "(/ 4)");
        t.check("2.5", "(/ 10 2 2)");
        t.check("-1.5", "(+ -2 0.5)");
        t.check("4.0", "(inc 3)");
        t.check("Execution error: Divide by zero", "(/ 1 0)");
        t.check("Execution error: + expects numbers, got: \"a\"", "(+ 1 \"a\")");

        // Nested expressions
        t.check("11.0", "(+ (* 2 3) (/ 20 4))");
        t.check("21.0", "(* (+ 1 2) (- 10 3))");

        // Comparisons
        t.check("true", "(> 10 5)");
        t.check("true", "(< 3 8)");
        t.check("true", "(= 42 42)");
        t.check("true", "(= \"hello\" \"hello\")");
        t.check("false", "(= 1 2)");
        t.check("true", "(< 1 2 3)");
        t.check("false", "(< 1 3 2)");
        t.check("true", "(>= 3 3 1)");
        t.check("true", "(not= 1 2)");
        t.check("true", "(= [1 2] '(1 2))");
        t.check("false", "(not 0)");
        t.check("true", "(not nil)");

        // Variables
        t.check("#'user/x", "(def x 5)");
        t.check("#'user/y", "(def y 10)");
        t.check("15.0", "(+ x y)");
        t.check("Syntax error: Unable to resolve symbol: z in this context", "(+ x z)");

        // Conditionals
        t.check("\"adult\"", "(def age 18)", "(if (> age 17) \"adult\" \"child\")");
        t.check("\"child\"", "(if (> 3 17) \"adult\" \"child\")");
        t.check("nil", "(if false 1)");
        t.check("2.0", "(when true 1 2)");
        t.check("#'user/sign", "(defn sign [x] (cond (> x 0) :pos (< x 0) :neg :else :zero))");
        t.check(":pos", "(sign 5)");
        t.check(":neg", "(sign -5)");
        t.check(":zero", "(sign 0)");
        t.check("nil", "(cond false 1 nil 2)");
        t.check("nil", "(cond)");
        t.check("\"b\"", "(cond (= 1 2) \"a\"", "      (= 1 1) \"b\")");
        t.check("2.0", "(cond nil (/ 1 0) 1 2 :else (/ 1 0))");
        t.check("Syntax error: cond requires an even number of forms", "(cond true)");
        t.check("3.0", "(and 1 2 3)");
        t.check("false", "(and 1 false 3)");
        t.check("1.0", "(or nil 1)");

        // when-not, if-not, if-let, when-let, case
        t.check("2.0", "(when-not false 1 2)");
        t.check("nil", "(when-not true 1)");
        t.check(":a", "(if-not false :a :b)");
        t.check("nil", "(if-not true :a)");
        t.check("2.0", "(if-let [x (get {:a 1} :a)] (inc x) :none)");
        t.check(":none", "(if-let [x (get {:a 1} :b)] (inc x) :none)");
        t.check("nil", "(if-let [x false] :yes)");
        t.check("3.0", "(when-let [[a b] [1 2]] (+ a b))");
        t.check("nil", "(when-let [x nil] (/ 1 0))");
        t.check("Syntax error: if-let requires exactly 2 forms in binding vector", "(if-let [x 1 y 2] x)");
        t.check("Syntax error: when-let requires a vector for its binding", "(when-let x 1)");
        t.check("\"out\"", "(case :score :intake \"in\" :score \"out\" \"idle\")");
        t.check("\"idle\"", "(case :park :intake \"in\" :score \"out\" \"idle\")");
        t.check("1.0", "(case :hold (:intake :hold) 1 :score 2)");
        t.check("1.0", "(case \"a\" \"a\" 1 \"b\" 2)");
        t.check(":nil", "(case nil nil :nil 1 :one)");
        t.check(":vec", "(case [1 2] [1 2] :vec :no)");
        t.check(":two", "(case (+ 1 1) 1 :one 2 :two)");
        t.check("Execution error: No matching clause: 5.0", "(case 5 1 :one)");
        t.check("Syntax error: Duplicate case test constant: 1.0", "(case 1 1 :a (2 1) :b)");

        // let, fn, defn
        t.check("3.0", "(let [a 1 b (+ a 1)] (+ a b))");
        t.check("#'user/square", "(defn square \"Squares a number.\" [n] (* n n))");
        t.check("81.0", "(square 9)");
        t.check("#'user/fact", "(defn fact [n] (if (<= n 1) 1 (* n (fact (- n 1)))))");
        t.check("120.0", "(fact 5)");
        t.check("7.0", "((fn [a b] (+ a b)) 3 4)");
        t.check("(2.0 3.0)", "((fn [a & more] more) 1 2 3)");
        t.check("Execution error: Wrong number of args (2) passed to: square", "(square 1 2)");
        t.check("#function[square]", "square");
        t.check("#function[+]", "+");
        t.check("Execution error: 1.0 is not a function", "(1 2)");

        // Several arities
        t.check("#'user/drive", "(defn drive \"Drives.\" ([p] (drive p p)) ([l r] [l r]) ([l r & more] (count more)))");
        t.check("[1.0 1.0]", "(drive 1)");
        t.check("[1.0 2.0]", "(drive 1 2)");
        t.check("2.0", "(drive 1 2 3 4)");
        t.check("Execution error: Wrong number of args (0) passed to: drive", "(drive)");
        t.check(":one", "((fn ([] :none) ([a] :one)) 1)");
        t.check("#'user/cnt", "(defn cnt ([n] (cnt n 0)) ([n acc] (if (zero? n) acc (recur (dec n) (inc acc)))))");
        t.check("5.0", "(cnt 5)");
        t.check("Syntax error: Can't have 2 overloads with same arity", "(fn ([a] 1) ([b] 2))");
        t.check("Syntax error: Can't have more than 1 variadic overload", "(fn ([& a] 1) ([& b] 2))");
        t.check("Syntax error: Can't have fixed arity function with more params than variadic function", "(fn ([a b c] 1) ([a & b] 2))");
        t.check("Syntax error: Parameter declaration missing", "(fn ([a] 1) 2)");
        t.check("Syntax error: Parameter declaration missing", "(defn nothing \"doc\")");
        t.check("Syntax error: Can only recur from tail position", "(fn ([a] (inc (recur a))))");

        // doc
        t.check("-------------------------\ndrive\n([p] [l r] [l r & more])\n  Drives.\nnil", "(doc drive)");
        t.check("-------------------------\nclamp\n([x lo hi])\n  Limits x to the range lo..hi, e.g. (clamp power -1 1).\nnil", "(doc clamp)");
        t.check("-------------------------\nsquare\n([n])\n  Squares a number.\nnil", "(doc square)");
        t.check("-------------------------\n+\nnil", "(doc +)");
        t.check("nil", "(doc no-such-thing)");
        t.check("Syntax error: doc requires a name, e.g. (doc clamp)", "(doc 1)");

        // Threading
        t.check("0.0", "(-> 0.03 (deadband 0.05) (* 2))");
        t.check("1.0", "(-> -2 abs (clamp -1 1))");
        t.check("-3.0", "(-> 5 (- 2) -)");
        t.check("-3.0", "(->> 5 (- 2))");
        t.check("9.0", "(->> [1 2 3] (map inc) (reduce +))");
        t.check("1.0", "(-> {:a 1} :a)");
        t.check("5.0", "(-> 5)");
        t.check("Syntax error: -> requires a function or a call at each step, got: ()", "(-> 5 ())");
        t.check(":end", "(loop [x 3] (if (zero? x) :end (-> x dec (recur))))");
        t.check("Syntax error: Can only recur from tail position", "(loop [x 3] (-> x (recur) inc))");

        // Destructuring
        t.check("3.0", "(let [[a b] [1 2]] (+ a b))");
        t.check("[1.0 nil]", "(let [[a b] [1]] [a b])");
        t.check("[1.0 (2.0 3.0) [1.0 2.0 3.0]]", "(let [[a & more :as all] [1 2 3]] [a more all])");
        t.check("nil", "(let [[a & more] [1]] more)");
        t.check("[1.0 2.0 3.0]", "(let [[[x y] z] [[1 2] 3]] [x y z])");
        t.check("[1.0 2.0]", "(let [[a b] '(1 2)] [a b])");
        t.check("[nil nil]", "(let [[a b] nil] [a b])");
        t.check("Execution error: Cannot destructure as a sequence: {:a 1.0}", "(let [[a] {:a 1}] a)");
        t.check("[0.5 0.2]", "(let [{:keys [forward turn]} {:forward 0.5 :turn 0.2}] [forward turn])");
        t.check("[0.5 nil]", "(let [{:keys [forward strafe]} {:forward 0.5}] [forward strafe])");
        t.check("[1.0 0.5]", "(let [{:keys [angle speed] :or {speed 0.5}} {:angle 1}] [angle speed])");
        t.check("[1.0 nil]", "(let [{:keys [a] :or {a 9}} {:a 1 :b nil}] [a (:b {:a 1 :b nil})])");
        t.check("nil", "(let [{:keys [a] :or {a 9}} {:a nil}] a)");
        t.check("[1.0 {:a 1.0}]", "(let [{x :a :as m} {:a 1}] [x m])");
        t.check("30.0", "(let [{{:keys [angle]} :arm} {:arm {:angle 30}}] angle)");
        t.check("[1.0 2.0]", "(let [{[x y] :pos} {:pos [1 2]}] [x y])");
        t.check("1.0", "(let [{:keys [a]} nil] (if a a 1))");
        t.check("Execution error: Cannot destructure as a map: [1.0]", "(let [{:keys [a]} [1]] a)");
        t.check("3.0", "(let [{:keys [:a]} {:a 3}] a)");
        t.check("#'user/drive", "(defn drive [{:keys [forward strafe turn] :or {strafe 0}}] [forward strafe turn])");
        t.check("[0.5 0.0 0.1]", "(drive {:forward 0.5 :turn 0.1})");
        t.check("#'user/dist", "(defn dist [[x1 y1] [x2 y2]] (Math/hypot (- x2 x1) (- y2 y1)))");
        t.check("5.0", "(dist [0 0] [3 4])");
        t.check("Execution error: Wrong number of args (1) passed to: dist", "(dist [0 0])");
        t.check("#'user/arm", "(defn arm [angle & {:keys [speed] :or {speed 0.5}}] [angle speed])");
        t.check("[30.0 0.5]", "(arm 30)");
        t.check("[30.0 1.0]", "(arm 30 :speed 1)");
        t.check("(1.0 2.0)", "(map (fn [[k v]] v) {:a 1 :b 2})");
        t.check("{:a 2.0, :b 3.0}", "(into {} (map (fn [[k v]] [k (inc v)]) {:a 1 :b 2}))");
        t.check("Syntax error: Unsupported binding form: 1.0", "(let [1 2] 3)");
        t.check("Syntax error: Unsupported binding form: \"a\"", "(fn [\"a\"] 1)");
        t.check("Syntax error: & must be followed by exactly one binding form", "(fn [a &] 1)");
        t.check("Syntax error: Only :as can follow the & binding form", "(let [[a & b c] [1]] a)");
        t.check("Syntax error: :as must be followed by a symbol, at the end of the binding vector", "(let [[a :as] [1]] a)");
        t.check("Syntax error: :keys must be followed by a vector of symbols", "(let [{:keys a} {}] a)");
        t.check("Syntax error: Unsupported map binding option: :strs", "(let [{:strs [a]} {}] a)");
        t.check("Syntax error: Unsupported parameter: :as", "(fn [a :as all] a)");

        // loop/recur and other loops
        t.check("120.0", "(loop [i 5 acc 1] (if (> i 1) (recur (dec i) (* acc i)) acc))");
        t.check("100000.0", "(loop [i 0] (if (< i 100000) (recur (inc i)) i))");
        t.check("[3.0 2.0 1.0]", "(loop [[x & more] [1 2 3] out []] (if x (recur more (into [x] out)) out))");
        t.check(":done", "(loop [i 0] (cond (< i 3) (recur (inc i)) :else :done))");
        t.check("nil", "(loop [x 3] (when-not (zero? x) (recur (dec x))))");
        t.check(":done", "(loop [x 3] (if-not (pos? x) :done (recur (dec x))))");
        t.check(":done", "(loop [x 1] (if-let [y x] (recur nil) :done))");
        t.check("2.0", "(loop [x 1] (case x 1 (recur 2) x))");
        t.check("Syntax error: Can only recur from tail position", "(loop [x 1] (case (recur 2) 1 x))");
        t.check("3.0", "(loop [i 0] (when (< i 3) (recur (inc i))) )", "(loop [i 0] (if (< i 3) (recur (inc i)) i))");
        t.check("#'user/count-down", "(defn count-down [n] (if (> n 0) (recur (dec n)) :liftoff))");
        t.check(":liftoff", "(count-down 100000)");
        t.check("#'user/total", "(defn total [acc & xs] (if (seq xs) (recur (+ acc (first xs)) (rest xs)) acc))");
        t.check("10.0", "(total 0 1 2 3 4)");
        t.check("3.0", "((fn [n] (if (< n 3) (recur (inc n)) n)) 0)");
        t.check("Syntax error: Can only recur from tail position", "(loop [i 0] (+ 1 (recur i)))");
        t.check("Syntax error: Can only recur from tail position", "(recur 1)");
        t.check("Syntax error: Can only recur from tail position", "(defn bad [x] (recur x) x)");
        t.check("Syntax error: Can only recur from tail position", "(loop [i 0] (if (recur 1) 1 2))");
        t.check("Syntax error: Can only recur from tail position", "(fn [] (let [a (recur)] a))");
        t.check("Syntax error: Can only recur from tail position", "(loop [x 1] (while true (recur 2)))");
        t.check("Execution error: Mismatched argument count to recur, expected: 1 args, got: 2", "(loop [i 0] (recur 1 2))");
        t.check("Syntax error: loop requires an even number of forms in binding vector", "(loop [i] i)");
        t.check("(quote (recur))", "''(recur)");
        t.check("nil", "(while false (/ 1 0))");
        t.check("0.0\n1.0\n2.0\nnil", "(dotimes [i 3] (println i))");
        t.check("nil", "(dotimes [i 0] (/ 1 0))");
        t.check("Syntax error: dotimes requires exactly one symbol and a count, e.g. [i 3]", "(dotimes [[i] 3] i)");
        t.check("a 1.0\nb 2.0\nnil", "(doseq [[k v] {:a 1 :b 2}] (println (name k) v))");
        t.check("1.0 :x\n1.0 :y\n2.0 :x\n2.0 :y\nnil", "(doseq [n [1 2] k [:x :y]] (println n k))");
        t.check("Syntax error: Unsupported doseq modifier: :when", "(doseq [x [1] :when true] x)");
        t.check("nil", "(time/sleep 0.001)");

        // Atoms
        t.check("#'user/kp", "(def kp (atom 0.02))");
        t.check("0.02", "@kp");
        t.check("0.02", "(deref kp)");
        t.check("#<Atom 0.02>", "kp");
        t.check("0.03", "(reset! kp 0.03)");
        t.check("0.03", "@kp");
        t.check("#'user/pieces", "(def pieces (atom 0))");
        t.check("1.0", "(swap! pieces inc)");
        t.check("6.0", "(swap! pieces + 2 3)");
        t.check("#'user/state", "(def state (atom {:mode :fast :count 0}))");
        t.check("{:mode :slow, :count 0.0}", "(swap! state assoc :mode :slow)");
        t.check("{:mode :slow, :count 1.0}", "(swap! state update :count inc)");
        t.check(":slow", "(:mode @state)");
        t.check("#<Atom \"text\">", "(atom \"text\")");
        t.check("#<Atom [1.0 #<Atom nil>]>", "(atom [1 (atom nil)])");
        t.check("3.0", "(let [a (atom 0)] (dotimes [i 3] (swap! a inc)) @a)");
        t.check("false", "(= (atom 1) (atom 1))");
        t.check("Execution error: deref expects an atom, got: 1.0", "@1");
        t.check("Execution error: swap! expects an atom, got: nil", "(swap! nil inc)");
        t.check("(deref x)", "'@x");

        // Futures
        t.check("3.0", "@(future (+ 1 2))");
        t.check("#'user/f", "(def f (future (time/sleep 0.05) :result))");
        t.check("false", "(future-done? f)");
        t.check("#<Future pending>", "f");
        t.check(":result", "@f");
        t.check("true", "(future-done? f)");
        t.check("#<Future :result>", "f");
        t.check("false", "(future-cancel f)");
        t.check("true", "(future? f)");
        t.check(":timeout", "(deref (future (time/sleep 5)) 10 :timeout)");
        t.check("#'user/forever", "(def forever (future (loop [] (recur))))");
        t.check("true", "(future-cancel forever)");
        t.check("[true true]", "[(future-done? forever) (future-cancelled? forever)]");
        t.check("#<Future cancelled>", "forever");
        t.check("Execution error: Future was cancelled", "@forever");
        t.check("#'user/hits", "(def hits (atom 0))");
        t.check("#'user/worker", "(def worker (future (while true (swap! hits inc) (time/sleep 0.001))))");
        t.check("true", "(do (time/sleep 0.05) (pos? @hits))");
        t.check("true", "(future-cancel worker)");
        t.check("true", "(let [n @hits] (time/sleep 0.03) (= n @hits))");
        t.check("Execution error: future-cancel expects a future, got: 1.0", "(future-cancel 1)");
        t.check("Syntax error: Can only recur from tail position", "(future (recur))");
        t.check("Future failed. Execution error: Divide by zero\nExecution error: Divide by zero",
                "(let [f (future (/ 1 0))] (time/sleep 0.05) @f)");

        // Data and printing
        t.check("(1.0 \"a\" :k sym)", "'(1 \"a\" :k sym)");
        t.check("[2.0 \"b\"]", "[(+ 1 1) \"b\"]");
        t.check("\"x=5.0\"", "(str \"x=\" x)");
        t.check("hello world [a]\nnil", "(println \"hello\" \"world\" [\"a\"])");
        t.check("\"line\\nbreak\"", "\"line\\nbreak\"");

        // Reader: comments, several forms per line, forms spanning lines
        t.check("3.0", "(+ 1 2) ; comment");
        t.check("1.0\n2.0", "1 2");
        t.check("6.0", "(+ 1", "2", "3)");
        t.check("Syntax error: Unmatched delimiter: )", ")");
        t.check("Syntax error: Unsupported syntax: `", "`a");
        t.check("Syntax error: Map literal must contain an even number of forms", "{:a}");
        t.check("Syntax error: Duplicate key: :a", "{:a 1 :a 2}");
        t.check("Syntax error: Duplicate key: 1.0", "#{1 1}");
        t.check("Syntax error: Invalid number: 1x", "1x");
        t.check("", "");

        // Vectors
        t.check("#'user/v", "(def v [1 2 3])");
        t.check("[1.0 2.0 3.0 4.0]", "(conj v 4)");
        t.check("[1.0 2.0 3.0]", "v");
        t.check("2.0", "(v 1)");
        t.check("3.0", "(nth v 2)");
        t.check(":none", "(nth v 9 :none)");
        t.check("Execution error: Index out of bounds: 9", "(nth v 9)");
        t.check("[1.0 :x 3.0]", "(assoc v 1 :x)");
        t.check("[1.0 2.0 3.0 4.0]", "(assoc v 3 4)");
        t.check("3.0", "(peek v)");
        t.check("[1.0 2.0]", "(pop v)");
        t.check("3.0", "(count v)");
        t.check("true", "(contains? v 0)");
        t.check("[0.0 1.0 2.0]", "(vec (range 3))");

        // Lists
        t.check("(0.0 1.0 2.0)", "(conj '(1 2) 0)");
        t.check("(0.0 1.0 2.0)", "(cons 0 [1 2])");
        t.check("1.0", "(first '(1 2 3))");
        t.check("(2.0 3.0)", "(rest [1 2 3])");
        t.check("()", "(rest [])");
        t.check("nil", "(next [1])");
        t.check("3.0", "(last [1 2 3])");
        t.check("(2.0 3.0)", "(pop '(1 2 3))");
        t.check("()", "()");
        t.check("true", "(list? (list 1 2))");
        t.check("(3.0 2.0 1.0)", "(reverse [1 2 3])");
        t.check("(-1.0 2.0 3.0)", "(sort [3 -1 2])");
        t.check("(\"a\" \"b\")", "(sort #{\"b\" \"a\"})");
        t.check("(:a :b :c)", "(sort '(:c :a :b))");
        t.check("([:a 2.0] [:b 1.0])", "(sort {:b 1 :a 2})");
        t.check("([1.0] [1.0 2.0] [1.0 3.0])", "(sort [[1 3] [1] [1 2]])");
        t.check("()", "(sort [])");
        t.check("()", "(sort nil)");
        t.check("(3.0 2.0 1.0)", "(sort > [1 3 2])");
        t.check("(3.0 2.0 1.0)", "(sort (fn [a b] (- b a)) [1 3 2])");
        t.check("([1.0 :x] [1.0 :y] [0.0 :z])", "(sort (fn [a b] (> (first a) (first b))) [[1 :x] [0 :z] [1 :y]])");
        t.check("[3.0 1.0 2.0]", "(let [v [3 1 2]] (sort v) v)");
        t.check("Execution error: sort cannot compare \"a\" and 1.0", "(sort [1 \"a\"])");
        t.check("Execution error: sort cannot compare nil and nil", "(sort [nil nil])");
        t.check("Execution error: sort expects a collection, got: 1.0", "(sort 1)");
        t.check("Execution error: Wrong number of args (3) passed to: sort", "(sort < [1] [2])");
        t.check("Execution error: Divide by zero", "(sort (fn [a b] (/ 1 0)) [1 2])");
        t.check("(1.0 2.0 3.0 4.0)", "(concat [1 2] '(3) [4])");
        t.check("Execution error: contains? not supported on: (1.0)", "(contains? '(1) 0)");

        // Maps
        t.check("#'user/robot", "(def robot {:name \"Bolt\" :speed 0.5 :arm {:angle 30}})");
        t.check("{:name \"Bolt\", :speed 0.5, :arm {:angle 30.0}}", "robot");
        t.check("0.5", "(:speed robot)");
        t.check("\"Bolt\"", "(robot :name)");
        t.check("nil", "(:color robot)");
        t.check(":red", "(:color robot :red)");
        t.check(":red", "(get robot :color :red)");
        t.check("30.0", "(get-in robot [:arm :angle])");
        t.check("{:name \"Bolt\", :speed 1.0, :arm {:angle 30.0}}", "(assoc robot :speed 1)");
        t.check("0.5", "(:speed robot)");
        t.check("{:name \"Bolt\", :arm {:angle 30.0}}", "(dissoc robot :speed)");
        t.check("{:name \"Bolt\", :speed 0.5, :arm {:angle 45.0}}", "(update-in robot [:arm :angle] + 15)");
        t.check("{:a {:b 1.0}}", "(assoc-in {} [:a :b] 1)");
        t.check("{:n 2.0}", "(update {:n 1} :n inc)");
        t.check("{:a 1.0, :b 3.0, :c 4.0}", "(merge {:a 1 :b 2} {:b 3} nil {:c 4})");
        t.check("(:a :b)", "(keys {:a 1 :b 2})");
        t.check("(1.0 2.0)", "(vals {:a 1 :b 2})");
        t.check("([:a 1.0] [:b 2.0])", "(seq {:a 1 :b 2})");
        t.check("{:a 1.0, :b 2.0}", "(conj {:a 1} [:b 2])");
        t.check("{:a 1.0, :b 2.0}", "(into {} [[:a 1] [:b 2]])");
        t.check("{:a 1.0}", "(hash-map :a 1)");
        t.check("{[1.0 2.0] :v}", "{[1 2] :v}");
        t.check(":v", "(get {[1 2] :v} '(1 2))");
        t.check("{:x 3.0}", "(let [k :x] {k (+ 1 2)})");
        t.check("true", "(= {:a 1 :b 2} {:b 2 :a 1})");
        t.check("true", "(contains? {:a nil} :a)");
        t.check("true", "(empty? {})");

        // Sets
        t.check("#'user/s", "(def s #{1 2 3})");
        t.check("#{1.0 2.0 3.0 4.0}", "(conj s 4)");
        t.check("#{1.0 2.0 3.0}", "(conj s 1)");
        t.check("#{1.0 3.0}", "(disj s 2)");
        t.check("2.0", "(s 2)");
        t.check("nil", "(s 5)");
        t.check("true", "(contains? s 3)");
        t.check("true", "(= #{1 2} #{2 1})");
        t.check("#{1.0 2.0}", "(set [1 2 1])");
        t.check("#{:a}", "(hash-set :a :a)");
        t.check("true", "(set? #{})");

        // Sequence functions
        t.check("(2.0 4.0 6.0)", "(map (fn [x] (* x 2)) [1 2 3])");
        t.check("(11.0 22.0)", "(map + [1 2 3] [10 20])");
        t.check("(:a :b)", "(map :id [{:id :a} {:id :b}])");
        t.check("(3.0 4.0)", "(filter (fn [x] (> x 2)) [1 2 3 4])");
        t.check("10.0", "(reduce + [1 2 3 4])");
        t.check("0.0", "(reduce + [])");
        t.check("{:a 1.0}", "(reduce conj {} [[:a 1]])");
        t.check("6.0", "(apply + 1 [2 3])");
        t.check("(5.0 3.0 1.0)", "(range 5 0 -2)");
        t.check("[[1.0] [2.0]]", "(vec (map vector [1 2]))");
        t.check(":a", "(some #{:a} [:b :a])");
        t.check("nil", "(some even? [1 3])");
        t.check("true", "(some even? [1 2])");
        t.check("true", "(every? pos? [1 2])");
        t.check("false", "(every? pos? [1 -2])");
        t.check("true", "(every? pos? [])");
        t.check("10.0", "((partial + 1 2) 3 4)");
        t.check("1.0", "((partial clamp 1.5) -1 1)");
        t.check("3.0", "((partial +) 1 2)");
        t.check("3.0", "((comp inc inc) 1)");
        t.check("7.0", "((comp) 7)");
        t.check("\"3.0\"", "((comp str +) 1 2)");
        t.check("6.0", "(#(* 2 %) 3)");
        t.check("(2.0 4.0)", "(map #(* 2 %) [1 2])");
        t.check("7.0", "(#(+ %1 %2) 3 4)");
        t.check("2.0", "(#(- %3 %1) 1 99 3)");
        t.check("5.0", "(#(+ % %1) 2.5)");
        t.check("6.0", "(#(apply + %&) 1 2 3)");
        t.check("[1.0 (2.0 3.0)]", "(#(vector % %&) 1 2 3)");
        t.check("0.5", "(do (def speed (atom 0.5)) (#(deref speed)))");
        t.check("()", "(#())");
        t.check("Execution error: Wrong number of args (1) passed to: fn", "(#(deref speed) 1)");
        t.check("Syntax error: Nested #()s are not allowed", "#(map #(* 2 %) %)");
        t.check("#'user/%", "(def % 5)");
        t.check("5.0", "%");

        // Math
        t.check("1.0", "(mod 7 3)");
        t.check("2.0", "(mod -7 3)");
        t.check("-2.0", "(mod 7 -3)");
        t.check("359.0", "(mod -1 360)");
        t.check("1.5", "(mod 5.5 2)");
        t.check("-1.0", "(rem -7 3)");
        t.check("3.0", "(quot 7 2)");
        t.check("-3.0", "(quot -7 2)");
        t.check("Execution error: Divide by zero", "(mod 1 0)");
        t.check("Execution error: Divide by zero", "(quot 1 0)");
        t.check("3.0", "(abs -3)");
        t.check("1.0", "(min 3 1 2)");
        t.check("3.0", "(max 3 1 2)");
        t.check("true", "(zero? 0)");
        t.check("true", "(pos? 0.1)");
        t.check("false", "(neg? 0)");
        t.check("true", "(even? 4)");
        t.check("true", "(odd? -3)");
        t.check("Execution error: Argument must be an integer: 1.5", "(even? 1.5)");
        t.check("3.141592653589793", "Math/PI");
        t.check("1.0", "(Math/sin (/ Math/PI 2))");
        t.check("3.0", "(Math/sqrt 9)");
        t.check("8.0", "(Math/pow 2 3)");
        t.check("5.0", "(Math/hypot 3 4)");
        t.check("45.0", "(Math/toDegrees (Math/atan2 1 1))");
        t.check("3.0", "(Math/round 2.5)");
        t.check("-2.0", "(Math/round -2.5)");
        t.check("2.0", "(Math/floor 2.7)");
        t.check("-1.0", "(Math/signum -0.3)");
        t.check("Execution error: Math/sqrt expects numbers, got: :a", "(Math/sqrt :a)");
        t.check("Execution error: Wrong number of args (1) passed to: Math/pow", "(Math/pow 2)");

        // Prelude
        t.check("1.0", "(clamp 1.7 -1 1)");
        t.check("-1.0", "(clamp -5 -1 1)");
        t.check("0.5", "(clamp 0.5 -1 1)");
        t.check("0.0", "(deadband 0.03 0.05)");
        t.check("-0.4", "(deadband -0.4 0.05)");
        t.check("15.0", "(lerp 10 20 0.5)");
        t.check("-170.0", "(wrap-degrees 190)");
        t.check("90.0", "(wrap-degrees -270)");
        t.check("-180.0", "(wrap-degrees 180)");
        t.check("true", "(< (abs (- (wrap-radians (* 3 Math/PI)) (- Math/PI))) 1e-9)");
        t.check("#'user/clamp", "(defn clamp [x lo hi] :redefined)");
        t.check(":redefined", "(clamp 1 2 3)");

        // Strings
        t.check("\"heading 12.3 deg\"", "(format \"heading %.1f deg\" 12.345)");
        t.check("\"[:a 1.0] true null\"", "(format \"%s %s %s\" [:a 1] true nil)");
        t.check("\"3\"", "(format \"%.0f\" 3)");
        t.check("Execution error: format: d != java.lang.Double (all numbers are doubles, so use %.0f instead of %d)", "(format \"%d\" 3)");
        t.check("\"\\\"a\\\" [\\\"b\\\"] 1.0\"", "(pr-str \"a\" [\"b\"] 1)");
        t.check("\"hi\" {:a \"b\"}\nnil", "(prn \"hi\" {:a \"b\"})");
        t.check("\"ell\"", "(subs \"hello\" 1 4)");
        t.check("\"llo\"", "(subs \"hello\" 2)");
        t.check("Execution error: String index out of range: begin 2, end 9, length 5", "(subs \"hello\" 2 9)");
        t.check("\"1.0, 2.0, :a\"", "(str/join \", \" [1 2 :a])");
        t.check("\",a\"", "(str/join \",\" [\"\" \"a\"])");
        t.check("\"ab\"", "(str/join [\"a\" \"b\"])");
        t.check("\"\"", "(str/join \",\" [])");
        t.check("\"ARM\"", "(str/upper-case \"arm\")");
        t.check("\"arm\"", "(str/lower-case \"ARM\")");
        t.check("\"x y\"", "(str/trim \"  x y \\n\")");
        t.check("true", "(str/blank? \"  \")");
        t.check("true", "(str/blank? nil)");
        t.check("false", "(str/blank? \"a\")");
        t.check("true", "(str/includes? \"left-motor\" \"motor\")");
        t.check("true", "(str/starts-with? \"left-motor\" \"left\")");
        t.check("false", "(str/ends-with? \"left-motor\" \"left\")");
        t.check("\"a-b-c\"", "(str/replace \"a.b.c\" \".\" \"-\")");
        t.check("[\"a\" \"b\" \"c\"]", "(str/split \"a.b.c\" \".\")");
        t.check("[\"a\" \"\" \"b\"]", "(str/split \"a,,b,,\" \",\")");
        t.check("0.5", "(parse-double \"0.5\")");
        t.check("nil", "(parse-double \"fast\")");
        t.check("Execution error: str/trim expects a string, got: 1.0", "(str/trim 1)");

        // control/start and control/stop
        t.check("false", "(control/running?)");
        t.check("false", "(control/stop)");
        t.check("#'user/ticks", "(def ticks (atom 0))");
        t.check("#'user/ticker", "(defn ticker [] (loop [] (swap! ticks inc) (time/sleep 0.001) (recur)))");
        t.check("#<Future pending>", "(control/start ticker)");
        t.check("true", "(do (time/sleep 0.03) (control/running?))");
        t.check("true", "(pos? @ticks)");
        t.check("#'user/first-loop", "(def first-loop @control/current)");
        t.check("#<Future pending>", "(control/start ticker)");
        t.check("true", "(future-cancelled? first-loop)");
        t.check("true", "(control/stop)");
        t.check("false", "(control/running?)");
        t.check("true", "(let [n @ticks] (time/sleep 0.03) (= n @ticks))");
        t.check("false", "(control/stop)");
        t.check(":finished", "(do (control/start (fn [] :finished)) (time/sleep 0.03) @@control/current)");
        t.check("false", "(do (control/start (fn [] (control/stop) (time/sleep 5))) (time/sleep 0.05) (control/running?))");
        t.check("false", "(control/running?)");

        // try/catch/finally
        t.check("3.0", "(try (+ 1 2))");
        t.check("nil", "(try)");
        t.check("\"Divide by zero\"", "(try (/ 1 0) (catch Exception e (ex-message e)))");
        t.check(":caught", "(try (/ 1 0) (catch Throwable e :caught))");
        t.check("cleanup\n2.0", "(try (inc 1) (finally (println \"cleanup\")))");
        t.check("cleanup\nExecution error: Divide by zero", "(try (/ 1 0) (finally (println \"cleanup\")))");
        t.check("cleanup\n:recovered", "(try (/ 1 0) (catch Exception e :recovered) (finally (println \"cleanup\")))");
        t.check("{:dist 30.0}", "(try (throw (ex-info \"too far\" {:dist 30})) (catch ExceptionInfo e (ex-data e)))");
        t.check("Execution error: too far", "(throw (ex-info \"too far\" {:dist 30}))");
        t.check("Execution error: Divide by zero", "(try (/ 1 0) (catch ExceptionInfo e :wrong-class))");
        t.check(":second", "(try (throw (ex-info \"x\" {})) (catch ExceptionInfo e :second) (catch Exception e :third))");
        t.check("#error {:message \"too far\", :data {:dist 30.0}}", "(ex-info \"too far\" {:dist 30})");
        t.check("#error {:message \"Divide by zero\"}", "(try (/ 1 0) (catch Exception e e))");
        t.check("nil", "(ex-data (try (/ 1 0) (catch Exception e e)))");
        t.check("nil", "(ex-message 1)");
        t.check("\"inner\"", "(try (try (throw (ex-info \"inner\" {})) (finally nil)) (catch Exception e (ex-message e)))");
        t.check("Execution error: from finally", "(try 1 (finally (throw (ex-info \"from finally\" {}))))");
        t.check("Execution error: throw expects an exception, e.g. (ex-info \"message\" {}), got: :oops", "(throw :oops)");
        t.check("Syntax error: Unable to resolve classname: Oops", "(try 1 (catch Oops e 2))");
        t.check("Syntax error: finally clause must be last in try expression", "(try 1 (finally 2) (catch Exception e 3))");
        t.check("Syntax error: Only catch or finally clause can follow catch in try expression", "(try 1 (catch Exception e 2) 3)");
        t.check("Syntax error: catch requires a class and a symbol, e.g. (catch Exception e ...)", "(try 1 (catch Exception))");
        t.check("Syntax error: Can only recur from tail position", "(loop [] (try (recur)))");
        t.check("3.0", "(loop [i 0] (if (< i 3) (do (try (/ 1 0) (catch Exception e nil)) (recur (inc i))) i))");
        t.check("#'user/stopped", "(def stopped (atom false))");
        t.check("#<Future pending>", "(control/start (fn [] (try (loop [] (time/sleep 0.001) (recur)) (finally (time/sleep 0.02) (reset! stopped true)))))");
        t.check("true", "(control/stop)");
        t.check("true", "@stopped");
        t.check("#<Future pending>", "(control/start (fn [] (loop [] (try (time/sleep 0.005) (catch Exception e :swallowed)) (recur))))");
        t.check("true", "(control/stop)");
        t.check("false", "(control/running?)");

        // defonce
        t.check("#'user/gain", "(defonce gain (atom 0.5))");
        t.check("0.8", "(reset! gain 0.8)");
        t.check("nil", "(defonce gain (atom 0.5))");
        t.check("0.8", "@gain");
        t.check("nil", "(defonce gain (/ 1 0))");
        t.check("#'user/gain", "(def gain (atom 0.5))");
        t.check("0.5", "@gain");
        t.check("#'user/fresh", "(let [v 7] (defonce fresh v))");
        t.check("7.0", "fresh");
        t.check("nil", "(defonce + 1)");
        t.check("3.0", "(+ 1 2)");
        t.check("Syntax error: First argument to defonce must be a Symbol", "(defonce \"x\" 1)");
        t.check("Syntax error: Too few arguments to defonce", "(defonce y)");

        // Version
        t.check("{:version \"1.1.9\", :major 1.0, :minor 1.0, :patch 9.0, :commit \"main\"}", "(version)");
        t.check("\"1.1.9\"", "(:version (version))");
        t.check("Execution error: Wrong number of args (1) passed to: version", "(version 1)");

        // Time
        t.check("true", "(< 0 (time/elapsed) 5)");
        t.check("true", "(let [a (time/elapsed) b (time/elapsed)] (<= a b))");
        t.check("true", "(let [start (time/elapsed)] (time/sleep 0.03) (>= (- (time/elapsed) start) 0.03))");
        t.check("Execution error: Wrong number of args (1) passed to: time/elapsed", "(time/elapsed 1)");
        t.check("true", "(let [end (+ (time/elapsed) 0.03) n (atom 0)] (while (< (time/elapsed) end) (swap! n inc) (time/sleep 0.005)) (and (>= @n 3) (<= @n 7)))");
        t.check("Syntax error: Unable to resolve symbol: System/nanoTime in this context", "(System/nanoTime)");
        t.check("Syntax error: Unable to resolve symbol: Thread/sleep in this context", "(Thread/sleep 10)");
        t.check("true", "(let [start (time/elapsed)] (time/sleep 0.05) (>= (- (time/elapsed) start) 0.05))");
        t.check("true", "(let [start (time/elapsed)] (time/sleep -1) (time/sleep 0) (< (- (time/elapsed) start) 0.05))");
        t.check("nil", "(time/sleep 0.001)");
        t.check("Execution error: time/sleep expects numbers, got: \"1\"", "(time/sleep \"1\")");
        t.check("Execution error: Wrong number of args (0) passed to: time/sleep", "(time/sleep)");
        t.check("Syntax error: Too few arguments to time", "(time)");
        t.buffer.getBuffer().setLength(0);
        t.repl.feed("(time (+ 1 2))");
        count++;
        if (!t.buffer.toString().trim().matches("\"Elapsed time: [0-9.E-]+ msecs\"\\s+3\\.0")) {
            failures++;
            System.out.println("FAIL: (time (+ 1 2)) printed " + t.buffer.toString().trim());
        }

        // nil, booleans, keywords and symbols
        t.check("true", "(nil? nil)");
        t.check("false", "(nil? false)");
        t.check("true", "(some? false)");
        t.check("true", "(true? true)");
        t.check("false", "(true? 1)");
        t.check("true", "(false? false)");
        t.check("false", "(boolean nil)");
        t.check("true", "(boolean 0)");
        t.check("true", "(boolean? false)");
        t.check("nil", "nil");
        t.check("0.0", "(count nil)");
        t.check("nil", "(first nil)");
        t.check("(1.0)", "(conj nil 1)");
        t.check("{:a 1.0}", "(assoc nil :a 1)");
        t.check(":speed", ":speed");
        t.check(":speed", "(keyword \"speed\")");
        t.check("true", "(keyword? :a)");
        t.check("true", "(= :a (keyword \"a\"))");
        t.check("\"speed\"", "(name :speed)");
        t.check("\":a\"", "(str :a)");
        t.check("foo", "'foo");
        t.check("foo", "(symbol \"foo\")");
        t.check("true", "(symbol? 'foo)");
        t.check("true", "(= 'foo (symbol \"foo\"))");
        t.check("false", "(symbol? :foo)");
        t.check("true", "(fn? +)");
        t.check("true", "(string? \"a\")");
        t.check("true", "(number? 1)");
        t.check("5.0", "(count \"hello\")");

        checkConcurrentSwaps();
        checkCancelAll();
        checkStatus();
        checkTimeRestart();


        checkInterrupted("(while true (+ 1 1))");
        checkInterrupted("(loop [] (recur))");
        checkInterrupted("(time/sleep 60)");
        checkInterrupted("(try (while true (+ 1 1)) (catch Exception e :swallowed))");
        checkInterrupted("(dotimes [i 1e12] i)");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
