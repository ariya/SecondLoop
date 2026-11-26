package secondloop;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Random;

/** Checks battery/voltage against RobotSimulator's simulated battery, at rest and drained by the motors. Run: java -cp build/classes secondloop.SimBatteryTest */
public final class SimBatteryTest {
    private static int failures;
    private static int count;

    private final StringWriter buffer;
    private final Repl repl;

    private SimBatteryTest() {
        Env globals = new Clojure().createGlobals();
        BatteryModule.install(globals, RobotSimulator.createOpMode());
        buffer = new StringWriter();
        repl = new ClojureRepl(globals, new PrintWriter(buffer));
    }

    private SimBatteryTest(Repl repl, StringWriter buffer) {
        this.repl = repl;
        this.buffer = buffer;
    }

    private void check(String expected, String line) {
        buffer.getBuffer().setLength(0);
        repl.feed(line);
        check(expected, buffer.toString().trim(), line);
    }

    private static void check(String expected, String actual, String label) {
        count++;
        if (!actual.equals(expected)) {
            failures++;
            System.out.println("FAIL: " + label);
            System.out.println("  expected: " + expected);
            System.out.println("  actual:   " + actual);
        }
    }

    /** The motors of a robot drain its battery, which sags under load. */
    private static void drain() {
        SimMotorTest.StepClock clock = new SimMotorTest.StepClock();
        SimBattery battery = new SimBattery(12.8, SimBattery.CAPACITY, SimBattery.RESISTANCE);
        SimRobot robot = new SimRobot(battery, clock);
        Env globals = new Clojure().createGlobals();
        BatteryModule.install(globals, RobotSimulator.createOpMode(robot));
        StringWriter out = new StringWriter();
        Repl repl = new ClojureRepl(globals, new PrintWriter(out));
        SimBatteryTest t = new SimBatteryTest(repl, out);

        t.check("12.8", "(battery/voltage)");
        robot.motors[0].setDirection(DcMotorSimple.Direction.REVERSE);
        robot.motors[2].setDirection(DcMotorSimple.Direction.REVERSE);
        for (SimMotor m : robot.motors) {
            m.setPower(1);
        }
        clock.seconds(0.01);
        t.check("9.93", "(battery/voltage)"); // four motors starting at full power
        clock.seconds(3);
        t.check("12.07", "(battery/voltage)"); // cruising at full speed
        for (SimMotor m : robot.motors) {
            m.setPower(0);
        }
        clock.seconds(2);
        t.check("12.8", "(battery/voltage)"); // at rest again, a little emptier

        robot.flywheel.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        robot.flywheel.setVelocity(2000);
        clock.seconds(0.01);
        t.check("12.11", "(battery/voltage)"); // the Flywheel spinning up
        clock.seconds(3);
        t.check("12.74", "(battery/voltage)"); // and at speed

        // A match: driving back and forth at 0.6 for 150 s with the Flywheel at speed.
        double before = battery.charge();
        for (int i = 0; i < 75; i++) {
            for (SimMotor m : robot.motors) {
                m.setPower(i % 2 == 0 ? 0.6 : -0.6);
            }
            clock.seconds(2);
        }
        for (SimMotor m : robot.motors) {
            m.setPower(0);
        }
        robot.flywheel.setVelocity(0);
        clock.seconds(5);
        t.check("12.69", "(battery/voltage)");
        check("7", String.valueOf(Math.round((before - battery.charge()) * 100)), "percent of the charge a match uses");
    }

    public static void main(String[] args) {
        SimBatteryTest t = new SimBatteryTest();
        t.check("true", "(<= 12.5 (battery/voltage) 12.95)");
        t.check("true", "(= (battery/voltage) (battery/voltage))");
        t.check("true", "(let [v (* 100 (battery/voltage))] (< (Math/abs (- v (Math/round v))) 1e-6))");

        // Many seeds: always within range and rounded to hundredths.
        boolean ok = true;
        for (int seed = 0; seed < 1000; seed++) {
            double v = new SimBattery(new Random(seed)).getVoltage();
            ok &= v >= SimBattery.MIN && v <= SimBattery.MAX && v == Math.round(v * 100) / 100.0;
        }
        check("true", String.valueOf(ok), "1000 random batteries");

        drain();

        System.out.println((count - failures) + "/" + count + " passed");
        if (failures > 0) {
            System.exit(1);
        }
    }
}
