package secondloop;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;
import com.qualcomm.robotcore.hardware.VoltageSensor;

/**
 * A DC motor of RobotSimulator, as a first-order system: the velocity moves toward
 * the commanded velocity with time constant tau, like a motor with a load
 * whose inertia dominates. The commanded velocity is power times the free
 * speed, which scales with the battery voltage. In RUN_USING_ENCODER a PIDF
 * controller sets the power from the encoder every CONTROL_SECONDS, so the
 * gains change the response as on a real hub. In RUN_TO_POSITION a
 * proportional controller steers to the target, no faster than the power allows.
 *
 * A motor without an encoder reads 0 for velocity and position, and the
 * controllers that need it misbehave as on a real hub: RUN_USING_ENCODER runs
 * at full power, and RUN_TO_POSITION never gets there.
 *
 * The current follows from the same model: the motor draws its stall current,
 * scaled by how far it is from the commanded velocity, to accelerate, plus a
 * small current to keep its load turning. The battery supplies that times the
 * power the hub applies, so a motor at power 0 draws nothing.
 *
 * At zero power (power 0, or velocity 0 in RUN_USING_ENCODER), BRAKE shorts
 * the windings, so the motor slows down with tau, as when driven to 0. FLOAT
 * lets it coast, and only friction slows it, with the longer coastTau.
 *
 * A motor on its own advances on every call, from the time since the previous
 * one. A motor in a SimRobot is advanced by it instead, so that all motors and
 * the battery move in step; it also shares the robot's lock.
 */
final class SimMotor implements DcMotorEx {
    static final double NOMINAL_VOLTS = 12;
    /**
     * How fast RUN_TO_POSITION closes the gap: ticks per second for each tick
     * away. With the lag of tau = 0.4 s it overshoots by about 16%.
     */
    static final double POSITION_GAIN = 2.5;
    /** The step of the simulation, small enough for RUN_TO_POSITION to settle. */
    static final double STEP_SECONDS = 0.001;
    /**
     * How often the velocity controller updates the power. Its output is in
     * units where FULL_POWER is full power, and the integral adds the error
     * once per update, so the hub's default gains settle well, and an F of
     * FULL_POWER divided by the top speed gives about the right power alone.
     */
    static final double CONTROL_SECONDS = 0.05;
    static final double FULL_POWER = 32767;

    /** Where the time comes from, so that tests can step it. */
    static class Clock {
        long nanos() {
            return System.nanoTime();
        }
    }

    private final double freeSpeed;
    private final double ticksPerRev;
    private final double tau;
    private final double coastTau;
    private final double stallAmps;
    private final double loadAmps;
    private final boolean hasEncoder;
    private final VoltageSensor battery;
    private final Clock clock;
    private final SimRobot robot;
    private final Object lock;

    private Direction direction = Direction.FORWARD;
    private RunMode mode = RunMode.RUN_WITHOUT_ENCODER;
    private ZeroPowerBehavior zeroPowerBehavior = ZeroPowerBehavior.BRAKE;
    private double power;
    private double targetVelocity;
    private int targetPosition;
    private PIDFCoefficients pidf = new PIDFCoefficients(10, 3, 0, 0);

    // The velocity controller: the power it holds until the next update.
    private double controlPower;
    private double integral;
    private double lastError = Double.NaN;
    private double untilControl;

    // In the motor's own direction; FORWARD and REVERSE only flip what is reported.
    private double velocity;
    /** What the encoder counts: reset by STOP_AND_RESET_ENCODER, and always 0 without one. */
    private double count;
    /** The average battery current over the last step. */
    private double amps;
    private long lastNanos;

    /**
     * @param freeSpeed ticks per second with no load at full power and NOMINAL_VOLTS
     * @param ticksPerRev encoder ticks for one turn of the output shaft
     * @param tau seconds to reach 63% of a change in commanded velocity
     * @param coastTau seconds to lose 63% of the velocity when it floats at zero power
     * @param stallAmps current with the shaft held, at NOMINAL_VOLTS
     * @param loadAmps current to keep the load turning at free speed
     * @param robot the robot that advances this motor, or null for a motor on its own
     */
    SimMotor(double freeSpeed, double ticksPerRev, double tau, double coastTau, double stallAmps, double loadAmps,
             boolean hasEncoder, VoltageSensor battery, Clock clock, SimRobot robot) {
        this.freeSpeed = freeSpeed;
        this.ticksPerRev = ticksPerRev;
        this.tau = tau;
        this.coastTau = coastTau;
        this.stallAmps = stallAmps;
        this.loadAmps = loadAmps;
        this.hasEncoder = hasEncoder;
        this.battery = battery;
        this.clock = clock;
        this.robot = robot;
        lock = robot == null ? this : robot;
        lastNanos = clock.nanos();
    }

    /**
     * A goBILDA 5203 motor at 6000 RPM with a 28-tick encoder, spinning a
     * flywheel: 9.2 A at stall, and about 1 A to keep the flywheel at speed.
     * Left to float, the heavy flywheel takes a few seconds to spin down.
     */
    static SimMotor flywheel(VoltageSensor battery, Clock clock, SimRobot robot) {
        return new SimMotor(6000 / 60.0 * 28, 28, 0.4, 3, 9.2, 1.0, true, battery, clock, robot);
    }

    /**
     * A goBILDA 5203 motor at 312 RPM (537.7 ticks per turn) driving a wheel,
     * with no encoder plugged in: 9.2 A at stall, and about 1.5 A to roll its
     * share of the robot at full speed. Left to float, the robot rolls on
     * for about a second, slowed by the friction of the rollers.
     */
    static SimMotor driveWheel(VoltageSensor battery, Clock clock, SimRobot robot) {
        return new SimMotor(312 / 60.0 * 537.7, 537.7, 0.2, 0.5, 9.2, 1.5, false, battery, clock, robot);
    }

    private double sign() {
        return direction == Direction.REVERSE ? -1 : 1;
    }

    private double maxSpeed() {
        return freeSpeed * battery.getVoltage() / NOMINAL_VOLTS;
    }

    /** True when the hub applies no power, so the zero power behavior decides. */
    private boolean zeroPower() {
        switch (mode) {
            case RUN_USING_ENCODER:
                return targetVelocity == 0;
            case STOP_AND_RESET_ENCODER:
                return true;
            default:
                return power == 0;
        }
    }

    /** The velocity the motor is driven toward, in its own direction, when max is its top speed now. */
    private double commanded(double max) {
        switch (mode) {
            case RUN_USING_ENCODER:
                return sign() * controlPower * max;
            case RUN_TO_POSITION:
                double limit = Math.abs(power) * max;
                return Math.max(-limit, Math.min(limit, POSITION_GAIN * (sign() * targetPosition - count)));
            case STOP_AND_RESET_ENCODER:
                return 0;
            default:
                return sign() * power * max;
        }
    }

    /**
     * Runs the motor for elapsed seconds and returns how far the shaft turned,
     * in turns. The caller holds the lock.
     */
    double step(double elapsed) {
        double ticks = 0;
        double coulombs = 0;
        double total = elapsed;
        while (elapsed > 0) {
            // Only the two controllers change their command as the motor moves.
            boolean controlled = mode == RunMode.RUN_TO_POSITION || mode == RunMode.RUN_USING_ENCODER;
            double dt = controlled ? Math.min(elapsed, STEP_SECONDS) : elapsed;
            elapsed -= dt;
            if (mode == RunMode.RUN_USING_ENCODER) {
                untilControl -= dt;
                if (untilControl <= 1e-9) {
                    control();
                    untilControl += CONTROL_SECONDS;
                }
            }
            // The exact first-order response over dt, for a command held constant.
            double max = maxSpeed();
            double target = commanded(max);
            double t = zeroPowerBehavior == ZeroPowerBehavior.FLOAT && zeroPower() ? coastTau : tau;
            double decay = Math.exp(-dt / t);
            double gap = (target - velocity) * t * (1 - decay); // the gap to the command, summed over dt
            double moved = target * dt - gap;
            velocity = target + (velocity - target) * decay;
            // The winding current, averaged over dt: stall current for the
            // gap, plus the load. The battery sees it times the power applied.
            double motorAmps = (stallAmps * gap + loadAmps * moved) / (freeSpeed * dt);
            double applied = max > 0 ? target / max : 0;
            coulombs += Math.max(0, applied * motorAmps) * dt;
            ticks += moved;
            if (hasEncoder) {
                count += moved;
            }
        }
        if (mode == RunMode.STOP_AND_RESET_ENCODER) {
            count = 0; // the hub holds the count at 0 until the mode changes
        }
        amps = total > 0 ? coulombs / total : 0;
        return ticks / ticksPerRev;
    }

    /**
     * One update of the velocity controller, in the direction the caller sees.
     * A motor without an encoder reads 0, so the error never closes and the
     * power ends up at full. The caller holds the lock.
     */
    private void control() {
        if (targetVelocity == 0) {
            resetControl();
            return;
        }
        double measured = hasEncoder ? Math.round(sign() * velocity) : 0;
        double error = targetVelocity - measured;
        double derivative = Double.isNaN(lastError) ? 0 : (error - lastError) / CONTROL_SECONDS;
        lastError = error;
        integral += error;
        double out = (pidf.f * targetVelocity + pidf.p * error + pidf.i * integral + pidf.d * derivative) / FULL_POWER;
        if (Math.abs(out) > 1) {
            integral -= error; // stop winding up while the power is at its limit
            out = Math.signum(out);
        }
        controlPower = out;
    }

    private void resetControl() {
        controlPower = 0;
        integral = 0;
        lastError = Double.NaN;
    }

    /** The average battery current over the last step. The caller holds the lock. */
    double amps() {
        return amps;
    }

    private void advance() {
        if (robot != null) {
            robot.advance();
            return;
        }
        long now = clock.nanos();
        step((now - lastNanos) / 1e9);
        lastNanos = now;
    }

    @Override
    public void setDirection(Direction direction) {
        synchronized (lock) {
            advance();
            this.direction = direction;
        }
    }

    @Override
    public Direction getDirection() {
        synchronized (lock) {
            return direction;
        }
    }

    @Override
    public void setZeroPowerBehavior(ZeroPowerBehavior zeroPowerBehavior) {
        synchronized (lock) {
            advance();
            this.zeroPowerBehavior = zeroPowerBehavior;
        }
    }

    @Override
    public ZeroPowerBehavior getZeroPowerBehavior() {
        synchronized (lock) {
            return zeroPowerBehavior;
        }
    }

    @Override
    public void setPower(double power) {
        synchronized (lock) {
            advance();
            this.power = power;
        }
    }

    @Override
    public double getPower() {
        synchronized (lock) {
            return mode == RunMode.STOP_AND_RESET_ENCODER ? 0 : power;
        }
    }

    @Override
    public void setMode(RunMode mode) {
        synchronized (lock) {
            advance();
            if (mode != this.mode) {
                resetControl();
                untilControl = 0;
            }
            this.mode = mode;
            if (mode == RunMode.STOP_AND_RESET_ENCODER) {
                power = 0;
                targetVelocity = 0;
            }
        }
    }

    @Override
    public RunMode getMode() {
        synchronized (lock) {
            return mode;
        }
    }

    @Override
    public void setTargetPosition(int position) {
        synchronized (lock) {
            advance();
            targetPosition = position;
        }
    }

    @Override
    public int getTargetPosition() {
        synchronized (lock) {
            return targetPosition;
        }
    }

    @Override
    public int getCurrentPosition() {
        synchronized (lock) {
            advance();
            return (int) Math.round(sign() * count);
        }
    }

    @Override
    public void setVelocity(double angularRate) {
        synchronized (lock) {
            advance();
            targetVelocity = angularRate;
        }
    }

    @Override
    public double getVelocity() {
        synchronized (lock) {
            advance();
            // The hub reports whole ticks per second.
            return hasEncoder ? Math.round(sign() * velocity) : 0;
        }
    }

    @Override
    public void setPIDFCoefficients(DcMotor.RunMode mode, PIDFCoefficients pidfCoefficients) {
        synchronized (lock) {
            pidf = new PIDFCoefficients(pidfCoefficients.p, pidfCoefficients.i, pidfCoefficients.d, pidfCoefficients.f);
        }
    }

    @Override
    public PIDFCoefficients getPIDFCoefficients(DcMotor.RunMode mode) {
        synchronized (lock) {
            return new PIDFCoefficients(pidf.p, pidf.i, pidf.d, pidf.f);
        }
    }
}
