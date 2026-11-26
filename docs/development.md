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
| `src/secondloop/` | The runtime that any language can share: builtins, futures, the REPL servers and the printer. Plain Java, no FTC dependency. |
| `src/clojure/` | The Clojure front end: the reader, the evaluator (`ClojureInterp`), closures, destructuring, the REPL and the Prelude. |
| `src/ftc/` | Modules that use the FTC SDK, such as `GamepadModule`, `MotorModule`, `ServoModule`, `BatteryModule`, `ImuModule`, `PinpointModule` and `TelemetryModule`. |
| `src/SecondLoop.java` | The OpMode template. The sources are inserted at `// @@SECONDLOOP_SOURCES@@`. |
| `tools/amalgamate.py` | Merges everything into `build/SecondLoop.java`. |
| `src/notebook.html` | The notebook page. `tools/amalgamate.py` turns it into the string `NotebookPage.HTML`, also written as `build/gen/NotebookPage.java`, and `NotebookServer` serves it on port 9002. |
| `test/secondloop/` | `ReplTest`, `GamepadTest`, `MotorTest`, `ServoTest`, `BatteryTest`, `ImuTest`, `PinpointTest`, `TelemetryTest`, `SimTelemetryTest`, `SimImuTest`, `SimBatteryTest`, `SimMotorTest`, `SimDrivetrainTest`, `SimPinpointTest`, `SimWebUiTest` and `NotebookTest`, plus `RobotSimulator` and its web page, `robot-simulator.html`. |
| `test/ftc-stubs/` | Minimal stand-ins for the FTC SDK classes, used only for compiling and testing. |
| `docs/` | This documentation, built with Zensical. |

Functions live in `Builtins`, `MathBuiltins`, `StringBuiltins` and
`CollBuiltins`; helpers written in the REPL language itself live in
`Prelude.java`. Each file's top-level types become static nested classes of
`SecondLoop` in the combined file.

The runtime doesn't depend on any one language, so another language can reuse
it. A language implements `Language`, which creates the globals and a `Repl`
for each session, and extends `Interp`, which is what builtins see when they
call back into the language. The build checks this split: it also compiles
`src/secondloop/` and `src/ftc/` without `src/clojure/`.

## Build

```console
$ ./build.sh
```

The script amalgamates, compiles against the FTC stand-ins, checks that the
runtime compiles without the Clojure front end, runs every test, and then
compile-checks the single file. It needs Python 3 and a JDK, and if
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

Above these, `showStatus` keeps one status line, such as `Second Loop 1.1.22
at http://192.168.43.1:9002 (1 client)`, so the copy that runs and the
address a laptop needs are on the Driver Hub from INIT. The first
`(telemetry/clear)` removes it for good, so a program gets the whole screen. When
the robot has several addresses, `ReplServer.preferredAddress` picks
`192.168.43.1` or `192.168.49.1` if either is there.

## Notebook

`NotebookServer` serves the notebook over HTTP on plain sockets, since Android
has no HTTP server class. Each browser tab opens `/events`, a stream of
Server-Sent Events, and that request's thread runs the tab's REPL session,
just as a thread runs each `nc` connection. The page sends code with `POST
/eval`. `ReplServer` starts both, and stops both when the OpMode ends.

Anyone on the robot's WiFi can already reach port 9001, so the notebook adds
no exposure there. It answers only requests whose `Host` is an IP address or
`localhost`, so a web site cannot reach the robot through the browser by
DNS rebinding.

## RobotSimulator

`RobotSimulator` simulates a robot on a laptop. See
[Robot Simulator](reference/simulator.md) for how to run it and what it
simulates.

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
