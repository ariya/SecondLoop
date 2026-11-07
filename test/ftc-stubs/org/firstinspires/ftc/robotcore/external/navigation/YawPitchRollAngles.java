package org.firstinspires.ftc.robotcore.external.navigation;

/** Stand-in for the FTC SDK class; only what Second Loop uses. Angles are kept in degrees. */
public class YawPitchRollAngles {
    private final double yaw;
    private final double pitch;
    private final double roll;

    public YawPitchRollAngles(AngleUnit unit, double yaw, double pitch, double roll, long acquisitionTime) {
        double k = unit == AngleUnit.RADIANS ? 180 / Math.PI : 1;
        this.yaw = yaw * k;
        this.pitch = pitch * k;
        this.roll = roll * k;
    }

    public double getYaw(AngleUnit unit) {
        return convert(yaw, unit);
    }

    public double getPitch(AngleUnit unit) {
        return convert(pitch, unit);
    }

    public double getRoll(AngleUnit unit) {
        return convert(roll, unit);
    }

    private static double convert(double degrees, AngleUnit unit) {
        return unit == AngleUnit.RADIANS ? Math.toRadians(degrees) : degrees;
    }
}
