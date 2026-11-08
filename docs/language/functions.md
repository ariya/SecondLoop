# Functions

`defn` names a function; the parameter vector comes next, then the body:

```clojure
user=> (defn double [x] (* x 2))
#'user/double
user=> (double 3)
6.0
```

The body's last form is the result, so most functions need no `return`. The same
shape gives `triple`:

```clojure
user=> (defn triple [x] (* x 3))
#'user/triple
user=> (triple 3)
9.0
```

Strings go through the same `defn`; here `str` joins the pieces:

```clojure
user=> (defn greet [name] (str "Hello, " name))
#'user/greet
user=> (greet "Ary")
"Hello, Ary"
```

## Arities

Start with the smallest useful robot function:

```clojure
user=> (defn drive [left right] (println "left" left "right" right))
#'user/drive
user=> (drive 0.3 0.4)
left 0.3 right 0.4
nil
```

Driving straight is the common case, so add a one-argument arity that calls the
two-argument one:

```clojure
(defn drive
  "Sets the drive power; one value drives straight."
  ([power] (drive power power))
  ([left right] (println "left" left "right" right)))
```

```clojure
user=> (drive 0.5)
left 0.5 right 0.5
nil
```

Each arity has its own parameter vector. Two arities cannot have the same
number of parameters.

## Doc strings

The string after the name is documentation, not code. `(doc name)` prints the
arities and the doc string:

```clojure
user=> (doc drive)
-------------------------
drive
([power] [left right])
  Sets the drive power; one value drives straight.
nil
```

`doc` prints rather than returns, so the trailing `nil` is its value. Functions
written in Java, such as `+`, have no doc string; the robot helpers in the
prelude do, e.g. `(doc clamp)` shows how to call it.

## Variadic functions

`&` collects the remaining arguments into a list:

```clojure
user=> (defn total [& xs] (reduce + xs))
#'user/total
user=> (total 1 2 3)
6.0
```

Combined with map destructuring this gives keyword arguments:

```clojure
(defn arm [angle & {:keys [speed] :or {speed 0.5}}]
  [angle speed])
```

```clojure
user=> (arm 30)
[30.0 0.5]
user=> (arm 30 :speed 1)
[30.0 1.0]
```

`(arm 30 :speed 1)` passes the map `{:speed 1}` as the rest arguments, and the
map pattern destructures it. `:or` supplies the default for a missing key.

## Destructuring

`let` and function parameters can unpack a collection instead of naming the
whole thing. Vectors unpack by position:

```clojure
user=> (let [[x y heading] [10 20 90]] [x y heading])
[10.0 20.0 90.0]
```

`&` takes the rest, and `:as` keeps the whole value:

```clojure
user=> (let [[first & others :as all] [1 2 3]] [first others all])
[1.0 (2.0 3.0) [1.0 2.0 3.0]]
```

Maps unpack by key, with defaults and nesting:

```clojure
(defn drive-summary [{:keys [forward strafe turn] :or {strafe 0}}]
  [forward strafe turn])
```

```clojure
user=> (drive-summary {:forward 0.5 :turn -0.2})
[0.5 0.0 -0.2]
```

Patterns nest to any depth, which suits a robot configuration map:

```clojure
user=> (let [{{:keys [angle]} :arm} {:arm {:angle 30}}] angle)
30.0
```

`:strs` and `:syms` are not supported. See
[Differences](../reference/differences.md).

## Higher-order functions

`fn` makes a function without naming it, which is how you pass behaviour to
`map` and friends:

```clojure
user=> (map (fn [x] (* x 2)) [1 2 3])
(2.0 4.0 6.0)
```

`partial` fills in the first arguments, and `comp` chains right to left:

```clojure
user=> (def half (partial * 0.5))
#'user/half
user=> (half 3)
1.5
user=> ((comp (partial * 2) inc) 3)   ; (* 2 (inc 3))
8.0
```

Both are written in the REPL language in `Prelude`, so you can redefine them.

`apply` spreads a collection as arguments:

```clojure
user=> (apply + [1 2 3])
6.0
```

## Threading

`->` inserts the value as the first argument of each step, which reads like a
pipeline:

```clojure
user=> (-> 5 inc (* 2))
12.0
```

That is `(* (inc 5) 2)`. `->>` inserts it last, which suits `map` and `reduce`:

```clojure
user=> (->> [1 2 3] (map inc) (reduce +))
9.0
```

On the robot this turns a stick reading into a motor power in one readable
expression (see [Gamepad](../robot/gamepad.md)):

```clojure
(-> (:left-stick-y (gamepad 1))
    -                        ; FTC reports forward as a negative value
    (deadband 0.05)          ; 0.0 near the centre
    (* 0.6)                  ; slow mode
    (clamp -1 1))
```

Each step is a plain call, so you can try any of them separately at the REPL.
