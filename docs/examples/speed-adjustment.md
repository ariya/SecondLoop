# Speed Adjustment

At full power, the robot can be hard to steer in a tight space. Keep a speed
factor in an [atom](../language/atom.md), and multiply the forward, strafe and
rotate values by it. This builds on `mecanum-drive` and `drive-with-gamepad`
from [Mecanum Drive](mecanum-drive.md):

```clojure
(defonce speed (atom 0.5))

(defn drive-with-gamepad []
  (loop []
    (let [pad     (gamepad 1)
          forward (* @speed (- (:left-stick-y pad)))
          strafe  (* @speed (:left-stick-x pad))
          rotate  (* @speed (:right-stick-x pad))]
      (mecanum-drive forward strafe rotate))
    (time/sleep 0.02)
    (recur)))
```

Paste it, then run `(task/start drive-with-gamepad)` again. A running loop
keeps the old definition, and `task/start` stops it before starting the new
one.

Now change the speed while you drive:

```clojure
user=> (reset! speed 0.3)
0.3
user=> (reset! speed 1)
1.0
```

Next, show where the robot is with [location tracking](location-tracking.md).
