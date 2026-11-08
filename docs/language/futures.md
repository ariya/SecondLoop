# Futures

A future runs some code in the background and hands back a value that stands
for its result. The prompt comes back at once, so you can keep typing while the
code runs, and ask for the result later.

[`control/start`](../robot/control-loops.md#start) is built on a future and covers the
usual case of one control loop. Reach for `future` directly for anything else,
such as a one-off task that should not hold up the prompt.

- **Output goes to the connection that started it.** That is why a `println`
  in a control loop shows up on the connection that ran `control/start`.
- **Failures are reported at once.** If the code fails, that connection prints
  `Future failed.` and the error as soon as it happens, without waiting for
  anyone to ask for the result. See [Errors](errors.md#future-failed).
- **STOP cancels every future.** Pressing STOP on the Driver Hub ends every
  future that is still running, along with the OpMode.
- **A cancel cannot be caught.** Not even `(catch Throwable e ...)` keeps a
  cancelled future running, though a `finally` still runs to the end. See
  [Exceptions](exceptions.md).

## future

`(future body...)` starts running the body in the background and returns at
once. `@f`, or `(deref f)`, waits for the result:

```clojure
user=> (def f (future (+ 1 2)))
#'user/f
user=> @f
3.0
```

`(deref f ms timeout-value)` waits at most `ms` milliseconds, and returns
`timeout-value` if the future is still running:

```clojure
user=> (deref (future (time/sleep 0.2) :late) 10 :timeout)
:timeout
```

If the body failed, `deref` raises the same error again. If the future was
cancelled, it raises `Execution error: Future was cancelled`.

## future-cancel

`(future-cancel f)` stops a future, ending any running loop or `time/sleep`.
It returns `true`, or `false` if the future had already finished:

```clojure
user=> (def g (future (loop [] (time/sleep 0.01) (recur))))
#'user/g
user=> (future-cancel g)
true
user=> (future-cancel g)
false
```

`future-cancel` waits up to a second for the future to end, so a cancelled loop
cannot take one more step, such as setting a motor's power, after the call
returns. Without the wait, a loop could move a motor once more.

A cancelled future prints no `Future failed.` line, because it was cancelled
rather than broken.

## future-done?

`(future-done? f)` is `true` once the future has finished, whether it returned
a value, failed or was cancelled:

```clojure
user=> (future-done? (future (time/sleep 1)))
false
user=> (future-done? g)
true
```

It never waits, so it is the way to check on a future without blocking.
[`control/running?`](../robot/control-loops.md#running) is built on it.

## future-cancelled?

`(future-cancelled? f)` is `true` only if the future was stopped with
`future-cancel`, `control/stop` or STOP:

```clojure
user=> (future-cancelled? g)
true
user=> (future-cancelled? f)
false
```

## future?

`(future? x)` is `true` if `x` is a future:

```clojure
user=> (future? f)
true
user=> (future? 3)
false
```
