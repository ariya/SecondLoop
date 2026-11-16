package secondloop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;

/**
 * Runs the REPL on a laptop, without a robot.
 *   java -cp build/classes secondloop.LocalRepl          serve on port 9001, like the robot
 *   java -cp build/classes secondloop.LocalRepl --stdin  read from the terminal directly
 *
 * When serving, it also answers /js/rcInfo.json on port 8080 as the robot
 * controller does, since secondloop.py asks for it before connecting.
 */
public final class LocalRepl {
    static final int RC_PORT = 8080;
    // Made-up versions: there is no robot controller or SDK here, only stand-ins.
    static final String RC_INFO = "{\"deviceName\":\"LocalRepl\",\"rcVersion\":\"0.0\",\"sdkVersion\":\"0.0.0\"}";

    public static void main(String[] args) throws Exception {
        Env globals = Builtins.createGlobals();
        new PublishModule().install(globals); // (publish/setup ...) serves on port 5599, like the robot
        if (args.length > 0 && args[0].equals("--stdin")) {
            new Repl(globals, new PrintWriter(System.out)).run(
                    new BufferedReader(new InputStreamReader(System.in, "UTF-8")));
            return;
        }
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9001;
        ReplServer server = new ReplServer(globals, port);
        server.start();
        try {
            serveRcInfo();
        } catch (IOException e) {
            System.out.println("Could not serve rcInfo.json on port " + RC_PORT + ": " + e.getMessage()
                    + ". secondloop.py will not connect.");
        }
        System.out.println(Version.greeting(Version.COMMIT));
        System.out.println(ReplServer.status(ReplServer.preferredAddress(ReplServer.localAddresses()), port, 0)
                + ". Try: ./secondloop.py localhost" + (port == 9001 ? "" : ":" + port));
        Thread.currentThread().join();
    }

    static void serveRcInfo() throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(RC_PORT), 0);
        http.createContext("/js/rcInfo.json", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] body = RC_INFO.getBytes("UTF-8");
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                OutputStream out = exchange.getResponseBody();
                out.write(body);
                out.close();
            }
        });
        http.start(); // its thread is not a daemon, like the REPL's: both run until Ctrl-C
    }
}
