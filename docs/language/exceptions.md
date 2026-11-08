# Exceptions

An error in a form arrives as an exception, and `try` decides which ones you
catch. [Errors](errors.md) is the message you see when nothing does.

`catch` names a class, and there are three:

| Class | Catches |
| --- | --- |
| `Exception` | Any error, including `ExceptionInfo` |
| `ExceptionInfo` | An error from `ex-info` |
| `Throwable` | Any error, and stack overflow |

```clojure
(try
  (throw (ex-info "arm too far" {:angle 95}))
  (catch ExceptionInfo e (ex-data e))      ; => {:angle 95.0}
  (catch Exception e (ex-message e)))      ; any other error
```

The first matching `catch` wins, so `ExceptionInfo` comes before `Exception`:
`Exception` would catch the `ex-info` error too.

`ex-info` takes a message and a map, `ex-message` returns the message, and
`ex-data` returns the map:

```clojure
user=> (try (throw (ex-info "boom" {:code 7}))
         (catch ExceptionInfo e [(ex-message e) (ex-data e)]))
["boom" {:code 7.0}]
```

## finally

`finally` runs whichever way the body leaves: with a value, with a caught error,
or with one that nothing catches:

```clojure
user=> (try (throw (ex-info "boom" {:code 7})) (finally (println "cleanup")))
cleanup
Execution error: boom
```

Nothing cuts a `finally` short, so it is the place to undo what the body did. In
a loop it runs when the loop stops, by `(control/stop)`, by STOP or by an error;
see [Control Loops](../robot/control-loops.md).

## recur and try

`recur` cannot be wrapped in a `try`, so a loop that catches must keep the `try`
inside one round:

```clojure
user=> (loop [i 0] (when (< i 3) (try (println i)) (recur (inc i))))
0.0
1.0
2.0
nil
```

The other way round is a syntax error:

```clojure
user=> (loop [] (try (recur)))
Syntax error: Can only recur from tail position
```
