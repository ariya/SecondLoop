package secondloop;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.IMU;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * (imu/init :up :backward), (imu/reset-yaw), (imu/angles) and (imu/yaw),
 * (imu/pitch), (imu/roll): the IMU in the Control Hub, which the robot
 * configuration names "imu". imu/angles reads yaw, pitch and roll together, so
 * the three come from the same moment; imu/yaw and the others read just one. Angles are
 * in radians, ready for Math/sin and Math/cos; Math/toDegrees converts them.
 */
public final class ImuModule {
    static final String NAME = "imu";

    private ImuModule() {
    }

    public static void install(Env env, final OpMode opMode) {
        // Angles mean nothing until the IMU knows how the hub is mounted, so
        // reading before imu/init is an error rather than a silent wrong value.
        final AtomicBoolean initialized = new AtomicBoolean(false);

        Builtins.define(env, new Builtin("imu/init") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 2, 2);
                RevHubOrientationOnRobot.LogoFacingDirection logo =
                        RevHubOrientationOnRobot.LogoFacingDirection.valueOf(direction(args[0], name(), "logo"));
                RevHubOrientationOnRobot.UsbFacingDirection usb =
                        RevHubOrientationOnRobot.UsbFacingDirection.valueOf(direction(args[1], name(), "USB"));
                RevHubOrientationOnRobot orientation;
                try {
                    orientation = new RevHubOrientationOnRobot(logo, usb);
                } catch (IllegalArgumentException e) {
                    throw new ScriptError(name() + " expects the logo and the USB port to face different axes, got: "
                            + Printer.print(args[0]) + " " + Printer.print(args[1]));
                }
                IMU imu = lookup(opMode);
                if (!imu.initialize(new IMU.Parameters(orientation))) {
                    throw new ScriptError(name() + " could not initialize the IMU");
                }
                initialized.set(true);
                return null;
            }
        });
        Builtins.define(env, new Builtin("imu/reset-yaw") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 0, 0);
                ready(opMode, initialized).resetYaw();
                return null;
            }
        });
        Builtins.define(env, new Builtin("imu/angles") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 0, 0);
                YawPitchRollAngles angles = ready(opMode, initialized).getRobotYawPitchRollAngles();
                FormMap m = new FormMap();
                m.put(Keyword.of("yaw"), angles.getYaw(AngleUnit.RADIANS));
                m.put(Keyword.of("pitch"), angles.getPitch(AngleUnit.RADIANS));
                m.put(Keyword.of("roll"), angles.getRoll(AngleUnit.RADIANS));
                return m;
            }
        });
        defineAngle(env, opMode, initialized, "yaw");
        defineAngle(env, opMode, initialized, "pitch");
        defineAngle(env, opMode, initialized, "roll");
    }

    /** imu/yaw, imu/pitch or imu/roll: one angle, for a field or a quick look. */
    private static void defineAngle(Env env, final OpMode opMode, final AtomicBoolean initialized, final String angle) {
        Builtins.define(env, new Builtin("imu/" + angle) {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 0, 0);
                YawPitchRollAngles angles = ready(opMode, initialized).getRobotYawPitchRollAngles();
                if (angle.equals("yaw")) {
                    return angles.getYaw(AngleUnit.RADIANS);
                }
                return angle.equals("pitch") ? angles.getPitch(AngleUnit.RADIANS) : angles.getRoll(AngleUnit.RADIANS);
            }
        });
    }

    /** The SDK enum constant for :up, :down, :left, :right, :forward or :backward. */
    private static String direction(Object arg, String fnName, String part) {
        if (arg instanceof Keyword) {
            String name = ((Keyword) arg).name;
            if (name.equals("up") || name.equals("down") || name.equals("left") || name.equals("right")
                    || name.equals("forward") || name.equals("backward")) {
                return name.toUpperCase();
            }
        }
        throw new ScriptError(fnName + " expects the " + part
                + " direction as :up, :down, :left, :right, :forward or :backward, got: " + Printer.print(arg));
    }

    private static IMU ready(OpMode opMode, AtomicBoolean initialized) {
        if (!initialized.get()) {
            throw new ScriptError("The IMU is not set up: first call imu/init with the way the hub is mounted, e.g. (imu/init :up :backward)");
        }
        return lookup(opMode);
    }

    private static IMU lookup(OpMode opMode) {
        IMU imu = opMode.hardwareMap == null ? null : opMode.hardwareMap.tryGet(IMU.class, NAME);
        if (imu == null) {
            throw new ScriptError("No IMU named \"" + NAME + "\" in the robot configuration");
        }
        return imu;
    }
}
