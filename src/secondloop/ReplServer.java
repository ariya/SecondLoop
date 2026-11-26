package secondloop;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accepts TCP connections (e.g. from `nc ROBOT_IP 9001`) and gives each one
 * its own REPL, and serves the notebook (see NotebookServer), whose browser
 * tabs each get their own REPL too. All of them share the same global
 * definitions.
 */
public final class ReplServer {
    /** Deeply recursive code needs more stack than Android gives a thread by default. */
    static final long THREAD_STACK_SIZE = 16L * 1024 * 1024;

    private final Language language;
    private final Env globals;
    private final int port;
    private final NotebookServer notebook;
    /** Each connected client and the thread running its REPL. */
    private final Map<Socket, Thread> clients = new HashMap<Socket, Thread>();
    private ServerSocket serverSocket;
    private volatile boolean running;

    public ReplServer(Language language, Env globals, int port, int webPort) {
        this.language = language;
        this.globals = globals;
        this.port = port;
        this.notebook = new NotebookServer(language, globals, webPort, NotebookServer.RC_INFO_URL);
    }

    public synchronized void start() throws IOException {
        final ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
        try {
            notebook.start();
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
        serverSocket = socket;
        running = true;
        Thread acceptor = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop(socket);
            }
        }, "SecondLoop-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public synchronized void stop() {
        running = false;
        closeQuietly(serverSocket);
        notebook.stop();
        synchronized (clients) {
            for (Map.Entry<Socket, Thread> e : clients.entrySet()) {
                closeQuietly(e.getKey());
                e.getValue().interrupt(); // ends any loop or time/sleep still running
            }
            clients.clear();
        }
        Future.cancelAll();
    }

    /** Both nc connections and notebook tabs. */
    public int clientCount() {
        synchronized (clients) {
            return clients.size() + notebook.sessionCount();
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
            Thread session = new Thread(null, new Runnable() {
                @Override
                public void run() {
                    serve(client);
                }
            }, "SecondLoop-client", THREAD_STACK_SIZE);
            session.setDaemon(true);
            synchronized (clients) {
                if (!running) {
                    closeQuietly(client);
                    return;
                }
                clients.put(client, session);
            }
            session.start();
        }
    }

    private void serve(Socket client) {
        try {
            client.setTcpNoDelay(true);
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), "UTF-8"));
            out.println(Version.greeting(Version.COMMIT));
            language.newRepl(globals, out).run(in);
        } catch (IOException e) {
            // The client disconnected.
        } finally {
            synchronized (clients) {
                clients.remove(client);
            }
            closeQuietly(client);
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException e) {
            // The socket is unusable either way.
        }
    }

    /** The notebook's address, e.g. "http://192.168.43.1:9002". */
    public static String url(String address, int webPort) {
        return "http://" + address + ":" + webPort;
    }

    /** The IPv4 addresses of this device, e.g. "192.168.43.1", for showing on telemetry. */
    public static List<String> localAddresses() {
        List<String> found = new ArrayList<String>();
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nif.getInetAddresses())) {
                    if (address instanceof Inet4Address) {
                        found.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            // Nothing found: the caller shows no address.
        }
        return found;
    }

    /**
     * The address a laptop should connect to, or null if there is none. The
     * Control Hub's own WiFi is 192.168.43.1 and a phone's WiFi Direct is
     * 192.168.49.1, so either wins over, say, an Ethernet address.
     */
    public static String preferredAddress(List<String> addresses) {
        for (String address : addresses) {
            if (address.equals("192.168.43.1") || address.equals("192.168.49.1")) {
                return address;
            }
        }
        return addresses.isEmpty() ? null : addresses.get(0);
    }

    /**
     * The one status line on the Driver Hub, e.g. "Second Loop 1.1.22 at
     * http://192.168.43.1:9002 (1 client)". The version is there so a team
     * can tell which copy of the file the robot runs.
     */
    public static String status(String address, int webPort, int clients) {
        String where = address == null ? "on port " + webPort : "at " + url(address, webPort);
        return Version.title(Version.COMMIT) + " " + where + " (" + clients + (clients == 1 ? " client)" : " clients)");
    }
}
