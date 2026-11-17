package secondloop;

import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The web UI of RobotSimulator: the telemetry, the battery and gamepad 1,
 * on the JDK's own HTTP server, so it needs no dependency. It serves:
 *   /                  the page (robot-simulator.html, next to this class)
 *   /state             the telemetry screen and the battery voltage, as JSON
 *   /gamepad           POST: one gamepad's state from the page, form-encoded
 *   /js/rcInfo.json    what the robot controller answers, since secondloop.py asks for it
 */
final class SimWebUi {
    static final int PORT = 8080;
    // Made-up versions: there is no robot controller or SDK here, only stand-ins.
    static final String RC_INFO = "{\"deviceName\":\"RobotSimulator\",\"rcVersion\":\"0.0\",\"sdkVersion\":\"0.0.0\"}";

    private final SimTelemetry telemetry;
    private final VoltageSensor battery;
    private final SimGamepads gamepads;
    private volatile String status = "";
    private HttpServer http;

    SimWebUi(SimTelemetry telemetry, VoltageSensor battery, SimGamepads gamepads) {
        this.telemetry = telemetry;
        this.battery = battery;
        this.gamepads = gamepads;
    }

    /** The line under the version, such as "Ready at 192.168.43.1:9001 (1 client)". */
    void setStatus(String status) {
        this.status = status;
    }

    void start(int port) throws IOException {
        http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (!exchange.getRequestURI().getPath().equals("/")) {
                    send(exchange, 404, "text/plain", "Not found");
                    return;
                }
                InputStream in = SimWebUi.class.getResourceAsStream("robot-simulator.html");
                if (in == null) {
                    send(exchange, 500, "text/plain", "robot-simulator.html is missing: run ./build.sh");
                    return;
                }
                send(exchange, 200, "text/html; charset=utf-8", new String(readAll(in), "UTF-8"));
            }
        });
        http.createContext("/state", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                send(exchange, 200, "application/json", state());
            }
        });
        http.createContext("/gamepad", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (!exchange.getRequestMethod().equals("POST")) {
                    send(exchange, 405, "text/plain", "POST only");
                    return;
                }
                Map<String, String> fields = parseForm(new String(readAll(exchange.getRequestBody()), "UTF-8"));
                String pad = fields.get("pad");
                if (!"1".equals(pad) && !"2".equals(pad)) {
                    send(exchange, 400, "text/plain", "pad must be 1 or 2");
                    return;
                }
                gamepads.update(Integer.parseInt(pad), fields, System.nanoTime());
                send(exchange, 204, "text/plain", null);
            }
        });
        http.createContext("/js/rcInfo.json", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                send(exchange, 200, "application/json", RC_INFO);
            }
        });
        http.start();
    }

    /** The port it listens on, for a server started on port 0. */
    int port() {
        return http.getAddress().getPort();
    }

    void stop() {
        if (http != null) {
            http.stop(0);
        }
    }

    /** The telemetry lines as the OpMode shows them (the version, the status, then the rest) and the battery voltage. */
    String state() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"version\":").append(quote(Version.greeting(Version.COMMIT)));
        sb.append(",\"status\":").append(quote(status));
        sb.append(",\"battery\":").append(battery.getVoltage());
        sb.append(",\"telemetry\":[");
        List<String> lines = telemetry.lines();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(i > 0 ? "," : "").append(quote(lines.get(i)));
        }
        return sb.append("]}").toString();
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    static Map<String, String> parseForm(String body) throws IOException {
        Map<String, String> fields = new HashMap<String, String>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                fields.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                        URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            }
        }
        return fields;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    private static void send(HttpExchange exchange, int code, String type, String body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
            return;
        }
        byte[] bytes = body.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
    }
}
