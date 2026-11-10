# Examples

These examples assume your motors are named `"front_left_drive"`,
`"front_right_drive"`, `"back_left_drive"` and `"back_right_drive"`. If yours
have other names, change the examples to match.

## Drivetrain

A [mecanum drivetrain](https://gm0.org/en/latest/docs/software/tutorials/mecanum-drive.html)
has four motors. Define a function that turns three numbers into four wheel
powers: how fast to drive forward, strafe right and turn right, each from -1.0
to 1.0. If the three numbers add up to more than 1, all four powers are scaled
down together, so the robot still moves in the direction you asked for:

```clojure
(defn mecanum-drive [forward strafe rotate]
  (motor/set-direction "front_left_drive" :forward)
  (motor/set-direction "back_left_drive" :forward)
  (motor/set-direction "front_right_drive" :reverse)
  (motor/set-direction "back_right_drive" :reverse)
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

The two motors on the right are mounted the other way round, so the function
reverses them first.

Paste it at the prompt.

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

## Game loop

Instead of typing each move, let a control loop make them in order. This one
drives forward, strafes right, drives backward, then strafes left, each for 3
seconds at power 0.3, so the robot ends up roughly where it started:

```clojure
(defn drive-square []
  (let [start (time/elapsed)]
    (loop []
      (let [t (- (time/elapsed) start)]             ; seconds since start
        (when (< t 12)
          (cond (< t 3) (mecanum-drive 0.3 0 0)     ; forward
                (< t 6) (mecanum-drive 0 0.3 0)     ; strafe right
                (< t 9) (mecanum-drive -0.3 0 0)    ; backward
                :else   (mecanum-drive 0 -0.3 0))   ; strafe left
          (time/sleep 0.02)
          (recur))))
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

To keep typing while the robot drives, use `control/start` instead. It runs
the function in the background and returns at once:

```clojure
user=> (control/start drive-square)
#<Future pending>
```

`#<Future pending>` means the work hasn't finished yet: the robot is still
driving the square, and you can type the next command while it does.

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

Start it with `(control/start drive-with-gamepad)` and end it with `(control/stop)`.
The wheels keep their last power after the loop ends, so stop them with
`(mecanum-drive 0 0 0)`. Pressing STOP on the Driver Hub stops them too.

Pushing a stick forward gives a negative `:left-stick-y`, so the loop negates
it (see [Gamepad](robot/gamepad.md)). The directions are relative to the
robot: forward is the way it faces.

## Speed adjustment

At full power, the robot can be hard to steer in a tight space. Keep a speed
factor in an [atom](language/atom.md), and multiply the forward, strafe and
rotate values by it:

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

Paste it, then run `(control/start drive-with-gamepad)` again. A running loop
keeps the old definition, and `control/start` stops it before starting the new
one.

Now change the speed at the prompt while you drive:

```clojure
user=> (reset! speed 0.3)
0.3
user=> (reset! speed 1)
1.0
```

## Location tracking

If the robot has a [goBILDA Pinpoint](robot/pinpoint.md) odometry computer,
the driving loop can also show where the robot is, which way it faces and how
fast it is moving:

```clojure
(defonce speed (atom 0.5))

(defn drive-with-gamepad []
  (pinpoint/init :swingarm -3.3 -6.6 :forward :reverse) ; set up once, robot still
  (loop [n 0]
    (let [pad     (gamepad 1)
          forward (* @speed (- (:left-stick-y pad)))
          strafe  (* @speed (:left-stick-x pad))
          rotate  (* @speed (:right-stick-x pad))]
      (mecanum-drive forward strafe rotate))
    (when (and (zero? (mod n 25))                      ; twice a second
               (= :ready (pinpoint/status)))
      (let [p (pinpoint/pose)
            v (pinpoint/velocity)]
        (println (format "xy = (%6.1f, %6.1f)  vx,vy = (%5.1f, %5.1f) in/s  %4.0f°"
                         (:x p) (:y p) (:x v) (:y v)
                         (Math/toDegrees (:heading p))))))
    (time/sleep 0.02)
    (recur (inc n))))
```

`pinpoint/init` runs only once, before the loop, because it resets the position
to 0 and calibrates the IMU. Keep the robot still when you start the loop.

The arguments `:swingarm -3.3 -6.6 :forward :reverse` describe one particular
robot: its type of odometry pods, where they are mounted, and which way each
pod counts. On this robot the Y pod is reversed, because `y` went down instead
of up when the robot strafed left. Change them to match your robot, as
explained under [init](robot/pinpoint.md#init) on the Pinpoint page.

The loop still runs fifty times a second, but it counts its rounds in `n` and
prints on every 25th round only, so the output stays readable. Each number in
the format has a fixed width, such as `%6.1f` (at least 6 characters, 1 after
the decimal point), so the columns stay lined up even when a value grows or
turns negative. The width fits a position up to ±999.9 inches and a speed up to
±99.9 inches per second:

```clojure
user=> (control/start drive-with-gamepad)
#<Future pending>
xy = (   0.0,    0.0)  vx,vy = (  0.0,   0.0) in/s     0°
xy = (   3.2,    0.1)  vx,vy = ( 11.8,   0.2) in/s     1°
xy = (   9.5,   -0.3)  vx,vy = ( 12.4,  -0.4) in/s     2°
...
```

The position is in inches, measured from where the robot was when the loop
started: `x` is forward and `y` is to the left. `vx` and `vy` are the speeds
along `x` and `y`, in inches per second. The last number is the heading, which
increases as the robot turns left.

The loop prints only while `pinpoint/status` is `:ready`. On a robot without a
Pinpoint, `pinpoint/init` and `pinpoint/status` return `:not-configured`, so
the loop just drives, as before.
