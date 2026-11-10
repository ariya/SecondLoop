package com.qualcomm.hardware.gobilda;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit;

/** Stand-in for the FTC SDK class (SDK 10.3 and later); only what Second Loop uses. Tests override it. */
public class GoBildaPinpointDriver {
    public enum EncoderDirection {
        FORWARD, REVERSED
    }

    public enum GoBildaOdometryPods {
        goBILDA_SWINGARM_POD, goBILDA_4_BAR_POD
    }

    public enum DeviceStatus {
        NOT_READY, READY, CALIBRATING, FAULT_X_POD_NOT_DETECTED, FAULT_Y_POD_NOT_DETECTED,
        FAULT_NO_PODS_DETECTED, FAULT_IMU_RUNAWAY, FAULT_BAD_READ
    }

    public void update() {
    }

    public void setOffsets(double xOffset, double yOffset, DistanceUnit distanceUnit) {
    }

    public void resetPosAndIMU() {
    }

    public void setEncoderDirections(EncoderDirection xEncoder, EncoderDirection yEncoder) {
    }

    public void setEncoderResolution(GoBildaOdometryPods pods) {
    }

    public void setEncoderResolution(double ticksPerUnit, DistanceUnit distanceUnit) {
    }

    public DeviceStatus getDeviceStatus() {
        return DeviceStatus.NOT_READY;
    }

    public double getPosX(DistanceUnit distanceUnit) {
        return 0;
    }

    public double getPosY(DistanceUnit distanceUnit) {
        return 0;
    }

    public double getHeading(AngleUnit angleUnit) {
        return 0;
    }

    public double getVelX(DistanceUnit distanceUnit) {
        return 0;
    }

    public double getVelY(DistanceUnit distanceUnit) {
        return 0;
    }

    public double getHeadingVelocity(UnnormalizedAngleUnit unnormalizedAngleUnit) {
        return 0;
    }
}
