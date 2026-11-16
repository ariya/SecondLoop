# Control Loops

A robot does several things at once: it drives, works a claw, and reports what
it sees. Each of these is a [loop](language/loops.md) of its own: read the
world, act on it, sleep, and repeat. This page starts with one loop, then runs
it in the background, and finally runs several loops side by side.

## One loop

This loop drives with gamepad 1, using `mecanum-drive` from
[Mecanum Drive](examples/mecanum-drive.md#wheel-powers):

```clojure
(defn drive []
  (loop []
    (let [pad (gamepad 1)]
      (mecanum-drive (- (:left-stick-y pad))
                     (:left-stick-x pad)
                     (:right-stick-x pad)))
    (time/sleep 0.02)
    (recur)))
```

Call it at the prompt:

```clojure
user=> (drive)
```

The robot follows the sticks, but driving is now the only thing it can do. The
loop never ends, so nothing else gets a turn: no spinning up a flywheel to
launch game elements, no working the claw, while the robot drives.

## In the background

[`task/start`](robot/task.md#start) runs a function in the background as a
[task](robot/task.md) and returns right away, leaving room for other work:

```clojure
user=> (task/start drive)
#<Future pending>
```

The robot keeps driving, and other loops can now run alongside it (see
[Parallel tasks](#parallel-tasks)). `#<Future pending>` means the task is still
running (see [Futures](language/futures.md)).

If a round fails, for example by dividing by zero, the task ends and the
connection that started it prints `Future failed.` and the error. See
[Errors](language/errors.md#future-failed).

## Stopping

[`task/stop`](robot/task.md#stop) ends a task:

```clojure
user=> (task/stop drive)
true
```

It returns `true` if the task was running. `(task/stop)` with no argument ends
every task at once. Everything you defined stays, so you can edit `drive` and
start it again. `task/start` also stops the task of the same name before
starting it, so after an edit, `(task/start drive)` alone is enough.

Closing the connection does **not** stop a running task: reconnect and call
`(task/stop)`.

## Cleanup

When a loop ends, by `task/stop` or an error, whatever it set last stays set:
the wheels keep their last power. Wrap the loop in `try` with a `finally` to
put things back:

```clojure
(defn drive []
  (try
    (loop []
      (let [pad (gamepad 1)]
        (mecanum-drive (- (:left-stick-y pad))
                       (:left-stick-x pad)
                       (:right-stick-x pad)))
      (time/sleep 0.02)
      (recur))
    (finally
      (mecanum-drive 0 0 0))))                 ; stop the wheels
```

The `finally` runs to the end even when the loop is being stopped, so it can
use a short `time/sleep` if it needs to. Cleanup is never cut short.
[Exceptions](language/exceptions.md) covers `try`, `catch` and `finally` in
general.

## Parallel tasks

Each function started with `task/start` gets its own task, named after the
function. Add a second loop that moves the claw as far as the right trigger is
pressed (see [Servos](robot/servos.md)):

```clojure
(defn grip []
  (loop []
    (servo/set-position "claw" (:right-trigger (gamepad 1)))
    (time/sleep 0.02)
    (recur)))
```

Start both loops. They run side by side, each at its own pace:

```clojure
user=> (task/start drive)
#<Future pending>
user=> (task/start grip)
#<Future pending>
```

The robot drives and the claw follows the trigger. Stop one task and the other
carries on, as [`task/running?`](robot/task.md#running) shows:

```clojure
user=> (task/stop grip)
true
user=> (task/running? drive)
true
```

Give each task its own hardware. Two tasks that set the same motor take turns
overriding each other, and the motor jitters between their values.

[Task](robot/task.md) covers every `task/*` function, including how to name a
task yourself.
