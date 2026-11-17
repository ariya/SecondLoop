package secondloop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.Gamepad;

import java.util.Map;

/**
 * Gamepads 1 and 2 of RobotSimulator. The web UI sends gamepad 1, from a real
 * controller; gamepad 2 stays neutral. A pad that the page
 * stops sending, for example because its tab was closed, goes back to neutral
 * after TIMEOUT_NANOS, so a stick cannot stay pushed and keep the robot driving.
 */
final class SimGamepads {
    static final long TIMEOUT_NANOS = 500 * 1000000L;

    static final String[] AXES = {"left_stick_x", "left_stick_y", "right_stick_x", "right_stick_y",
        "left_trigger", "right_trigger"};
    static final String[] BUTTONS = {"a", "b", "x", "y", "left_bumper", "right_bumper",
        "dpad_up", "dpad_down", "dpad_left", "dpad_right", "left_stick_button", "right_stick_button",
        "start", "back", "guide"};

    private final Gamepad[] pads;
    private final long[] lastUpdate = new long[2];
    private final boolean[] connected = new boolean[2];

    SimGamepads(OpMode opMode) {
        pads = new Gamepad[] {opMode.gamepad1, opMode.gamepad2};
    }

    /**
     * Sets gamepad n (1 or 2) from the page's fields: axes from -1 to 1,
     * buttons as "1" or "0". A field that is missing or bad reads as neutral.
     */
    synchronized void update(int n, Map<String, String> fields, long nanos) {
        Gamepad g = pads[n - 1];
        try {
            for (String axis : AXES) {
                Gamepad.class.getField(axis).setFloat(g, axis(fields.get(axis)));
            }
            for (String button : BUTTONS) {
                Gamepad.class.getField(button).setBoolean(g, "1".equals(fields.get(button)));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e); // the names above are the Gamepad's own fields
        }
        lastUpdate[n - 1] = nanos;
        connected[n - 1] = true;
    }

    private static float axis(String text) {
        try {
            float v = Float.parseFloat(text);
            return v >= -1 && v <= 1 ? v : 0; // also rejects NaN
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Lets go of every pad the page has not sent for TIMEOUT_NANOS. */
    synchronized void checkTimeout(long nanos) {
        for (int i = 0; i < 2; i++) {
            if (connected[i] && nanos - lastUpdate[i] > TIMEOUT_NANOS) {
                Gamepad g = pads[i];
                g.left_stick_x = g.left_stick_y = g.right_stick_x = g.right_stick_y = 0;
                g.left_trigger = g.right_trigger = 0;
                g.a = g.b = g.x = g.y = g.left_bumper = g.right_bumper = false;
                g.dpad_up = g.dpad_down = g.dpad_left = g.dpad_right = false;
                g.left_stick_button = g.right_stick_button = g.start = g.back = g.guide = false;
                connected[i] = false;
            }
        }
    }
}
