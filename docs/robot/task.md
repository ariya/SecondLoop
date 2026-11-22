# Task

The `task/*` functions run a function in the background as a *task*: a
[future](../language/futures.md) that they remember by name. They are how a
[control loop](../control-loops.md) runs alongside other work; that page shows
how to write one.

Every task has a name, and one task runs per name. `task/stop` and
`task/running?` pick a task by the function itself, or by its name as a
keyword, string or symbol, so `(task/stop arm)`, `(task/stop :arm)` and
`(task/stop "arm")` are the same.

## start

`(task/start f)` runs `(f)` in the background and returns the
[future](../language/futures.md) at once:

```clojure
user=> (task/start drive-with-gamepad)
#<Future pending>
```

The task is named after the function, here `drive-with-gamepad`.
`task/start` first stops a task with the same name, even after you redefine
the function, so after editing `drive-with-gamepad` you can just run
`(task/start drive-with-gamepad)` again. Tasks with other names keep running.

`(task/start name f)` gives the task a name of its own, so
`(task/start :spin f)` names it `spin` whatever the function is called.
`(fn [] ...)` and `#(...)` have no name, so they all share one unnamed task,
and starting one stops the unnamed task started before. To keep an inline task
apart, name it: `(task/start :spin (fn [] ...))`, then `(task/stop :spin)`.

`println` inside the task goes back to the notebook tab that started it. If
the task fails with an error, that tab prints `Future failed.` and the
error; see [Errors](../language/errors.md#future-failed).

## running?

`(task/running?)` reports whether any task is still going, and
`(task/running? f)` whether the task of `f` is:

```clojure
user=> (task/running? drive-with-gamepad)
true
```

It is `false` before the first `task/start`, after `task/stop`, and after
the task fails with an error.

## stop

`(task/stop)` ends every task, and `(task/stop f)` only the task of `f`:

```clojure
user=> (task/stop)
true
```

It returns `true` if a task was running. Any notebook tab can stop it, not only
the one that started it. Stopping is prompt: the loop cannot take one more step
after a stop request. A stopped loop prints no `Future failed.` line, because
it was cancelled rather than broken.

A stop request is never caught, not even by `(catch Throwable e ...)`, so an
error handler cannot keep a stopped loop running. A `finally` still runs to
the end; see [Cleanup](../control-loops.md#cleanup). The classes are on
[Exceptions](../language/exceptions.md).

Closing the notebook does **not** stop a task, it keeps running even when
nobody is connected. See [Stopping](../control-loops.md#stopping).
