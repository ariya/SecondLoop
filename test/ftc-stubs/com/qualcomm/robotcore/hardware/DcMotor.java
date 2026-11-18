package com.qualcomm.robotcore.hardware;

/** Stand-in for the FTC SDK interface; only what Second Loop uses. */
public interface DcMotor extends DcMotorSimple {
    enum RunMode {
        RUN_WITHOUT_ENCODER, RUN_USING_ENCODER, RUN_TO_POSITION, STOP_AND_RESET_ENCODER
    }

    enum ZeroPowerBehavior {
        UNKNOWN, BRAKE, FLOAT
    }

    void setMode(RunMode mode);

    RunMode getMode();

    void setTargetPosition(int position);

    int getTargetPosition();

    int getCurrentPosition();

    void setZeroPowerBehavior(ZeroPowerBehavior zeroPowerBehavior);

    ZeroPowerBehavior getZeroPowerBehavior();
}
