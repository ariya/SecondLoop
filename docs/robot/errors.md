# Errors

An error ends the form that caused it. [Exceptions](../language/exceptions.md)
is how to catch one; this page is the message you get, and where it appears.

An uncaught error prints where the value would have been, and the prompt comes
back, so the session carries on:

```clojure
user=> (/ 1 0)
Execution error: Divide by zero
user=> (+ 1 2)
3.0
```

## Syntax errors

A form that does not parse is never run, so nothing in it takes effect, and the
message says `Syntax error` instead:

```clojure
user=> (def)
Syntax error: Too few arguments to def
user=> (loop [] (try (recur)))
Syntax error: Can only recur from tail position
```

## Futures

A control loop started with `control/start` runs in a future. If a round fails,
the connection that started it prints `Future failed.` and the error as soon as
the thread ends, even if nobody calls `deref`:

```clojure
user=> (def bad (future (/ 1 0)))
#'user/bad
```

A moment later, the same connection prints:

```clojure
Future failed. Execution error: Divide by zero
```

`@bad` raises the same error again. The line can arrive between other output,
because it comes from another thread. Waiting for `deref` would let a control
loop that died go unnoticed. [Futures](futures.md) covers the rest of what a
future does.

## Stopping

STOP interrupts every running form and every `time/sleep`, which is why a loop
run at the prompt ends with `Execution error: Interrupted`. A loop started with
`control/start` is cancelled rather than broken, so it prints no
`Future failed.` line, and dereferencing that future raises
`Execution error: Future was cancelled`.

[Control Loops](control-loops.md#stopping) has what each kind of stop ends.
