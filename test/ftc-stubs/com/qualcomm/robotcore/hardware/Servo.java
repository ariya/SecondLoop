package com.qualcomm.robotcore.hardware;

/** Stand-in for the FTC SDK interface; only what Second Loop uses. */
public interface Servo {
    enum Direction {
        FORWARD, REVERSE
    }

    void setDirection(Direction direction);

    Direction getDirection();

    void setPosition(double position);

    double getPosition();
}
