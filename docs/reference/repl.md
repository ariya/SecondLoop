# REPL

The [Overview](../index.md) shows how to connect with `secondloop.py`. This
page explains what the REPL is, then covers the rest of the client: its
options, line editing, history, and how to use plain `nc` when something goes
wrong.

REPL stands for **read, evaluate, print, loop**, and it is how you work with
Second Loop: the prompt waits for a form, runs it right away, prints the
value, and then waits for the next one.

- **Read.** The prompt is `user=>`. A form is anything with balanced
  brackets, so `(+ 1 2)` is a form, and so is a whole `(defn ...)`.
- **Evaluate.** The interpreter on the robot runs it.
- **Print.** The value appears on the next line, in this case `3.0`.
- **Loop.** The prompt returns, and everything you defined is still there.

Because each form runs as soon as it is complete, a change takes one line at
the prompt instead of a build and a deploy. If a form fails, the error is
printed where the value would have been, and the loop continues with the next
form:

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
- Several laptops can connect at once and share the same definitions.
- Pressing STOP on the Driver Hub ends the OpMode, and everything you
  defined is gone on the next INIT. `(control/stop)` stops only the control
  loop and keeps your definitions.
- A long loop started at the prompt keeps that connection busy. To keep the
  prompt free, start loops with `(control/start f)` instead; see
  [Control Loops](../robot/control-loops.md).

## secondloop.py

Download `secondloop.py` from the [Second Loop repository](https://github.com/ariya/SecondLoop).
It needs only Python 3 and its standard library, and it runs on Linux, macOS
and Windows.

```console
$ ./secondloop.py                  # connects to 192.168.43.1:9001
$ ./secondloop.py 192.168.49.1     # another address
$ ./secondloop.py localhost 9001   # an address and a port
$ ./secondloop.py localhost:9001   # the same, as HOST:PORT
```

On Windows, run it as `python secondloop.py` instead.

| Option | Effect |
| --- | --- |
| `--history FILE` | Keep history in `FILE` instead of `~/.secondloop_history`. Pass an empty name to keep no history. |
| `--no-color` | Never use colors. |

Press `Ctrl-D` on an empty line to quit. Quitting closes only that
connection: the OpMode keeps running, and everything you defined stays.

## Line editing

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
| `Tab` | Insert two spaces. |
| `Ctrl-L` | Clear the screen. |
| `Ctrl-C` | Discard the line. |
| `Ctrl-D` on an empty line | Quit. |

## History

`Up` and `Down` (or `Ctrl-P` and `Ctrl-N`) step through earlier forms. History
is saved in `~/.secondloop_history`, a plain text file with a blank line
between entries, so it carries over from one session to the next.

`Ctrl-R` searches it as you type and finds the most recent entry that
contains the text:

| Keys | Action |
| --- | --- |
| `Ctrl-R` | Find an older match. |
| `Ctrl-S` | Find a newer match. |
| `Enter` | Run the entry shown. |
| `Ctrl-G`, `Esc` | Cancel and return to what you were typing. |
| Any other key | Keep the entry and continue editing it. |

## Colors

In a terminal that supports colors, the prompt, your input, errors and the
`Ctrl-R` search each have their own color. To turn colors off, set `NO_COLOR`
or pass `--no-color`.

## nc

The REPL is plain text over TCP, so `nc` also works as a client. It has no
line editing or history, but it is available almost everywhere and puts
nothing between you and the robot, which helps when you want to rule out the
client as the problem:

```console
$ nc 192.168.43.1 9001
Second Loop REPL
user=> (+ 1 2)
3.0
```

`nc` sends each line as you type it. Closing it closes only that connection.

If neither client connects:

- Check that INIT has been pressed. The REPL listens from INIT until the
  OpMode stops.
- Use the address that the Driver Hub shows after **Ready at**. It is
  the robot's own IP address and depends on your setup. The port is always
  9001. If the robot has several addresses, it shows the Control Hub's WiFi
  address (`192.168.43.1`) or a phone's WiFi Direct address (`192.168.49.1`).
- Make sure the laptop is on the robot's WiFi.
- If the prompt appears but nothing responds, a loop started at the prompt
  may be keeping that connection busy. Several connections can be open at
  once, so connect again from a second window, and start long loops with
  `(control/start f)`; see [Control Loops](../robot/control-loops.md).
