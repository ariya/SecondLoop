package org.firstinspires.ftc.robotcore.external;

/** Stand-in for the FTC SDK interface; only what Second Loop uses. */
public interface Telemetry {
    Item addData(String caption, Object value);

    Line addLine(String lineCaption);

    boolean update();

    interface Item {
    }

    interface Line {
    }
}
