package secondloop;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit;

/**
 * The goBILDA Pinpoint of RobotSimulator. Its pods roll with the robot without
 * slipping, so it tracks the robot exactly, from where it was last reset:
 * x forward, y to the left, heading counterclockwise. The simulated pods count
 * the right way with :forward :forward; a pod set to :reverse flips its axis,
 * as a wrongly set pod does on a real robot. The pods sit where the docs put
 * them, the X pod 3 in to the right and the Y pod 7 in behind the center,
 * so other offsets make the pose drift as the robot turns, as on a real robot.
 * The pods are goBILDA 4-bar pods, so another pod type or resolution scales
 * the distances they count. Its state is guarded by the robot's lock, which
 * moves it.
 */
final class SimPinpoint extends GoBildaPinpointDriver {
    private static final double X_POD_LEFT = -3;
    private static final double Y_POD_FORWARD = -7;
    // Encoder ticks per mm, as in the SDK.
    private static final double SWINGARM_TICKS_PER_MM = 13.26291192;
    private static final double FOUR_BAR_TICKS_PER_MM = 19.89436789;

    private final SimRobot robot;
    private double xOffset = X_POD_LEFT;
    private double yOffset = Y_POD_FORWARD;
    private double xSign = 1;
    private double ySign = 1;
    private double ticksPerMm = FOUR_BAR_TICKS_PER_MM;
    private double x;
    private double y;
    private double heading;
    private double velX;
    private double velY;
    private double velHeading;

    SimPinpoint(SimRobot robot) {
        this.robot = robot;
        robot.attach(this);
    }

    /** Called by the robot, under its lock, for each step it moves. */
    void step(double forward, double left, double turn, double dt) {
        // What each pod rolls, corrected by the offsets the program gave.
        // The heading comes from the IMU, so a wrong resolution scales only the pods.
        double scale = FOUR_BAR_TICKS_PER_MM / ticksPerMm;
        double f = scale * xSign * (forward - turn * X_POD_LEFT) + turn * xOffset;
        double l = scale * ySign * (left + turn * Y_POD_FORWARD) - turn * yOffset;
        double mid = heading + turn / 2;
        double dx = f * Math.cos(mid) - l * Math.sin(mid);
        double dy = f * Math.sin(mid) + l * Math.cos(mid);
        x += dx;
        y += dy;
        heading += turn;
        velX = dx / dt;
        velY = dy / dt;
        velHeading = turn / dt;
    }

    @Override
    public void update() {
        robot.advance();
    }

    @Override
    public void setOffsets(double xOffset, double yOffset, DistanceUnit distanceUnit) {
        synchronized (robot) {
            robot.advance();
            double scale = distanceUnit == DistanceUnit.MM ? 1 / 25.4 : 1;
            this.xOffset = xOffset * scale;
            this.yOffset = yOffset * scale;
        }
    }

    @Override
    public void resetPosAndIMU() {
        synchronized (robot) {
            robot.advance();
            x = 0;
            y = 0;
            heading = 0;
        }
    }

    @Override
    public void setEncoderDirections(EncoderDirection xEncoder, EncoderDirection yEncoder) {
        synchronized (robot) {
            robot.advance();
            xSign = xEncoder == EncoderDirection.REVERSED ? -1 : 1;
            ySign = yEncoder == EncoderDirection.REVERSED ? -1 : 1;
        }
    }

    @Override
    public void setEncoderResolution(GoBildaOdometryPods pods) {
        synchronized (robot) {
            robot.advance();
            ticksPerMm = pods == GoBildaOdometryPods.goBILDA_SWINGARM_POD
                    ? SWINGARM_TICKS_PER_MM : FOUR_BAR_TICKS_PER_MM;
        }
    }

    @Override
    public void setEncoderResolution(double ticksPerUnit, DistanceUnit distanceUnit) {
        synchronized (robot) {
            robot.advance();
            ticksPerMm = distanceUnit == DistanceUnit.MM ? ticksPerUnit : ticksPerUnit / 25.4;
        }
    }

    @Override
    public DeviceStatus getDeviceStatus() {
        return DeviceStatus.READY;
    }

    private static double distance(double inches, DistanceUnit unit) {
        return unit == DistanceUnit.MM ? inches * 25.4 : inches;
    }

    @Override
    public double getPosX(DistanceUnit distanceUnit) {
        synchronized (robot) {
            return distance(x, distanceUnit);
        }
    }

    @Override
    public double getPosY(DistanceUnit distanceUnit) {
        synchronized (robot) {
            return distance(y, distanceUnit);
        }
    }

    @Override
    public double getHeading(AngleUnit angleUnit) {
        synchronized (robot) {
            // Wrapped to -π..π, as the SDK does.
            double h = Math.atan2(Math.sin(heading), Math.cos(heading));
            return angleUnit == AngleUnit.DEGREES ? Math.toDegrees(h) : h;
        }
    }

    @Override
    public double getVelX(DistanceUnit distanceUnit) {
        synchronized (robot) {
            return distance(velX, distanceUnit);
        }
    }

    @Override
    public double getVelY(DistanceUnit distanceUnit) {
        synchronized (robot) {
            return distance(velY, distanceUnit);
        }
    }

    @Override
    public double getHeadingVelocity(UnnormalizedAngleUnit unnormalizedAngleUnit) {
        synchronized (robot) {
            return unnormalizedAngleUnit == UnnormalizedAngleUnit.DEGREES ? Math.toDegrees(velHeading) : velHeading;
        }
    }
}
