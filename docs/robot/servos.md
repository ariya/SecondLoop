# Servos

A servo is named by the string it has in the robot configuration on the
Driver Hub, such as `"claw"` or `"wrist"`. As with [motors](motors.md),
every `servo/*` function takes that name.

This program moves the claw as far as the right trigger on gamepad 1 is
pressed:

```clojure
(defn grip []
  (loop []
    (servo/set-position "claw" (:right-trigger (gamepad 1)))
    (time/sleep 0.02)
    (recur)))

(control/start grip)
```

The trigger reads from 0.0 when released to 1.0 when fully pressed, which is
exactly the range of a servo position. Stop it with `(control/stop)` (see
[Control Loops](control-loops.md)).

The `servo/*` functions are for standard servos, configured as *Servo*. A servo
configured as *Continuous Rotation Servo* turns round and round, like a small
motor, which suits an intake roller or a turntable. It has its own `crservo/*`
functions, and is given a power instead of a position. There is no encoder, so
there is no velocity to set or read.

A typo in a name, or a device of the wrong kind, is reported with the name:

```clojure
user=> (servo/set-position "clow" 0.3)
Execution error: No servo named "clow" in the robot configuration
user=> (crservo/set-power "claw" 0.5)
Execution error: No continuous rotation servo named "claw" in the robot configuration
```

## direction

`(servo/direction name)` is `:forward` or `:reverse`: which end of its travel
0.0 is. `(crservo/direction name)` is which way a positive power turns it.

```clojure
user=> (servo/direction "left claw")
:forward
user=> (crservo/direction "intake")
:forward
```

## set-direction

`(servo/set-direction name d)` and `(crservo/set-direction name d)` set the
direction to `:forward` or `:reverse`, and return it.

Two servos that mirror each other, such as the two sides of a claw, turn the
opposite way for the same position. Reverse one, and the same position moves
both the same way:

```clojure
user=> (servo/set-direction "left claw" :reverse)
:reverse
user=> (servo/direction "left claw")
:reverse
```

Reversed, a position `p` moves the servo to where `1 - p` would move it
forward. A reversed continuous rotation servo turns the other way for the same
power:

```clojure
user=> (crservo/set-direction "intake" :reverse)
:reverse
```

## position

`(servo/position name)` is the position the servo was last given:

```clojure
user=> (servo/position "claw")
0.3
```

A servo cannot tell where it is, so this is only what it was last told, not a
measurement. If something blocks the arm, the two differ.

## set-position

`(servo/set-position name p)` moves the servo to position `p`, from 0.0 to 1.0,
and returns it, so you can see at the prompt what was sent to the servo.

```clojure
user=> (servo/set-position "claw" 0.3)
0.3
```

0.0 and 1.0 are the two ends of the servo's travel, and 0.5 is the middle.
`servo/set-position` returns at once, while the servo is still on its way; it
then holds that position until it is given another. A servo stays limp until it
is first given a position.

Anything outside 0.0 to 1.0 is an error rather than being cut down silently:

```clojure
user=> (servo/set-position "claw" 1.5)
Execution error: servo/set-position expects a position from 0.0 to 1.0, got: 1.5
```

Use `clamp` to limit a computed position on purpose, e.g.
`(servo/set-position "claw" (clamp p 0 1))`.

## power

This applies only to continuous rotation servos; a standard servo has a
[position](#position) instead. `(crservo/power name)` is the power it was last
given:

```clojure
user=> (crservo/power "intake")
0.5
```

## set-power

This applies only to continuous rotation servos; a standard servo is moved with
[set-position](#set-position) instead. `(crservo/set-power name p)` runs it at
power `p`, from
-1.0 to 1.0, and returns it. 0 stops it. As with `motor/set-power`, a power
outside -1.0 to 1.0 is an error.

```clojure
user=> (crservo/set-power "intake" 0.5)
0.5
user=> (crservo/set-power "intake" 0)
0.0
```
