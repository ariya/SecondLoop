# Motors

A DC motor is named by the string it has in the robot configuration on the
Driver Hub, such as `"left"` or `"flywheel"`. There is nothing to look up
first: every `motor/*` function takes that name.

This program spins the flywheel as hard as the right trigger on gamepad 1 is
pressed:

```clojure
(defn spin []
  (loop []
    (motor/set-power "flywheel" (:right-trigger (gamepad 1)))
    (time/sleep 0.02)
    (recur)))

(control/start spin)
```

The trigger reads from 0.0 when released to 1.0 when fully pressed, which is
exactly the range of a motor power. Stop it with `(control/stop)` (see
[Control Loops](control-loops.md)); pressing STOP on the Driver Hub also stops
every motor.

A typo in a name, or a device that isn't a DC motor, is reported with the name:

```clojure
user=> (motor/set-power "moter" 0.1)
Execution error: No motor named "moter" in the robot configuration
```

## direction

`(motor/direction name)` is `:forward` or `:reverse`: which way a positive power
turns the motor.

```clojure
user=> (motor/direction "left")
:forward
```

## set-direction

`(motor/set-direction name d)` sets the direction to `:forward` or `:reverse`,
and returns it.

On a drivetrain, the motors on one side are mounted the other way round.
Reverse them once, and the same positive power then drives every wheel forward:

```clojure
user=> (motor/set-direction "left" :reverse)
:reverse
user=> (motor/direction "left")
:reverse
```

## power

`(motor/power name)` is the power the motor was last given:

```clojure
user=> (motor/power "flywheel")
0.1
```

## set-power

`(motor/set-power name p)` runs the motor at power `p`, from -1.0 to 1.0, and
returns it, so you can see at the prompt what was sent to the motor. 0 stops it.

```clojure
user=> (motor/set-power "flywheel" 0.1)
0.1
user=> (motor/set-power "flywheel" 0)
0.0
```

The power is a fraction of the battery voltage. A negative power turns the motor the other way. A power outside -1.0 to 1.0 is an
error instead of being silently clipped, so a mistake in a formula shows up at
once:

```clojure
user=> (motor/set-power "flywheel" 1.5)
Execution error: motor/set-power expects a power from -1.0 to 1.0, got: 1.5
```

To limit a computed power on purpose, use `clamp`, for example
`(motor/set-power "flywheel" (clamp p 0 0.2))`.

`motor/set-power` switches the motor to `RUN_WITHOUT_ENCODER`, so the power
means the same thing whether or not an encoder is plugged in.

## velocity

`(motor/velocity name)` is the speed the encoder measures, in encoder ticks per
second:

```clojure
user=> (motor/velocity "flywheel")
1187.0
```

The number of ticks in one turn depends on the motor; check its data sheet.

## set-velocity

`(motor/set-velocity name v)` asks the hub to hold a speed of `v` encoder ticks
per second, and returns it. A flywheel should spin at the same speed no matter
how charged the battery is, and with an encoder plugged in, this does that:

```clojure
user=> (motor/set-velocity "flywheel" 1200)
1200.0
user=> (motor/velocity "flywheel")
1187.0
user=> (motor/set-velocity "flywheel" 0)
0.0
```

The measured speed settles close to the target after a moment. A negative
velocity turns the motor the other way.

`motor/set-velocity` switches the motor to `RUN_USING_ENCODER`, and a later
`motor/set-power` switches it back, so the two can be mixed on one motor.

!!! warning "Check the encoder cable first"

    The hub keeps raising the power until the encoder reports the target
    speed. If the encoder isn't plugged in, that never happens, and the motor
    runs at full power. Try a small velocity first and watch `(motor/velocity name)`.
