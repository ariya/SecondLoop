package com.qualcomm.hardware.rev;

import com.qualcomm.robotcore.hardware.ImuOrientationOnRobot;

/** Stand-in for the FTC SDK class; only what Second Loop uses. */
public class RevHubOrientationOnRobot implements ImuOrientationOnRobot {
    public enum LogoFacingDirection {
        UP, DOWN, LEFT, RIGHT, FORWARD, BACKWARD
    }

    public enum UsbFacingDirection {
        UP, DOWN, LEFT, RIGHT, FORWARD, BACKWARD
    }

    public final LogoFacingDirection logo;
    public final UsbFacingDirection usb;

    /** Like the SDK, rejects a logo and USB port that face along the same axis. */
    public RevHubOrientationOnRobot(LogoFacingDirection logo, UsbFacingDirection usb) {
        if (logo.ordinal() / 2 == usb.ordinal() / 2) {
            throw new IllegalArgumentException("The specified REV Hub orientation is invalid: the logo and USB port cannot face the same axis");
        }
        this.logo = logo;
        this.usb = usb;
    }
}
