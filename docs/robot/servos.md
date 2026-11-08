# Servos

A servo is named by the string it has in the robot configuration on the
Driver Hub, such as `"claw"` or `"wrist"`. As with [motors](motors.md),
every `servo/*` function takes that name.

| Function | Does |
| --- | --- |
| `(servo/set-position name p)` | Moves the servo to position `p`, from 0.0 to 1.0. |
| `(servo/position name)` | The position it was last given. |
| `(servo/set-direction name d)` | `:forward` or `:reverse`: which end of its travel 0.0 is. |
| `(servo/direction name)` | `:forward` or `:reverse`. |

Each `servo/set-*` function returns the value it was given, so you can see at
the prompt what was sent to the servo. The `crservo/set-*` functions below do
the same.

These are for standard servos, configured as *Servo*. A servo configured as
*Continuous Rotation Servo* has its own functions; see
[Continuous rotation servos](#continuous-rotation-servos).

## Position

```clojure
user=> (servo/set-position "claw" 0.3)
0.3
user=> (servo/position "claw")
0.3
```

0.0 and 1.0 are the two ends of the servo's travel, and 0.5 is the middle.
`servo/set-position` returns at once, while the servo is still on its way; it
then holds that position until it is given another. A servo stays limp until it
is first given a position.

A servo cannot tell where it is, so `servo/position` is only what it was last
told, not a measurement. If something blocks the arm, the two differ.

Anything outside 0.0 to 1.0 is an error rather than being cut down silently:

```clojure
user=> (servo/set-position "claw" 1.5)
Execution error: servo/set-position expects a position from 0.0 to 1.0, got: 1.5
```

Use `clamp` to limit a computed position on purpose, e.g.
`(servo/set-position "claw" (clamp p 0 1))`.

## Direction

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
forward.

## Continuous rotation servos

A continuous rotation servo turns round and round, like a small motor, so it is
given a power instead of a position. It suits an intake roller or a turntable:

| Function | Does |
| --- | --- |
| `(crservo/set-power name p)` | Runs it at power `p`, from -1.0 to 1.0. 0 stops it. |
| `(crservo/power name)` | The power it was last given. |
| `(crservo/set-direction name d)` | `:forward` or `:reverse`: which way a positive power turns. |
| `(crservo/direction name)` | `:forward` or `:reverse`. |

```clojure
user=> (crservo/set-power "intake" 0.5)
0.5
user=> (crservo/power "intake")
0.5
user=> (crservo/set-power "intake" 0)
0.0
```

As with `motor/set-power`, a power outside -1.0 to 1.0 is an error. There is no
encoder, so there is no velocity to set or read.

## Unknown names

A typo in a name, or a device of the wrong kind, is reported with the name:

```clojure
user=> (servo/set-position "clow" 0.3)
Execution error: No servo named "clow" in the robot configuration
user=> (crservo/set-power "claw" 0.5)
Execution error: No continuous rotation servo named "claw" in the robot configuration
```

## Testing

Before a claw or an arm can be programmed, you need its positions: where the
claw is open, where it is closed. This loop moves one servo, named `"servo"`,
from the gamepad and shows where it is. D-pad up and down move it by 0.01, the
bumpers by 0.1, and A and B pick the direction:

```clojure
(def servo-name "servo")
(defonce position (atom 0.5))      ; change it from the REPL too: (reset! position 0.25)

(defn pressed?
  "True when button k is down now but was up in the previous snapshot."
  [prev pad k]
  (and (k pad) (not (k prev))))

(defn nudge [d]
  (swap! position (fn [p] (clamp (+ p d) 0 1))))

(defn step [prev pad]
  (when (pressed? prev pad :a) (servo/set-direction servo-name :forward))
  (when (pressed? prev pad :b) (servo/set-direction servo-name :reverse))
  (when (pressed? prev pad :dpad-up) (nudge 0.01))
  (when (pressed? prev pad :dpad-down) (nudge -0.01))
  (when (pressed? prev pad :right-bumper) (nudge 0.1))
  (when (pressed? prev pad :left-bumper) (nudge -0.1))
  (servo/set-position servo-name @position)
  (telemetry/clear)
  (telemetry/add-line "Servo" servo-name)
  (telemetry/add-line "Direction" (servo/direction servo-name))
  (telemetry/add-line (format "Position %5.2f" @position)))

(defn control-loop []
  (loop [prev (gamepad 1)]
    (let [pad (gamepad 1)]
      (step prev pad)
      (time/sleep 0.02)
      (recur pad))))
```

Send it, then start the loop with
`(control/start control-loop)` and stop it with `(control/stop)` (see
[Control Loops](control-loops.md)).

- **Start in the middle.** The servo jumps to 0.5 as soon as the loop starts,
  so make sure nothing is in the way. If the mechanism can only travel part of
  the servo's range, `(reset! position ...)` to a safe value before starting.
- **Note the numbers.** Move the servo to each position you need and write down
  what the Driver Hub shows; those go into your program.
- **Nothing to clean up.** Unlike the motor tester, there is no `finally`: a
  servo that stops getting new positions simply stays where it is.
