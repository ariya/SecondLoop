# Motors

A DC motor is named by the string it has in the robot configuration on the
Driver Hub, such as `"left"` or `"flywheel"`. There is nothing to look up
first: every `motor/*` function takes that name.

| Function | Does |
| --- | --- |
| `(motor/set-power name p)` | Runs the motor at power `p`, from -1.0 to 1.0. 0 stops it. |
| `(motor/power name)` | The power it was last given. |
| `(motor/set-velocity name v)` | Holds a speed of `v` encoder ticks per second. |
| `(motor/velocity name)` | The speed the encoder measures, in ticks per second. |
| `(motor/set-direction name d)` | `:forward` or `:reverse`: which way a positive power turns. |
| `(motor/direction name)` | `:forward` or `:reverse`. |

A typo in a name, or a device that isn't a DC motor, is reported with the name:

```clojure
user=> (motor/set-power "moter" 0.1)
Execution error: No motor named "moter" in the robot configuration
```

Each `motor/set-*` function returns the value it was given, so you can see at
the prompt what was sent to the motor.

## Power

```clojure
user=> (motor/set-power "motor" 0.1)
0.1
user=> (motor/power "motor")
0.1
user=> (motor/set-power "motor" 0)
0.0
```

The power is a fraction of the battery voltage, just like `setPower` in Java. A
negative power turns the motor the other way. A power outside -1.0 to 1.0 is an
error instead of being silently clipped, so a mistake in a formula shows up at
once:

```clojure
user=> (motor/set-power "motor" 1.5)
Execution error: motor/set-power expects a power from -1.0 to 1.0, got: 1.5
```

To limit a computed power on purpose, use `clamp`, for example
`(motor/set-power "motor" (clamp p 0 0.2))`.

`motor/set-power` switches the motor to `RUN_WITHOUT_ENCODER`, so the power
means the same thing whether or not an encoder is plugged in.

## Velocity

A flywheel should spin at the same speed no matter how charged the battery is.
With an encoder plugged in, `motor/set-velocity` asks the hub to hold a speed in encoder
ticks per second, and `motor/velocity` reads back what the encoder measures:

```clojure
user=> (motor/set-velocity "flywheel" 1200)
1200.0
user=> (motor/velocity "flywheel")
1187.0
user=> (motor/set-velocity "flywheel" 0)
0.0
```

The measured speed settles close to the target after a moment. A negative
velocity turns the motor the other way. The number of ticks in one turn depends
on the motor; check its data sheet.

`motor/set-velocity` switches the motor to `RUN_USING_ENCODER`, and a later
`motor/set-power` switches it back, so the two can be mixed on one motor.

!!! warning "Check the encoder cable first"

    The hub keeps raising the power until the encoder reports the target
    speed. If the encoder isn't plugged in, that never happens, and the motor
    runs at full power. Try a small velocity first and watch `(motor/velocity name)`.

## Direction

On a drivetrain, the motors on one side are mounted the other way round.
Reverse them once, and the same positive power then drives every wheel forward:

```clojure
user=> (motor/set-direction "left" :reverse)
:reverse
user=> (motor/direction "left")
:reverse
```

## Testing

This loop tests one motor, named `"motor"`. A and B pick the direction, D-pad
up and down change the power in steps of 0.01 between 0 and 0.2, and the motor
runs while you hold the right trigger:

```clojure
(def motor-name "motor")
(defonce power (atom 0.1))         ; change it from the REPL too: (reset! power 0.15)

(defn pressed?
  "True when button k is down now but was up in the previous snapshot."
  [prev pad k]
  (and (k pad) (not (k prev))))

(defn step [prev pad]
  (when (pressed? prev pad :a) (motor/set-direction motor-name :forward))
  (when (pressed? prev pad :b) (motor/set-direction motor-name :reverse))
  (when (pressed? prev pad :dpad-up) (swap! power (fn [p] (clamp (+ p 0.01) 0 0.2))))
  (when (pressed? prev pad :dpad-down) (swap! power (fn [p] (clamp (- p 0.01) 0 0.2))))
  (motor/set-power motor-name (if (> (:right-trigger pad) 0) @power 0))
  (telemetry/clear)
  (telemetry/add-line "Motor" motor-name)
  (telemetry/add-line "Direction" (motor/direction motor-name))
  (telemetry/add-line (format "Power %6.2f" @power)))

(defn control-loop []
  (try
    (loop [prev (gamepad 1)]
      (let [pad (gamepad 1)]
        (step prev pad)
        (time/sleep 0.02)
        (recur pad)))
    (finally
      (motor/set-power motor-name 0))))
```

Send it, then start the loop with
`(control/start control-loop)` and stop it with `(control/stop)` (see
[Control Loops](control-loops.md)).

- **A press, not a hold.** A snapshot from `(gamepad 1)` only says whether a
  button is down. The loop keeps the previous snapshot in `prev`, and
  `pressed?` is true only in the round where the button goes down. Holding
  D-pad up therefore raises the power once, not fifty times a second.
- **Tune it live.** `power` is an atom, so `(reset! power 0.15)` from another
  connection changes it while the loop runs; see [Atom](../language/atom.md).
- **Stop safely.** The `finally` sets the power back to 0 when the loop ends,
  whether by `control/stop`, STOP or an error. Pressing STOP on the Driver
  Hub also stops every motor.
