package secondloop;

import com.qualcomm.robotcore.hardware.VoltageSensor;

/**
 * The simulated robot of RobotSimulator: the battery, the Flywheel, and a mecanum
 * drivetrain with the pose it drives. Every motor runs from the one battery, so
 * they all advance together, in steps of STEP_SECONDS: the motors move, their
 * current drains the battery, and the robot moves by how far each wheel
 * rolled, using the usual mecanum kinematics. The wheels do not slip, and the
 * drive motors have no encoders, since driving is done by power.
 *
 * The pose is in inches and radians, as the Pinpoint reports it: x forward and
 * y to the left of where the robot started, heading counterclockwise.
 */
final class SimRobot {
    /** Launches the game artifacts. */
    static final String FLYWHEEL = "flywheel";
    /** The drive motors, named as in the docs' mecanum example: front left, front right, back left, back right. */
    static final String[] NAMES = {"front_left_drive", "front_right_drive", "back_left_drive", "back_right_drive"};
    /** A goBILDA 104 mm mecanum wheel. */
    static final double WHEEL_CIRCUMFERENCE = Math.PI * 104 / 25.4;
    /** Half the distances between the left and right wheels, and between the front and back wheels. */
    static final double HALF_TRACK = 7;
    static final double HALF_WHEELBASE = 6;
    /** Small enough that the robot follows a curve when it drives and turns at once. */
    static final double STEP_SECONDS = 0.005;

    final SimBattery battery;
    final SimMotor flywheel;
    final SimMotor[] motors = new SimMotor[4];
    private final SimMotor.Clock clock;
    private SimPinpoint pinpoint;
    private long lastNanos;
    private double x;
    private double y;
    private double heading;

    SimRobot(final SimBattery battery, SimMotor.Clock clock) {
        this.battery = battery;
        this.clock = clock;
        // The voltage the motors see within a step, without advancing the robot again.
        VoltageSensor terminals = new VoltageSensor() {
            @Override
            public double getVoltage() {
                return battery.volts();
            }
        };
        flywheel = SimMotor.flywheel(terminals, clock, this);
        for (int i = 0; i < 4; i++) {
            motors[i] = SimMotor.driveWheel(terminals, clock, this);
        }
        battery.attach(this);
        lastNanos = clock.nanos();
    }

    /** The Pinpoint riding on this robot, moved along with it. */
    synchronized void attach(SimPinpoint pinpoint) {
        this.pinpoint = pinpoint;
    }

    synchronized void advance() {
        long now = clock.nanos();
        double elapsed = (now - lastNanos) / 1e9;
        lastNanos = now;
        while (elapsed > 0) {
            double dt = Math.min(elapsed, STEP_SECONDS);
            elapsed -= dt;
            // How far each wheel rolled forward. The left motors are mounted
            // the other way round, which is why programs reverse them.
            double fl = -motors[0].step(dt) * WHEEL_CIRCUMFERENCE;
            double fr = motors[1].step(dt) * WHEEL_CIRCUMFERENCE;
            double bl = -motors[2].step(dt) * WHEEL_CIRCUMFERENCE;
            double br = motors[3].step(dt) * WHEEL_CIRCUMFERENCE;
            flywheel.step(dt);
            double amps = flywheel.amps();
            for (SimMotor motor : motors) {
                amps += motor.amps();
            }
            battery.drain(amps, dt);
            double forward = (fl + fr + bl + br) / 4;
            double left = (-fl + fr + bl - br) / 4;
            double turn = (-fl + fr - bl + br) / (4 * (HALF_TRACK + HALF_WHEELBASE));
            double mid = heading + turn / 2;
            x += forward * Math.cos(mid) - left * Math.sin(mid);
            y += forward * Math.sin(mid) + left * Math.cos(mid);
            heading += turn;
            if (pinpoint != null) {
                pinpoint.step(forward, left, turn, dt);
            }
        }
    }

    /** The heading in radians, counterclockwise, not wrapped. */
    synchronized double heading() {
        advance();
        return heading;
    }

    /** {x, y, heading}: inches and radians. */
    synchronized double[] pose() {
        advance();
        return new double[] {x, y, heading};
    }
}
