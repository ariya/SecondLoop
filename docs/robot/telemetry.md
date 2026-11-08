# Telemetry

[Telemetry](https://ftc-docs.firstinspires.org/en/latest/glossary/glossary.html#term-Telemetry)
is the data sent to the Driver Hub, where it appears as lines of text. It is the
primary way to see what a program is doing, which makes it useful for logging
and troubleshooting.

## add-line

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

## clear

`(telemetry/clear)` removes every line that was added:

```clojure
user=> (telemetry/clear)
nil
```
