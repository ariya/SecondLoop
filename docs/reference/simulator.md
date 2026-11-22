# Robot Simulator

`RobotSimulator` simulates a robot on a laptop. It runs the same interpreter, and
for now it simulates [telemetry](../robot/telemetry.md),
[gamepad](../robot/gamepad.md) 1, the [battery](../robot/battery.md), a flywheel
and a mecanum drivetrain driven by [motors](../robot/motors.md), the
[IMU](../robot/imu.md) and a [Pinpoint](../robot/pinpoint.md). Start it with:

```bash
$ java -cp build/classes secondloop.RobotSimulator
```

Then open <http://localhost:8080> in a browser. Like the Driver Hub, the page
shows the telemetry and reads gamepad 1 from a controller plugged into the
laptop.

To connect to it, run the REPL client in another terminal:

```bash
$ ./secondloop.py localhost
```

Port 8080 also answers `/js/rcInfo.json`, as the robot controller does, so
`secondloop.py` and its [web REPL](repl.md#web-repl) work against
`RobotSimulator` unchanged.

The battery is a goBILDA
[12 V, 3000 mAh NiMH pack](https://www.gobilda.com/12v-nimh-nested-battery-3000mah-mh-fc-xt30-connector/).
At rest it reads slightly below 13 V,
between 12.5 and 12.95 V, picked at random when the simulator starts. The
Flywheel and the drive motors drain it. A motor draws a lot of current while
it speeds up and a little to keep turning, so the voltage dips when a motor
starts and recovers as it reaches speed. As the battery empties, its voltage
at rest slowly falls. Here the Flywheel starts at full power, and the voltage
is read at once and after two seconds; then the Flywheel stops, and two
seconds later the voltage is back:

```clojure
user=> (battery/voltage)
12.74
user=> (motor/set-power "flywheel" 1)
1.0
user=> (battery/voltage)
11.57
user=> (battery/voltage)
12.6
user=> (motor/set-power "flywheel" 0)
0.0
user=> (battery/voltage)
12.74
```

Starting all four drive motors at full power dips it to about 10 V. A
two-and-a-half-minute match of driving and launching uses about 7% of the
charge, and the voltage at rest falls by about a tenth of a volt.

The `"flywheel"` motor launches the game artifacts. It is a goBILDA
[5203 Series 6000 RPM motor](https://www.gobilda.com/5203-series-yellow-jacket-motor-1-1-ratio-24mm-length-8mm-rex-shaft-6000-rpm-3-3-5v-encoder/)
with a 28-tick encoder, so at full power it turns at
about 2800 ticks per second, scaled by the battery voltage. Its speed follows a
first-order response to the power, with a time constant of 0.4 s, so at a fixed
power it covers 63% of the gap in 0.4 s and is nearly there after 2 s.

With [`motor/set-velocity`](../robot/motors.md#set-velocity), a PIDF controller sets the power 20 times a second
from the measured speed, using the gains from [`motor/set-pidf`](../robot/motors.md#set-pidf), so tuning them
changes the response as on a real hub (see
[PIDF Tuning](../examples/pidf-tuning.md)). Its output is in units where 32767
is full power, and the integral adds the error once per update. With the
hub's default gains it starts at full power, so here it spins up to a launch
speed faster than a fixed power would, read after about 0.4 s and again after
2 s:

```clojure
user=> (motor/set-velocity "flywheel" 2000)
2000.0
user=> (motor/velocity "flywheel")
1544.0
user=> (motor/velocity "flywheel")
1989.0
```

With [`:run-to-position`](../robot/motors.md#set-run-mode) it steers to the target, no faster than the power allows,
and overshoots by about 16% before settling.

Every simulated motor starts with [`:brake`](../robot/motors.md#set-zero-power-behavior). At power 0 (or velocity 0 with
[`motor/set-velocity`](../robot/motors.md#set-velocity)), a braking motor slows down with its usual time
constant, while with [`:float`](../robot/motors.md#set-zero-power-behavior) it coasts and only friction slows it, so the
Flywheel then takes about 3 s, rather than 0.4 s, to lose 63% of its speed.

The mecanum drivetrain has the four motors of the
[Mecanum Drive](../examples/mecanum-drive.md) example, named `"front_left_drive"`,
`"front_right_drive"`, `"back_left_drive"` and `"back_right_drive"`. They are
312 RPM motors (for example goBILDA's
[5203 Series](https://www.gobilda.com/5203-series-yellow-jacket-planetary-gear-motor-19-2-1-ratio-24mm-length-8mm-rex-shaft-312-rpm-3-3-5v-encoder/))
on 104 mm mecanum wheels, with the left two mounted the
other way round, so a program reverses them as on a real robot. Each follows
the same first-order response, with a time constant of 0.2 s for the weight of
the robot, and the robot moves by how far each wheel rolled, without slipping.
At power 0.3 it drives about 20 inches per second. With [`:float`](../robot/motors.md#set-zero-power-behavior), the wheels
coast with a time constant of 0.5 s, so the robot rolls on further after it
stops.

The drive motors have no encoders, since driving is done by power. As on a real
robot, [`motor/velocity`](../robot/motors.md#velocity) and [`motor/current-position`](../robot/motors.md#current-position) read 0, and
[`motor/set-velocity`](../robot/motors.md#set-velocity) runs a wheel at full power because its controller sees no
speed.

The [IMU](../robot/imu.md) and the [Pinpoint](../robot/pinpoint.md) both follow the robot. The [IMU](../robot/imu.md)'s yaw is its heading,
which increases as it turns left; the floor is flat, so pitch and roll stay at
0. The [Pinpoint](../robot/pinpoint.md), named `"pinpoint"`, tracks the robot exactly, as if its pods
never slip, from where it was last reset, with `x` forward and `y` to the left. The
pod type and offsets given to [`pinpoint/init`](../robot/pinpoint.md#init) are taken to be right, and the
simulated pods count the right way with `:forward :forward`. Setting one to
`:reverse` flips its axis, as on a real robot whose pod is set the wrong way.

With `mecanum-drive` from the example, turn on the spot for one second, then
drive forward for two:

```clojure
user=> (imu/init :up :backward)
nil
user=> (pinpoint/init :swingarm -3.3 -6.6)
:ready
user=> (mecanum-drive 0 0 0.3)
[0.3 -0.3 0.3 -0.3]
user=> (mecanum-drive 0 0 0)
[0.0 0.0 0.0 0.0]
user=> (Math/toDegrees (imu/yaw))
-91.5787017918707
user=> (mecanum-drive 0.3 0 0)
[0.3 0.3 0.3 0.3]
user=> (mecanum-drive 0 0 0)
[0.0 0.0 0.0 0.0]
user=> (pinpoint/pose)
{:x -1.278853782668248, :y -43.12244461261836, :heading -1.6005536231698685}
```
