# Development

Second Loop keeps its sources in normal Java files and combines them into one file
for [OnBot Java](https://ftc-docs.firstinspires.org/en/latest/programming_resources/onbot_java/OnBot-Java-Tutorial.html),
so the robot only needs a single upload.

## Layout

| Path | Contents |
| --- | --- |
| `src/secondloop/` | The interpreter. Plain Java, no FTC dependency. |
| `src/ftc/` | Modules that use the FTC SDK, such as `GamepadModule`, `MotorModule`, `ServoModule`, `BatteryModule`, `ImuModule`, `PinpointModule` and `TelemetryModule`. |
| `src/SecondLoop.java` | The OpMode template. The sources are inserted at `// @@SECONDLOOP_SOURCES@@`. |
| `tools/amalgamate.py` | Merges everything into `build/SecondLoop.java`. |
| `secondloop.py` | A terminal client with line editing and history, used instead of `nc`. |
| `test/secondloop/` | `ReplTest`, `GamepadTest`, `MotorTest`, `ServoTest`, `BatteryTest`, `ImuTest`, `PinpointTest` and `TelemetryTest`, plus `LocalRepl`. |
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

`showStatus` adds one line above these, such as `Ready at 192.168.43.1:9001 (1
client)`, so the address a laptop needs is on the Driver Hub from INIT. When
the robot has several addresses, `ReplServer.preferredAddress` picks
`192.168.43.1` or `192.168.49.1` if either is there.

## LocalRepl

`LocalRepl` runs the same interpreter on a laptop, with no gamepad, motor, servo,
battery, IMU or telemetry functions:

```console
$ java -cp build/classes secondloop.LocalRepl --stdin  # type directly
$ java -cp build/classes secondloop.LocalRepl          # serve on port 9001
$ nc localhost 9001                                    # in another terminal
```

It has the interpreter and the prelude, but not `gamepad`, `motor/*`, `servo/*`,
`crservo/*`, `battery/voltage`, `imu/*`, `pinpoint/*` or `telemetry/*`, which need
the FTC SDK.

!!! tip "Try forms here first"

    `LocalRepl` runs the same code as the robot, so it is the quickest way to
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
