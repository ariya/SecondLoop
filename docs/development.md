# Development

Second Loop keeps its sources in normal Java files and combines them into one file,
so the robot only needs a single upload through
[OnBot Java](https://ftc-docs.firstinspires.org/en/latest/programming_resources/onbot_java/OnBot-Java-Tutorial.html)
or a single file added to an
[Android Studio](https://ftc-docs.firstinspires.org/en/latest/programming_resources/android_studio_java/opmode/opmode.html)
project.

## Layout

| Path | Contents |
| --- | --- |
| `src/secondloop/` | The interpreter. Plain Java, no FTC dependency. |
| `src/ftc/` | Modules that use the FTC SDK, such as `GamepadModule`, `MotorModule`, `ServoModule`, `BatteryModule`, `ImuModule`, `PinpointModule` and `TelemetryModule`. |
| `src/SecondLoop.java` | The OpMode template. The sources are inserted at `// @@SECONDLOOP_SOURCES@@`. |
| `tools/amalgamate.py` | Merges everything into `build/SecondLoop.java`. |
| `secondloop.py` | A terminal client with line editing and history, used instead of `nc`. |
| `test/secondloop/` | `ReplTest`, `GamepadTest`, `MotorTest`, `ServoTest`, `BatteryTest`, `ImuTest`, `PinpointTest`, `TelemetryTest`, `SimTelemetryTest`, `SimImuTest`, `SimBatteryTest`, `SimMotorTest`, `SimDrivetrainTest`, `SimPinpointTest` and `SimWebUiTest`, plus `RobotSimulator` and its web page, `robot-simulator.html`. |
| `test/ftc-stubs/` | Minimal stand-ins for the FTC SDK classes, used only for compiling and testing. |
| `docs/` | This documentation, built with Zensical. |

Functions live in `Builtins`, `MathBuiltins`, `StringBuiltins` and
`CollBuiltins`; helpers written in the REPL language itself live in
`Prelude.java`. Each file's top-level types become static nested classes of
`SecondLoop` in the combined file.

## Build

```console
$ ./build.sh
```

The script amalgamates, compiles against the FTC stand-ins, runs every test,
and then compile-checks the single file. It needs Python 3 and a JDK, and if
there is no `javac` binary it uses the JDK's compiler module instead.

The tests pair a REPL form with its expected printed output:

```java
t.check("6.0", "(+ 1 2 3)");
```

FTC modules have their own test classes, which run against the stand-ins. Any
new feature or fix gets a check there or in `ReplTest`.

## Constraints

- **Java 8 only.** The Control Hub runs Android 7.1, which rules out lambdas,
  method references, streams, `String.join`, `var` and `List.of`. Write
  anonymous inner classes and loops.
- **Only the FTC SDK and the standard library.** No third-party dependencies.
- **Thread safety.** The REPL sessions, futures and the OpMode thread run at
  the same time, and FTC objects such as `Telemetry` are not thread-safe, so the
  OpMode thread does the rendering (see `TelemetryModule.render`).
- **Stopping must be prompt.** Loops call `Interp.checkStop()`, `catch` never
  swallows `Interrupted`, and `Future.cancel()` waits for the thread to end.

## Telemetry

FTC's `Telemetry` forgets its lines after every `update()`, so `TelemetryModule`
holds them instead: the functions only edit a list, and the OpMode thread draws
it on every status tick, every 100 ms (`STATUS_MILLIS` in
`src/SecondLoop.java`). Nothing in the language calls `telemetry.update()`, and
lines stay until `(telemetry/clear)` runs. It works during INIT, before START is
pressed.

Only the newest 50 lines are kept (`MAX_LINES`), so a loop that adds a line
every round cannot fill memory. In a loop, clear and add the lines each round,
so the display shows the current state rather than a history:

```clojure
(telemetry/clear)
(telemetry/add-line "power" power)
(telemetry/add-line (format "heading %.1f" heading))
```

`showStatus` adds two lines above these, the version such as `Second Loop
version 1.1.17` and then the address such as `Ready at 192.168.43.1:9001 (1
client)`, so the copy that runs and the address a laptop needs are on the
Driver Hub from INIT. When
the robot has several addresses, `ReplServer.preferredAddress` picks
`192.168.43.1` or `192.168.49.1` if either is there.

## RobotSimulator

`RobotSimulator` simulates a robot on a laptop. It runs the same interpreter, and
for now it simulates telemetry, gamepad 1, the battery, a flywheel, a mecanum
drivetrain, the IMU and a Pinpoint:

```console
$ java -cp build/classes secondloop.RobotSimulator --stdin  # type directly
$ java -cp build/classes secondloop.RobotSimulator          # serve on port 9001
$ ./secondloop.py localhost                             # in another terminal
```

It also has a web UI at <http://localhost:8080/>. It shows the telemetry, in
white text on black as on the Driver Hub, with the battery voltage in the
top-right corner, and reads
gamepad 1 from a controller plugged into the laptop by USB or Bluetooth: press
any button on it once the page is open. The page draws the controller and
shows what is pressed; without a controller, the drawing is dimmed. If the page
stops sending, for example because its tab is closed, gamepad 1 goes back to neutral after half a second, so the robot cannot
keep driving on a stick that seems stuck. Gamepad 2 stays neutral.

Port 8080 also answers `/js/rcInfo.json`, as the robot controller does, so
`secondloop.py` works against `RobotSimulator` unchanged.

Telemetry also shows in the simulator's own terminal, on its standard output.
Like the OpMode, the simulator redraws telemetry every 100 ms. It prints only what changed: lines added since the last redraw,
or `--------` followed by the current lines after `telemetry/clear`. With
`(telemetry/add-line "power" 0.5)`, then `(telemetry/add-line "heading" 12.3)`,
`(telemetry/clear)` and `(telemetry/add-line "done")` typed in the REPL client,
the simulator's terminal shows:

```console
power 0.5
heading 12.3
--------
done
```

Forms that run within one redraw show only the end result, as on the Driver Hub.

The battery is a goBILDA
[12 V, 3000 mAh NiMH pack](https://www.gobilda.com/12v-nimh-nested-battery-3000mah-mh-fc-xt30-connector/).
At rest it reads slightly below 13 V,
between 12.5 and 12.95 V, picked at random when the simulator starts. The
Flywheel and the drive motors drain it: a motor draws a lot of current while
it speeds up and a little to keep turning, so the voltage dips when a motor
starts and recovers as it reaches speed. As the battery empties, its voltage
at rest slowly falls. Here the Flywheel starts at full power, and the voltage
is read at once and after two seconds; then the Flywheel stops, and two
seconds later the voltage is back:

```console
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
first-order response with a time constant of 0.4 s: after a change it covers
63% of the gap in 0.4 s and is nearly there after 2 s. Here it spins up to a
launch speed, and its speed is read after about 0.4 s and again after 2 s:

```console
user=> (motor/set-velocity "flywheel" 2000)
2000.0
user=> (motor/velocity "flywheel")
1189.0
user=> (motor/velocity "flywheel")
1985.0
```

With `motor/set-velocity` it is as if the hub's controller held the speed
perfectly: `motor/set-pidf` is accepted but does not change the response. With
`:run-to-position` it steers to the target, no faster than the power allows,
and overshoots by about 16% before settling.

Every simulated motor starts with `:brake`. At power 0 (or velocity 0 with
`motor/set-velocity`), a braking motor slows down with its usual time
constant, while with `:float` it coasts and only friction slows it: the
Flywheel then takes about 3 s, rather than 0.4 s, to lose 63% of its speed.

The mecanum drivetrain has the four motors of the
[Mecanum Drive](examples/mecanum-drive.md) example: `"front_left_drive"`,
`"front_right_drive"`, `"back_left_drive"` and `"back_right_drive"`. They are
312 RPM motors (for example goBILDA's
[5203 Series](https://www.gobilda.com/5203-series-yellow-jacket-planetary-gear-motor-19-2-1-ratio-24mm-length-8mm-rex-shaft-312-rpm-3-3-5v-encoder/))
on 104 mm mecanum wheels, with the left two mounted the
other way round, so a program reverses them as on a real robot. Each follows
the same first-order response, with a time constant of 0.2 s for the weight of
the robot, and the robot moves by how far each wheel rolled, without slipping.
At power 0.3 it drives about 20 inches per second. With `:float`, the wheels
coast with a time constant of 0.5 s, so the robot rolls on further after it
stops.

The drive motors have no encoders, since driving is done by power. As on a real
robot, `motor/velocity` and `motor/current-position` read 0, and
`motor/set-velocity` runs a wheel at full power because its controller sees no
speed.

The IMU and the Pinpoint both follow the robot. The IMU's yaw is its heading,
which increases as it turns left; the floor is flat, so pitch and roll stay at
0. The Pinpoint, named `"pinpoint"`, tracks the robot exactly, as if its pods
never slip, from where it was last reset: `x` forward, `y` to the left. The
pod type and offsets given to `pinpoint/init` are taken to be right, and the
simulated pods count the right way with `:forward :forward`. Setting one to
`:reverse` flips its axis, as on a real robot whose pod is set the wrong way.

With `mecanum-drive` from the example, turn on the spot for one second, then
drive forward for two:

```console
user=> (imu/init :up :backward)
nil
user=> (pinpoint/init :swingarm -3.3 -6.6)
:ready
user=> (mecanum-drive 0 0 0.3)
[-0.3 0.3 -0.3 0.3]
user=> (mecanum-drive 0 0 0)
[0.0 0.0 0.0 0.0]
user=> (Math/toDegrees (imu/yaw))
91.5787017918707
user=> (mecanum-drive 0.3 0 0)
[0.3 0.3 0.3 0.3]
user=> (mecanum-drive 0 0 0)
[0.0 0.0 0.0 0.0]
user=> (pinpoint/pose)
{:x -1.278853782668248, :y 43.12244461261836, :heading 1.6005536231698685}
```

In the simulator, the turn is a quarter turn counterclockwise, to the left, so
driving forward afterwards moves the robot toward `+y`.

It does not have `servo/*` or `crservo/*` yet.

!!! tip "Try forms here first"

    `RobotSimulator` runs the same code as the robot, so it is the quickest way to
    check a form, a destructuring pattern or an error message.

## Documentation

The docs are Markdown in `docs/`, with site settings in `zensical.toml` and a
single Python dependency in `pyproject.toml`. With
[uv](https://docs.astral.sh/uv/) installed:

```console
$ uv run zensical serve          # preview at http://127.0.0.1:8000
$ uv run zensical build --clean  # write the site to site/
```

CI runs `uv run zensical build --clean` on every push and pull request, and
deploys `site/` to GitHub Pages on `main`.
