package secondloop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Simulates a robot on a laptop, so the REPL can be tried without one.
 * It simulates telemetry, which it redraws on stdout every STATUS_MILLIS like
 * the OpMode does (see SimTelemetry), gamepads 1 and 2 (see SimGamepads), and
 * a robot (see SimRobot): a battery that the motors drain, a motor named
 * "flywheel", a mecanum drivetrain, and an IMU and a Pinpoint that follow it.
 *   java -cp build/classes secondloop.RobotSimulator          serve on port 9001, like the robot
 *   java -cp build/classes secondloop.RobotSimulator --stdin  read from the terminal directly
 *
 * Either way, its web UI on port 8080 (see SimWebUi) shows the telemetry
 * screen and the battery, and reads gamepad 1. Port 8080 also answers /js/rcInfo.json as the robot
 * controller does, since secondloop.py asks for it before connecting.
 */
public final class RobotSimulator {
    static final long STATUS_MILLIS = 100;

    private static final TelemetryModule telemetryModule = new TelemetryModule();
    private static final SimTelemetry telemetry = new SimTelemetry(System.out);

    public static void main(String[] args) throws Exception {
        Env globals = Builtins.createGlobals();
        new PublishModule().install(globals); // (publish/setup ...) serves on port 5599, like the robot
        telemetryModule.install(globals);
        SimRobot robot = new SimRobot(new SimBattery(new Random()), new SimMotor.Clock());
        OpMode opMode = createOpMode(robot);
        GamepadModule.install(globals, opMode);
        ImuModule.install(globals, opMode);
        BatteryModule.install(globals, opMode);
        MotorModule.install(globals, opMode);
        PinpointModule.install(globals, opMode);
        SimGamepads gamepads = new SimGamepads(opMode);
        SimWebUi ui = new SimWebUi(telemetry, robot.battery, gamepads);
        boolean stdin = args.length > 0 && args[0].equals("--stdin");
        try {
            ui.start(SimWebUi.PORT);
            // On stderr, so that --stdin output stays the REPL's alone.
            System.err.println("Web UI: http://localhost:" + SimWebUi.PORT + "/");
        } catch (IOException e) {
            System.err.println("Could not serve the web UI on port " + SimWebUi.PORT + ": " + e.getMessage()
                    + (stdin ? "" : ". secondloop.py will not connect."));
        }
        if (stdin) {
            ui.setStatus("Reading from the terminal");
            startDisplay(gamepads, ui, null, 0);
            new Repl(globals, new PrintWriter(System.out)).run(
                    new BufferedReader(new InputStreamReader(System.in, "UTF-8")));
            showTelemetry(); // the last lines, in case input ended within a frame
            ui.stop();
            return;
        }
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9001;
        ReplServer server = new ReplServer(globals, port);
        server.start();
        startDisplay(gamepads, ui, server, port);
        System.out.println(Version.greeting(Version.COMMIT));
        System.out.println(ReplServer.status(ReplServer.preferredAddress(ReplServer.localAddresses()), port, 0)
                + ". Try: ./secondloop.py localhost" + (port == 9001 ? "" : ":" + port));
        Thread.currentThread().join(); // the REPL and the web UI run until Ctrl-C
    }

    static OpMode createOpMode() {
        return createOpMode(new SimMotor.Clock());
    }

    static OpMode createOpMode(SimMotor.Clock clock) {
        return createOpMode(new SimRobot(new SimBattery(new Random()), clock));
    }

    /** An OpMode whose hardware map holds the simulated devices, under the names a robot configuration would use. */
    static OpMode createOpMode(SimRobot robot) {
        final Map<String, Object> devices = new HashMap<String, Object>();
        devices.put(ImuModule.NAME, new SimImu(robot));
        devices.put(PinpointModule.NAME, new SimPinpoint(robot));
        devices.put(SimRobot.FLYWHEEL, robot.flywheel);
        for (int i = 0; i < 4; i++) {
            devices.put(SimRobot.NAMES[i], robot.motors[i]);
        }
        OpMode opMode = new OpMode() {
        };
        opMode.gamepad1 = new Gamepad();
        opMode.gamepad2 = new Gamepad();
        opMode.hardwareMap = new HardwareMap() {
            @Override
            public <T> T tryGet(Class<? extends T> type, String name) {
                Object device = devices.get(name);
                return type.isInstance(device) ? type.cast(device) : null;
            }
        };
        opMode.hardwareMap.voltageSensor.put("Control Hub", robot.battery);
        return opMode;
    }

    /**
     * Redraws telemetry from its own thread, as the OpMode thread does on the
     * robot, and lets go of gamepads the web UI no longer sends.
     */
    static void startDisplay(final SimGamepads gamepads, final SimWebUi ui, final ReplServer server, final int port) {
        final String address = ReplServer.preferredAddress(ReplServer.localAddresses());
        Thread display = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        showTelemetry();
                        gamepads.checkTimeout(System.nanoTime());
                        if (server != null) {
                            ui.setStatus(ReplServer.status(address, port, server.clientCount()));
                        }
                        Thread.sleep(STATUS_MILLIS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "RobotSimulator display");
        display.setDaemon(true); // so --stdin exits when input ends
        display.start();
    }

    static synchronized void showTelemetry() {
        telemetryModule.render(telemetry);
        telemetry.update();
    }
}
