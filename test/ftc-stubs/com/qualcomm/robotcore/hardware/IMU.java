package com.qualcomm.robotcore.hardware;

import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

/** Stand-in for the FTC SDK interface; only what Second Loop uses. */
public interface IMU {
    /** Stand-in for the SDK class; tests read the orientation back. */
    class Parameters {
        public ImuOrientationOnRobot imuOrientationOnRobot;

        public Parameters(ImuOrientationOnRobot imuOrientationOnRobot) {
            this.imuOrientationOnRobot = imuOrientationOnRobot;
        }
    }

    boolean initialize(Parameters parameters);

    void resetYaw();

    YawPitchRollAngles getRobotYawPitchRollAngles();
}
