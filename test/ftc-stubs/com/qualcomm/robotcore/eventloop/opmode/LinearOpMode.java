package com.qualcomm.robotcore.eventloop.opmode;

/** Compile-only stand-in for the FTC SDK class; only what Second Loop uses. */
public abstract class LinearOpMode extends OpMode {
    public abstract void runOpMode() throws InterruptedException;

    public void waitForStart() {
    }

    public final boolean opModeIsActive() {
        return false;
    }

    public final boolean isStarted() {
        return false;
    }

    public final boolean isStopRequested() {
        return true;
    }

    public final void sleep(long milliseconds) {
    }
}
