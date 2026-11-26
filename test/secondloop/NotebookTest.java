package secondloop;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks the notebook's HTTP server and its REPL sessions. Run: java -cp build/classes secondloop.NotebookTest */
public final class NotebookTest {
    private static int failures;
    private static int count;

    private static void check(Object expected, Object actual, String label) {
        count++;
        if (!String.valueOf(actual).equals(String.valueOf(expected))) {
            failures++;
            System.out.println("FAIL: " + label);
            System.out.println("  expected: " + expected);
            System.out.println("  actual:   " + actual);
        }
    }

    /** Raw requests, since HttpURLConnection will not send a made-up Host. */
    private static Socket open(int port, String method, String path, String host, String body) throws Exception {
        Socket socket = new Socket("localhost", port);
        socket.setSoTimeout(5000);
        byte[] bytes = body == null ? new byte[0] : body.getBytes("UTF-8");
        String head = method + " " + path + " HTTP/1.1\r\nHost: " + host + "\r\n"
                + (body == null ? "" : "Content-Length: " + bytes.length + "\r\n") + "\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(head.getBytes("UTF-8"));
        out.write(bytes);
        out.flush();
        return socket;
    }

    /** The status code, then the body. */
    private static String request(int port, String method, String path, String host, String body) throws Exception {
        Socket socket = open(port, method, path, host, body);
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            bytes.write(buffer, 0, n);
        }
        socket.close();
        String response = bytes.toString("UTF-8");
        String code = response.substring(9, 12);
        return code + " " + response.substring(response.indexOf("\r\n\r\n") + 4);
    }

    /** Reads Server-Sent Events, one "name data" at a time, skipping pings. */
    private static final class Events {
        final InputStream in;

        Events(Socket socket) throws Exception {
            in = new BufferedInputStream(socket.getInputStream());
            String line;
            while (!(line = NotebookServer.readLine(in)).isEmpty()) {
                // The headers.
            }
        }

        String next() throws Exception {
            String name = null;
            String data = null;
            String line;
            while (!(line = NotebookServer.readLine(in)).isEmpty() || name == null) {
                if (line.startsWith("event: ")) {
                    name = line.substring(7);
                } else if (line.startsWith("data: ")) {
                    data = line.substring(6);
                }
            }
            return name + " " + data;
        }

        /** Output events until one ends with the prompt, joined and decoded. */
        String untilPrompt() throws Exception {
            StringBuilder sb = new StringBuilder();
            while (!sb.toString().endsWith(ClojureRepl.PROMPT)) {
                String event = next();
                if (!event.startsWith("output ")) {
                    return sb + " [" + event + "]";
                }
                sb.append(unquote(event.substring(7)));
            }
            return sb.toString();
        }
    }

    /** Enough of JSON strings for what quote() writes. */
    private static String unquote(String json) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < json.length() - 1; i++) {
            char c = json.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            c = json.charAt(++i);
            if (c == 'n') {
                sb.append('\n');
            } else if (c == 'u') {
                sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                i += 4;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        check(true, NotebookServer.allowedHost("192.168.43.1:9002"), "robot address");
        check(true, NotebookServer.allowedHost("localhost:9002"), "localhost");
        check(true, NotebookServer.allowedHost("127.0.0.1"), "no port");
        check(true, NotebookServer.allowedHost("[::1]:9002"), "IPv6");
        check(false, NotebookServer.allowedHost("evil.example:9002"), "DNS rebinding");
        check(false, NotebookServer.allowedHost("192.168.43.1.evil.example"), "an address inside a name");
        check(false, NotebookServer.allowedHost(null), "no Host");

        check("\"a\\\"b\\\\c\\nd\\u00e9\\u0001\"", NotebookServer.quote("a\"b\\c\nd\u00e9\u0001"), "quote");
        check("{\"deviceName\":\"Robot\",\"activeConfigName\":\"a \\\"b\\\"\"}",
                NotebookServer.rcFields("{\"deviceName\":\"Robot\",\"x\":1,\"activeConfigName\":\"a \\\"b\\\"\",\"other\":\"z\"}"),
                "rcFields keeps only what the page shows");

        Env globals = new Clojure().createGlobals();
        NotebookServer server = new NotebookServer(new Clojure(), globals, 0, null);
        server.start();
        int port = server.port();
        String host = "localhost:" + port;
        try {
            String page = request(port, "GET", "/", host, null);
            check(true, page.startsWith("200 <!DOCTYPE html>") && page.contains("Connecting\u2026"), "the page, with its non-ASCII text intact");
            check("403 Forbidden", request(port, "GET", "/", "evil.example:" + port, null), "a foreign Host");
            check("404 Not found", request(port, "GET", "/nothing", host, null), "an unknown path");
            check("410 Not connected", request(port, "POST", "/eval?session=nope", host, "(+ 1 2)"), "an unknown session");

            Socket stream = open(port, "GET", "/events", host, null);
            Events events = new Events(stream);
            String session = events.next();
            Matcher m = Pattern.compile("session \\{\"id\":\"([0-9a-f]{32})\",\"rc\":null\\}").matcher(session);
            check(true, m.matches(), "the session event: " + session);
            String id = m.matches() ? m.group(1) : "";
            check(Version.greeting(Version.COMMIT) + "\n" + ClojureRepl.PROMPT, events.untilPrompt(), "the greeting");
            check(1, server.sessionCount(), "one session");

            check("200 OK", request(port, "POST", "/eval?session=" + id, host, "(+ 1 2)"), "eval");
            check("3.0\n" + ClojureRepl.PROMPT, events.untilPrompt(), "the result");
            check("200 OK", request(port, "POST", "/eval?session=" + id, host, "(def x\n  \"h\u00e9\")"), "a form over two lines");
            check("#'user/x\n" + ClojureRepl.PROMPT, events.untilPrompt(), "two lines make one form");
            request(port, "POST", "/eval?session=" + id, host, "x");
            check("\"h\u00e9\"\n" + ClojureRepl.PROMPT, events.untilPrompt(), "UTF-8 both ways");
            request(port, "POST", "/eval?session=" + id, host, "(/ 1 0)");
            check(true, events.untilPrompt().startsWith("Execution error"), "an error");

            // Stopping must end a running loop and the stream.
            request(port, "POST", "/eval?session=" + id, host, "(loop [] (recur))");
            Thread.sleep(100);
            long start = System.nanoTime();
            server.stop();
            InputStream in = stream.getInputStream();
            byte[] buffer = new byte[8192];
            while (in.read(buffer) > 0) {
                // Whatever was still on its way.
            }
            check(true, (System.nanoTime() - start) < 2000000000L, "stop() ends the stream promptly");
            for (int i = 0; i < 50 && server.sessionCount() > 0; i++) {
                Thread.sleep(20);
            }
            check(0, server.sessionCount(), "no session after stop()");
            stream.close();
        } finally {
            server.stop();
        }

        System.out.println(count - failures + "/" + count + " notebook checks passed.");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
