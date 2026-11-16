# Location Tracking

If the robot has a [goBILDA Pinpoint](../robot/pinpoint.md) odometry computer,
the driving loop from [Speed Adjustment](speed-adjustment.md) can also show
where the robot is, which way it faces and how fast it is moving:

```clojure
(defonce speed (atom 0.5))

(defn drive-with-gamepad []
  (pinpoint/init :swingarm -3.3 -6.6 :forward :reverse) ; set up once, robot still
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

The arguments `:swingarm -3.3 -6.6 :forward :reverse` describe one particular
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
