# Mecanum Drive

This example assumes your motors are named `"front_left_drive"`,
`"front_right_drive"`, `"back_left_drive"` and `"back_right_drive"`. If yours
have other names, change the code to match.

## Wheel powers

A [mecanum drivetrain](https://gm0.org/en/latest/docs/software/tutorials/mecanum-drive.html)
has four motors. Define a function that turns three numbers into four wheel
powers: how fast to drive forward, strafe right and turn right, each from -1.0
to 1.0. If the three numbers add up to more than 1, all four powers are scaled
down together, so the robot still moves in the direction you asked for:

```clojure
(defn mecanum-drive [forward strafe rotate]
  (motor/set-direction "front_left_drive" :reverse)
  (motor/set-direction "back_left_drive" :reverse)
  (let [d           (max (+ (abs forward) (abs strafe) (abs rotate)) 1)
        front-left  (/ (+ forward strafe rotate) d)
        front-right (/ (- forward strafe rotate) d)
        back-left   (/ (+ (- forward strafe) rotate) d)
        back-right  (/ (- (+ forward strafe) rotate) d)]
    (motor/set-power "front_left_drive" front-left)
    (motor/set-power "front_right_drive" front-right)
    (motor/set-power "back_left_drive" back-left)
    (motor/set-power "back_right_drive" back-right)
    [front-left front-right back-left back-right]))
```

The two motors on the left are mounted the other way round, so the function
reverses them first.

Also make all four motors brake when their power is 0, so the robot stops
where you stop it instead of rolling on (see
[set-zero-power-behavior](../robot/motors.md#set-zero-power-behavior)):

```clojure
(motor/set-zero-power-behavior "front_left_drive" :brake)
(motor/set-zero-power-behavior "front_right_drive" :brake)
(motor/set-zero-power-behavior "back_left_drive" :brake)
(motor/set-zero-power-behavior "back_right_drive" :brake)
```

Paste both at the prompt.

Now drive the robot by hand, one move at a time.

First, drive forward slowly, then stop by setting everything to 0:

```clojure
user=> (mecanum-drive 0.3 0 0)
[0.3 0.3 0.3 0.3]
user=> (mecanum-drive 0 0 0)
[0.0 0.0 0.0 0.0]
```

Each vector is what `mecanum-drive` returns: the power it gave each wheel, in
the order front left, front right, back left, back right. Driving forward sets
all four wheels to 0.3, and stopping sets them all to 0.0.

Move sideways to the right, then stop:

```clojure
user=> (mecanum-drive 0 0.3 0)
[0.3 -0.3 -0.3 0.3]
user=> (mecanum-drive 0 0 0)
[0.0 0.0 0.0 0.0]
```

Turn right on the spot, then stop:

```clojure
user=> (mecanum-drive 0 0 0.3)
[0.3 -0.3 0.3 -0.3]
user=> (mecanum-drive 0 0 0)
[0.0 0.0 0.0 0.0]
```

If a wheel turns the wrong way, fix its direction with `motor/set-direction`
and try again.

## Drive a square

Instead of typing each move, put them in a function. This one drives forward,
strafes right, drives backward, then strafes left, each for 3 seconds at power
0.3, so the robot ends up roughly where it started:

```clojure
(defn drive-square []
  (let [speed 0.3]
    (mecanum-drive speed 0 0)                       ; forward
    (time/sleep 3)
    (mecanum-drive 0 speed 0)                       ; strafe right
    (time/sleep 3)
    (mecanum-drive (- speed) 0 0)                   ; backward
    (time/sleep 3)
    (mecanum-drive 0 (- speed) 0)                   ; strafe left
    (time/sleep 3)
    (mecanum-drive 0 0 0)))                         ; stop
```

Pasting this only defines `drive-square`; the robot doesn't move yet. To drive
the square, call it:

```clojure
user=> (drive-square)
[0.0 0.0 0.0 0.0]
```

The robot drives the square, but you have to wait the full 12 seconds. The
result and the next prompt appear only after the robot stops, and you can't
type anything in the meantime.

**While it drives, the robot can't do anything else.** Each `time/sleep` holds
the prompt, so you can't spin the launcher, read the gamepad or change the
speed until the square is done.

To keep typing while the robot drives, use `task/start` instead. It runs
the function in the background and returns at once:

```clojure
user=> (task/start drive-square)
#<Future pending>
```

`#<Future pending>` means the work hasn't finished yet: the robot is still
driving the square, and you can type the next command while it does.
[Control Loops](../control-loops.md) explains how to run, stop and clean up
background work like this.

## Driver control

Now hand the robot over to the gamepad. This loop reads gamepad 1 fifty times
a second: the left stick drives forward, backward and sideways, and the right
stick turns:

```clojure
(defn drive-with-gamepad []
  (loop []
    (let [pad     (gamepad 1)
          forward (- (:left-stick-y pad))
          strafe  (:left-stick-x pad)
          rotate  (:right-stick-x pad)]
      (mecanum-drive forward strafe rotate))
    (time/sleep 0.02)
    (recur)))
```

Start it with `(task/start drive-with-gamepad)` and end it with `(task/stop)`.
The wheels keep their last power after the loop ends, so stop them with
`(mecanum-drive 0 0 0)`. Pressing STOP on the Driver Hub stops them too.

Pushing a stick forward gives a negative `:left-stick-y`, so the loop negates
it (see [Gamepad](../robot/gamepad.md)). The directions are relative to the
robot: forward is the way it faces.

Next, make the robot easier to steer with a [speed adjustment](speed-adjustment.md).
