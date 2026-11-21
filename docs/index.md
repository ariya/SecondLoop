---
hide:
  - title
---

**Second Loop** is a live coding environment for your
[FTC robot](https://www.firstinspires.org/programs/ftc/), built for rapid
iteration.

Setup takes [just 5 minutes](setup.md), with no Java coding.
You talk to the robot from your laptop, and it acts on each command **right
away**.

Want to tune the launcher PID, tweak the drivetrain, or fix the odometry? Change it while the robot runs and see the result instantly, with **no waiting** to
rebuild, redeploy, and restart.

You try ideas and tune your robot much faster, and get more out of every
practice session.

![Setup with a laptop and the robot](setup.jpg){ width="850" }

Once Second Loop is running, the telemetry on the Driver Hub shows the address
to connect to:

```
Second Loop version 1.1.17
Ready at 192.168.43.1:9001 (0 clients)
```

Now run `secondloop.py` on the computer. It should detect the setup
automatically:

```console
$ ./secondloop.py
Connected to 30618-RC using RC version 12.0 (SDK v12.0.0)
Active configuration is biobuzz
Second Loop version 1.1.17
user=>
```

Type at the `user=>` prompt. Each line runs on the robot as soon as you press
`Enter`, and the result appears below it. Use the arrow keys to edit the line
and to recall earlier ones. To quit, press `Ctrl+D` on an empty line.

!!! info

    History search, the full list of keys, and other details are covered
    in the [REPL](reference/repl.md) reference.

First, ask the robot for its [battery voltage](robot/battery.md):

```clojure
user=> (battery/voltage)
12.84
```

The answer comes from the robot itself, in volts.

Next, show some messages in the Driver Hub's [telemetry](robot/telemetry.md):

```clojure
user=> (telemetry/add-line "Hello from my computer")
nil
user=> (telemetry/add-line "Battery" (battery/voltage))
nil
```

From here, the examples build up step by step:
[reading the robot heading](examples/heading.md),
[spinning a launcher motor](examples/launcher.md),
[tuning its PIDF gains](examples/pidf-tuning.md), driving a
[tank](examples/tank-drive.md) or [mecanum](examples/mecanum-drive.md)
drivetrain from the gamepad, [adjusting the speed](examples/speed-adjustment.md)
on the fly, and [tracking where the robot is](examples/location-tracking.md) on
the field.
