# IMU

The Control Hub has an IMU, a sensor that tells which way the robot faces. The
robot configuration names it `"imu"`, and the `imu/*` functions use that name.
Angles are in radians, as `Math/sin` and `Math/cos` expect; `Math/toDegrees`
turns one into degrees, and `Math/toRadians` turns degrees back into radians.

| Function | Does |
| --- | --- |
| `(imu/init logo usb)` | Tells the IMU how the hub is mounted. Call it once, before the others. |
| `(imu/reset-yaw)` | Makes the way the robot faces now the yaw of 0. |
| `(imu/angles)` | A map of `:yaw`, `:pitch` and `:roll`, read at the same moment. |

`:yaw` is the heading: turning left (counter-clockwise) increases it, from -π
to π (-180° to 180°). `:pitch` is the nose tilting up or down, and `:roll` the robot leaning
to one side. Most programs only need the yaw.

## Setup

The IMU measures the hub, not the robot, so it needs to know how the hub sits
on the robot. Give the direction the REV logo faces, then the direction the
USB ports face, each one of `:up`, `:down`, `:left`, `:right`, `:forward` or
`:backward`. For a hub lying flat with the USB ports towards the back:

```clojure
user=> (imu/init :up :backward)
nil
user=> (imu/reset-yaw)
nil
user=> (:yaw (imu/angles))
0.0
```

Turn the robot a quarter turn to the left by hand, and the yaw follows. A
quarter turn is π/2, about 1.57 radians; `Math/toDegrees` shows it in degrees:

```clojure
user=> (:yaw (imu/angles))
1.5655603390389137
user=> (Math/toDegrees (:yaw (imu/angles)))
89.7
```

To show it on the Driver Hub, round it too:

```clojure
user=> (telemetry/add-line (format "Yaw %.1f°" (Math/toDegrees (:yaw (imu/angles)))))
nil
```

Reading before `imu/init` is an error, rather than an angle measured the wrong
way round:

```clojure
user=> (imu/angles)
Execution error: The IMU is not set up: first call imu/init with the way the hub is mounted, e.g. (imu/init :up :backward)
```

The logo and the USB ports cannot face along the same axis, such as `:up` and
`:down`; that is an error too.

## Turning

The difference between the yaw and the heading you want tells which way to
turn and how hard. `wrap-radians` keeps it within -π to π, so the robot turns
the short way round:

```clojure
(defn turn-power
  "Rotate power that turns the robot towards heading, in radians."
  [heading]
  (* 0.6 (clamp (wrap-radians (- (:yaw (imu/angles)) heading)) -1 1)))
```

A heading in degrees is converted first, e.g.
`(turn-power (Math/toRadians 90))` to face a quarter turn left of where
`imu/reset-yaw` was called.

A positive power turns right, which lowers the yaw. Pass it as the `rotate` of
`mecanum-drive` (see the [Examples](../examples.md)), or as
opposite powers to the left and right motors. Near the heading the power gets
small, so the robot slows down as it arrives.
