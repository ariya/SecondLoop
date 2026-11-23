package secondloop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.VoltageSensor;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;

/** Checks RobotSimulator's web UI (telemetry and gamepad 1) over HTTP. Run: java -cp build/classes secondloop.SimWebUiTest */
public final class SimWebUiTest {
    private static int failures;
    private static int count;

    private static void check(String expected, String actual, String label) {
        count++;
        if (!actual.equals(expected)) {
            failures++;
            System.out.println("FAIL: " + label);
            System.out.println("  expected: " + expected);
            System.out.println("  actual:   " + actual);
        }
    }

    private static String join(String[] words) {
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            sb.append(sb.length() == 0 ? "" : " ").append(w);
        }
        return sb.toString();
    }

    /** The status code, then the body if there is one. */
    private static String request(int port, String method, String path, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
        c.setRequestMethod(method);
        if (body != null) {
            c.setDoOutput(true);
            OutputStream out = c.getOutputStream();
            out.write(body.getBytes("UTF-8"));
            out.close();
        }
        int code = c.getResponseCode();
        InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                bytes.write(buffer, 0, n);
            }
            in.close();
        }
        return code + " " + bytes.toString("UTF-8");
    }

    public static void main(String[] args) throws Exception {
        OpMode opMode = RobotSimulator.createOpMode();
        VoltageSensor battery = new VoltageSensor() {
            @Override
            public double getVoltage() {
                return 12.74;
            }
        };
        Env globals = Builtins.createGlobals();
        GamepadModule.install(globals, opMode);
        StringWriter out = new StringWriter();
        Repl repl = new Repl(globals, new PrintWriter(out));
        SimTelemetry telemetry = new SimTelemetry(new PrintStream(new ByteArrayOutputStream()));
        SimGamepads gamepads = new SimGamepads(opMode);
        SimWebUi ui = new SimWebUi(telemetry, battery, gamepads);
        ui.start(0);
        int port = ui.port();
        try {
            check("200 {\"battery\":12.74,\"telemetry\":[]}",
                    request(port, "GET", "/state", null), "the Driver Hub at rest");
            telemetry.addLine("power 0.5");
            telemetry.addLine("say \"hi\"\\");
            telemetry.update();
            check("200 {\"battery\":12.74,"
                    + "\"telemetry\":[\"power 0.5\",\"say \\\"hi\\\"\\\\\"]}",
                    request(port, "GET", "/state", null), "telemetry lines, quoted");

            check("204 ", request(port, "POST", "/gamepad", "pad=1&left_stick_y=-0.8&a=1&right_trigger=0.5"
                    + "&left_stick_x=NaN&right_stick_x=7&b=yes"), "send gamepad 1");
            out.getBuffer().setLength(0);
            repl.feed("(let [g (gamepad 1)] [(:left-stick-y g) (:a g) (:right-trigger g) (:left-stick-x g) (:right-stick-x g) (:b g)])");
            check("[-0.8 true 0.5 0.0 0.0 false]", out.toString().trim(), "gamepad 1 as sent; bad values read as neutral");

            gamepads.checkTimeout(System.nanoTime() + SimGamepads.TIMEOUT_NANOS + 1);
            out.getBuffer().setLength(0);
            repl.feed("(let [g (gamepad 1)] [(:left-stick-y g) (:a g) (:right-trigger g)])");
            check("[0.0 false 0.0]", out.toString().trim(), "neutral once the page stops sending");
            out.getBuffer().setLength(0);
            repl.feed("(:a (gamepad 2))");
            check("false", out.toString().trim(), "gamepad 2 stays neutral");

            check("400 pad must be 1 or 2", request(port, "POST", "/gamepad", "pad=3"), "only gamepads 1 and 2");
            check("405 POST only", request(port, "GET", "/gamepad", null), "gamepad is POST only");

            String page = request(port, "GET", "/", null);
            check("true", String.valueOf(page.startsWith("200 <!doctype html>")), "the page");
            check("404 Not found", request(port, "GET", "/nothing", null), "other paths");
            check("200 " + SimWebUi.RC_INFO, request(port, "GET", "/js/rcInfo.json", null), "rcInfo.json for the notebook");
        } finally {
            ui.stop();
        }

        String url = "http://localhost:9002/";
        check("open " + url, join(RobotSimulator.browserCommand("Mac OS X", url)), "browser on macOS");
        check("rundll32 url.dll,FileProtocolHandler " + url, join(RobotSimulator.browserCommand("Windows 11", url)), "browser on Windows");
        check("xdg-open " + url, join(RobotSimulator.browserCommand("Linux", url)), "browser on Linux");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
