# Syntax

The syntax of Second Loop is inspired by [Clojure](https://clojure.org/), a
modern dialect of [Lisp](https://en.wikipedia.org/wiki/Lisp_%28programming_language%29).
Second Loop is a small subset of it, chosen for programming a robot.

!!! info

    In 1999, NASA hot-patched a deadlocked race condition on Deep Space 1 from
    100 million kilometers away by tapping directly into the live, interactive
    read-eval-print loop (REPL) of the onboard Lisp, saving the autonomous
    mission without a single reboot.

This page covers what you can type and how it is evaluated; [Functions](functions.md),
[Branching](branching.md), [Loop](loops.md), [Collections](collections.md) and
[Strings](strings.md) build on it.

The simplest thing to type is a number:

```clojure
user=> 42
42.0
```

It prints as `42.0` because all numbers are doubles. Arithmetic uses prefix
notation, and calls nest:

```clojure
user=> (+ 1 2)
3.0
user=> (+ 1 (* 2 3))
7.0
```

A list whose first element is a function is a call; the remaining elements are
its arguments. Arguments are evaluated inside-out: `(* 2 3)` first, then `+`.

## Data

| Form | Examples | Notes |
| --- | --- | --- |
| Number | `42`, `-1.5`, `0.02` | Always a double. |
| String | `"Bolt"`, `"power %.2f"` | Double quotes; `\"`, `\\` and `\n` escapes. |
| Keyword | `:speed`, `:left-stick-y` | Starts with `:` and evaluates to itself. |
| Symbol | `age`, `Math/PI` | A name; evaluates to what it is bound to. |
| Boolean | `true`, `false` | Only `false` (and `nil`) are falsey. |
| Nil | `nil` | The empty value. |
| List | `(list 1 2)`, `'(1 2)` | A call, unless quoted. |
| Vector | `[1 2 3]` | Indexed, keeps order. |
| Map | `{:name "Bolt" :speed 0.5}` | Keys keep insertion order. |
| Set | `#{1 2 3}` | No duplicates, keeps insertion order. |
| Atom | `@kp` | The atom's current value. |
| Function | `#(* 2 %)` | Short for `(fn [x] (* 2 x))`; see [Functions](functions.md#higher-order-functions). |
| Comment | `; anything` | From `;` to the end of the line. |

Two details surprise newcomers:

- **Quoting.** `'robot` and `(quote robot)` both mean the symbol itself, not its
  value. That is how you pass a name around; there are no macros.
- **Commas are whitespace.** `{:name "Bolt", :speed 0.5}` and the same map
  without commas are identical. Commas are only for readability.

## Names

`def` creates or replaces a global name:

```clojure
user=> (def speed 0.5)
#'user/speed
user=> (str "speed " speed)
"speed 0.5"
```

The reply `#'user/speed` is the var that `def` returns. The `user/` part is
fixed, because there are no namespaces: names such as `Math/PI` and `str/join`
are plain names with a slash in them.

`def` can redefine anything, including functions from the
[prelude](../reference/functions.md#robot-helpers) such as `clamp`.

## Truthiness

Only `nil` and `false` are falsey, so `0` and `""` are true:

```clojure
user=> (if 0 "yes" "no")
"yes"
user=> (if nil "yes" "no")
"no"
```

## Printing

Each result is printed readably: strings keep their quotes, and `nil` prints as
`nil`.

```clojure
user=> "Bolt"
"Bolt"
user=> (println "Bolt")
Bolt
nil
```

`println` prints its arguments separated by spaces and returns `nil`, so the
second `nil` is the value of the call. `str` and `format` build strings without
quotes or a trailing newline; see [Strings](strings.md).

## Collections as functions

A keyword, map, vector or set looks itself up when called:

```clojure
user=> (def robot {:name "Bolt" :speed 0.5})
#'user/robot
user=> (:speed robot)
0.5
user=> (robot :name)
"Bolt"
user=> ([10 20 30] 1)
20.0
user=> (#{:left :right} :left)
:left
```

A map or set returns the value for the key, or `nil` when it is missing. A
vector treats the argument as an index.

## Immutability

Collections are values: changing one returns a new collection and leaves the
old one alone.

```clojure
user=> (def robot {:name "Bolt" :speed 0.5 :arm {:angle 30}})
#'user/robot
user=> (update-in robot [:arm :angle] + 15)
{:name "Bolt", :speed 0.5, :arm {:angle 45.0}}
user=> (:arm robot)
{:angle 30.0}
```

[Collections](collections.md) covers reading and changing them in full.

## Special forms

These are part of the language rather than functions; they cannot be stored in a
variable or passed to another function:

```
def defonce if if-not cond case do let if-let when-let fn defn when when-not
and or -> ->> quote loop recur while dotimes doseq future try time doc
```

Everything else, such as `throw`, `deref` and `str`, is an ordinary function.
