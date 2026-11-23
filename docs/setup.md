# Setup

![Setup with a laptop and the robot](setup.jpg){ width="850" }

Second Loop is a single OpMode, `SecondLoop.java`, that runs on the robot. It
also serves the [Notebook](notebook.md), a page where you talk to the robot
from the web browser on your laptop.

You put `SecondLoop.java` on the robot only once. After that, everything you
type in the notebook runs on the robot right away, with no rebuilding.

You need:

- A robot with a REV Control Hub, and a Driver Hub.
- A laptop with a web browser.

While the laptop still has a working Internet connection, download
`SecondLoop.java` from the
[Second Loop repository](https://github.com/ariya/SecondLoop).

Then put `SecondLoop.java` on the robot using **one** of the two methods
below. If you are not sure which one to pick, use OnBot Java, it needs only a
web browser.

## OnBot Java

[OnBot Java](https://ftc-docs.firstinspires.org/en/latest/programming_resources/onbot_java/OnBot-Java-Tutorial.html)
is a code editor built into the robot. You use it from a web browser.

1. Turn on the robot.
2. Connect the laptop to the Control Hub's WiFi (e.g. `30618-RC`).
3. Open `http://192.168.43.1:8080` in a web browser.
4. Click **OnBot Java**.
5. Select the `teamcode` folder.
6. Click **Upload Files** and choose `SecondLoop.java`.
7. Click **Build Everything** and wait for `Build SUCCEEDED!`.

On the Driver Hub, select the **Second Loop** TeleOp and press **INIT**, then
**START**. The telemetry shows the address to connect to:

```
Second Loop 1.1.22 at http://192.168.43.1:9002 (0 clients)
```

With the laptop still on the robot's WiFi, open that address,
<http://192.168.43.1:9002>, in a web browser. The [Notebook](notebook.md)
appears, ready for your first command.

That's it! Head back to the [Overview](index.md) for a first tour, or browse
the [Examples](examples/heading.md).

## Android Studio

Use [Android Studio](https://ftc-docs.firstinspires.org/en/latest/programming_resources/android_studio_java/opmode/opmode.html)
if your team already programs the robot with it. Second Loop then sits next to
your own OpModes.

1. Open your team's robot project in Android Studio.
2. Copy `SecondLoop.java` into `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`.
3. Connect to the Control Hub via USB or `adb connect 192.168.43.1:5555`.
4. Click **Run** to build and deploy **TeamCode**.
5. Connect the laptop to the Control Hub's WiFi (e.g. `30618-RC`).

On the Driver Hub, select the **Second Loop** TeleOp and press **INIT**, then
**START**. The telemetry shows the address to connect to:

```
Second Loop 1.1.22 at http://192.168.43.1:9002 (0 clients)
```

With the laptop still on the robot's WiFi, open that address,
<http://192.168.43.1:9002>, in a web browser. The [Notebook](notebook.md)
appears, ready for your first command.

That's it! Head back to the [Overview](index.md) for a first tour, or browse
the [Examples](examples/heading.md).
