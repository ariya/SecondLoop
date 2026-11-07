package com.qualcomm.robotcore.hardware;

/** Stand-in for the FTC SDK interface; only what Second Loop uses. */
public interface DcMotorSimple {
    enum Direction {
        FORWARD, REVERSE
    }

    void setDirection(Direction direction);

    Direction getDirection();

    void setPower(double power);

    double getPower();
}
