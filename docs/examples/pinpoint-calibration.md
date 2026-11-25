# Pinpoint Calibration

The [Pinpoint](../robot/pinpoint.md) needs to know where its two pods are, the
offsets given to [`pinpoint/init`](../robot/pinpoint.md#init). Instead of
measuring them with a ruler, let the robot measure them by turning on the spot
a few times.

This example uses `mecanum-drive` from [Mecanum Drive](mecanum-drive.md).

## How it works

When the robot turns on the spot, its center does not move. So with the right
offsets, x and y of [`pinpoint/pose`](../robot/pinpoint.md#pose) stay at
(0, 0) the whole turn. The Pinpoint only sees its pods, which roll along as the
robot turns, and it uses the offsets to take that rolling away. With a wrong
offset, some is left, so x and y drift away during the turn and come back only
when the turn is complete.

So the robot can measure its own offsets. Set both to 0 in `pinpoint/init`,
turn on the spot, and watch x and y go up and down. How far they go gives the
offsets, and that is what [`find-offsets`](#find-the-offsets) measures.

The drawing shows it for the X pod alone, with the Y offset already right. The
X pod is 3 inches to the right of the center C, so its offset should be −3 in,
but it is set to 0. The Pinpoint then tracks P, 3 inches to the right of C:

![Left: top view of the robot, front up. The X pod is 3 inches to the right of the center C, but its offset is set to 0, so the Pinpoint tracks the point P, 3 inches to the right of C. As the robot turns right, P goes round C, and it is behind C at −90°, on the left at −180° and in front at −270°. Right: x of the tracked point during three turns, on the same scale. With the x offset set to 0, x follows a wave that goes down to −3 inches at −90° and up to +3 inches at −270°, once per turn, so the wave is as high as the x offset. With the x offset set to −3 inches, the Pinpoint tracks C and x stays at 0.](pinpoint-calibration.svg#only-light)
![Left: top view of the robot, front up. The X pod is 3 inches to the right of the center C, but its offset is set to 0, so the Pinpoint tracks the point P, 3 inches to the right of C. As the robot turns right, P goes round C, and it is behind C at −90°, on the left at −180° and in front at −270°. Right: x of the tracked point during three turns, on the same scale. With the x offset set to 0, x follows a wave that goes down to −3 inches at −90° and up to +3 inches at −270°, once per turn, so the wave is as high as the x offset. With the x offset set to −3 inches, the Pinpoint tracks C and x stays at 0.](pinpoint-calibration-dark.svg#only-dark)

As the robot turns right, P goes round C, and x, how far P is to the front,
goes up and down once per turn. After a quarter turn, at −90°, P is behind C
and x is −3 in, exactly the X offset. After three quarters, at −270°, P is in
front and x is +3 in. With the offset set to −3 in, the Pinpoint tracks C
itself, which does not move, so x stays at 0. So the height of the wave is the
X offset, and three turns give three waves to measure it from.

## Spin and record

`spin` turns the robot right at power 0.3, slow enough to be safe, until the
Pinpoint has counted the given number of full turns. It reads the pose every
100 ms, prints it after every quarter turn, and returns all the poses it read:

```clojure
(defn spin [turns]
  (mecanum-drive 0 0 0.3)
  (loop [prev (:heading (pinpoint/pose)) total 0 shown 0 poses []]
    (let [p       (pinpoint/pose)
          total   (+ total (wrap-radians (- (:heading p) prev)))
          quarter (Math/floor (/ (abs total) (/ Math/PI 2)))
          poses   (conj poses p)]
      (when (> quarter shown)
        (println (format "%5.0f°  x %6.2f  y %6.2f"
                         (Math/toDegrees total) (:x p) (:y p))))
      (if (< (abs total) (* turns 2 Math/PI))
        (do (time/sleep 0.1)
            (recur (:heading p) total (max quarter shown) poses))
        (do (mecanum-drive 0 0 0)
            poses)))))
```

The heading jumps from -π to π once per turn, so `spin` adds up the change
between two reads, wrapped with `wrap-radians`, to count the turns.

Try it first with the offsets you have, for example 0, and spin two turns.
`spin` returns every pose it read, about forty per turn, so count them
instead of printing them all:

```clojure
user=> (pinpoint/init :4-bar 0 0)
:ready
user=> (count (spin 2))
  -93°  x   4.39  y  10.15
 -187°  x  14.31  y   5.13
 -271°  x   9.86  y  -4.06
 -364°  x  -0.20  y   0.52
 -457°  x   4.90  y  10.32
 -541°  x  14.04  y   5.91
 -634°  x   9.54  y  -4.18
 -727°  x  -0.32  y   0.89
81.0
```

The quarter-turn lines appear as the robot turns, and the result comes once it
stops.

## Find the offsets

`find-offsets` spins, then fits the poses to the path the tracked point
follows. It prints the offsets and also returns them, so that a program can
use them:

```clojure
(defn find-offsets [turns]
  (let [poses (spin turns)
        n     (count poses)
        avg   (fn [f] (/ (reduce + (map f poses)) n))
        c     (avg (fn [p] (Math/cos (:heading p))))
        s     (avg (fn [p] (Math/sin (:heading p))))
        x     (avg (fn [p] (:x p)))
        y     (avg (fn [p] (:y p)))
        cx+sy (avg (fn [p] (+ (* (Math/cos (:heading p)) (:x p))
                               (* (Math/sin (:heading p)) (:y p)))))
        cy-sx (avg (fn [p] (- (* (Math/cos (:heading p)) (:y p))
                               (* (Math/sin (:heading p)) (:x p)))))
        d     (- 1 (* c c) (* s s))
        y-offset (/ (- cx+sy (+ (* c x) (* s y))) d)
        x-offset (/ (- cy-sx (- (* c y) (* s x))) d)]
    (println (format "x offset %.2f  y offset %.2f" x-offset y-offset))
    {:x-offset x-offset, :y-offset y-offset}))
```

The fit is a least-squares fit of x and y to waves of the heading, explained
below. It does not need the poses to be spread evenly around the turn, nor the
robot to start at 0.

Put the robot on the floor with room to turn. Check first that the pods count
the right way (see [init](../robot/pinpoint.md#init)), since turning on the
spot cannot tell. A pod that counts the wrong way only flips the sign of its offset,
so `find-offsets` gives the right size with the wrong sign, and the robot still
seems to turn perfectly. Only driving straight shows the mistake. Give the same
pod type and directions here, but with both offsets 0. Then turn three times:

```clojure
user=> (pinpoint/init :4-bar 0 0)
:ready
user=> (find-offsets 3)
  -94°  x   4.44  y  10.17
 -188°  x  14.35  y   4.98
 -273°  x   9.65  y  -4.14
 -367°  x  -0.30  y   0.81
 -451°  x   4.12  y  10.05
 -545°  x  14.22  y   5.43
 -638°  x   8.94  y  -4.36
 -723°  x  -0.15  y   0.38
 -817°  x   4.92  y  10.33
 -902°  x  14.12  y   5.71
 -996°  x   9.23  y  -4.28
-1081°  x  -0.03  y   0.06
x offset -3.00  y offset -7.00
{:x-offset -2.9999959513638963, :y-offset -7.0000193153256625}
```

The wrong point goes round in a circle and comes back near 0 after every full
turn. After half a turn it is farthest away, at twice the offsets, since 14 is
twice 7, and 6 is twice 3.

As the drawing in [How it works](#how-it-works) shows, with the x offset at 0,
x of the pose follows a wave whose height is the x offset. `find-offsets`
does not trust one point, since noise or a little slip can move any of them.
It looks for the wave that passes as close as possible to all the points, the
one with the smallest sum of squared misses, and takes its height.

When both offsets are wrong, each one adds its own wave to x and to y. For a
heading θ, the tracked point is at

```
x = Cx + (y offset) cos θ − (x offset) sin θ
y = Cy + (y offset) sin θ + (x offset) cos θ
```

where Cx and Cy are where the center C is. The x offset gives the sin wave in
x, as in the drawing, and the y offset gives the cos wave. `find-offsets`
finds the four numbers Cx, Cy, x offset and y offset that fit all the x and y
points best, at once. The heading is read from the Pinpoint, so it is not part
of the guess, and every pose counts the same, wherever in the turn it was
read.

The offsets are measured from the point the robot turns around, which for a
mecanum drivetrain is close to the middle of the four wheels. That is the point
`pinpoint/pose` then tracks. If the robot slides around while it turns, the
result is off, so run it two or three times and compare.

## Auto Calibrate

The pod directions are only a setting of `pinpoint/init`, so the robot can find
them too. With both pods set to `:forward`, `find-directions` drives forward,
then to the left, each for half a second at power 0.3. A pod whose reading goes
down instead of up has to be `:reverse`:

```clojure
(defn find-directions []
  (let [start (pinpoint/pose)]
    (mecanum-drive 0.3 0 0)                ; forward
    (time/sleep 0.5)
    (mecanum-drive 0 0 0)
    (time/sleep 0.5)
    (let [middle (pinpoint/pose)]
      (mecanum-drive 0 -0.3 0)             ; to the left
      (time/sleep 0.5)
      (mecanum-drive 0 0 0)
      (time/sleep 0.5)
      (let [end   (pinpoint/pose)
            x-dir (if (> (:x middle) (:x start)) :forward :reverse)
            y-dir (if (> (:y end) (:y middle)) :forward :reverse)]
        (println "X pod" x-dir "and Y pod" y-dir)
        [x-dir y-dir]))))
```

Try it. The robot drives for 1 second in all, so leave some space around it
on every side. Set both pods to `:forward` first, which is what
`pinpoint/init` does when you leave the directions out:

```clojure
user=> (pinpoint/init :4-bar 0 0)
:ready
user=> (find-directions)
X pod :forward and Y pod :forward
[:forward :forward]
```

Here both pods count the right way. A pod reported as `:reverse` needs
`:reverse` in `pinpoint/init`.

`spin-calibrate` puts it all together. Give it only the pod type. It finds the
pod directions, finds the offsets with three turns, gives both to the
Pinpoint, then turns once more to check them. With the right offsets, the
center stays where it is during that turn, so the pose should stay at 0 the
whole time, not only at the end, where a full turn hides any error.

```clojure
(defn spin-calibrate [pods]
  (pinpoint/init pods 0 0 :forward :forward)
  (let [[x-dir y-dir] (find-directions)]
    (pinpoint/init pods 0 0 x-dir y-dir)
    (let [offsets  (find-offsets 3)
          x-offset (:x-offset offsets)
          y-offset (:y-offset offsets)]
      (time/sleep 0.5)                     ; let the robot come to rest
      (pinpoint/init pods x-offset y-offset x-dir y-dir)
      (println "Checking with one more turn")
      (let [poses (spin 1)
            drift (apply max (map (fn [p] (Math/hypot (:x p) (:y p))) poses))]
        (println (format "Drift at most %.2f in" drift))
        (if (< drift 0.5)
          (println (format "OK, use (pinpoint/init %s %.2f %.2f %s %s)"
                           pods x-offset y-offset x-dir y-dir))
          (println "Too much drift, the robot slipped, so run it again"))))))
```

Each `pinpoint/init` resets the pose to 0 and calibrates the IMU, which needs
the robot to be still. `find-directions` and `spin` stop the wheels before
they return, and `find-directions` and the pause give the robot time to come to
rest.

`drift` is the farthest the pose got from 0 during the checking turn. Half an
inch allows for a little wheel slip; tighten it if your robot turns cleanly.
Too much drift means that the robot slid around or did not turn on the spot.

Put the robot on the floor and run it:

```clojure
user=> (spin-calibrate :4-bar)
X pod :forward and Y pod :forward
  -95°  x   4.71  y  10.55
 -182°  x  14.16  y   6.04
 -278°  x   9.09  y  -4.03
 -363°  x  -0.11  y   0.72
 -459°  x   5.17  y  10.69
 -545°  x  14.29  y   5.68
 -631°  x   9.88  y  -3.76
 -727°  x  -0.25  y   1.14
 -813°  x   4.37  y  10.43
 -908°  x  14.39  y   5.33
 -994°  x   9.59  y  -3.87
-1089°  x  -0.33  y   1.43
x offset -3.01  y offset -7.00
Checking with one more turn
  -97°  x   0.00  y  -0.01
 -183°  x  -0.00  y  -0.01
 -278°  x  -0.01  y  -0.00
 -364°  x   0.00  y  -0.00
Drift at most 0.01 in
OK, use (pinpoint/init :4-bar -3.01 -7.00 :forward :forward)
nil
```

After the first three turns the tracked point goes round in a circle, as
before. During the checking turn it stays at 0, so the offsets are right, and
the Pinpoint is now set up with them. From now on, use the `pinpoint/init`
it printed in your program.
