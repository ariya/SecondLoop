# Differences

Second Loop borrows its syntax from Clojure but is a much smaller language,
chosen for driving a robot rather than for general programming. Where
faithfulness to Clojure and staying small conflict, it stays small.

## Language

- **No namespaces.** `Math/sin` and `str/join` are simply names of functions.
- **No regexes or characters.** `str/split` and `str/replace` match plain text,
  and a string is not a sequence of characters.
- **Macros are not supported.**

## Errors

- **`catch` knows three classes:** `Exception` (any error), `ExceptionInfo`
  (from `ex-info`) and `Throwable` (also stack overflow). A stop request is
  never caught, and `finally` runs to the end even while stopping.
- **An error inside a future is printed at once** by the connection that
  started it, as well as being raised again by `deref`. Printing it only when
  `deref` runs would risk a dead control loop going unnoticed.

## Collections

- **Collections are copied, not shared.** Nothing is ever modified in place, but
  every change copies the whole collection; there are no persistent data
  structures. Maps and sets keep the order items were added.
- **Everything is eager.** `map`, `filter` and `range` build their whole result
  straight away, so `range` needs an end.
- **Destructuring** supports `&` and `:as` in vectors and `:keys`, `:or` and
  `:as` in maps, but not `:strs` or `:syms`.
- **`doseq`** takes one or more bindings but not the `:when`, `:while` or `:let`
  modifiers.
- **`sort`** compares numbers, strings, keywords and vectors of them, but not
  `nil` or a mix of types. There is no `compare` or `sort-by`.

## Numbers and time

- **Numbers are always doubles.** `(+ 1 2)` is `3.0` and `(quot 7 2)` is `3.0`.
  In `format`, use `%.0f` instead of `%d`.
- **Dividing by zero is an error**, rather than `##Inf`, so an infinite value
  never reaches a motor.
- **Time is in seconds.** `(time/elapsed)` gives seconds since START and
  `(time/sleep s)` waits in seconds. Java's `System/currentTimeMillis`,
  `System/nanoTime` and `Thread/sleep` are left out, so no code has to convert
  milliseconds or nanoseconds.

## Atoms and futures

- **Atoms** have no watches or validators, and no `add-watch`. To react to a
  change, read the atom in the loop, which already does that once per round.
- **`future-cancel` waits** (up to a second) for the future's thread to end, so
  a cancelled loop cannot take one more step, such as setting a motor's power,
  after the call returns.
