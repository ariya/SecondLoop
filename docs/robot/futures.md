# Futures

`control/start` is built on futures, and covers the usual case of one control
loop. For anything else, `(future body...)` runs the body on its own thread and
returns at once, so the REPL stays free.

```clojure
user=> (def f (future (+ 1 2)))
#'user/f
user=> @f
3.0
```

`@f`, or `(deref f)`, waits for the result.

## Timeout

`(deref f 1000 :timeout)` waits at most a second, and returns the given value if
the future is still running:

```clojure
user=> (deref (future (time/sleep 0.2) :late) 10 :timeout)
:timeout
```

## Cancel

`future-cancel` stops the thread, ending any running loop or `time/sleep`.
`future-done?` and `future-cancelled?` report its state:

```clojure
user=> (def g (future (loop [] (time/sleep 0.01) (recur))))
#'user/g
user=> (future-cancel g)
true
user=> (future-cancelled? g)
true
user=> (future-done? g)
true
```

`future-cancel` waits up to a second for the thread to end, so a cancelled loop
cannot take one more step, such as setting a motor's power, after the call
returns. That is deliberate: without the wait, a loop could move a motor once
more. Dereferencing a cancelled future raises
`Execution error: Future was cancelled`.

## Output

Output from a future goes to the connection that started it, which is why a
`println` in a control loop shows up in the window that ran `control/start`.

## Failures

A failure is printed there as soon as the thread ends, rather than waiting for
`deref`; see [Errors](errors.md#futures).
