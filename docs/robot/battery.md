# Battery

`(battery/voltage)` is the [battery](https://ftc-docs.firstinspires.org/en/latest/control_hard_compon/rc_components/power_distr/power-distr.html)
voltage in volts:

```clojure
user=> (battery/voltage)
12.84
```

Every Control Hub and Expansion Hub measures the voltage it receives. With more
than one hub, `battery/voltage` gives the lowest reading, because that hub is
the first to suffer when the voltage sags. If no hub reports a voltage, it is
`0.0`.

A fully charged 12 V FTC battery reads about 13 V or more, and it drops under
load, for example while the drivetrain accelerates.

A quick way to keep an eye on the battery is to print its voltage to the
[telemetry](telemetry.md) on the Driver Hub:

```clojure
user=> (telemetry/add-line (format "Battery %.1f V" (battery/voltage)))
nil
```

One way to use the battery voltage is to compensate the motor power.
[`motor/set-power`](motors.md#power) gives a fraction of the battery voltage, so
the same power turns a motor more slowly on a tired battery. To get the same
speed, scale the power by a reference voltage, here 12 V, over the voltage now:

```clojure
(defn compensated [p]
  (clamp (* p (/ 12 (battery/voltage))) -1 1))

(motor/set-power "flywheel" (compensated 0.6))
```

At 12.84 V that gives 0.56, and at 10.5 V it gives 0.69. With an encoder,
[`motor/set-velocity`](motors.md#velocity) does this for you.
