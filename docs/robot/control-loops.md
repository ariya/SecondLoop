# Control Loops

A robot program is usually one central loop: read something, act on it, and
repeat. The forms inside the loop are part of the language, covered in
[Loop](../language/loops.md); this page is about running one on the robot.

`loop`/`recur` repeats without growing the stack (see
[Loop](../language/loops.md#loop-and-recur)), so a loop
can run for a whole match. Start with the smallest loop that does something
visible. It runs three rounds and ends on its own:

```clojure
user=> (loop [i 0]
         (when (< i 3)
           (println "tick" i)
           (time/sleep 1)
           (recur (inc i))))
tick 0.0
tick 1.0
tick 2.0
nil
```

That keeps the REPL busy until it finishes, which is fine for three seconds and
wrong for a whole match.

## Elapsed time

This one needs no robot hardware. Once a second it puts the time since it
started on the Driver Hub (see [Telemetry](telemetry.md)), and it repeats
until you stop it:

```clojure
(defn control-loop []
  (let [start (time/elapsed)]                  ; read once, before the loop
    (loop []
      (let [elapsed (- (time/elapsed) start)]
        (telemetry/clear)
        (telemetry/add-line (format "elapsed %.1f s" elapsed))
        (time/sleep 1)                         ; one round a second
        (recur)))))
```

The Driver Hub shows `elapsed 0.0 s`, then `elapsed 1.0 s`, and so on.
`start` stays fixed while `elapsed` is recomputed from the clock every round.
That is the shape of every control loop: read the world, act, sleep, repeat.

## Background

Calling `(control-loop)` at the prompt would keep that connection busy until
STOP. Start it with `control/start` instead: it runs `(f)` on its own thread and
returns at once, so the REPL stays free.

```clojure
user=> (control/start control-loop)
#<Future pending>
```

The elapsed-time lines appear on the Driver Hub, not in the `nc` window.
`(control/running?)` reports whether the loop is still going, and
`(control/stop)` ends it:

```clojure
user=> (control/running?)
true
user=> (control/stop)
true
```

`control/start` stops any loop it started earlier first, so after editing
`control-loop` you can just run `(control/start control-loop)` again. The loop is
shared by every connection, so any of them can stop it.

## Gamepad

On the robot the same shape reads the controls. `(gamepad 1)` returns a snapshot
of controller 1 as a map (see [Gamepad](gamepad.md)); this loop reads the right
trigger fifty times a second and prints the power it would send:

```clojure
(defn control-loop []
  (loop []
    (let [pad (gamepad 1)]
      (when (> (:right-trigger pad) 0.5)
        (println "power" (* 0.6 (:right-trigger pad)))))
    (time/sleep 0.02)                     ; about 50 rounds a second
    (recur)))
```

`when` there is the ordinary one from
[Branching](../language/branching.md#when); `cond` and
`case` work in the loop body in the same way.

`println` goes back to the connection that started the loop, which is handy
while testing. On the robot, show the value on the Driver Hub instead:

```clojure
(let [pad (gamepad 1)]
  (telemetry/clear)
  (telemetry/add-line (format "power %.2f" (* 0.6 (:right-trigger pad)))))
```

Start and stop it with `control/start` and `control/stop`, as above. The numbers
are hard-coded here, so every round does the same thing; an
[atom](../language/atom.md) lets you change them while the loop runs.

- **`recur` must be last.** It starts the next round, so it is the last thing
  each round does; anything after it is a syntax error
  ([Loop](../language/loops.md#loop-and-recur)).
- **Errors are reported at once.** If a round fails, for example by dividing by
  zero, the connection that started the loop prints `Future failed.` and the
  error. See [Errors](errors.md#futures).

## Stopping

Stopping has to be prompt: the OpMode, the connections and every running future
stop together, and a loop cannot take one more step after a stop request.

| Action | Control loop | Definitions |
| --- | --- | --- |
| `(control/stop)` | ends | kept |
| STOP on the Driver Hub | ends | lost on the next INIT |
| Closing `nc` | keeps running | kept |

Closing `nc` does **not** stop a loop that is already running: reconnect and
call `(control/stop)`. Calling `(control-loop)` directly, without
`control/start`, keeps that REPL busy until STOP instead of returning.

Pressing STOP also ends the OpMode, so everything you defined is gone on the
next INIT; `(control/stop)` keeps it. A running form ends with
`Execution error: Interrupted`, and a stopped loop prints no `Future failed.`
line, because it was cancelled rather than broken.

A stop request is never caught, not even by `(catch Throwable e ...)`, so an
error handler cannot keep a stopped loop running. The classes are on
[Exceptions](../language/exceptions.md).

## Cleanup

When a loop ends, by `control/stop`, STOP or an error, whatever it set last
stays set: a motor keeps its last power. Wrap the loop in `try` with a `finally`
to put things back:

```clojure
(defn control-loop []
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
[Exceptions](../language/exceptions.md)
covers `try`, `catch` and `finally` in general.
