# Atom

A `def` fixes a value when you type it. An atom holds a value that can change
while the robot runs, and every connection and every loop sees the change.
That is what makes live tuning possible.

`(atom x)` creates one, and `def` gives it a name:

```clojure
user=> (def kp (atom 0.02))
#'user/kp
user=> @kp
0.02
```

`@x` is shorthand for `(deref x)`: it reads the current value.

## defonce

When you paste your code again after an edit, `def` would put every atom back
to its starting value. `defonce` defines a name only if it is not defined yet,
and does not even evaluate the expression when it is:

```clojure
user=> (defonce tuned (atom 0.5))
#'user/tuned
user=> (reset! tuned 0.9)
0.9
user=> (defonce tuned (atom 0.5))   ; pasting it again leaves it alone
nil
user=> @tuned
0.9
```

So the atoms you tuned keep their values, while `defn` still picks up the new
code. Use `def` instead of `defonce` when you want pasting the code again to
reset a value.

## reset!

`(reset! a v)` replaces the value and returns the new one:

```clojure
user=> (reset! kp 0.03)
0.03
user=> @kp
0.03
```

## swap!

`(swap! a f args...)` sets the atom to `(f @a args...)`:

```clojure
user=> (def pieces (atom 0))
#'user/pieces
user=> (swap! pieces inc)
1.0
```

`swap!` is atomic. Two connections, or a connection and a running loop, can
update the same atom at once and no update is lost. Never do
`(reset! a (inc @a))` for this: between the read and the write another update
could slip in.
