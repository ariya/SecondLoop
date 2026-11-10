package secondloop;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * (pinpoint/init :swingarm -3.3 -6.6), (pinpoint/pose) and friends: the goBILDA
 * Pinpoint odometry computer, which the robot configuration names "pinpoint".
 * Distances are in inches, the unit of the FTC field, and angles in radians,
 * as for imu/angles.
 */
public final class PinpointModule {
    static final String NAME = "pinpoint";
    private static final Keyword NOT_CONFIGURED = Keyword.of("not-configured");

    private PinpointModule() {
    }

    public static void install(Env env, final OpMode opMode) {
        // Without the pods and offsets, the pose is measured wrongly, so
        // reading before pinpoint/init is an error rather than a wrong value.
        final AtomicBoolean initialized = new AtomicBoolean(false);

        Builtins.define(env, new Builtin("pinpoint/init") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 3, 5);
                if (args.length == 4) {
                    throw new ScriptError(name() + " expects both the X and the Y pod direction, or neither");
                }
                Object pods = args[0];
                double xOffset = finite(args[1], name(), "X offset");
                double yOffset = finite(args[2], name(), "Y offset");
                GoBildaPinpointDriver.EncoderDirection xDirection = GoBildaPinpointDriver.EncoderDirection.FORWARD;
                GoBildaPinpointDriver.EncoderDirection yDirection = GoBildaPinpointDriver.EncoderDirection.FORWARD;
                if (args.length == 5) {
                    xDirection = direction(args[3], name());
                    yDirection = direction(args[4], name());
                }
                GoBildaPinpointDriver.GoBildaOdometryPods podType = null;
                double ticksPerInch = 0;
                if (Keyword.of("swingarm").equals(pods)) {
                    podType = GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_SWINGARM_POD;
                } else if (Keyword.of("4-bar").equals(pods)) {
                    podType = GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD;
                } else if (pods instanceof Double && (Double) pods > 0 && !((Double) pods).isInfinite()) {
                    ticksPerInch = (Double) pods;
                } else {
                    throw new ScriptError(name() + " expects the pods as :swingarm, :4-bar or encoder ticks per inch, got: "
                            + Printer.print(pods));
                }
                GoBildaPinpointDriver pinpoint = find(opMode);
                if (pinpoint == null) {
                    return NOT_CONFIGURED;
                }
                GoBildaPinpointDriver.DeviceStatus status;
                synchronized (pinpoint) {
                    pinpoint.setOffsets(xOffset, yOffset, DistanceUnit.INCH);
                    if (podType != null) {
                        pinpoint.setEncoderResolution(podType);
                    } else {
                        // In millimetres: some SDK versions fail on any other unit.
                        pinpoint.setEncoderResolution(ticksPerInch / 25.4, DistanceUnit.MM);
                    }
                    pinpoint.setEncoderDirections(xDirection, yDirection);
                    pinpoint.resetPosAndIMU();
                    status = awaitCalibration(pinpoint);
                }
                initialized.set(true);
                return keyword(status);
            }
        });
        Builtins.define(env, new Builtin("pinpoint/reset") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 0, 0);
                GoBildaPinpointDriver pinpoint = ready(opMode, initialized);
                GoBildaPinpointDriver.DeviceStatus status;
                synchronized (pinpoint) {
                    pinpoint.resetPosAndIMU();
                    status = awaitCalibration(pinpoint);
                }
                return keyword(status);
            }
        });
        Builtins.define(env, new Builtin("pinpoint/pose") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 0, 0);
                GoBildaPinpointDriver pinpoint = ready(opMode, initialized);
                FormMap m = new FormMap();
                // update() reads everything at once; holding the lock keeps another
                // thread's update() from mixing two readings into one pose.
                synchronized (pinpoint) {
                    pinpoint.update();
                    m.put(Keyword.of("x"), pinpoint.getPosX(DistanceUnit.INCH));
                    m.put(Keyword.of("y"), pinpoint.getPosY(DistanceUnit.INCH));
                    m.put(Keyword.of("heading"), pinpoint.getHeading(AngleUnit.RADIANS));
                }
                return m;
            }
        });
        Builtins.define(env, new Builtin("pinpoint/velocity") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 0, 0);
                GoBildaPinpointDriver pinpoint = ready(opMode, initialized);
                FormMap m = new FormMap();
                synchronized (pinpoint) {
                    pinpoint.update();
                    m.put(Keyword.of("x"), pinpoint.getVelX(DistanceUnit.INCH));
                    m.put(Keyword.of("y"), pinpoint.getVelY(DistanceUnit.INCH));
                    // Unnormalized, so a fast spin is not wrapped to -π..π.
                    m.put(Keyword.of("heading"), pinpoint.getHeadingVelocity(UnnormalizedAngleUnit.RADIANS));
                }
                return m;
            }
        });
        // Works before pinpoint/init too, to check the wiring.
        Builtins.define(env, new Builtin("pinpoint/status") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 0, 0);
                GoBildaPinpointDriver pinpoint = find(opMode);
                if (pinpoint == null) {
                    return NOT_CONFIGURED;
                }
                GoBildaPinpointDriver.DeviceStatus status;
                synchronized (pinpoint) {
                    pinpoint.update();
                    status = pinpoint.getDeviceStatus();
                }
                return keyword(status);
            }
        });
    }

    static final long CALIBRATION_TIMEOUT_MILLIS = 500;     // goBILDA: "approximately 0.25 seconds"
    private static final long CALIBRATION_POLL_MILLIS = 10;

    /**
     * Waits for the IMU calibration after a reset (about a quarter of a second),
     * so the pose is not read while it runs. Gives up after
     * CALIBRATION_TIMEOUT_MILLIS and returns the last status, e.g. CALIBRATING,
     * rather than hanging on a stuck device.
     */
    private static GoBildaPinpointDriver.DeviceStatus awaitCalibration(GoBildaPinpointDriver pinpoint) {
        long deadline = System.nanoTime() + CALIBRATION_TIMEOUT_MILLIS * 1000000L;
        while (true) {
            pinpoint.update();
            GoBildaPinpointDriver.DeviceStatus status = pinpoint.getDeviceStatus();
            boolean busy = status == GoBildaPinpointDriver.DeviceStatus.CALIBRATING
                    || status == GoBildaPinpointDriver.DeviceStatus.NOT_READY;
            if (!busy || System.nanoTime() >= deadline) {
                return status;
            }
            try {
                Thread.sleep(CALIBRATION_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Interrupted();
            }
        }
    }

    private static Keyword keyword(GoBildaPinpointDriver.DeviceStatus status) {
        return Keyword.of(status.name().toLowerCase().replace('_', '-'));
    }

    private static double finite(Object arg, String fnName, String what) {
        double value = Builtins.toNumber(arg, fnName);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new ScriptError(fnName + " expects a finite " + what + ", got: " + Printer.print(arg));
        }
        return value;
    }

    private static GoBildaPinpointDriver.EncoderDirection direction(Object arg, String fnName) {
        if (Keyword.of("forward").equals(arg)) {
            return GoBildaPinpointDriver.EncoderDirection.FORWARD;
        }
        if (Keyword.of("reverse").equals(arg)) {
            return GoBildaPinpointDriver.EncoderDirection.REVERSED;
        }
        throw new ScriptError(fnName + " expects a pod direction as :forward or :reverse, got: " + Printer.print(arg));
    }

    // The device is looked up first, so without one the error names the
    // missing device rather than asking for pinpoint/init.
    private static GoBildaPinpointDriver ready(OpMode opMode, AtomicBoolean initialized) {
        GoBildaPinpointDriver pinpoint = lookup(opMode);
        if (!initialized.get()) {
            throw new ScriptError("The Pinpoint is not set up: first call pinpoint/init");
        }
        return pinpoint;
    }

    private static GoBildaPinpointDriver find(OpMode opMode) {
        return opMode.hardwareMap == null ? null
                : opMode.hardwareMap.tryGet(GoBildaPinpointDriver.class, NAME);
    }

    private static GoBildaPinpointDriver lookup(OpMode opMode) {
        GoBildaPinpointDriver pinpoint = find(opMode);
        if (pinpoint == null) {
            throw new ScriptError("No Pinpoint named \"" + NAME + "\" in the robot configuration");
        }
        return pinpoint;
    }
}
