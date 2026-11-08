# Time

`(time/elapsed)` is the number of seconds since START was pressed on the Driver
Hub:

```clojure
user=> (time/elapsed)
1.164393094
user=> (format "%.1f s" (time/elapsed))
"1.2 s"
```

It is the same as the SDK's `ElapsedTime`, reset right after `waitForStart()`,
so it tells you how far into the match you are: autonomous lasts 30 seconds and
TeleOp 2 minutes. Before START it counts from INIT, and every press of INIT
starts a new count.

It is the only clock. Java's `System/currentTimeMillis` and `System/nanoTime`
are not available, so with `time/sleep` every time in your code is in seconds.

## Pause

`(time/sleep s)` pauses the current thread for `s` seconds, so
`(time/sleep 0.02)` is 20 milliseconds. On the robot it is also how a loop slows
down: a loop with no sleep would run as fast as the CPU allows and starve the
rest of the OpMode. A time of zero or less does not wait at all, so
`(time/sleep (- deadline (time/elapsed)))` is safe even after the deadline.

## Fixed duration

Set a deadline once, then compare against it each round:

```clojure
(let [end (+ (time/elapsed) 2)]
  (while (< (time/elapsed) end)
    (println "driving")                 ; e.g. set the motor power
    (time/sleep 0.02)))
```

This is the usual "drive for two seconds" shape. The check is at the top, so the
body always runs at least once.

## Timeout

```clojure
(let [deadline (+ (time/elapsed) 3)]
  (loop []
    (cond (:a (gamepad 1)) :pressed
          (> (time/elapsed) deadline) :timed-out
          :else (do (time/sleep 0.02) (recur)))))
```

The loop returns `:pressed` or `:timed-out`, which is easier to test than a
`nil` from a loop that just stops.

## Timing

`(time expr)` evaluates the expression, prints how long it took, and returns the
value:

```clojure
user=> (time (+ 1 2))
"Elapsed time: 0.02035 msecs"
3.0
```

The duration varies from run to run; the important part is that `3.0` is still
the value, so `time` can wrap any expression.

## Round time

A PID controller needs the time since the last round. Carry the previous
`(time/elapsed)` through `recur` and subtract:

```clojure
(defn control-loop []
  (loop [last (time/elapsed)]
    (let [now (time/elapsed)
          dt (- now last)]               ; seconds since the last round
      ;; ... use dt in the controller ...
      (time/sleep 0.02)
      (recur now))))
```

`dt` is in seconds, about `0.02` here.
