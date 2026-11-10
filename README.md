# Second Loop

**Second Loop** is a live coding environment for your
[FTC robot](https://www.firstinspires.org/programs/ftc/), built for rapid
iteration.

Setup takes [just 5 minutes](https://secondloop.io/setup), with no Java coding.

You talk to the robot from your laptop with [`secondloop.py`](secondloop.py),
and it acts on each command **right away**:

```console
$ ./secondloop.py
Connected to 30618-RC using RC version 12.0 (SDK v12.0.0)
Active configuration is biobuzz
Second Loop version 1.1.9
user=> (battery/voltage)
12.84
user=> (motor/set-power "flywheel" 0.8)
0.8
```

Want to tune the launcher PID, debug AprilTag detection, or fix the odometry?
Change it while the robot runs and see the result instantly, with **no waiting** to
rebuild, redeploy, and restart.

See the [documentation](https://secondloop.io) for the full manual.
