# REPL

Most of the time you use the REPL through the [Notebook](../notebook.md).
This page explains what the REPL is, then covers the other ways to connect
when you want something different or something goes wrong.

REPL stands for **read, evaluate, print, loop**, and it is how you work with
Second Loop, you type a form, it runs right away, the value is printed, and
then the next form is awaited.

- **Read.** A form is anything with balanced brackets, so `(+ 1 2)` is a
  form, and so is a whole `(defn ...)`.
- **Evaluate.** The interpreter on the robot runs it.
- **Print.** The value appears below it, in this case `3.0`.
- **Loop.** You type the next form, and everything you defined is still
  there.

Because each form runs as soon as it is complete, a change takes one line
instead of a build and a deploy. If a form fails, the error is printed where
the value would have been, and the loop continues with the next form.

The pages of this manual write an exchange as a transcript, with each form
after the `user=>` prompt and its value on the next line:

```clojure
user=> (/ 1 0)
Execution error: Divide by zero
user=> (+ 1 2)
3.0
```

The `user` in `user=>`, and in a var such as `#'user/launcher`, is a fixed
prefix, because there are no namespaces. [Syntax](../language/basics.md)
explains this and everything else you can type.

## Sessions

- The robot accepts connections from INIT until the OpMode stops, so you can
  disconnect and reconnect without losing your definitions.
- Several laptops, and several notebook tabs, can connect at once and share
  the same definitions.
- Pressing STOP on the Driver Hub ends the OpMode, and everything you
  defined is gone on the next INIT. `(task/stop)` stops only the
  tasks and keeps your definitions.
- A long loop started from a form keeps that notebook tab busy. To keep it
  free, start loops with `(task/start f)` instead; see
  [Control Loops](../control-loops.md).

## Version

`(version)` tells which Second Loop is on the robot, which helps after you
upload a new `SecondLoop.java`:

```clojure
user=> (version)
{:version "1.1.22", :major 1.0, :minor 1.0, :patch 22.0, :commit "main"}
```

`:commit` is `"main"` for a development build.

## Netcat

The REPL is plain text over TCP on port 9001, so Netcat (`nc`) works as a
client too. It has no line editing or history, but it is available almost
everywhere and puts nothing between you and the robot. This helps when you
want to rule out the browser as the problem.

Connect with the robot's address and port 9001, then type forms after the
prompt:

```console
$ nc 192.168.43.1 9001
Second Loop version 1.1.22
user=> (+ 1 2)
3.0
```

- `nc` sends each line when you press Enter. A form can span several lines;
  the prompt comes back only once the brackets are balanced.
- Press Ctrl+C to quit. This closes only your connection, and your
  definitions stay on the robot.
- `nc` is built into macOS and most Linux systems. On Windows, install
  [Nmap](https://nmap.org/download.html) and use its `ncat` the same way.
- For line editing and history, run it as `rlwrap nc 192.168.43.1 9001`
  where [rlwrap](https://github.com/hanslub42/rlwrap) is installed.

## Troubleshooting

- Check that INIT has been pressed. The REPL listens from INIT until the
  OpMode stops.
- Use the address that the Driver Hub shows in its Second Loop status line. It is
  the robot's own IP address and depends on your setup. The notebook is
  always on port 9002, and `nc` on port 9001. If the robot has several
  addresses, it shows the Control Hub's WiFi address (`192.168.43.1`) or a
  phone's WiFi Direct address (`192.168.49.1`).
- Make sure the laptop is on the robot's WiFi.
- Type the address with `http://`, by its numbers. The notebook does not
  answer to other names for the robot.
- Try `nc 192.168.43.1 9001`. If it connects, the robot is fine and the
  problem is in the browser.
- If a form is sent but nothing responds, a loop started from an earlier form
  may be keeping that connection busy. Several connections can be open at
  once, so open another browser tab, and start long loops with
  `(task/start f)`; see [Control Loops](../control-loops.md).
