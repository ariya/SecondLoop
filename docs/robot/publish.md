# Publish

Second Loop can publish a continuous stream of values from the robot to
subscribers on port 5599. A value can be anything: from the robot heading,
motor velocity, or the result of any function. Each subscriber chooses which
fields it receives and how many samples arrive each second.

To publish the battery voltage as a field named `battery`, type this at the
prompt:

```clojure
user=> (publish/setup {:battery battery/voltage})
nil
```

A subscriber connects to port 5599 (the default, which [can be
changed](#setup)) and sends one command per line:

| Command | Reply |
| --- | --- |
| `HELLO` or `FIELDS` | the published fields, e.g. `FIELDS battery,double,;elapsed,double,` |
| `SUB elapsed,battery rate=10` | `OK`, then `DATA 1.9,12.84` lines, 10 a second |
| `SUB ALL rate=10` | the same, for every field, sorted by name |
| `SUB` | `OK`, then no more `DATA` lines (unsubscribe) |

`FIELDS` lists each field as its name, type and unit, separated by `,`. Fields
are separated by `;`. The type is `double` for a number or `string` for text.
The unit is always empty.

`rate=` is the number of samples per second, as a whole number. If it is left
out, the rate is 20. The maximum is 100. A field that is not published yet is
sent empty, so the columns of a log never shift. A subscriber that disconnects
is unsubscribed.

You can try this with `nc`. The Control Hub is usually at `192.168.43.1`. In
the session below, you type `FIELDS`, `SUB battery rate=2` and `SUB`, and the
robot sends the other lines. The subscriber asks which fields are published,
subscribes to `battery` at two samples a second, then unsubscribes:

```text
$ nc 192.168.43.1 5599
FIELDS
FIELDS battery,double,
SUB battery rate=2
OK
DATA 12.94
DATA 12.93
DATA 12.93
DATA 12.93
SUB
OK
```

## setup

`(publish/setup fields)` publishes the fields on port 5599 and returns right
away. Its argument is a map of field names to
[functions](../language/functions.md) or [atoms](../language/atom.md).

The following example publishes the battery voltage and the elapsed time:

```clojure
user=> (publish/setup {:battery battery/voltage :elapsed time/elapsed})
nil
```

Each function takes no arguments. `publish/setup` calls each function once to
check it. Nothing else runs until a subscriber subscribes. Then each subscriber
gets its own loop in the background. The loop calls the functions and sends
their values at the rate that subscriber asked for, up to 100 times a second.
When no one is subscribed, nothing is sampled.

An atom is read on every sample. Here `speed` is the atom from
[Speed adjustment](../examples.md#speed-adjustment). If any connection runs
`(reset! speed 0.3)`, subscribers see the new value in the next sample. Write
`speed`, not `@speed`. `@speed` is read only once, when `publish/setup` runs:

```clojure
user=> (publish/setup {:speed speed})
nil
```

Any function works, including your own. [`imu/yaw`](imu.md#yaw) gives the
robot heading in radians. To publish it in degrees, wrap the conversion in
`#(...)`, which is a short way to write a function with no arguments (see
[Higher-order functions](../language/functions.md#higher-order-functions)):

```clojure
user=> (publish/setup {:heading #(Math/toDegrees (imu/yaw))})
nil
```

A function name is looked up only once, when `publish/setup` runs. With
`:x my-reading`, redefining `my-reading` has no effect until the next
`publish/setup`. With `:x #(my-reading)`, the name is looked up on every
sample.

To publish on another port, give the port first. The port is a whole number
from 1 to 65535:

```clojure
user=> (publish/setup 6789 {:battery battery/voltage})
nil
```

Only one set of fields is published at a time. Calling `publish/setup` again
replaces it, for example to add a field. On the same port, subscribers stay
connected and keep receiving data. To see a new field, a subscriber must ask
for the field list again. On a different port, the old port closes and its
subscribers are disconnected, so they have to connect to the new port. If the
new port is already in use, `publish/setup` fails and the old fields stay
published on the old port.

An error stops publishing. If a field fails on the first call, `publish/setup`
itself fails. If a field fails later, the connection that ran `publish/setup`
prints `Future failed.` and the error, the same way a
[control loop](control-loops.md) does.
