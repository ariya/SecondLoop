# Errors

An error ends the form that caused it. If nothing catches it, the message is
printed where the value would have been, and the prompt comes back:

```clojure
user=> (/ 1 0)
Execution error: Divide by zero
user=> (+ 1 2)
3.0
```

- **The session carries on.** Only the failing form ends; everything defined
  earlier is kept.
- **The first word says when it failed.** A syntax error is found before the
  form runs, an execution error while it runs.
- **`try` can catch it.** An error that runs into a `try` with a matching
  `catch` prints nothing. See [Exceptions](exceptions.md).
- **A stop is not an error.** `control/stop`, `future-cancel` and STOP on the
  Driver Hub cancel instead, and no handler can catch them. See
  [Control Loops](../robot/control-loops.md#stop).

## Syntax error

A form that does not parse, or is not put together correctly, is never run, so
nothing in it takes effect:

```clojure
user=> (def)
Syntax error: Too few arguments to def
user=> (loop [] (try (recur)))
Syntax error: Can only recur from tail position
```

Fix the form and send it again.

## Execution error

A form that parses but fails while it runs reports an execution error. Anything
it did before the failure, such as setting a motor's power, has already taken
effect:

```clojure
user=> (/ 1 0)
Execution error: Divide by zero
user=> (throw (ex-info "arm too far" {:angle 95}))
Execution error: arm too far
```

An error from `ex-info` shows its message, as above. A form that ends because
of STOP shows `Execution error: Interrupted`.

## Future failed

A [future](futures.md) runs in the background, so its error has no prompt to
print at. Instead, the connection that started it prints `Future failed.` and
the error as soon as the future ends, even if nobody calls `deref`:

```clojure
user=> (def bad (future (/ 1 0)))
#'user/bad
```

A moment later, the same connection prints:

```clojure
Future failed. Execution error: Divide by zero
```

The line can arrive between other output. Reporting it at once means a
[control loop](../robot/control-loops.md) that died does not go unnoticed.
`@bad` raises the same error again.

A cancelled future prints no such line, because it was cancelled rather than
broken; see [Futures](futures.md#future-cancel).
