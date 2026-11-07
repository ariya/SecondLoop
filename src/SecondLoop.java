package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import java.io.IOException;

import secondloop.BatteryModule;
import secondloop.Builtins;
import secondloop.Env;
import secondloop.GamepadModule;
import secondloop.ImuModule;
import secondloop.MotorModule;
import secondloop.ReplServer;
import secondloop.ServoModule;
import secondloop.TelemetryModule;
import secondloop.TimeModule;

/**
 * Second Loop: a REPL for FTC robots.
 *
 * Select this OpMode on the Driver Hub and press INIT. From a laptop on
 * the robot's WiFi, connect with: nc ROBOT_IP 9001
 * (the address is shown on the Driver Hub telemetry).
 */
@TeleOp(name = "Second Loop", group = "Second Loop")
public class SecondLoop extends LinearOpMode {
    public static final int PORT = 9001;

    /** How often the Driver Hub display is redrawn. */
    private static final long STATUS_MILLIS = 100;

    private final TelemetryModule telemetryModule = new TelemetryModule();

    @Override
    public void runOpMode() {
        Env globals = Builtins.createGlobals();
        GamepadModule.install(globals, this);
        MotorModule.install(globals, this);
        ServoModule.install(globals, this);
        BatteryModule.install(globals, this);
        ImuModule.install(globals, this);
        telemetryModule.install(globals);
        ReplServer server = new ReplServer(globals, PORT);
        try {
            server.start();
        } catch (IOException e) {
            telemetry.addData("Second Loop", "Cannot listen on port " + PORT + ": " + e.getMessage());
            telemetry.update();
            while (!isStopRequested()) {
                sleep(100);
            }
            return;
        }

        try {
            String address = ReplServer.preferredAddress(ReplServer.localAddresses());
            while (!isStarted() && !isStopRequested()) {
                showStatus(server, address);
                sleep(STATUS_MILLIS);
            }
            waitForStart();
            TimeModule.restart(globals);
            while (opModeIsActive()) {
                showStatus(server, address);
                sleep(STATUS_MILLIS);
            }
        } finally {
            server.stop();
        }
    }

    private void showStatus(ReplServer server, String address) {
        telemetry.addLine(ReplServer.status(address, PORT, server.clientCount()));
        telemetryModule.render(telemetry);
        telemetry.update();
    }

    // @@SECONDLOOP_SOURCES@@
}
