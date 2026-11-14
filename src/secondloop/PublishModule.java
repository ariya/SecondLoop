package secondloop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * (publish/start port sample sample-fn): the part of publish/setup (in the
 * Prelude) that needs sockets and threads. It is internal: only publish/setup
 * is documented for users. It publishes values to subscribers, such as a
 * laptop or a phone app, for graphs and CSV logs, without flooding the REPL,
 * over a small line-based text protocol on the given port, or on 5599 when the
 * port is nil.
 *
 * The keys of the first sample name the fields, in their order. Each remote client that
 * subscribes gets its own handler, which calls (sample-fn) for a map of fresh
 * values and sends them at the rate that client asked for.
 * Nothing is sampled while no client is subscribed. Two clients read the
 * hardware twice, which is fine: there is usually only one.
 *
 * A handler runs as an ordinary future, so STOP cancels it, cancelling waits
 * for it to end, and a failure is reported on the connection that called
 * publish/start.
 *
 * LISTCFG and SET, for changing values from the client, are accepted and
 * ignored: every definition can already be changed from the REPL.
 */
public final class PublishModule {
    public static final int PORT = 5599;
    static final int DEFAULT_RATE = 20;
    /** More than a graph can show, and little load on the Control Hub. */
    static final int MAX_RATE = 100;

    private final List<Client> clients = new CopyOnWriteArrayList<Client>();
    private ServerSocket serverSocket;
    /** The port used when publish/setup names none. */
    private final int defaultPort;
    /** Guarded by this. The port serverSocket was asked for, which is 0 for any free port. */
    private int listenPort;
    private Env env;

    // Guarded by this. feed stays after the stream ends, so FIELDS still lists the fields.
    private Feed feed;
    private boolean on;
    private final List<Future> tasks = new ArrayList<Future>();

    /** The fields of one publish/start, in the order of its first sample, and the connection that reports failures. */
    private static final class Feed {
        final Interp owner;
        final List<String> names;
        final Fn sampleFn;
        /** Guarded by this. The first non-nil value decides a type, e.g. a keyword makes it a string. */
        private final String[] types;
        private final boolean[] typed;

        Feed(Interp owner, List<String> names, Fn sampleFn) {
            this.owner = owner;
            this.names = names;
            this.sampleFn = sampleFn;
            types = new String[names.size()];
            typed = new boolean[names.size()];
            for (int i = 0; i < types.length; i++) {
                types[i] = "double";
            }
        }

        /** One value per field, in the order of names, from a fresh (sample-fn). */
        Object[] sample(Interp interp) {
            return valuesOf(interp.apply(sampleFn, new Object[0]));
        }

        Object[] valuesOf(Object sample) {
            if (!(sample instanceof Map)) {
                throw new ScriptError("publish/start expects a sample map, got: " + Printer.print(sample));
            }
            Map<String, Object> byName = new HashMap<String, Object>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) sample).entrySet()) {
                byName.put(nameOf(e.getKey()), e.getValue());
            }
            Object[] values = new Object[names.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = byName.get(names.get(i));
            }
            return values;
        }

        synchronized void settle(Object[] values) {
            for (int i = 0; i < values.length; i++) {
                if (!typed[i] && values[i] != null) {
                    typed[i] = true;
                    types[i] = values[i] instanceof Double ? "double" : "string";
                }
            }
        }

        /** "FIELDS name,type,unit;..." with no units, which Second Loop does not track. */
        synchronized String fieldsLine() {
            StringBuilder sb = new StringBuilder("FIELDS ");
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    sb.append(';');
                }
                sb.append(names.get(i)).append(',').append(types[i]).append(',');
            }
            return sb.toString();
        }
    }

    /** The body of each handler future: sample, send, sleep, until the client no longer wants data. */
    private final Builtin handler = new Builtin("publish/handler") {
        @Override
        public Object invoke(Interp interp, Object[] args) {
            Client client = (Client) args[0];
            Feed f = (Feed) args[1];
            while (true) {
                Interp.checkStop();
                long millis = client.send(f, f.sample(interp));
                if (millis < 0) {
                    return null;
                }
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new Interrupted();
                }
            }
        }
    };

    public PublishModule() {
        this(PORT);
    }

    /** Port 0 picks a free port, for tests. */
    public PublishModule(int port) {
        this.defaultPort = port;
    }

    public void install(Env env) {
        this.env = env;
        Builtins.define(env, new Builtin("publish/start") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 3, 3);
                publish(interp, args[0], args[1], args[2]);
                return null;
            }
        });
    }

    /** Replaces any stream, and starts a handler for each client already subscribed. */
    void publish(Interp interp, Object portArg, Object sample, Object sampleFn) {
        int requested = portOf(portArg);
        if (!(sample instanceof Map) || ((Map<?, ?>) sample).isEmpty() || !(sampleFn instanceof Fn)) {
            throw new ScriptError("publish/start expects a sample map and a function that returns one");
        }
        List<String> names = new ArrayList<String>();
        for (Object key : ((Map<?, ?>) sample).keySet()) {
            String name = nameOf(key);
            // The protocol separates fields with "," and ";", and commands with spaces.
            if (!(key instanceof Keyword || key instanceof String) || name.isEmpty() || name.matches(".*[,;=\\s].*") || names.contains(name)) {
                throw new ScriptError("publish/start cannot use the field name: " + Printer.print(key));
            }
            names.add(name);
        }
        Feed f = new Feed(interp, names, (Fn) sampleFn);
        // Before endStream, so a port in use leaves the current stream running.
        listen(requested);
        endStream();
        f.settle(f.valuesOf(sample));
        synchronized (this) {
            feed = f;
            on = true;
        }
        for (Client client : clients) {
            client.restart();
        }
    }

    /** nil means the default port. */
    private int portOf(Object arg) {
        if (arg == null) {
            return defaultPort;
        }
        if (arg instanceof Double) {
            double d = (Double) arg;
            if (d == Math.floor(d) && d >= 1 && d <= 65535) {
                return (int) d;
            }
        }
        throw new ScriptError("publish/setup expects a port from 1 to 65535, got: " + Printer.print(arg));
    }

    private static String nameOf(Object key) {
        return key instanceof Keyword ? ((Keyword) key).name : String.valueOf(key);
    }

    /** Ends the stream and waits for every handler to end. */
    private void endStream() {
        List<Future> running;
        synchronized (this) {
            on = false;
            running = new ArrayList<Future>(tasks);
            tasks.clear();
        }
        // Outside the lock: a handler may need it while it finishes.
        for (Future task : running) {
            task.cancel();
        }
    }

    /** A handler that failed ends the stream, so its error is printed only once. */
    private boolean anyFailed() {
        for (Future task : tasks) {
            if ("failed".equals(task.status())) {
                return true;
            }
        }
        return false;
    }

    private synchronized String fieldsLine() {
        return feed == null ? "FIELDS " : feed.fieldsLine();
    }

    private static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double) {
            double d = (Double) value;
            // Empty, because a CSV reader cannot parse ##NaN.
            return Double.isNaN(d) || Double.isInfinite(d) ? "" : Double.toString(d);
        }
        return Printer.display(value).replace(",", ";").replace("\n", " ").replace("\r", " ");
    }

    /**
     * Opens the port on the first publish/setup. Connected clients stay connected
     * while the port stays the same. Moving to another port closes the old one,
     * and every client connected to it.
     */
    private void listen(int requested) {
        List<Client> old;
        synchronized (this) {
            if (serverSocket != null && requested == listenPort) {
                return;
            }
            // The new port opens first, so a port in use keeps the old one.
            final ServerSocket socket;
            try {
                socket = new ServerSocket();
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(requested));
            } catch (IOException e) {
                throw new ScriptError("publish/setup cannot listen on port " + requested + ": " + e.getMessage());
            }
            old = new ArrayList<Client>(clients);
            closeServer();
            serverSocket = socket;
            listenPort = requested;
            startAcceptor(socket);
        }
        // Outside the lock, like endStream.
        for (Client client : old) {
            client.close();
        }
    }

    private void startAcceptor(final ServerSocket socket) {
        Thread acceptor = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop(socket);
            }
        }, "SecondLoop-publish-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** Closes the port and every client; the OpMode calls it when it stops. */
    public void stop() {
        endStream();
        synchronized (this) {
            closeServer();
        }
        for (Client client : clients) {
            client.close();
        }
    }

    private void closeServer() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // Closed either way.
            }
            serverSocket = null;
        }
    }

    /** The port listened on, or -1. */
    public synchronized int port() {
        return serverSocket == null ? -1 : serverSocket.getLocalPort();
    }

    public int clientCount() {
        return clients.size();
    }

    private void acceptLoop(ServerSocket socket) {
        while (true) {
            final Client client;
            try {
                client = new Client(socket.accept());
            } catch (IOException e) {
                return; // closed by stop()
            }
            clients.add(client);
            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    client.readLoop();
                }
            }, "SecondLoop-publish-client");
            reader.setDaemon(true);
            reader.start();
        }
    }

    /** One remote client connection: its subscription, its handler, and the commands it sends. */
    private final class Client {
        private final Socket socket;
        private final Writer out;
        /** Names rather than indexes, so a field the client knows already keeps its column. */
        private volatile List<String> subscribed = Collections.emptyList();
        private volatile int rate = DEFAULT_RATE;
        private volatile boolean closed;
        /** Guarded by the module, like the list of every handler. */
        private Future task;

        Client(Socket socket) throws IOException {
            this.socket = socket;
            socket.setTcpNoDelay(true);
            out = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");
        }

        void readLoop() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                String line;
                while ((line = in.readLine()) != null) {
                    handle(line.trim());
                }
            } catch (IOException e) {
                // The client disconnected.
            } finally {
                close();
            }
        }

        private void handle(String line) throws IOException {
            if (line.isEmpty()) {
                return;
            }
            String upper = line.toUpperCase(Locale.US);
            if (upper.equals("HELLO") || upper.equals("FIELDS")) {
                write(fieldsLine());
            } else if (upper.equals("LISTCFG") || upper.startsWith("SET")) {
                // Ignored: a client may try to change values, but that is done from the REPL.
            } else if (upper.startsWith("SUB")) {
                cancelTask();
                // Holding the write lock means OK goes out before the first DATA line.
                synchronized (this) {
                    subscribe(line.substring(3).trim());
                    write("OK");
                    startTask();
                }
            } else {
                write("ERR unknown");
            }
        }

        /** "SUB a,b rate=20" or "SUB ALL rate=20". */
        private void subscribe(String args) {
            String[] parts = args.split("\\s+");
            int r = DEFAULT_RATE;
            for (int i = 1; i < parts.length; i++) {
                if (parts[i].startsWith("rate=")) {
                    try {
                        r = Integer.parseInt(parts[i].substring(5));
                    } catch (NumberFormatException e) {
                        // Keep the default.
                    }
                }
            }
            rate = r <= 0 ? DEFAULT_RATE : Math.min(r, MAX_RATE);
            List<String> chosen = new ArrayList<String>();
            if (parts[0].equalsIgnoreCase("ALL") || parts[0].equals("*")) {
                // Fixed now, so the columns of a CSV log never shift.
                Feed f;
                synchronized (PublishModule.this) {
                    f = feed;
                }
                if (f != null) {
                    chosen.addAll(f.names);
                }
            } else {
                for (String name : parts[0].split(",")) {
                    if (!name.trim().isEmpty()) {
                        chosen.add(name.trim());
                    }
                }
            }
            subscribed = chosen;
        }

        /** A new handler for the current stream, e.g. after publish/setup. */
        void restart() {
            cancelTask();
            synchronized (this) {
                startTask();
            }
        }

        /** Starts a handler future, if the stream is on and this client is subscribed. */
        private void startTask() {
            Future started;
            synchronized (PublishModule.this) {
                if (!on || anyFailed() || closed || subscribed.isEmpty()) {
                    return;
                }
                FormList call = new FormList();
                call.add(handler);
                call.add(this);
                call.add(feed);
                List<Object> body = new ArrayList<Object>();
                body.add(call);
                started = new Future(feed.owner, body, env);
                task = started;
                for (Iterator<Future> it = tasks.iterator(); it.hasNext(); ) {
                    Future old = it.next();
                    if (old.isDone() && !"failed".equals(old.status())) {
                        it.remove();
                    }
                }
                tasks.add(started);
            }
            started.start();
        }

        /** Waits for this client's handler to end. Never called with the write lock held, which the handler may need. */
        private void cancelTask() {
            Future old;
            synchronized (PublishModule.this) {
                old = task;
                task = null;
            }
            if (old != null) {
                old.cancel();
            }
        }

        /** Sends one DATA line; returns the milliseconds until the next one, or -1 to end the handler. */
        long send(Feed f, Object[] values) {
            List<String> fields = subscribed;
            if (closed || fields.isEmpty()) {
                return -1;
            }
            f.settle(values);
            StringBuilder sb = new StringBuilder("DATA ");
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                int index = f.names.indexOf(fields.get(i));
                sb.append(index < 0 ? "" : format(values[index]));
            }
            try {
                write(sb.toString());
            } catch (IOException e) {
                close();
                return -1;
            }
            return 1000 / rate;
        }

        private synchronized void write(String line) throws IOException {
            out.write(line);
            out.write('\n');
            out.flush();
        }

        void close() {
            closed = true;
            clients.remove(this);
            try {
                socket.close();
            } catch (IOException e) {
                // Closed either way.
            }
            cancelTask();
        }

        @Override
        public String toString() {
            return "#<publish client>";
        }
    }
}
