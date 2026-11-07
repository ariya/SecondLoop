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
 * its own REPL. All connections share the same global definitions.
 */
public final class ReplServer {
    /** Deeply recursive code needs more stack than Android gives a thread by default. */
    static final long THREAD_STACK_SIZE = 16L * 1024 * 1024;

    private final Env globals;
    private final int port;
    /** Each connected client and the thread running its REPL. */
    private final Map<Socket, Thread> clients = new HashMap<Socket, Thread>();
    private ServerSocket serverSocket;
    private volatile boolean running;

    public ReplServer(Env globals, int port) {
        this.globals = globals;
        this.port = port;
    }

    public synchronized void start() throws IOException {
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
        }, "SecondLoop-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public synchronized void stop() {
        running = false;
        closeQuietly(serverSocket);
        synchronized (clients) {
            for (Map.Entry<Socket, Thread> e : clients.entrySet()) {
                closeQuietly(e.getKey());
                e.getValue().interrupt(); // ends any loop or time/sleep still running
            }
            clients.clear();
        }
        Future.cancelAll();
    }

    public int clientCount() {
        synchronized (clients) {
            return clients.size();
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
            out.println("Second Loop REPL");
            new Repl(globals, out).run(in);
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

    /** The one status line on the Driver Hub, e.g. "Ready at 192.168.43.1:9001 (1 client)". */
    public static String status(String address, int port, int clients) {
        String where = address == null ? "on port " + port : "at " + address + ":" + port;
        return "Ready " + where + " (" + clients + (clients == 1 ? " client)" : " clients)");
    }
}
