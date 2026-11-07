package com.qualcomm.robotcore.hardware;

/** Stand-in for the FTC SDK interface; only what Second Loop uses. */
public interface DcMotorEx extends DcMotor {
    void setVelocity(double angularRate);

    double getVelocity();
}
