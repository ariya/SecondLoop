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

(task/start spin)
```

The trigger reads from 0.0 when released to 1.0 when fully pressed, which is
exactly the range of a motor power. Stop it with `(task/stop)` (see
[Task](task.md)); pressing STOP on the Driver Hub also stops
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

## zero-power-behavior

`(motor/zero-power-behavior name)` is what the motor does when its power is 0:
`:brake` or `:float`.

```clojure
user=> (motor/zero-power-behavior "left_drive")
:brake
```

## set-zero-power-behavior

`(motor/set-zero-power-behavior name b)` sets what the motor does when its
power is 0, and returns it. `b` is one of:

- `:brake`: the motor resists turning, so it stops quickly and holds its place.
  Use it for a drivetrain, so the robot stops where the driver lets go of the
  stick instead of rolling on.
- `:float`: the motor turns freely and slows down only by friction. Use it for
  a flywheel or launcher: a heavy wheel that is stopped hard puts a lot of
  strain on the motor and gearbox, and spins down gently instead.

```clojure
user=> (motor/set-zero-power-behavior "left_drive" :brake)
:brake
user=> (motor/set-zero-power-behavior "flywheel" :float)
:float
```

Set it once at the start of the program, rather than relying on the default.
It changes nothing while the motor has power.

## run-mode

`(motor/run-mode name)` is the mode the motor controller is in:
`:run-without-encoder`, `:run-using-encoder`, `:stop-and-reset-encoder` or
`:run-to-position`.

```clojure
user=> (motor/run-mode "flywheel")
:run-using-encoder
```

## set-run-mode

`(motor/set-run-mode name m)` switches the motor to mode `m`, and returns it.
For example, switch the flywheel to `:run-using-encoder` and read its speed:

```clojure
user=> (motor/set-run-mode "flywheel" :run-using-encoder)
:run-using-encoder
user=> (motor/velocity "flywheel")
1187.0
```

`m` is one of these
[`DcMotor.RunMode`](https://javadoc.io/doc/org.firstinspires.ftc/RobotCore/latest/com/qualcomm/robotcore/hardware/DcMotor.RunMode.html)
values:

- `:run-without-encoder`
- `:run-using-encoder`
- `:run-to-position`, after
  [`motor/set-target-position`](#set-target-position)
- `:stop-and-reset-encoder`

## current-position

`(motor/current-position name)` is where the encoder is, in encoder ticks
counted from the last reset:

```clojure
user=> (motor/current-position "arm")
412.0
```

To count from zero again, reset the encoder, then switch back to a mode that
runs the motor:

```clojure
user=> (motor/set-run-mode "arm" :stop-and-reset-encoder)
:stop-and-reset-encoder
user=> (motor/current-position "arm")
0.0
user=> (motor/set-run-mode "arm" :run-using-encoder)
:run-using-encoder
```

## target-position

`(motor/target-position name)` is the position, in encoder ticks, that the
motor was last asked to go to:

```clojure
user=> (motor/target-position "arm")
0.0
```

## set-target-position

`(motor/set-target-position name t)` sets the target position to `t` encoder
ticks, and returns it. Ticks are whole numbers, so `t` is rounded, and the
result shows what was sent:

```clojure
user=> (motor/set-target-position "arm" 300)
300.0
user=> (motor/set-target-position "arm" (* 1.5 537.7))
807.0
```

The motor moves to the target only in the `:run-to-position` mode, and only
with a power to move it. The power is the top speed on the way there, and the
hub slows the motor down as it gets close. This lifts the arm to 300 ticks at
30% power:

```clojure
user=> (motor/set-target-position "arm" 300)
300.0
user=> (motor/set-run-mode "arm" :run-to-position)
:run-to-position
user=> (motor/set-power "arm" 0.3)
0.3
user=> (motor/current-position "arm")
298.0
```

The motor keeps holding the target, so move the arm again by giving it a new
one. A [velocity](#set-velocity) also works as the top speed.

`:run-to-position` without a target is an error:

```clojure
user=> (motor/set-run-mode "lift" :run-to-position)
Execution error: motor/set-run-mode needs a target first: call motor/set-target-position
```

!!! warning "Start slow"

    The motor pushes as hard as the power allows to reach the target. If the
    target is past the end of the arm's travel, or the encoder isn't plugged
    in, it keeps pushing. Try a small power first, and stop it with
    `(motor/set-power name 0)`.

## power

`(motor/power name)` is the power the motor was last given:

```clojure
user=> (motor/power "flywheel")
0.1
```

## set-power

`(motor/set-power name p)` runs the motor at power `p`, from -1.0 to 1.0, and
returns it, so you can see what was sent to the motor. 0 stops it.

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
means the same thing whether or not an encoder is plugged in. A motor in
`:run-to-position` stays there, and the power is its top speed on the way to
the [target](#set-target-position).

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
`motor/set-power` switches it back, so the two can be mixed on one motor. A
motor in `:run-to-position` stays there, as with `motor/set-power`.

!!! warning "Check the encoder cable first"

    The hub keeps raising the power until the encoder reports the target
    speed. If the encoder isn't plugged in, that never happens, and the motor
    runs at full power. Try a small velocity first and watch `(motor/velocity name)`.

## pidf

`(motor/pidf name)` is the set of gains the hub uses to hold a speed for
[`motor/set-velocity`](#set-velocity):

```clojure
user=> (motor/pidf "flywheel")
{:p 10.0, :i 3.0, :d 0.0, :f 0.0}
```

The numbers shown are typical defaults; yours depend on the motor.

## set-pidf

`(motor/set-pidf name gains)` changes those gains and returns the full set. A
gain that is left out becomes 0:

```clojure
user=> (motor/set-pidf "flywheel" {:p 20 :f 14})
{:p 20.0, :i 0.0, :d 0.0, :f 14.0}
```

Each gain must be 0 or more. `:f` gives power in proportion to the target
speed, and `:p`, `:i` and `:d` correct the error between the target and the
measured speed. [PIDF Tuning](../examples/pidf-tuning.md) shows how to pick
them for a flywheel.

Keep the gains you start with, so you can go back to them:

```clojure
user=> (def original (motor/pidf "flywheel"))
#'user/original
user=> (motor/set-pidf "flywheel" original)
{:p 10.0, :i 3.0, :d 0.0, :f 0.0}
```

The hub does not keep the gains for good. After it restarts, they are back to
the defaults.
