package secondloop;

import com.qualcomm.robotcore.hardware.VoltageSensor;

import java.util.Random;

/**
 * The battery of RobotSimulator: a 12 V, 3000 mAh NiMH pack such as the FTC
 * batteries. At rest it reads slightly below 13 V (12.5 to 12.95 V), picked at
 * random when the simulator starts. The motors drain it: its open-circuit
 * voltage falls as charge is used, from FULL to EMPTY, and under load it sags
 * by the current times the internal resistance, so a hard start dips the
 * voltage for a moment. Its state is guarded by the SimRobot's lock.
 */
final class SimBattery implements VoltageSensor {
    static final double MIN = 12.5;
    static final double MAX = 12.95;
    /** Open-circuit voltage when full and when empty; in between it falls in a straight line. */
    static final double FULL = 13.0;
    static final double EMPTY = 11.5;
    /** 3000 mAh, in coulombs. */
    static final double CAPACITY = 3.0 * 3600;
    /** The battery, its wires and connectors, in ohms. */
    static final double RESISTANCE = 0.12;

    private final double capacity;
    private final double resistance;
    /** From 0 (empty) to 1 (full). */
    private double charge;
    private double amps;
    private SimRobot robot;

    SimBattery(Random random) {
        this(Math.round((MIN + random.nextDouble() * (MAX - MIN)) * 100) / 100.0, CAPACITY, RESISTANCE);
    }

    /** A battery that reads restVolts with nothing drawing. Tests use no resistance and endless capacity for a steady 12 V. */
    SimBattery(double restVolts, double capacity, double resistance) {
        this.capacity = capacity;
        this.resistance = resistance;
        charge = (restVolts - EMPTY) / (FULL - EMPTY);
    }

    /** Called once by the robot it powers, which then drains it on every step. */
    void attach(SimRobot robot) {
        this.robot = robot;
    }

    /** Draws amps for dt seconds. The caller holds the robot's lock. */
    void drain(double amps, double dt) {
        this.amps = amps;
        charge = Math.max(0, charge - amps * dt / capacity);
    }

    /** The voltage at the terminals now, as the motors see it. The caller holds the robot's lock. */
    double volts() {
        return EMPTY + (FULL - EMPTY) * charge - amps * resistance;
    }

    /** From 0 (empty) to 1 (full). */
    double charge() {
        if (robot == null) {
            return charge;
        }
        synchronized (robot) {
            robot.advance();
            return charge;
        }
    }

    @Override
    public double getVoltage() {
        if (robot == null) {
            return round(volts());
        }
        synchronized (robot) {
            robot.advance();
            return round(volts());
        }
    }

    /** To hundredths, as the Driver Hub shows it. */
    private static double round(double volts) {
        return Math.round(volts * 100) / 100.0;
    }
}
