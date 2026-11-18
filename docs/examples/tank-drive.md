# Tank Drive

!!! info

    If your robot has a mecanum drivetrain instead, see the next example,
    [Mecanum Drive](mecanum-drive.md).

With a [tank drivetrain](https://gm0.org/en/latest/docs/common-mechanisms/drivetrains/tank.html),
you can drive the whole robot from the gamepad. Assuming the two motors are
named `left_drive` and `right_drive`, this loop sends each stick to its own
side:

```clojure
(defn drive-tank []
  (motor/set-direction "left_drive" :reverse)
  (motor/set-zero-power-behavior "left_drive" :brake)
  (motor/set-zero-power-behavior "right_drive" :brake)
  (loop []
    (let [pad (gamepad 1)]
      (motor/set-power "left_drive" (- (:left-stick-y pad)))
      (motor/set-power "right_drive" (- (:right-stick-y pad))))
    (time/sleep 0.02)                          ;; 50 times/sec
    (recur)))
```

The two motors face opposite ways, so one of them is reversed. If the robot
spins in place or drives backward, reverse `right_drive` instead of
`left_drive`. Pushing a stick forward gives a negative value, so the loop
negates it (see [Gamepad](../robot/gamepad.md)). Both motors brake at power 0,
so the robot stops as soon as you let go of the sticks, instead of rolling on
(see [set-zero-power-behavior](../robot/motors.md#set-zero-power-behavior)).

Paste the code at the prompt, then run it:

```clojure
user=> (drive-tank)
```

That's it: about ten lines of code, and the robot is ready to drive. The loop
keeps going until you press STOP on the Driver Hub.

Next, a [mecanum drivetrain](mecanum-drive.md) with four motors, driven both
from the prompt and from the gamepad.
