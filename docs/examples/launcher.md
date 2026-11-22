# Launcher Motor

If a [motor](../robot/motors.md) is connected, such as a flywheel that launches
artifacts, you can control it from your laptop. Assuming the motor is named
`flywheel` in the robot configuration, this sets its direction, spins it for
three seconds, and then stops it:

```clojure
(let [power 0.8]
  (motor/set-direction "flywheel" :reverse)
  (motor/set-zero-power-behavior "flywheel" :float) ; spin down gently
  (motor/set-power "flywheel" power)           ; spin
  (time/sleep 3)                               ; for 3 seconds
  (motor/set-power "flywheel" 0))              ; then stop
```

The form keeps running while the motor spins, and finishes once it stops. If the
flywheel spins the wrong way, use `:forward` instead of `:reverse`.

With `:float`, the flywheel isn't stopped hard at power 0: it coasts and spins
down by itself over a few seconds, which is easier on the motor and its
gearbox (see [set-zero-power-behavior](../robot/motors.md#set-zero-power-behavior)).

Next, hold the flywheel at a steady speed with [PIDF tuning](pidf-tuning.md).
