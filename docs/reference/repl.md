# REPL

Most of the time you use the REPL through the [Notebook](../notebook.md).
This page explains what the REPL is, then covers the options of
`secondloop.py`, and the other ways to connect when you want something
different or something goes wrong.

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
{:version "1.1.17", :major 1.0, :minor 1.0, :patch 17.0, :commit "main"}
```

`:commit` is `"main"` for a development build.

## secondloop.py

Download `secondloop.py` from the [Second Loop repository](https://github.com/ariya/SecondLoop).
It needs only Python 3 and its standard library, and it runs on Linux, macOS
and Windows.

```console
$ ./secondloop.py                  # tries 192.168.43.1, then 192.168.49.1
$ ./secondloop.py 192.168.49.1     # another address
$ ./secondloop.py localhost 9001   # an address and a port
$ ./secondloop.py localhost:9001   # the same, as HOST:PORT
```

On Windows, run it as `python secondloop.py` instead.

| Option | Effect |
| --- | --- |
| `--no-browser` | Don't open the [notebook](../notebook.md) in the default web browser. It is still served at <http://localhost:4188>. |
| `--no-web` | Don't serve the [notebook](../notebook.md) at all. |
| `--history FILE` | Keep history in `FILE` instead of `~/.secondloop_history`. Pass an empty name to keep no history. |
| `--no-color` | Never use colors in the terminal. |

Keep `secondloop.py` running for as long as you use the notebook. Pressing
`Ctrl-D` in its terminal quits it. Quitting closes only its connections, the
OpMode keeps running, and everything you defined stays.

## Other clients

The [Notebook](../notebook.md) is not the only way in. The same REPL is also
available in the terminal, and through anything that speaks plain text over
TCP.

### Terminal

The terminal where `secondloop.py` runs also shows a `user=>` prompt. It is
one more connection, like a browser tab, so it shares definitions and history
with the notebook. History is saved in `~/.secondloop_history`, a plain text
file with a blank line between entries, so it carries over from one session
to the next.

Lines are edited on the laptop, as with readline, and a form is sent only
once its brackets balance. An unfinished form can span several lines, and
history recalls it as a single entry.

| Keys | Action |
| --- | --- |
| `Left` `Right`, `Ctrl-B` `Ctrl-F` | Move one character. |
| `Alt-B` `Alt-F`, `Ctrl-Left` `Ctrl-Right` | Move one word. |
| `Home` `End`, `Ctrl-A` `Ctrl-E` | Move to the start or end of the line. |
| `Backspace` | Delete the character before the cursor. |
| `Delete`, `Ctrl-D` | Delete the character under the cursor. |
| `Ctrl-K` | Delete to the end of the line. |
| `Ctrl-U` | Delete to the start of the line. |
| `Ctrl-W`, `Alt-Backspace` | Delete the word before the cursor. |
| `Alt-D` | Delete the word after the cursor. |
| `Ctrl-Y` | Paste the most recently deleted text. |
| `Up` `Down`, `Ctrl-P` `Ctrl-N` | Step through history. |
| `Tab` | Insert two spaces. |
| `Ctrl-L` | Clear the screen. |
| `Ctrl-C` | Discard the line. |
| `Ctrl-D` on an empty line | Quit. |

`Ctrl-R` searches history as you type and finds the most recent entry that
contains the text:

| Keys | Action |
| --- | --- |
| `Ctrl-R` | Find an older match. |
| `Ctrl-S` | Find a newer match. |
| `Enter` | Run the entry shown. |
| `Ctrl-G`, `Esc` | Cancel and return to what you were typing. |
| Any other key | Keep the entry and continue editing it. |

In a terminal that supports colors, the prompt, your input, errors and the
`Ctrl-R` search each have their own color. To turn colors off, set `NO_COLOR`
or pass `--no-color`.

### nc

The REPL is plain text over TCP, so `nc` also works as a client. It has no
line editing or history, but it is available almost everywhere and puts
nothing between you and the robot, which helps when you want to rule out
`secondloop.py` as the problem:

```console
$ nc 192.168.43.1 9001
Second Loop version 1.1.17
user=> (+ 1 2)
3.0
```

`nc` sends each line as you type it. Closing it closes only that connection.

## If it doesn't connect

- Check that INIT has been pressed. The REPL listens from INIT until the
  OpMode stops.
- Use the address that the Driver Hub shows after **Ready at**. It is
  the robot's own IP address and depends on your setup. The port is always
  9001. If the robot has several addresses, it shows the Control Hub's WiFi
  address (`192.168.43.1`) or a phone's WiFi Direct address (`192.168.49.1`).
- Make sure the laptop is on the robot's WiFi.
- If the browser doesn't open, go to <http://localhost:4188> yourself while
  `secondloop.py` is running.
- If a form is sent but nothing responds, a loop started from an earlier form
  may be keeping that connection busy. Several connections can be open at
  once, so open another browser tab, and start long loops with
  `(task/start f)`; see [Control Loops](../control-loops.md).
