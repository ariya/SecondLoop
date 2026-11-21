# PIDF Tuning

A flywheel should launch every artifact at the same speed, whether the battery
is full or nearly empty, and it should get back to that speed quickly after
each shot. [`motor/set-velocity`](../robot/motors.md#set-velocity) asks the hub
to hold a speed, and four gains decide how well it does that:

- `:f` (feedforward): power for the target speed. It does most of the work.
- `:p` (proportional): power for the error, the gap between target and
  measured speed. It recovers the speed after a shot.
- `:i` (integral): the error added up over time. It removes a small leftover
  gap.
- `:d` (derivative): reacts to how fast the error changes. A flywheel rarely
  needs it, so it stays 0.

The hub's default gains work for many motors, but not well for a heavy
flywheel. This page follows the
[REV PIDF tuning guide](https://docs.revrobotics.com/first-global/fgc-pid):
measure the top speed, then work out the gains from it.

Start with the [Launcher Motor](launcher.md) setup:

```clojure
user=> (motor/set-direction "flywheel" :reverse)
:reverse
user=> (motor/set-zero-power-behavior "flywheel" :float)
:float
```

First, measure the top speed. With a fully charged battery, run the flywheel
at full power for three seconds and keep the highest speed the encoder
reports:

```clojure
(defn max-velocity [name]
  (try
    (motor/set-power name 1)
    (let [deadline (+ (time/elapsed) 3)]
      (loop [top 0]
        (if (< (time/elapsed) deadline)
          (do (time/sleep 0.02)
              (recur (max top (motor/velocity name))))
          top)))
    (finally
      (motor/set-power name 0))))  ; stop, even after an error or STOP
```

```clojure
user=> (def top (max-velocity "flywheel"))
#'user/top
user=> top
2933.0
```

The speed is in encoder ticks per second. From it, the
[REV guide](https://docs.revrobotics.com/first-global/fgc-pid) works out a
starting point for the gains. `:f` is 32767 divided by the top speed.
The hub's controller works in units where 32767 is full power, so with this
`:f` alone, the target speed asks for about the power that gives that speed.
`:p` is a tenth of `:f`, `:i` is a tenth of `:p`, and `:d` stays 0.

As a function, with the gains rounded to three decimals so they are easy to
read and type:

```clojure
(defn velocity-gains [max-v]
  (let [round (fn [x] (/ (Math/round (* x 1000)) 1000))
        f     (/ 32767 max-v)
        p     (* 0.1 f)
        i     (* 0.1 p)]
    {:p (round p) :i (round i) :d 0 :f (round f)}))
```

```clojure
user=> (velocity-gains top)
{:p 1.117, :i 0.112, :d 0.0, :f 11.172}
```

Apply them with [`motor/set-pidf`](../robot/motors.md#set-pidf). The hub
forgets the gains when it restarts, so set them at the start of your program,
before the flywheel spins. Write the numbers in as constants rather than
calling `velocity-gains`, since that needs the top speed, and measuring it
again spins the flywheel at full power for three seconds every time the
program starts. The gains stay the same as long as the flywheel does:

```clojure
user=> (motor/set-pidf "flywheel" {:p 1.117 :i 0.112 :f 11.172})
{:p 1.117, :i 0.112, :d 0.0, :f 11.172}
user=> (motor/set-velocity "flywheel" 1450)
1450.0
```

These gains are a starting point. The flywheel may overshoot the target a
little and take a few seconds to settle, so adjust `:p` and `:i` if needed.

Next, drive a whole robot with a [tank drivetrain](tank-drive.md).
