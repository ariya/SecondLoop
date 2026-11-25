# Location Tracking

If the robot has a [goBILDA Pinpoint](../robot/pinpoint.md) odometry computer,
the driving loop from [Speed Adjustment](speed-adjustment.md) can also show
where the robot is, which way it faces and how fast it is moving:

```clojure
(defonce speed (atom 0.5))

(defn drive-with-gamepad []
  (pinpoint/init :4-bar -3 -7 :forward :reverse) ; set up once, robot still
  (loop []
    (let [pad     (gamepad 1)
          forward (* @speed (- (:left-stick-y pad)))
          strafe  (* @speed (:left-stick-x pad))
          rotate  (* @speed (:right-stick-x pad))]
      (mecanum-drive forward strafe rotate))
    (when (= :ready (pinpoint/status))
      (let [p (pinpoint/pose)
            v (pinpoint/velocity)]
        (telemetry/clear)
        (telemetry/add-line (format "xy    = (%6.1f, %6.1f) in" (:x p) (:y p)))
        (telemetry/add-line (format "vx,vy = (%6.1f, %6.1f) in/s" (:x v) (:y v)))
        (telemetry/add-line (format "heading %4.0f°" (Math/toDegrees (:heading p))))))
    (time/sleep 0.02)
    (recur)))
```

`pinpoint/init` runs only once, before the loop, because it resets the position
to 0 and calibrates the IMU. Keep the robot still when you start the loop.

The arguments `:4-bar -3 -7 :forward :reverse` describe one particular
robot: its type of odometry pods, where they are mounted, and which way each
pod counts. On this robot the Y pod is reversed, because `y` went down instead
of up when the robot strafed left. Change them to match your robot, as
explained under [init](../robot/pinpoint.md#init) on the Pinpoint page.

Every round, the loop clears the [telemetry](../robot/telemetry.md) and adds
three fresh lines, so the Driver Hub always shows the latest values in place.
After `(task/start drive-with-gamepad)`, the Driver Hub shows something like:

```
xy    = (   9.5,   -0.3) in
vx,vy = (  12.4,   -0.4) in/s
heading    2°
```

The position is in inches, measured from where the robot was when the loop
started: `x` is forward and `y` is to the left. `vx` and `vy` are the speeds
along `x` and `y`, in inches per second. The heading increases as the robot
turns left.

Since `x` and `y` stay fixed to the field, so do `vx` and `vy`. They tell how
fast the robot moves forward and sideways only while it still faces the way it
did when the loop started. Turn left a quarter turn and drive forward, and
`vx` stays near 0 while `vy` grows.

To see the velocity the way the robot does, turn it by the heading.
`robot-velocity` does that, so that `:forward` is along the way the robot
faces now and `:left` is toward its left side:

```clojure
(defn robot-velocity
  "The Pinpoint's velocity as seen from the robot, :forward and :left."
  []
  (let [v     (pinpoint/velocity)
        h     (:heading (pinpoint/pose))
        cos-h (Math/cos h)
        sin-h (Math/sin h)]
    {:forward (+ (* (:x v) cos-h) (* (:y v) sin-h))
     :left    (- (* (:y v) cos-h) (* (:x v) sin-h))}))
```

While the robot still faces the way it did at `pinpoint/init`, the heading is
0, and `:forward` and `:left` are the same as `:x` and `:y`.

Add it to the telemetry, as one more line in the loop:

```clojure
(defn drive-with-gamepad []
  (pinpoint/init :4-bar -3 -7 :forward :reverse) ; set up once, robot still
  (loop []
    (let [pad     (gamepad 1)
          forward (* @speed (- (:left-stick-y pad)))
          strafe  (* @speed (:left-stick-x pad))
          rotate  (* @speed (:right-stick-x pad))]
      (mecanum-drive forward strafe rotate))
    (when (= :ready (pinpoint/status))
      (let [p (pinpoint/pose)
            v (pinpoint/velocity)
            r (robot-velocity)]
        (telemetry/clear)
        (telemetry/add-line (format "xy    = (%6.1f, %6.1f) in" (:x p) (:y p)))
        (telemetry/add-line (format "vx,vy = (%6.1f, %6.1f) in/s" (:x v) (:y v)))
        (telemetry/add-line (format "heading %4.0f°" (Math/toDegrees (:heading p))))
        (telemetry/add-line (format "robot velocity = (%6.1f, %6.1f) in/s" (:forward r) (:left r)))))
    (time/sleep 0.02)
    (recur)))
```

Turn left a quarter turn, then drive forward, and the Driver Hub shows
something like:

```
xy    = (  -0.9,   16.7) in
vx,vy = (  -1.2,   20.5) in/s
heading   92°
robot velocity = (  20.5,    0.7) in/s
```

The robot drives forward at 20.5 inches per second. On the field, that is
along `y`, since it now faces left, so `vx` is near 0. The robot velocity
shows it as the robot sees it, forward and to the left, whichever way it
faces.
