# Launcher Motor

If a [motor](../robot/motors.md) is connected, such as a flywheel that launches
artifacts, you can control it from the prompt. Assuming the motor is named
`flywheel` in the robot configuration, this sets its direction, spins it for
three seconds, and then stops it:

```clojure
(let [power 0.8]
  (motor/set-direction "flywheel" :reverse)
  (motor/set-power "flywheel" power)           ; spin
  (time/sleep 3)                               ; for 3 seconds
  (motor/set-power "flywheel" 0))              ; then stop
```

The prompt waits while the motor spins, and returns once it stops. If the
flywheel spins the wrong way, use `:forward` instead of `:reverse`.

Next, drive a whole robot with a [tank drivetrain](tank-drive.md).
