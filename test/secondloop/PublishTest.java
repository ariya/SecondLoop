package secondloop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;

/** Checks publish/setup and its protocol over a local socket. Run: java -cp build/classes secondloop.PublishTest */
public final class PublishTest {
    private static int failures;
    private static int count;

    private final PublishModule module = new PublishModule(0);
    private final StringWriter buffer = new StringWriter();
    private final Repl repl;

    private PublishTest() {
        Env globals = Builtins.createGlobals();
        module.install(globals);
        repl = new Repl(globals, new PrintWriter(buffer));
    }

    private void feed(String expectedResult, String line) {
        buffer.getBuffer().setLength(0);
        repl.feed(line);
        check(expectedResult, buffer.toString().trim(), line);
    }

    private static void check(String expected, String actual, String label) {
        count++;
        if (!actual.equals(expected)) {
            failures++;
            System.out.println("FAIL: " + label);
            System.out.println("  expected: " + expected);
            System.out.println("  actual:   " + actual);
        }
    }

    /** A remote client speaking the protocol. */
    static final class RemoteClient {
        final Socket socket;
        final BufferedReader in;
        final PrintWriter out;

        RemoteClient(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(2000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        }

        String ask(String command) throws IOException {
            out.println(command);
            return in.readLine();
        }

        /** The next line, or "(nothing)" when none comes within the time. */
        String next(int millis) throws IOException {
            socket.setSoTimeout(millis);
            try {
                String line = in.readLine();
                return line == null ? "(closed)" : line;
            } catch (SocketTimeoutException e) {
                return "(nothing)";
            } finally {
                socket.setSoTimeout(2000);
            }
        }
    }

    private static void check(boolean ok, String label) {
        check("true", String.valueOf(ok), label);
    }

    private static int freePort() throws IOException {
        java.net.ServerSocket socket = new java.net.ServerSocket(0);
        int port = socket.getLocalPort();
        socket.close();
        return port;
    }

    /** Skips lines already queued until the expected one; gives the last line read if it never comes. */
    static String until(RemoteClient remote, String expected) throws IOException {
        String line = "(nothing)";
        for (int i = 0; i < 200; i++) {
            line = remote.next(1000);
            if (line.equals(expected) || line.startsWith("(")) {
                break;
            }
        }
        return line;
    }

    /** Reads until the line goes quiet. */
    static void drain(RemoteClient remote) throws IOException {
        while (!remote.next(100).equals("(nothing)")) {
            // keep reading
        }
    }

    public static void main(String[] args) throws Exception {
        PublishTest t = new PublishTest();
        t.feed("Execution error: publish/setup expects a map of field names to functions or atoms, such as {:battery battery/voltage}, got: 1.0", "(publish/setup 1)");
        t.feed("Execution error: publish/setup expects a map of field names to functions or atoms, such as {:battery battery/voltage}, got: {}", "(publish/setup {})");
        t.feed("Execution error: publish/setup expects a map of field names to functions or atoms, such as {:battery battery/voltage}, got: {:a 1.0}", "(publish/setup {:a 1})");
        t.feed("Execution error: publish/setup cannot use the field name: \"a,b\"", "(publish/setup {\"a,b\" inc})");
        t.feed("Execution error: publish/setup cannot use the field name: \"\"", "(publish/setup {\"\" inc})");
        t.feed("Execution error: publish/setup expects keyword or string field names, got: 1.0", "(publish/setup {1 inc})");
        t.feed("Execution error: publish/setup has the same field name twice", "(publish/setup {:a inc \"a\" inc})");
        t.feed("Execution error: publish/start expects a sample map and a function that returns one", "(publish/start nil {:a 1} 2)");
        t.feed("Execution error: publish/start cannot use the field name: \"a;b\"", "(publish/start nil {\"a;b\" 1} inc)");
        String arity = "Execution error: publish/setup expects an optional port and a map of fields, such as (publish/setup 6789 {:battery battery/voltage})";
        t.feed(arity, "(publish/setup)");
        t.feed(arity, "(publish/setup 6789 {:a inc} 1)");
        t.feed("Execution error: publish/setup expects a port from 1 to 65535, got: 0.0", "(publish/setup 0 {:a inc})");
        t.feed("Execution error: publish/setup expects a port from 1 to 65535, got: 65536.0", "(publish/setup 65536 {:a inc})");
        t.feed("Execution error: publish/setup expects a port from 1 to 65535, got: 6789.5", "(publish/setup 6789.5 {:a inc})");
        t.feed("Execution error: publish/setup expects a port from 1 to 65535, got: \"6789\"", "(publish/setup \"6789\" {:a inc})");
        t.feed("Execution error: publish/setup expects a port from 1 to 65535, got: 0.0", "(publish/start 0 {:a 1} inc)");
        t.feed("Execution error: publish/setup expects a map of field names to functions or atoms, such as {:battery battery/voltage}, got: 1.0", "(publish/setup 6789 1)");
        check("-1", String.valueOf(t.module.port()), "a bad call opens no port");

        t.feed("#'user/mode", "(def mode (atom :fast))");
        t.feed("#'user/calls", "(def calls (atom 0))");
        t.feed("nil", "(publish/setup {:a (fn [] (swap! calls inc) 1) :mode (fn [] @mode) :gone (fn [] nil)})");
        int port = t.module.port();
        RemoteClient remote = new RemoteClient(port);
        check("FIELDS a,double,;gone,double,;mode,string,", remote.ask("HELLO"), "HELLO lists the fields sorted by name, with the type of their first value");
        remote.out.println("LISTCFG");
        remote.out.println("SET kp=1");
        check("(nothing)", remote.next(200), "LISTCFG and SET get no reply");
        check("ERR unknown", remote.ask("HUH"), "unknown command");
        check("(nothing)", remote.next(100), "nothing is sent before SUB");
        t.feed("1.0", "@calls");

        check("OK", remote.ask("SUB mode,a,later rate=50"), "SUB");
        check("DATA :fast,1.0,", remote.next(1000), "values in subscription order; an unknown field stays empty");
        t.feed("0.0", "(reset! calls 0)");
        Thread.sleep(500);
        t.feed("true", "(<= 15 @calls 35)");
        t.feed(":slow", "(reset! mode :slow)");
        check("DATA :slow,1.0,", until(remote, "DATA :slow,1.0,"), "changes are sampled");

        RemoteClient fast = new RemoteClient(port);
        check("OK", fast.ask("SUB a rate=500"), "a second remote client, faster than the cap");
        check("DATA 1.0", fast.next(1000), "the second remote client gets its own data");
        t.feed("0.0", "(reset! calls 0)");
        Thread.sleep(500);
        t.feed("true", "(<= 50 @calls 85)");
        fast.socket.close();
        Thread.sleep(100);
        t.feed("0.0", "(reset! calls 0)");
        Thread.sleep(500);
        t.feed("true", "(<= 15 @calls 35)");

        remote.out.println("SUB ALL");
        check("OK", until(remote, "OK"), "SUB ALL");
        check("DATA 1.0,,:slow", remote.next(1000), "ALL sorted by name; nil is empty");
        remote.out.println("SUB");
        check("OK", until(remote, "OK"), "an empty SUB");
        t.feed("0.0", "(reset! calls 0)");
        check("(nothing)", remote.next(300), "nothing is sent after an empty SUB");
        t.feed("0.0", "@calls");
        check("OK", remote.ask("SUB a rate=50"), "subscribing again");
        check("DATA 1.0", remote.next(1000), "a new handler starts");

        t.feed("nil", "(publish/setup {:b (fn [] 2)})");
        check("DATA ", remote.next(1000), "a remote client already subscribed gets a handler at once, with its old columns");
        remote.out.println("FIELDS");
        check("FIELDS b,double,", until(remote, "FIELDS b,double,"), "the remote client stays connected through a new publish/setup");
        remote.out.println("SUB b rate=1000");
        check("OK", until(remote, "OK"), "SUB with a high rate");
        check("DATA 2.0", remote.next(1000), "rate is capped, not refused");

        t.feed("Execution error: Divide by zero", "(publish/setup {:bad (fn [] (/ 1 0))})");
        t.feed("0.0", "(reset! calls 0)");
        t.repl.feed("(publish/setup {:b (fn [] (if (> (swap! calls inc) 2) (/ 1 0) 2))})");
        Thread.sleep(300);
        String out = t.buffer.toString().trim();
        check("Future failed. Execution error: Divide by zero", out.substring(out.lastIndexOf('\n') + 1), "a field failing later is reported");
        remote.out.println("SUB b rate=50");
        check("OK", until(remote, "OK"), "subscribing after a failure");
        check("(nothing)", remote.next(300), "a failed stream starts no new handler");

        t.feed("#'user/level", "(def level (atom 0.25))");
        t.feed("nil", "(publish/setup {:level level})");
        remote.out.println("SUB level rate=50");
        check("OK", until(remote, "OK"), "SUB an atom field");
        check("DATA 0.25", until(remote, "DATA 0.25"), "an atom field sends its value");
        t.feed("0.75", "(reset! level 0.75)");
        check("DATA 0.75", until(remote, "DATA 0.75"), "an atom field is read on every sample");
        remote.out.println("SUB");
        check("OK", until(remote, "OK"), "unsubscribing from the atom field");
        t.feed("Execution error: publish/setup expects a map of field names to functions or atoms, such as {:battery battery/voltage}, got: {:level 0.75}", "(publish/setup {:level @level})");

        t.feed("nil", "(publish/start nil {:b 1 :a 2} (fn [] {:a 2 :b 1}))");
        remote.out.println("FIELDS");
        check("FIELDS b,double,;a,double,", until(remote, "FIELDS b,double,;a,double,"), "publish/start keeps the order of the first sample");
        t.feed("nil", "(publish/setup {\"b\" (fn [] 1) :c (fn [] 3) :a (fn [] 2)})");
        remote.out.println("FIELDS");
        check("FIELDS a,double,;b,double,;c,double,", until(remote, "FIELDS a,double,;b,double,;c,double,"), "publish/setup sorts the fields by name");

        int other = freePort();
        t.feed("nil", "(publish/setup " + other + " {:a (fn [] 1)})");
        check(String.valueOf(other), String.valueOf(t.module.port()), "publish/setup with a port listens on it");
        check("(closed)", until(remote, "(closed)"), "moving to another port closes the clients of the old one");
        RemoteClient moved = new RemoteClient(other);
        check("FIELDS a,double,", moved.ask("FIELDS"), "a subscriber connects on the new port");
        t.feed("nil", "(publish/setup " + other + " {:b (fn [] 2)})");
        moved.out.println("FIELDS");
        check("FIELDS b,double,", until(moved, "FIELDS b,double,"), "the same port keeps its subscribers connected");

        java.net.ServerSocket busy = new java.net.ServerSocket(0);
        t.repl.feed("(publish/setup " + busy.getLocalPort() + " {:c (fn [] 3)})");
        check(t.buffer.toString().contains("publish/setup cannot listen on port " + busy.getLocalPort()), "a port in use is reported");
        busy.close();
        check(String.valueOf(other), String.valueOf(t.module.port()), "a port in use keeps the old port open");
        moved.out.println("FIELDS");
        check("FIELDS b,double,", until(moved, "FIELDS b,double,"), "a port in use keeps the old subscribers connected");
        moved.out.println("SUB b rate=50");
        check("OK", until(moved, "OK"), "subscribing after a port in use");
        check("DATA 2.0", until(moved, "DATA 2.0"), "a port in use keeps the old stream running");

        t.module.stop();
        check("(closed)", until(moved, "(closed)"), "stop() closes every client");
        check("0", String.valueOf(t.module.clientCount()), "no clients after stop()");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
