# Branching

These forms test a value and choose one of several results. [Loop](loops.md)
covers repeating a body.

## if

`if` takes a test, a value when it is truthy, and an optional value otherwise:

```clojure
user=> (if (> 3 4) "big" "small")
"small"
user=> (if (> 3 4) "big")
nil
```

Remember that only `nil` and `false` are falsey; `0` is true.

## when

`when` runs several forms only if the test is truthy:

```clojure
user=> (when true (println "one") (println "two"))
one
two
nil
```

## when-not

`when-not` is the opposite of `when`: it runs the forms only if the test is
falsey:

```clojure
user=> (when-not false :ok)
:ok
```

## if-not

`if-not` is `if` with the branches swapped:

```clojure
user=> (if-not (empty? [1]) "has items" "empty")
"has items"
```

## cond

`cond` takes pairs of test and result and returns the first result whose test is
truthy. `:else` is just a keyword, which is truthy, so it works as the last
test:

```clojure
(defn power-band [power]
  (cond
    (> power 0.8) "fast"
    (> power 0.2) "cruising"
    :else "slow"))
```

```clojure
user=> (power-band 0.9)
"fast"
user=> (power-band 0.5)
"cruising"
user=> (power-band 0.1)
"slow"
```

Without a final `:else`, a `cond` with no match returns `nil`.

## case

`case` compares one value with constants, which suits a state machine. The
constants are not evaluated, and a list of constants matches any of them:

```clojure
(defn describe [state]
  (case state
    :intake "running the intake"
    (:score :dump) "scoring"
    "idle"))
```

```clojure
user=> (describe :intake)
"running the intake"
user=> (describe :dump)
"scoring"
user=> (describe :teleop)
"idle"
```

The last form is the default when it is not paired with a constant. Without a
default, an unmatched value is an error rather than `nil`, which catches typos:

```clojure
user=> (case :x :a 1)
Execution error: No matching clause: :x
```

## let

`let` names values for the rest of the body. Bindings are sequential, so a later
one can use an earlier one:

```clojure
user=> (let [a 1 b (+ a 1)] [a b])
[1.0 2.0]
```

Bindings are local to the body and shadow outer names. `let` can also destructure;
see [Functions](functions.md#destructuring).

## if-let

`if-let` binds a value and runs only when it is truthy, which saves a lookup and
a test:

```clojure
user=> (if-let [speed (:speed {:name "Bolt"})] speed 0)
0.0
user=> (if-let [speed (:speed {:speed 0.5})] speed 0)
0.5
```

`nil` counts as no value, so this is the natural companion to `get`, whose
default is `nil` when you omit it.

## when-let

`when-let` does the same as `if-let`, without an else branch:

```clojure
user=> (when-let [x 5] (* x 2))
10.0
```

## and, or and do

`and` returns the last truthy value or the first falsey one; `or` returns the
first truthy value. Both stop evaluating as soon as the answer is known:

```clojure
user=> (and true 1 nil)
nil
user=> (or nil false 7)
7.0
user=> (and false (/ 1 0))   ; the division never runs
false
```

`do` runs several forms and returns the last, which is how you put more than one
form where a single expression is expected:

```clojure
user=> (do (println "a") 1)
a
1.0
```
