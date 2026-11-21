package secondloop;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;
import com.qualcomm.robotcore.hardware.VoltageSensor;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Checks RobotSimulator's simulated motor, with time stepped by hand. Run: java -cp build/classes secondloop.SimMotorTest */
public final class SimMotorTest {
    private static int failures;
    private static int count;

    /** Time that moves only when a test says so. */
    static final class StepClock extends SimMotor.Clock {
        long now;

        @Override
        long nanos() {
            return now;
        }

        void seconds(double s) {
            now += (long) (s * 1e9);
        }
    }

    private static void check(String expected, Object actual, String label) {
        count++;
        if (!String.valueOf(actual).equals(expected)) {
            failures++;
            System.out.println("FAIL: " + label);
            System.out.println("  expected: " + expected);
            System.out.println("  actual:   " + actual);
        }
    }

    public static void main(String[] args) {
        final double[] volts = {12};
        VoltageSensor battery = new VoltageSensor() {
            @Override
            public double getVoltage() {
                return volts[0];
            }
        };
        StepClock clock = new StepClock();
        SimMotor m = SimMotor.flywheel(battery, clock, null);

        check("0.0", m.getVelocity(), "at rest");
        m.setPower(1);
        clock.seconds(0.4);
        check("1770.0", m.getVelocity(), "63% of 2800 after one time constant");
        clock.seconds(4.6);
        check("2800.0", m.getVelocity(), "free speed at 12 V");
        m.setPower(0);
        clock.seconds(5);
        check("0.0", m.getVelocity(), "coasts to a stop");
        check("14000", m.getCurrentPosition(), "position is the area under the velocity: 2800 for 5 s");

        check("BRAKE", m.getZeroPowerBehavior(), "brakes by default");
        m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        m.setPower(1);
        clock.seconds(5);
        m.setPower(0);
        clock.seconds(0.4);
        check("2450.0", m.getVelocity(), "floats: loses only 12% in 0.4 s");
        clock.seconds(2.6);
        check("1030.0", m.getVelocity(), "floats: 37% left after coastTau");
        m.setPower(1);
        clock.seconds(5);
        m.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        m.setVelocity(0);
        clock.seconds(3);
        check("1030.0", m.getVelocity(), "floats at velocity 0 too");
        m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        m.setPower(0);
        m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        clock.seconds(5);
        check("0.0", m.getVelocity(), "brakes again");

        volts[0] = 12.6;
        m.setPower(0.5);
        clock.seconds(10);
        check("1470.0", m.getVelocity(), "speed scales with battery voltage");
        volts[0] = 12;

        m.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        m.setVelocity(1500);
        clock.seconds(10);
        check("1500.0", m.getVelocity(), "holds the set velocity");
        m.setVelocity(5000);
        clock.seconds(10);
        check("2800.0", m.getVelocity(), "no faster than free speed");

        // The gains change the response: F alone is right only at the voltage it was measured at.
        m.setPIDFCoefficients(DcMotor.RunMode.RUN_USING_ENCODER, new PIDFCoefficients(0, 0, 0, 32767 / 2800.0));
        m.setVelocity(1400);
        clock.seconds(5);
        check("1400.0", m.getVelocity(), "F alone gives the speed at 12 V");
        volts[0] = 11;
        clock.seconds(5);
        check("1283.0", m.getVelocity(), "F alone falls short at 11 V");
        m.setPIDFCoefficients(DcMotor.RunMode.RUN_USING_ENCODER, new PIDFCoefficients(0, 0.5, 0, 32767 / 2800.0));
        clock.seconds(5);
        check("1400.0", m.getVelocity(), "I closes the gap");
        volts[0] = 12;

        m.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        check("0", m.getCurrentPosition(), "encoder reset");
        check("0.0", m.getPower(), "power off while resetting");
        clock.seconds(5);
        check("0.0", m.getVelocity(), "stops after reset");
        check("0", m.getCurrentPosition(), "encoder held at 0 while it coasts");

        m.setTargetPosition(1000);
        m.setPower(0.5);
        m.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        clock.seconds(0.5);
        check("true", m.getVelocity() <= 1400, "no faster than the power allows");
        clock.seconds(1.5);
        check("true", m.getCurrentPosition() > 1000, "overshoots a little");
        clock.seconds(6);
        check("1000", m.getCurrentPosition(), "settles at the target");
        check("0.0", m.getVelocity(), "stopped at the target");

        m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        m.setDirection(DcMotorSimple.Direction.REVERSE);
        check("-1000", m.getCurrentPosition(), "reverse flips the position");
        m.setPower(1);
        clock.seconds(5);
        check("2800.0", m.getVelocity(), "reverse: positive power still reads positive");

        // Through the REPL, by the name in RobotSimulator's configuration.
        StepClock replClock = new StepClock();
        Env globals = Builtins.createGlobals();
        MotorModule.install(globals, RobotSimulator.createOpMode(replClock));
        StringWriter out = new StringWriter();
        Repl repl = new Repl(globals, new PrintWriter(out));
        repl.feed("(motor/set-velocity \"flywheel\" 1500)");
        replClock.seconds(0.4);
        out.getBuffer().setLength(0);
        repl.feed("(motor/velocity \"flywheel\")");
        // The battery starts at a random voltage, so check a range: past the
        // 63% of a fixed power, as the controller starts at full power.
        double spun = Double.parseDouble(out.toString().trim());
        check("true", spun > 1200 && spun < 1450, "the default gains spin up at full power at first: " + spun);
        out.getBuffer().setLength(0);
        repl.feed("(motor/power \"Flywheel\")");
        check("Execution error: No motor named \"Flywheel\" in the robot configuration", out.toString().trim(),
                "names are case-sensitive");

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
