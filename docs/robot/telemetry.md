# Telemetry

Telemetry is the quickest way to see Second Loop working on real hardware: it
needs only a Control Hub and the Driver Hub, with no motors, servos or
sensors configured. (`LocalRepl` cannot show it, because `telemetry/*` needs the
FTC SDK.)

`(telemetry/add-line ...)` shows a line below Second Loop's own status line.
Like `println`, it takes any number of values and joins them with spaces:

```clojure
user=> (telemetry/add-line "Hello from the Driver Hub")
nil
user=> (telemetry/add-line "power" 0.54 "heading" 12.3)
nil
```

Format the values with `format`, so a changing number does not make the line
jitter:

```clojure
user=> (telemetry/add-line (format "heading %.1f" 12.34))
nil
```

## Clearing

`(telemetry/clear)` removes every line that was added:

```clojure
user=> (telemetry/clear)
nil
```

## Quick start

Connect after INIT and add a line; nothing else needs to be
configured. The [overview](../index.md) has
a short walkthrough, including a counter that updates itself.
