# Gamepad

`(gamepad 1)` and `(gamepad 2)` return what the controller is doing right now,
as a map. The keys are the FTC SDK's `Gamepad` field names as kebab-case
keywords:

| Keys | Values |
| --- | --- |
| `:left-stick-x` `:left-stick-y` `:right-stick-x` `:right-stick-y` | -1.0 to 1.0 |
| `:left-trigger` `:right-trigger` | 0.0 to 1.0 |
| `:a` `:b` `:x` `:y` `:left-bumper` `:right-bumper` | `true` or `false` |
| `:dpad-up` `:dpad-down` `:dpad-left` `:dpad-right` | `true` or `false` |
| `:left-stick-button` `:right-stick-button` | `true` or `false` |
| `:start` `:back` `:guide` | `true` or `false` |

```clojure
user=> (:a (gamepad 1))
false
user=> (:right-trigger (gamepad 1))
0.8
```

`A`, `B`, `X` and `Y` are `:a`, `:b`, `:x` and `:y`, each `true` or `false`.
`LT` and `RT` are `:left-trigger` and `:right-trigger`, from 0.0 to 1.0.
Destructuring names several at once:

```clojure
user=> (let [{:keys [a b x y]} (gamepad 1)] [a b x y])
[true false false true]
```

The sticks are `:left-stick-x`, `:left-stick-y`, `:right-stick-x` and
`:right-stick-y`:

```clojure
user=> (let [{:keys [left-stick-x left-stick-y]} (gamepad 1)]
         [left-stick-x left-stick-y])
[0.0 -0.35]
```

The values are not adjusted from what the SDK reports. As in FTC, pushing a
stick forward gives a *negative* `:left-stick-y`. To turn that into a forward
motor power, negate it; see the threading example in
[Functions](../language/functions.md#threading).

A map is a snapshot: it never changes afterwards. Call `(gamepad 1)` again to
see new input.

## Watching

To see A, B, X, Y, LT and RT while you press them, take a fresh snapshot each
round and put it on the Driver Hub:

```clojure
(defn show-gamepad []
  (loop []
    (let [pad (gamepad 1)]
      (telemetry/clear)
      (telemetry/add-line "A" (:a pad) "B" (:b pad) "X" (:x pad) "Y" (:y pad))
      (telemetry/add-line (format "LT %.2f  RT %.2f"
                                  (:left-trigger pad) (:right-trigger pad)))
      (time/sleep 0.02)                  ; about 50 snapshots a second
      (recur))))
```

Start it in the background with `(control/start show-gamepad)` and end it with
`(control/stop)`; see [Control Loops](control-loops.md). The Driver Hub then shows the values changing
as you press buttons:

```
A true B false X false Y false
LT 0.00  RT 0.42
```

`telemetry/clear` each round replaces the previous lines instead of piling them
up, and the two spaces in the `format` keep the LT/RT line from jumping around
(see [Telemetry](telemetry.md)).

`gamepad` exists only on the robot. [`LocalRepl`](../development.md#localrepl)
leaves the name unresolved, so try these calls on the robot.
