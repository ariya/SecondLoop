# Loop

`loop` and `recur` are the general way to repeat a body, and `dotimes`, `while`
and `doseq` cover the cases you meet most. [Branching](branching.md) covers the
tests a loop body uses.

## dotimes

`dotimes` runs a body a fixed number of times, counting from `0.0`:

```clojure
user=> (dotimes [i 3] (println "tick" i))
tick 0.0
tick 1.0
tick 2.0
nil
```

## loop and recur

`loop` names values and `recur` starts the next round with new ones. The call
stack does not grow, so a loop can run for a whole match:

```clojure
(loop [i 0]
  (when (< i 3)
    (println "tick" i)
    (time/sleep 1)
    (recur (inc i))))
```

`recur` must be the last thing its `loop` or function does, because there is
nothing left to return afterwards. Anything after it is a syntax error before
the code runs:

```clojure
user=> (loop [i 0] (when (< i 3) (recur (inc i)) (println "after")))
Syntax error: Can only recur from tail position
```

`recur` also works directly in a function body, which is how a function loops:

```clojure
user=> (defn count-down [n] (if (pos? n) (recur (dec n)) :done))
#'user/count-down
user=> (count-down 3)
:done
```

## while

`while` is the shorter form when the test is the only state. It re-evaluates the
test each round, so a sensor reading is fresh:

```clojure
user=> (let [i (atom 0)] (while (< @i 3) (swap! i inc)) @i)
3.0
```

On the robot you would read a sensor instead:

```clojure
(while (> (distance) 10)
  (drive 0.3))
```

## doseq

`doseq` runs a body once for each item of a collection, and takes more than one
binding to nest:

```clojure
user=> (doseq [m [1 2]] (println m))
1.0
2.0
nil
```

`doseq` takes no `:when`, `:while` or `:let` modifiers. Use `when` inside the
body, or `filter` the collection first.
