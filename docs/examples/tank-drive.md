# Tank Drive

!!! info

    If your robot has a mecanum drivetrain instead, see the next example,
    [Mecanum Drive](mecanum-drive.md).

With a [tank drivetrain](https://gm0.org/en/latest/docs/common-mechanisms/drivetrains/tank.html),
you can drive the whole robot from the gamepad. The left stick drives forward
and backward, and the right stick turns the robot. To turn, the two sides spin
in opposite directions:

![Two top views of a tank-drive robot, with a gamepad below each. On the left, the left stick is pushed forward, both sides drive forward, and the robot drives forward. On the right, the right stick is pushed to the right, the left side drives forward and the right side backward, and the robot turns to the right in place.](tank-drive.svg#only-light)
![Two top views of a tank-drive robot, with a gamepad below each. On the left, the left stick is pushed forward, both sides drive forward, and the robot drives forward. On the right, the right stick is pushed to the right, the left side drives forward and the right side backward, and the robot turns to the right in place.](tank-drive-dark.svg#only-dark)

Assuming the two motors are named `left_drive` and `right_drive`, this loop
adds the turn to the left side and subtracts it from the right side:

```clojure
(defn drive-tank []
  (motor/set-direction "left_drive" :reverse)
  (motor/set-zero-power-behavior "left_drive" :brake)
  (motor/set-zero-power-behavior "right_drive" :brake)
  (loop []
    (let [pad     (gamepad 1)
          forward (- (:left-stick-y pad))
          turn    (:right-stick-x pad)
          d       (max (+ (abs forward) (abs turn)) 1)]
      (motor/set-power "left_drive" (/ (+ forward turn) d))
      (motor/set-power "right_drive" (/ (- forward turn) d)))
    (time/sleep 0.02)                          ;; 50 times/sec
    (recur)))
```

The two motors face opposite ways, so one of them is reversed. If the robot
spins in place or drives backward, reverse `right_drive` instead of
`left_drive`. Pushing a stick forward gives a negative value, so the loop
negates it (see [Gamepad](../robot/gamepad.md)). A power must stay between -1
and 1, so if `forward` and `turn` add up to more than 1, both powers are scaled
down together. Both motors brake at power 0, so the robot stops as soon as you
let go of the sticks, instead of rolling on (see [set-zero-power-behavior](../robot/motors.md#set-zero-power-behavior)).

Paste the code into the notebook and run it, then start driving:

```clojure
user=> (drive-tank)
```

That's it, about a dozen lines of code, and the robot is ready to drive. The loop
keeps going until you press STOP on the Driver Hub.

Next, a [mecanum drivetrain](mecanum-drive.md) with four motors, driven both
from the notebook and from the gamepad.
