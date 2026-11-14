# Control Loops

A robot program is usually one central loop: read the world, act on it, sleep,
and repeat. The forms inside the loop are part of the language, covered in
[Loop](../language/loops.md); this page is about running one on the robot.

This loop needs no robot hardware. Once a second it puts the match time (see
[Time](time.md#elapsed)) on the Driver Hub (see [Telemetry](telemetry.md)), and
it repeats until it is stopped:

```clojure
(defn sample-loop []
  (loop []
    (telemetry/clear)
    (telemetry/add-line "elapsed" (time/elapsed))
    (time/sleep 1)                             ; one round a second
    (recur)))
```

The Driver Hub shows a line such as `elapsed 5.012`, then `elapsed 6.014`, and
so on. The clock is read again every round.

- **It can run for a whole match.** `loop`/`recur` repeats without growing the
  stack ([Loop](../language/loops.md#loop-and-recur)).
- **Loops are shared.** Every connection sees the same loops, so any of them
  can check on a loop or stop it.
- **`recur` must be last.** It starts the next round, so it is the last thing
  each round does; anything after it is a syntax error.
- **Sleep every round.** A short `time/sleep` sets the pace, for example
  `(time/sleep 0.02)` for about 50 rounds a second (see [Time](time.md#sleep)).
- **Errors end the loop.** If a round fails, for example by dividing by zero,
  the loop stops and the connection that started it prints `Future failed.`
  and the error. See [Errors](../language/errors.md#future-failed).

When the loop ends, by `control/stop`, STOP or an error, whatever it set last
stays set: a motor keeps its last power. Wrap the loop in `try` with a
`finally` to put things back:

```clojure
(defn sample-loop []
  (try
    (loop []
      ;; read the gamepad, drive...
      (time/sleep 0.02)
      (recur))
    (finally
      ;; e.g. set every motor's power to 0
      (println "loop ended"))))
```

The `finally` runs to the end even when the loop is being stopped, so it can use
a short `time/sleep` if it needs to. Cleanup is never cut short.
[Exceptions](../language/exceptions.md) covers `try`, `catch` and `finally` in
general.

Calling `(sample-loop)` directly keeps that connection busy until STOP on the
Driver Hub. The `control/*` functions run the loop in the background instead.

## start

`(control/start f)` runs `(f)` in the background as a
[future](../language/futures.md) and returns at once. This
program uses `mecanum-drive` from [Examples](../examples.md) to move the robot
forward for three seconds and then stop:

```clojure
(defn drive-forward []
  (mecanum-drive 0.7 0 0)                      ; move forward
  (time/sleep 3)                               ; for 3 seconds
  (mecanum-drive 0 0 0))                       ; then stop
```

Calling `(drive-forward)` directly makes the prompt wait the full three seconds
before you can type again. `control/start` hands the program off to run on its
own and gives the prompt back right away:

```clojure
user=> (control/start drive-forward)
#<Future pending>
user=> (control/running?)
true
```

While the robot drives, you can keep typing: check it with `control/running?`,
read the [IMU](imu.md), or end the run early with `control/stop`. After an early
stop the wheels keep their last power, so put the final `(mecanum-drive 0 0 0)`
in a `finally` as shown above. `println` inside the program goes back to the
connection that started it, which is handy while testing.

`control/start` first stops a program it started earlier with the same name, so
after editing `drive-forward` you can just run `(control/start drive-forward)`
again. Programs with other names keep running; see
[Several loops](#several-loops).

## running?

`(control/running?)` reports whether any loop is still going, and
`(control/running? f)` whether the loop of `f` is:

```clojure
user=> (control/running? drive-forward)
true
```

It is `false` before the first `control/start`, after `control/stop`, and after
a round fails with an error.

## stop

`(control/stop)` ends every loop, and `(control/stop f)` only the loop of `f`:

```clojure
user=> (control/stop)
true
```

It returns `true` if a loop was running. Any connection can stop it, not only
the one that started it. Stopping is prompt: the loop cannot take one more step
after a stop request. A stopped loop prints no `Future failed.` line, because
it was cancelled rather than broken.

| Action | Control loop | Definitions |
| --- | --- | --- |
| `(control/stop)` | all end | kept |
| STOP on the Driver Hub | ends | lost on the next INIT |
| Closing the connection | keeps running | kept |

Closing the connection does **not** stop a loop that is already running:
reconnect and call `(control/stop)`. Pressing STOP ends the OpMode and
everything running in it, so everything you defined is gone on the next INIT;
`(control/stop)` keeps it.

STOP also interrupts a form running at the prompt, such as `(sample-loop)`
called directly, and every `time/sleep`. That form ends with
`Execution error: Interrupted`.

A stop request is never caught, not even by `(catch Throwable e ...)`, so an
error handler cannot keep a stopped loop running. The classes are on
[Exceptions](../language/exceptions.md).

## Several loops

Each function started with `control/start` gets its own loop, named after the
function, so one loop can drive while another moves an arm:

```clojure
user=> (control/start drive)
#<Future pending>
user=> (control/start arm)
#<Future pending>
user=> (control/stop arm)
true
user=> (control/running? drive)
true
```

- **The name picks the loop.** `control/stop` and `control/running?` take the
  function itself, or its name as a keyword, string or symbol, so
  `(control/stop arm)`, `(control/stop :arm)` and `(control/stop "arm")` are the
  same.
- **The same name replaces.** Starting `arm` again, even after redefining it,
  stops the running `arm` loop first.
- **A name can be given.** `(control/start :spin f)` names the loop `spin`
  whatever the function is called.
- **Unnamed functions share one loop.** `(fn [] ...)` and `#(...)` have no
  name, so starting one stops the unnamed loop started before. To keep an
  inline loop apart, give it a name: `(control/start :spin (fn [] ...))`, then
  `(control/stop :spin)`.
- **Loops must not fight.** Two loops that set the same motor take turns
  overriding each other. Give each loop its own hardware.
