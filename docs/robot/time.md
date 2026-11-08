# Time

The `time/*` functions measure time on the robot, like the SDK's
[`ElapsedTime`](https://javadoc.io/doc/org.firstinspires.ftc/RobotCore/latest/com/qualcomm/robotcore/util/ElapsedTime.html).
All times are in seconds.

## elapsed

`(time/elapsed)` is the number of seconds since START was pressed on the Driver
Hub:

```clojure
user=> (time/elapsed)
1.164393094
user=> (format "%.1f s" (time/elapsed))
"1.2 s"
```

It tells you how far into the match you are: autonomous lasts 30 seconds and
TeleOp 2 minutes. Before START it counts from INIT, and every press of INIT
starts a new count.

## sleep

`(time/sleep s)` pauses for `s` seconds, so `(time/sleep 0.02)` is 20
milliseconds.

To do something for a fixed duration, set a deadline once, then compare against
it each round:

```clojure
(let [deadline (+ (time/elapsed) 2)]
  (while (< (time/elapsed) deadline)
    (println "driving")                 ; e.g. set the motor power
    (time/sleep 0.02)))
```

This is the usual "drive for two seconds" shape.

A time of zero or less does not wait at all, so
`(time/sleep (- deadline (time/elapsed)))` is safe even after the deadline.

## time

`(time expr)` evaluates the expression, prints how long it took, and returns the
value:

```clojure
user=> (time (+ 1 2))
"Elapsed time: 0.02035 msecs"
3.0
```

The duration changes from run to run. What matters is that `3.0` is still the
value, so `time` can wrap any expression.
