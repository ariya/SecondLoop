# Function Reference

Every name is a global; there are no namespaces. `Math/PI` and `str/join` are
simply names with a slash in them.

## Special forms

These are evaluated by the interpreter and cannot be passed as values.

| Form | Purpose | Page |
| --- | --- | --- |
| `def` `defonce` | Create a global name; `defonce` only when it is new. | [Atom](../language/atom.md) |
| `if` `if-not` | Two-way choice. | [Branching](../language/branching.md) |
| `when` `when-not` | Run forms for one truthy or falsey test. | [Branching](../language/branching.md) |
| `cond` | First truthy test wins. | [Branching](../language/branching.md) |
| `case` | Match a value against constants. | [Branching](../language/branching.md) |
| `do` | Several forms where one expression is expected. | [Branching](../language/branching.md) |
| `let` | Local names, possibly destructured. | [Branching](../language/branching.md) |
| `if-let` `when-let` | Bind and test in one step. | [Branching](../language/branching.md) |
| `fn` `defn` | Make a function, named or unnamed. | [Functions](../language/functions.md) |
| `and` `or` | Short-circuit logic. | [Branching](../language/branching.md) |
| `->` `->>` | Thread a value through calls. | [Functions](../language/functions.md) |
| `quote` | The form itself, not its value. | [Syntax](../language/basics.md) |
| `loop` `recur` | A loop that does not grow the stack. | [Loop](../language/loops.md) |
| `while` `dotimes` `doseq` | Repeat while a test holds, a fixed count, or per item. | [Loop](../language/loops.md) |
| `future` | Run forms on another thread. | [Futures](../language/futures.md) |
| `try` | `catch` and `finally`. | [Exceptions](../language/exceptions.md) |
| `time` | Print how long an expression took. | [Time](../robot/time.md) |
| `doc` | Print a function's arities and doc string. | [Functions](../language/functions.md#doc-strings) |

## Functions

| Area | Functions |
| --- | --- |
| Arithmetic | `+ - * / inc dec mod rem quot abs min max` |
| Comparison and logic | `= not= < > <= >= not zero? pos? neg? even? odd?` |
| `java.lang.Math` | `Math/PI Math/E Math/abs Math/sin Math/cos Math/tan Math/asin Math/acos Math/atan Math/atan2 Math/hypot Math/sqrt Math/cbrt Math/pow Math/exp Math/log Math/log10 Math/floor Math/ceil Math/round Math/signum Math/min Math/max Math/toRadians Math/toDegrees` |
| Robot helpers | `clamp deadband lerp wrap-degrees wrap-radians control/start control/stop control/running?` |
| Types | `nil? some? true? false? boolean boolean? number? string? keyword keyword? symbol symbol? name fn?` |
| Building collections | `list vector hash-map hash-set vec set range` |
| Collection types | `list? seq? vector? map? set? coll?` |
| Reading collections | `count empty? seq first second last rest next nth get get-in contains? keys vals peek` |
| Changing collections | `conj cons pop assoc assoc-in dissoc disj update update-in into merge` |
| Sequences | `concat reverse map filter reduce apply some every?` |
| Functions | `partial comp` |
| Strings | `str format subs pr-str parse-double str/join str/split str/replace str/trim str/upper-case str/lower-case str/blank? str/includes? str/starts-with? str/ends-with?` |
| Atoms | `atom deref reset! swap!` |
| Errors | `throw ex-info ex-message ex-data` |
| Futures | `future-cancel future-done? future-cancelled? future?` |
| Output | `println prn` |
| Time | `time/elapsed time/sleep` |
| Robot hardware | `gamepad motor/set-power motor/power motor/set-velocity motor/velocity motor/set-direction motor/direction servo/set-position servo/position servo/set-direction servo/direction crservo/set-power crservo/power crservo/set-direction crservo/direction battery/voltage imu/init imu/reset-yaw imu/angles pinpoint/init pinpoint/pose pinpoint/velocity pinpoint/reset pinpoint/status telemetry/add-line telemetry/clear` |

`future` is a special form and `deref` is an ordinary function; both appear in
the row for the area where you will reach for them.

## Robot helpers

`clamp`, `deadband`, `lerp`, `wrap-degrees`, `wrap-radians`, `partial`, `comp`
and the `control/*` functions are written in the REPL language itself, in
`src/secondloop/Prelude.java`. They are ordinary definitions, so you can read
them with `(doc name)` and redefine them from the REPL.

```clojure
user=> (clamp 1.3 -1 1)
1.0
user=> (deadband 0.02 0.05)
0.0
user=> (wrap-degrees 350)
-10.0
user=> (lerp 0 10 0.5)
5.0
```

`control/start` and `control/stop` are covered in
[Control Loops](../robot/control-loops.md).

## Argument checks

Functions check their argument count and types, and the error names the
function, so a typo shows up as:

```clojure
user=> (+ 1 "x")
Execution error: + expects numbers, got: "x"
user=> (count)
Execution error: Wrong number of args (0) passed to: count
```
