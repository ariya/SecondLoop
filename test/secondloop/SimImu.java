package secondloop;

import com.qualcomm.robotcore.hardware.IMU;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

/**
 * The IMU of RobotSimulator. The yaw follows the robot's heading, wrapped to
 * -180..180 degrees as the real IMU does. The simulated floor is flat, so pitch
 * and roll stay at 0.
 */
final class SimImu implements IMU {
    private final SimRobot robot;
    private double yawOffset;

    SimImu(SimRobot robot) {
        this.robot = robot;
    }

    @Override
    public boolean initialize(Parameters parameters) {
        return true;
    }

    @Override
    public synchronized void resetYaw() {
        yawOffset = robot.heading();
    }

    @Override
    public synchronized YawPitchRollAngles getRobotYawPitchRollAngles() {
        double yaw = robot.heading() - yawOffset;
        yaw = Math.atan2(Math.sin(yaw), Math.cos(yaw));
        return new YawPitchRollAngles(AngleUnit.RADIANS, yaw, 0, 0, System.nanoTime());
    }
}
