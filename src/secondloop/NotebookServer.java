package secondloop;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves the notebook page (NotebookPage) over HTTP. Android has no HTTP
 * server class, so this is a small one on plain sockets. It serves:
 *   /         the page
 *   /events   one REPL session, whose output reaches the page as Server-Sent Events
 *   /eval     POST: code for the session named by ?session=ID
 *
 * Each browser tab gets its own session, like a second nc connection: it
 * shares the definitions but not the prompt. The session runs on the thread
 * that serves /events, so closing the tab or stopping the server ends it.
 */
final class NotebookServer {
    /** How often an idle stream is pinged, which is how a closed tab gets noticed. */
    static final long PING_MILLIS = 15000;
    static final int MAX_LINE = 8192;
    static final int MAX_BODY = 1024 * 1024;
    /** The robot controller's web server; the page shows its name and configuration. */
    static final String RC_INFO_URL = "http://127.0.0.1:8080/js/rcInfo.json";

    private final Env globals;
    private final int port;
    private final String rcInfoUrl;
    private final SecureRandom random = new SecureRandom();
    /** Every open connection, so stop() can end them. */
    private final Map<Socket, Thread> connections = new HashMap<Socket, Thread>();
    private final Map<String, Session> sessions = new HashMap<String, Session>();
    private ServerSocket serverSocket;
    private volatile boolean running;
    private volatile String rcInfoCache;

    NotebookServer(Env globals, int port, String rcInfoUrl) {
        this.globals = globals;
        this.port = port;
        this.rcInfoUrl = rcInfoUrl;
    }

    synchronized void start() throws IOException {
        final ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
        serverSocket = socket;
        running = true;
        Thread acceptor = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop(socket);
            }
        }, "SecondLoop-web-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** The port it listens on, for a server started on port 0. */
    int port() {
        return serverSocket.getLocalPort();
    }

    synchronized void stop() {
        running = false;
        closeQuietly(serverSocket);
        synchronized (connections) {
            for (Map.Entry<Socket, Thread> e : connections.entrySet()) {
                closeQuietly(e.getKey());
                e.getValue().interrupt();
            }
            connections.clear();
        }
    }

    int sessionCount() {
        synchronized (sessions) {
            return sessions.size();
        }
    }

    private void acceptLoop(ServerSocket socket) {
        while (running) {
            final Socket client;
            try {
                client = socket.accept();
            } catch (IOException e) {
                return; // closed by stop()
            }
            // The stack is for the REPL session that /events runs.
            Thread thread = new Thread(null, new Runnable() {
                @Override
                public void run() {
                    serve(client);
                }
            }, "SecondLoop-web", ReplServer.THREAD_STACK_SIZE);
            thread.setDaemon(true);
            synchronized (connections) {
                if (!running) {
                    closeQuietly(client);
                    return;
                }
                connections.put(client, thread);
            }
            thread.start();
        }
    }

    private void serve(Socket client) {
        try {
            client.setTcpNoDelay(true);
            InputStream in = new BufferedInputStream(client.getInputStream());
            OutputStream out = client.getOutputStream();
            String requestLine = readLine(in);
            if (requestLine == null) {
                return;
            }
            Map<String, String> headers = new HashMap<String, String>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0 && headers.size() < 100) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
                }
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                send(out, 400, "text/plain", "Bad request");
                return;
            }
            String method = parts[0];
            String target = parts[1];
            int q = target.indexOf('?');
            String path = q < 0 ? target : target.substring(0, q);
            String query = q < 0 ? "" : target.substring(q + 1);
            if (!allowedHost(headers.get("host"))) {
                send(out, 403, "text/plain", "Forbidden");
            } else if (method.equals("GET") && path.equals("/")) {
                send(out, 200, "text/html; charset=utf-8", NotebookPage.HTML);
            } else if (method.equals("GET") && path.equals("/events")) {
                stream(out);
            } else if (method.equals("POST") && path.equals("/eval")) {
                String code = readBody(in, headers.get("content-length"));
                if (code == null) {
                    send(out, 413, "text/plain", "Too large");
                } else if (evaluate(param(query, "session"), code)) {
                    send(out, 200, "text/plain", "OK");
                } else {
                    send(out, 410, "text/plain", "Not connected");
                }
            } else {
                send(out, 404, "text/plain", "Not found");
            }
        } catch (IOException e) {
            // The browser went away.
        } finally {
            synchronized (connections) {
                connections.remove(client);
            }
            closeQuietly(client);
        }
    }

    /**
     * Only an IP address or localhost: a web site that points its own name
     * at the robot (DNS rebinding) must not reach the REPL through a browser.
     */
    static boolean allowedHost(String host) {
        if (host == null) {
            return false;
        }
        String name = host;
        if (name.startsWith("[")) {
            return name.indexOf(']') > 0; // an IPv6 address
        }
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(0, colon);
        }
        return name.equals("localhost") || name.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
    }

    /** The REPL session of one browser tab, from start to end. */
    private void stream(OutputStream raw) throws IOException {
        raw.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\n"
                + "Connection: close\r\n\r\n").getBytes("UTF-8"));
        raw.flush();
        EventWriter events = new EventWriter(raw);
        Session session = new Session(newId(), events);
        synchronized (sessions) {
            sessions.put(session.id, session);
        }
        try {
            events.event("session", "{\"id\":" + quote(session.id) + ",\"rc\":" + rcInfo() + "}");
            session.run();
        } finally {
            synchronized (sessions) {
                sessions.remove(session.id);
            }
        }
    }

    private boolean evaluate(String id, String code) {
        Session session;
        synchronized (sessions) {
            session = id == null ? null : sessions.get(id);
        }
        if (session == null) {
            return false;
        }
        session.queue.add(code);
        return true;
    }

    private final class Session {
        final String id;
        final EventWriter events;
        final PrintWriter out;
        final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>();

        Session(String id, EventWriter events) {
            this.id = id;
            this.events = events;
            this.out = new PrintWriter(events);
        }

        /** Like Repl.run, with the code coming from /eval. */
        void run() throws IOException {
            Repl repl = new Repl(globals, out);
            out.println(Version.greeting(Version.COMMIT));
            out.print(Repl.PROMPT);
            out.flush();
            while (running) {
                String code;
                try {
                    code = queue.poll(PING_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    return; // stopped
                }
                if (code == null) {
                    events.ping();
                    continue;
                }
                boolean done = false;
                for (String line : code.split("\n", -1)) {
                    done = repl.feed(line);
                }
                if (done) {
                    out.print(Repl.PROMPT);
                }
                out.flush();
                if (events.failed) {
                    return;
                }
            }
        }
    }

    /** Turns what the REPL prints into one "output" event per flush. */
    static final class EventWriter extends Writer {
        private final OutputStream out;
        private final StringBuilder pending = new StringBuilder();
        volatile boolean failed;

        EventWriter(OutputStream out) {
            this.out = out;
        }

        @Override
        public synchronized void write(char[] buf, int off, int len) {
            pending.append(buf, off, len);
        }

        @Override
        public synchronized void flush() throws IOException {
            if (pending.length() > 0) {
                String text = pending.toString();
                pending.setLength(0);
                event("output", quote(text));
            }
        }

        synchronized void event(String name, String json) throws IOException {
            sendRaw("event: " + name + "\ndata: " + json + "\n\n");
        }

        synchronized void ping() throws IOException {
            sendRaw(": ping\n\n");
        }

        private void sendRaw(String s) throws IOException {
            try {
                out.write(s.getBytes("UTF-8"));
                out.flush();
            } catch (IOException e) {
                failed = true;
                throw e;
            }
        }

        @Override
        public void close() {
        }
    }

    private String newId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * What the robot controller tells about itself, as JSON with only the
     * fields the page shows, or null if it does not answer. The active
     * configuration only changes with a new OpMode, so one answer is enough.
     */
    private String rcInfo() {
        String info = rcInfoCache;
        if (info != null || rcInfoUrl == null) {
            return info;
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(rcInfoUrl).toURL().openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            try {
                if (connection.getResponseCode() != 200) {
                    return null;
                }
                info = rcFields(new String(readAll(connection.getInputStream()), "UTF-8"));
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            return null;
        }
        rcInfoCache = info;
        return info;
    }

    private static final Pattern RC_FIELD = Pattern.compile(
            "\"(deviceName|rcVersion|sdkVersion|activeConfigName)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** Picks the string fields the page shows, still JSON-encoded, from rcInfo.json. */
    static String rcFields(String json) {
        StringBuilder sb = new StringBuilder("{");
        Matcher m = RC_FIELD.matcher(json);
        while (m.find()) {
            sb.append(sb.length() > 1 ? "," : "").append('"').append(m.group(1)).append("\":\"").append(m.group(2)).append('"');
        }
        return sb.append('}').toString();
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c < 0x20 || c > 0x7e) {
                // ASCII only, so a character never spans two writes.
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String param(String query, String name) throws IOException {
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
            }
        }
        return null;
    }

    /** One header line, or null at the end of input. Bytes stand for themselves: headers are ASCII. */
    static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') {
                int n = sb.length();
                return n > 0 && sb.charAt(n - 1) == '\r' ? sb.substring(0, n - 1) : sb.toString();
            }
            if (sb.length() >= MAX_LINE) {
                throw new IOException("line too long");
            }
            sb.append((char) c);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /** The body as text, or null when it is too large. */
    private static String readBody(InputStream in, String contentLength) throws IOException {
        int length;
        try {
            length = contentLength == null ? 0 : Integer.parseInt(contentLength);
        } catch (NumberFormatException e) {
            throw new IOException("bad Content-Length");
        }
        if (length < 0 || length > MAX_BODY) {
            return null;
        }
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n < 0) {
                throw new IOException("body ended early");
            }
            read += n;
        }
        return new String(body, "UTF-8");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    private static void send(OutputStream out, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        String head = "HTTP/1.1 " + code + " " + reason(code) + "\r\nContent-Type: " + type
                + "\r\nContent-Length: " + bytes.length + "\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
        out.write(bytes);
        out.flush();
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 410: return "Gone";
            case 413: return "Payload Too Large";
            default: return "Error";
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException e) {
            // Unusable either way.
        }
    }
}
