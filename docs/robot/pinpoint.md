# Pinpoint

The [goBILDA Pinpoint](https://www.gobilda.com/pinpoint-odometry-computer-imu-sensor-fusion-for-2-wheel-odometry/)
is an [odometry](https://gm0.org/en/latest/docs/software/concepts/odometry.html)
computer. It reads two odometry pods and its own IMU (inertial measurement
unit), and works out where the robot is on the field. Plug it into an I2C
port, and add it to the robot configuration as a "goBILDA Pinpoint Odometry
Computer" named `"pinpoint"`.

Before reading it, tell the Pinpoint about the pods. For goBILDA swingarm pods,
with the X pod 3.3 inches to the right of the robot's center and the Y pod 6.6
inches behind it:

```clojure
user=> (pinpoint/init :swingarm -3.3 -6.6)
:ready
user=> (pinpoint/pose)
{:x 0.0, :y 0.0, :heading 0.0}
```

Distances are in inches, the unit of the FTC field. Angles are in radians, as
for the [IMU](imu.md).

The offsets are measured from the point that `pinpoint/pose` tracks, usually
the center that the robot turns around. When the robot turns on the spot, that
point stays where it is, so `:x` and `:y` should stay near 0. If an offset is
wrong, they drift away from 0.

Over a full turn, the drift cancels out, so check with a half turn instead.
This function prints the pose every 100 ms while you turn the robot by hand,
and stops when the heading reaches about π (180°):

```clojure
(defn check-offsets []
  (loop []
    (let [p (pinpoint/pose)]
      (println (format "x %.2f  y %.2f  heading %.0f°"
                       (:x p) (:y p) (Math/toDegrees (:heading p))))
      (when (< (abs (:heading p)) 3.1)     ; until about 178° (π is 180°)
        (time/sleep 0.1)
        (recur)))))
```

Run it right after `pinpoint/init`, with the robot on the floor. Turn the
robot slowly on the spot, around its center, in either direction:

```clojure
user=> (check-offsets)
x 0.00  y 0.00  heading 0°
x 0.01  y 0.02  heading 12°
...
x 0.05  y -0.08  heading 179°
nil
```

The last `x` and `y` should be within a few hundredths of an inch of 0. If they
are about 1 inch off, an offset is wrong by about half an inch. Change one
offset a little, run `pinpoint/init` and `check-offsets` again, and see whether
the result moves closer to 0.

## status

`(pinpoint/status)` is the state of the device, such as `:ready`. It works
before `pinpoint/init`, so use it first to check the wiring:

```clojure
user=> (pinpoint/status)
:ready
```

A status of `:fault-x-pod-not-detected`, `:fault-y-pod-not-detected` or
`:fault-no-pods-detected` means that a pod is not plugged in.

`:not-configured` means that the robot configuration has no device named
`"pinpoint"`. In that case `pinpoint/init` returns `:not-configured` as well,
but `pinpoint/pose`, `pinpoint/velocity` and `pinpoint/reset` are an error. A
program that should also run without a Pinpoint therefore checks for `:ready`
before reading.

## init

`(pinpoint/init pods x-offset y-offset)` tells the Pinpoint about the pods,
then resets the pose to 0. Call it once, before the other `pinpoint/*`
functions.

- **pods:** `:swingarm` for goBILDA swingarm pods, `:4-bar` for goBILDA 4-bar
  pods, or for other encoders the number of encoder ticks per inch.
- **x-offset:** how far the X (forward) pod is to the side of the robot's
  center. Left is positive, right is negative.
- **y-offset:** how far the Y (sideways) pod is in front of the center.
  Forward is positive, backward is negative.

`pinpoint/init` also calibrates the IMU, which takes about a quarter of a
second, so keep the robot still while it runs. It waits for the calibration, then returns
the same status as `pinpoint/status`: `:ready`, a fault such as
`:fault-y-pod-not-detected`, or `:not-configured` if there is no Pinpoint in
the robot configuration. If the calibration has not finished after half a
second, it stops waiting and returns `:calibrating`.

When you push the robot forward by hand, `:x` should go up, and when you push
it to the left, `:y` should go up. If one of them goes down instead, that pod
counts the wrong way. To fix it, give the direction of each pod, `:forward` or
`:reverse`, after the offsets. Here the X pod is reversed:

```clojure
user=> (pinpoint/init :swingarm -3.3 -6.6 :reverse :forward)
:ready
```

Reading before `pinpoint/init` is an error, rather than a position measured
with the wrong pods:

```clojure
user=> (pinpoint/pose)
Execution error: The Pinpoint is not set up: first call pinpoint/init
```

## pose

`(pinpoint/pose)` is a map of `:x`, `:y` and `:heading`, all read at the same
moment.

`:x` and `:y` are measured from where the robot was at the last reset: `:x`
forward and `:y` to the left. `:heading` works like the IMU's yaw: it goes from
-π to π, and turning left (counter-clockwise) increases it.

```clojure
user=> (pinpoint/pose)
{:x 23.62, :y 0.41, :heading 0.003}
```

To show it on the Driver Hub with [Telemetry](telemetry.md), round it:

```clojure
user=> (let [{:keys [x y heading]} (pinpoint/pose)]
         (telemetry/add-line (format "x %.1f  y %.1f  heading %.0f°"
                                     x y (Math/toDegrees heading))))
nil
```

## velocity

`(pinpoint/velocity)` is a map of `:x` and `:y`, in inches per second, and
`:heading`, in radians per second.

```clojure
user=> (pinpoint/velocity)
{:x 12.4, :y -0.2, :heading 0.01}
```

To show it on the Driver Hub with [Telemetry](telemetry.md), round it:

```clojure
user=> (let [{:keys [x y heading]} (pinpoint/velocity)]
         (telemetry/add-line (format "x %.1f in/s  y %.1f in/s  heading %.0f°/s"
                                     x y (Math/toDegrees heading))))
nil
```

## reset

`(pinpoint/reset)` sets `:x`, `:y` and `:heading` back to 0 and calibrates the
IMU again. Call it while the robot is still, for example at the start of a
match:

```clojure
user=> (pinpoint/reset)
:ready
```

Like `pinpoint/init`, it waits up to half a second for the calibration, then
returns the status. Because the robot has to be still, don't call it inside a
driving loop.
