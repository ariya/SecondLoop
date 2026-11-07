# Second Loop

**Second Loop** is a live coding environment for your
[FTC robot](https://www.firstinspires.org/programs/ftc/), built for rapid
iteration.

It is easy to set up: a single OpMode file, `SecondLoop.java`, uploaded through
[OnBot Java](https://ftc-docs.firstinspires.org/en/latest/programming_resources/onbot_java/OnBot-Java-Tutorial.html),
with no Android Studio and no Gradle.

You talk to the robot from your laptop with [Netcat](https://nc110.sourceforge.io),
it acts on each command **right away**:

```console
$ nc 192.168.43.1 9001
Second Loop REPL
user=> (battery/voltage)
12.84
user=> (motor/set-power "flywheel" 0.8)
0.8
```

Want a faster flywheel, a gentler turn, or a different gamepad mapping? Change
it while the robot runs and see the result in seconds, with **no waiting** to
rebuild, redeploy, and restart.
