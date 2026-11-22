#!/usr/bin/env python3
"""A terminal client for the Second Loop REPL, a friendlier stand-in for nc.

    python3 secondloop.py                    # tries 192.168.43.1, then 192.168.49.1
    python3 secondloop.py 192.168.49.1
    python3 secondloop.py localhost 9001
    python3 secondloop.py < robot.clj        # send a file, like nc

It edits each line locally, the way readline does, and only sends a form once
its brackets balance, so an unfinished form keeps growing over several lines
and is recalled from history as one entry. History is a plain text file,
~/.secondloop_history, with one blank line between entries.

Keys: Left/Right, Ctrl-B/F, Alt-B/F, Ctrl-Left/Right, Home/End, Ctrl-A/E,
Backspace, Delete, Ctrl-D, Ctrl-K, Ctrl-U, Ctrl-W, Alt-D, Ctrl-Y, Ctrl-L,
Up/Down and Ctrl-P/N for history, Ctrl-R (and Ctrl-S) to search it, Ctrl-C to
discard the line, and Ctrl-D on an empty line to quit.

Ctrl-R works as in readline: type to find the latest entry holding the text,
Ctrl-R again for an older one, Ctrl-S for a newer one, Enter to run it, Ctrl-G
or Esc to give up, and any other key to keep the entry and go on editing.

It also serves a notebook-style web REPL at http://localhost:4188, for as long
as it runs, and opens it in the default web browser. Each browser tab gets its
own connection to the robot. Pass --no-browser to leave the browser alone, or
--no-web to turn the web REPL off.

In a terminal that understands ANSI colors, the prompt, the form being typed,
errors and the Ctrl-R search each get their own color. Set NO_COLOR, or pass
--no-color, to turn that off.

Only the Python standard library is used, and it runs on Linux, macOS and
Windows.
"""

import argparse
import codecs
import html
import http.server
import json
import os
import re
import secrets
import shutil
import socket
import subprocess
import sys
import threading
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import webbrowser

# A Control Hub's WiFi address, then a phone's WiFi Direct address.
DEFAULT_HOSTS = ("192.168.43.1", "192.168.49.1")
DEFAULT_PORT = 9001
RC_INFO_URL = "http://%s:8080/js/rcInfo.json"
HISTORY_FILE = os.path.join(os.path.expanduser("~"), ".secondloop_history")
HISTORY_LIMIT = 1000

WINDOWS = os.name == "nt"
if WINDOWS:
    import msvcrt
else:
    import select
    import termios


# ---------------------------------------------------------------------------
# Reading forms


def is_complete(text):
    """True when every bracket and string in text is closed.

    An extra closing bracket also counts as complete: the REPL reports it as a
    syntax error, which is better than waiting for input that can't fix it.
    """
    depth = 0
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == ";":
            while i < n and text[i] != "\n":
                i += 1
        elif c == '"':
            i += 1
            while i < n and text[i] != '"':
                if text[i] == "\\":
                    i += 1
                i += 1
            if i >= n:
                return False
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth < 0:
                return True
        i += 1
    return depth <= 0


# ---------------------------------------------------------------------------
# History


class History:
    """Entries kept in a flat file, separated by a blank line.

    An entry never contains a blank line itself: those are dropped when it is
    added, so the file can always be split on blank lines.

    The terminal and the web REPL share one History, so a form run in either
    is recalled in both. Entries are only ever appended, so an index into
    entries keeps pointing at the same entry.
    """

    def __init__(self, path):
        self.path = path
        self.entries = []
        self.lock = threading.Lock()
        if path and os.path.exists(path):
            try:
                with open(path, encoding="utf-8") as f:
                    self.entries = self.parse(f.read())[-HISTORY_LIMIT:]
            except (OSError, UnicodeDecodeError) as e:
                sys.stderr.write("Could not read history %s: %s\n" % (path, e))

    @staticmethod
    def parse(text):
        entries = []
        current = []
        for line in text.split("\n"):
            if line.strip():
                current.append(line)
            elif current:
                entries.append("\n".join(current))
                current = []
        if current:
            entries.append("\n".join(current))
        return entries

    @staticmethod
    def clean(entry):
        return "\n".join(line.rstrip() for line in entry.split("\n") if line.strip())

    def add(self, entry):
        entry = self.clean(entry)
        with self.lock:
            if not entry or (self.entries and self.entries[-1] == entry):
                return
            self.entries.append(entry)
            if not self.path:
                return
            try:
                with open(self.path, "a", encoding="utf-8") as f:
                    f.write(entry + "\n\n")
            except OSError:
                pass  # History is a convenience; never stop the REPL over it.


# ---------------------------------------------------------------------------
# Colors


class Colors:
    """ANSI styles for each kind of text, or none at all when colors are off."""

    PROMPT = "1;32"   # bold green
    INPUT = "1"       # bold
    ERROR = "31"      # red
    SEARCH = "33"     # yellow
    FAILED = "31"     # red
    MATCH = "1;4"     # bold underlined
    NOTE = "2"        # dim
    DEVICE = "1;36"   # bold cyan
    VERSION = "36"    # cyan
    CONFIG = "1;35"   # bold magenta
    HINT = "33"       # yellow
    HINT_NAME = "1;33"  # bold yellow

    ERRORS = ("Syntax error", "Execution error", "Future failed.")

    def __init__(self, on):
        self.on = on

    def code(self, style):
        return "\x1b[%sm" % style if self.on else ""

    def reset(self):
        return "\x1b[0m" if self.on else ""

    def paint(self, text, style):
        return self.code(style) + text + self.reset() if text and self.on else text

    def output(self, text):
        """Paints the error lines in text from the robot."""
        if not self.on:
            return text
        lines = text.split("\n")
        for i, line in enumerate(lines):
            if line.startswith(self.ERRORS):
                lines[i] = self.paint(line, self.ERROR)
        return "\n".join(lines)


def enable_ansi():
    """Lets the Windows console understand ANSI escapes; True when it does.

    Consoles older than Windows 10 refuse, and then get no colors.
    """
    if not WINDOWS:
        return True
    try:
        import ctypes
        kernel32 = ctypes.windll.kernel32
        handle = kernel32.GetStdHandle(-11)
        mode = ctypes.c_uint32()
        kernel32.GetConsoleMode(handle, ctypes.byref(mode))
        return bool(kernel32.SetConsoleMode(handle, mode.value | 0x0004))
    except Exception:
        return False


def color_wanted():
    """Whether the terminal is one that should get colors.

    It follows the NO_COLOR convention (https://no-color.org) and leaves out
    TERM=dumb, which is what editors such as Emacs set for their shells.
    """
    if os.environ.get("NO_COLOR"):
        return False
    term = os.environ.get("TERM", "")
    if WINDOWS:
        return term != "dumb"  # also needs the console to take ANSI escapes
    return bool(term) and term != "dumb"


# ---------------------------------------------------------------------------
# Terminal input


class PosixKeys:
    """Raw keyboard input on Linux and macOS."""

    def __init__(self):
        self.fd = sys.stdin.fileno()
        self.saved = None
        self.decoder = codecs.getincrementaldecoder("utf-8")("replace")
        self.pending = []
        self.ansi = True

    def __enter__(self):
        self.saved = termios.tcgetattr(self.fd)
        mode = termios.tcgetattr(self.fd)
        mode[0] &= ~(termios.IXON | termios.ICRNL | termios.INLCR)  # iflag
        mode[3] &= ~(termios.ICANON | termios.ECHO | termios.ISIG | termios.IEXTEN)  # lflag
        mode[6][termios.VMIN] = 1
        mode[6][termios.VTIME] = 0
        termios.tcsetattr(self.fd, termios.TCSADRAIN, mode)
        return self

    def __exit__(self, *exc):
        termios.tcsetattr(self.fd, termios.TCSADRAIN, self.saved)

    def char(self, timeout):
        """The next character, or None if none arrives within timeout seconds."""
        while not self.pending:
            ready, _, _ = select.select([self.fd], [], [], timeout)
            if not ready:
                return None
            data = os.read(self.fd, 1024)
            if not data:
                return "\x04"  # stdin closed: treat it as Ctrl-D
            self.pending.extend(self.decoder.decode(data))
        return self.pending.pop(0)

    def key(self, timeout):
        c = self.char(timeout)
        if c != "\x1b":
            return c
        c = self.char(0.05)
        if c is None:
            return "esc"
        if c == "[":
            return self.csi()
        if c == "O":
            return {"A": "up", "B": "down", "C": "right", "D": "left",
                    "H": "home", "F": "end"}.get(self.char(0.05), "")
        if c in ("\x7f", "\x08"):
            return "alt-backspace"
        return "alt-" + c

    def csi(self):
        seq = ""
        while True:
            c = self.char(0.05)
            if c is None:
                return ""
            seq += c
            if "@" <= c <= "~":
                break
        params, final = seq[:-1], seq[-1]
        modified = ";" in params and params.split(";")[1] in ("3", "5")
        if final in "ABCDHF":
            name = {"A": "up", "B": "down", "C": "right", "D": "left",
                    "H": "home", "F": "end"}[final]
            if modified and final in "CD":
                return "word-" + name
            return name
        if final == "~":
            return {"1": "home", "7": "home", "4": "end", "8": "end",
                    "3": "delete"}.get(params.split(";")[0], "")
        return ""


class WindowsKeys:
    """Raw keyboard input in the Windows console."""

    SPECIAL = {"H": "up", "P": "down", "K": "left", "M": "right", "G": "home",
               "O": "end", "S": "delete", "s": "word-left", "t": "word-right"}

    def __init__(self):
        self.ansi = False

    def __enter__(self):
        # Redrawing needs the console to understand ANSI escapes.
        self.ansi = enable_ansi()
        return self

    def __exit__(self, *exc):
        pass

    def key(self, timeout):
        deadline = time.time() + timeout
        while not msvcrt.kbhit():
            if time.time() >= deadline:
                return None
            time.sleep(0.01)
        try:
            c = msvcrt.getwch()
        except KeyboardInterrupt:
            return "\x03"
        if c in ("\x00", "\xe0"):
            return self.SPECIAL.get(msvcrt.getwch(), "")
        if c == "\x08":
            return "\x7f"
        return c


# ---------------------------------------------------------------------------
# Line editor


def char_width(c):
    if unicodedata.combining(c):
        return 0
    return 2 if unicodedata.east_asian_width(c) in ("W", "F") else 1


def is_word(c):
    return not (c.isspace() or c in "()[]{}\"'")


class Search:
    """The state of a Ctrl-R search through history."""

    def __init__(self, buf, pos):
        self.buf = buf       # the form being edited before the search, restored by Ctrl-G
        self.pos = pos
        self.query = ""
        self.match = None    # index of the history entry shown
        self.failed = False

    def label(self):
        return "(%sreverse-i-search)`%s': " % ("failed " if self.failed else "", self.query)

    def style(self):
        return Colors.FAILED if self.failed else Colors.SEARCH


class Editor:
    """Edits the current form below whatever the REPL has printed.

    Output from the robot can arrive at any time, for example from a future,
    so it is printed above the form being edited, which is then redrawn. The
    last unfinished line of output, normally the "user=> " prompt, is shown
    in front of the form.
    """

    def __init__(self, history, send, colors=None):
        self.history = history
        self.send = send
        self.colors = colors or Colors(False)
        self.lock = threading.RLock()
        self.tail = ""       # output since the last newline, normally the prompt
        self.buf = ""
        self.pos = 0
        self.row = 0         # screen row of the cursor, counted from the first drawn row
        self.index = None    # the history entry shown, or None for the draft
        self.draft = ""
        self.killed = ""
        self.search = None   # a Search while Ctrl-R is active
        self.last_query = ""

    # -- drawing ------------------------------------------------------------

    def write(self, s):
        sys.stdout.write(s)
        sys.stdout.flush()

    def erase(self):
        return ("\x1b[%dA" % self.row if self.row else "") + "\r\x1b[J"

    def render(self):
        """Draws tail + buf from the start of the current row and places the cursor."""
        width = max(shutil.get_terminal_size((80, 24)).columns, 10)
        colors = self.colors
        if self.search:
            prompt, prompt_style = self.search.label(), self.search.style()
        else:
            prompt = self.tail
            prompt_style = Colors.PROMPT if prompt.endswith("=> ") else None
        # A search shows where the query matched in the entry it found.
        match = (-1, -1)
        if self.search and self.search.query and self.buf.startswith(self.search.query, self.pos):
            match = (self.pos, self.pos + len(self.search.query))
        indent = " " * min(sum(char_width(c) for c in prompt), width // 2)
        out = []
        state = {"row": 0, "col": 0, "wrapped": False}

        def put(c):
            w = char_width(c)
            if state["col"] + w > width:
                state["row"] += 1
                state["col"] = 0
            out.append(c)
            state["col"] += w
            state["wrapped"] = False
            if state["col"] >= width:
                state["row"] += 1
                state["col"] = 0
                state["wrapped"] = True

        def newline():
            out.append("\n")
            if not state["wrapped"]:
                state["row"] += 1
            state["col"] = 0
            state["wrapped"] = False
            for c in indent:
                put(c)

        # Escapes go straight into out, since they take no room on screen.
        if prompt_style:
            out.append(colors.code(prompt_style))
        for c in prompt:
            put(c)
        out.append(colors.reset() + colors.code(Colors.INPUT))
        cursor = None
        for i, c in enumerate(self.buf):
            if i == self.pos:
                cursor = (state["row"], state["col"])
            if i == match[0]:
                out.append(colors.code(Colors.MATCH))
            elif i == match[1]:
                out.append(colors.reset() + colors.code(Colors.INPUT))
            if c == "\n":
                newline()
            else:
                put(c)
        out.append(colors.reset())
        if state["wrapped"]:
            out.append("\n")
        end = (state["row"], state["col"])
        if cursor is None:
            cursor = end
        if end[0] > cursor[0]:
            out.append("\x1b[%dA" % (end[0] - cursor[0]))
        out.append("\r")
        if cursor[1]:
            out.append("\x1b[%dC" % cursor[1])
        self.row = cursor[0]
        return "".join(out)

    def redraw(self):
        self.write(self.erase() + self.render())

    def commit(self, suffix):
        """Leaves the form on screen as it is and moves below it."""
        pos, self.pos = self.pos, len(self.buf)
        self.write(self.erase() + self.render() + suffix + "\n")
        self.pos = pos
        self.row = 0

    def output(self, text):
        """Prints text from the robot above the form being edited."""
        with self.lock:
            prompt = self.tail if self.tail.endswith("=> ") else ""
            if prompt:
                # Output from a future while the prompt waits: print it above
                # the prompt, which the robot won't send again.
                text = text.replace("\r\n", "\n")
            else:
                text = (self.tail + text).replace("\r\n", "\n")
            done, newline, self.tail = text.rpartition("\n")
            if prompt and newline and not self.tail:
                self.tail = prompt
            self.write(self.erase() + self.colors.output(done) + newline + self.render())

    def finish(self):
        """Leaves the cursor below everything, ready to exit."""
        with self.lock:
            if self.search:
                self.end_search(False)
            if self.buf or self.tail:
                self.commit("")
            else:
                self.write(self.erase())

    # -- editing ------------------------------------------------------------

    def set_buf(self, text, pos=None):
        self.buf = text
        self.pos = len(text) if pos is None else pos

    def insert(self, s):
        self.set_buf(self.buf[:self.pos] + s + self.buf[self.pos:], self.pos + len(s))

    def kill(self, start, end):
        if start < end:
            self.killed = self.buf[start:end]
            self.set_buf(self.buf[:start] + self.buf[end:], start)

    def line_start(self):
        return self.buf.rfind("\n", 0, self.pos) + 1

    def line_end(self):
        end = self.buf.find("\n", self.pos)
        return len(self.buf) if end < 0 else end

    def word_left(self):
        i = self.pos
        while i > 0 and not is_word(self.buf[i - 1]):
            i -= 1
        while i > 0 and is_word(self.buf[i - 1]):
            i -= 1
        return i

    def word_right(self):
        i = self.pos
        n = len(self.buf)
        while i < n and not is_word(self.buf[i]):
            i += 1
        while i < n and is_word(self.buf[i]):
            i += 1
        return i

    def vertical(self, step):
        """Moves within a multi-line form; returns False at its first or last line."""
        start = self.line_start()
        column = self.pos - start
        if step < 0:
            if start == 0:
                return False
            prev = self.buf.rfind("\n", 0, start - 1) + 1
            self.pos = min(prev + column, start - 1)
        else:
            end = self.line_end()
            if end == len(self.buf):
                return False
            next_end = self.buf.find("\n", end + 1)
            next_end = len(self.buf) if next_end < 0 else next_end
            self.pos = min(end + 1 + column, next_end)
        return True

    def recall(self, step):
        entries = self.history.entries
        index = (len(entries) if self.index is None else self.index) + step
        if not 0 <= index <= len(entries):
            return
        if self.index is None:
            self.draft = self.buf
        if index == len(entries):
            self.index = None
            self.set_buf(self.draft)
        else:
            self.index = index
            self.set_buf(entries[index])
        if step > 0 and "\n" in self.buf:
            # Arriving from above, start on the first line so Down keeps moving down.
            self.pos = self.buf.find("\n")

    # -- Ctrl-R ---------------------------------------------------------------

    def start_search(self):
        self.search = Search(self.buf, self.pos)

    def end_search(self, accept):
        search, self.search = self.search, None
        if search.query:
            self.last_query = search.query
        if accept and search.match is not None:
            self.index = search.match
        else:
            self.set_buf(search.buf, search.pos)

    def find(self, start, step):
        """Shows the nearest entry from start on that holds the query."""
        search = self.search
        entries = self.history.entries
        i = start
        while 0 <= i < len(entries):
            entry = entries[i]
            if search.query in entry and not (i != search.match and entry == self.buf):
                search.match = i
                search.failed = False
                self.set_buf(entry, entry.rfind(search.query))
                return
            i += step
        search.failed = True

    def handle_search(self, key):
        """Applies one key during Ctrl-R. Returns False when the key ends the
        search and should then be handled as an ordinary key."""
        search = self.search
        entries = self.history.entries
        here = len(entries) - 1 if search.match is None else search.match
        if key in ("", ""):  # Ctrl-R older, Ctrl-S newer
            if not search.query:
                search.query = self.last_query
                self.find(here, -1)
            elif key == "":
                self.find(here - 1, -1)
            else:
                self.find(here + 1, 1)
        elif key in ("", "esc"):  # Ctrl-G
            self.end_search(False)
        elif key in ("", ""):
            search.query = search.query[:-1]
            search.match = None
            self.set_buf(search.buf, search.pos)
            if search.query:
                self.find(len(entries) - 1, -1)
            else:
                search.failed = False
        elif len(key) == 1 and key >= " ":
            search.query += key
            self.find(here, -1)
        else:
            self.end_search(True)
            return False
        return True

    def submit(self):
        text = self.buf
        self.commit("")
        self.tail = ""
        self.set_buf("")
        self.history.add(text)
        self.index = None
        self.draft = ""
        self.send(text + "\n")

    def handle(self, key):
        """Applies one key. Returns False when the user wants to quit."""
        with self.lock:
            if self.search and self.handle_search(key):
                self.redraw()
                return True
            buf, pos = self.buf, self.pos
            if key == "\x12":  # Ctrl-R
                self.start_search()
            elif key in ("\r", "\n"):
                if is_complete(self.buf):
                    self.submit()
                    return True
                self.insert("\n")
            elif key == "\x04":  # Ctrl-D
                if not self.buf:
                    return False
                self.kill(self.pos, self.pos + 1)
            elif key == "\x03":  # Ctrl-C
                self.commit(self.colors.paint("^C", Colors.NOTE))
                self.set_buf("")
                self.index = None
            elif key in ("\x7f", "\x08"):
                if self.pos:
                    self.set_buf(buf[:pos - 1] + buf[pos:], pos - 1)
            elif key == "delete":
                self.set_buf(buf[:pos] + buf[pos + 1:], pos)
            elif key in ("left", "\x02"):
                self.pos = max(pos - 1, 0)
            elif key in ("right", "\x06"):
                self.pos = min(pos + 1, len(buf))
            elif key in ("home", "\x01"):
                self.pos = self.line_start()
            elif key in ("end", "\x05"):
                self.pos = self.line_end()
            elif key in ("word-left", "alt-b"):
                self.pos = self.word_left()
            elif key in ("word-right", "alt-f"):
                self.pos = self.word_right()
            elif key in ("up", "\x10"):
                if not self.vertical(-1):
                    self.recall(-1)
            elif key in ("down", "\x0e"):
                if not self.vertical(1):
                    self.recall(1)
            elif key == "\x0b":  # Ctrl-K
                end = self.line_end()
                self.kill(pos, end if end > pos else min(end + 1, len(buf)))
            elif key == "\x15":  # Ctrl-U
                self.kill(self.line_start(), pos)
            elif key == "\x17":  # Ctrl-W kills back to whitespace, like readline
                i = pos
                while i > 0 and buf[i - 1].isspace():
                    i -= 1
                while i > 0 and not buf[i - 1].isspace():
                    i -= 1
                self.kill(i, pos)
            elif key == "alt-backspace":
                self.kill(self.word_left(), pos)
            elif key == "alt-d":
                self.kill(pos, self.word_right())
            elif key == "\x19":  # Ctrl-Y
                self.insert(self.killed)
            elif key == "\x0c":  # Ctrl-L
                self.write("\x1b[H\x1b[2J")
                self.row = 0
            elif key == "\t":
                self.insert("  ")
            elif len(key) == 1 and key >= " ":
                self.insert(key)
            else:
                return True  # an unknown key: nothing changed
            self.redraw()
            return True


# ---------------------------------------------------------------------------
# Connection


def connect(host, port, colors):
    """Connects to the REPL, or exits with a hint to start it on the robot.

    The robot controller has answered by then, so a failure here almost
    always means the Second Loop TeleOp isn't running.
    """
    try:
        return socket.create_connection((host, port), timeout=5)
    except OSError as e:
        error = "Could not connect to %s:%d: %s" % (host, port, e)
        def styled(*parts):  # alternates plain text and names to stand out
            return "".join(colors.paint(part, Colors.HINT_NAME if i % 2 else Colors.HINT)
                           for i, part in enumerate(parts))
        hint = (styled("On your Driver Hub, select the ", "Second Loop", " TeleOp,") + "\n"
                + styled("press ", "INIT", " and then ", "START", ". Then try again."))
        sys.stderr.write("\n%s\n%s\n" % (colors.paint(error, Colors.ERROR), hint))
        sys.exit(1)


class Unreachable(Exception):
    """The robot controller didn't answer with HTTP 200."""


def fetch_rc_info(host):
    """The robot controller's rcInfo.json as a dict, or None if it isn't JSON.

    Raises Unreachable when the robot doesn't answer with HTTP 200.
    """
    url = RC_INFO_URL % ("[%s]" % host if ":" in host else host)
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            status = response.status
            body = response.read()
    except urllib.error.HTTPError as e:
        status, body = e.code, b""
    except (OSError, ValueError) as e:
        raise Unreachable("Could not get %s: %s" % (url, e))
    if status != 200:
        raise Unreachable("Could not get %s: HTTP %d" % (url, status))
    try:
        info = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, ValueError):
        return None
    return info if isinstance(info, dict) else None


def describe_rc(info, paint):
    """The lines that introduce the robot controller described by info.

    paint(text, style) marks up each value, e.g. Colors.paint."""
    def value(key, style):
        return paint(str(info.get(key) or "?"), style)

    lines = ["Connected to %s using RC version %s (SDK v%s)" % (
        value("deviceName", Colors.DEVICE),
        value("rcVersion", Colors.VERSION),
        value("sdkVersion", Colors.VERSION))]
    if info.get("activeConfigName") is not None:
        lines.append("Active configuration is %s" % value("activeConfigName", Colors.CONFIG))
    return "\n".join(lines)


REPL_PROMPT = "user=> "
VERSION_FIELD = re.compile(r':([a-z-]+) (?:"((?:[^"\\]|\\.)*)"|([^,}\s]+))')


def read_until_prompt(sock, decoder, text=""):
    """Reads until the robot shows the prompt, and returns all of it."""
    while not text.endswith(REPL_PROMPT):
        data = sock.recv(4096)
        if not data:
            raise OSError("connection closed")
        text += decoder.decode(data)
    return text


def fetch_version(sock):
    """Asks the robot for (version) before the REPL starts.

    Returns what the robot sent first (the greeting and the prompt), so the
    REPL can still show it, and the version as a dict, or None if the robot
    doesn't answer or is too old to know (version).
    """
    decoder = codecs.getincrementaldecoder("utf-8")("replace")
    greeting = ""
    sock.settimeout(5)
    try:
        greeting = read_until_prompt(sock, decoder)
        sock.sendall(b"(version)\n")
        reply = read_until_prompt(sock, decoder)[:-len(REPL_PROMPT)].strip()
    except OSError:
        return greeting, None
    finally:
        sock.settimeout(None)
    if not (reply.startswith("{") and reply.endswith("}")):
        return greeting, None
    version = {}
    for key, text, number in VERSION_FIELD.findall(reply):
        if number:
            try:
                version[key] = float(number)
            except ValueError:
                version[key] = number
        else:
            version[key] = text
    return greeting, version or None


def pump(sock, emit):
    """Passes everything the robot sends to emit, until it disconnects."""
    decoder = codecs.getincrementaldecoder("utf-8")("replace")
    while True:
        try:
            data = sock.recv(4096)
        except OSError:
            data = b""
        if not data:
            return
        text = decoder.decode(data)
        if text:
            emit(text)


class Splitter:
    """Prints what the robot sends to stdout, except its errors, which go to stderr.

    Errors are single lines, but without an echo of the input they come right
    after the prompt, as in "user=> Execution error: Divide by zero".
    """

    def __init__(self, colors):
        self.colors = colors
        self.pending = ""

    def out(self, text):
        if text:
            sys.stdout.write(text)
            sys.stdout.flush()

    def err(self, line):
        sys.stdout.flush()
        sys.stderr.write(self.colors.paint(line.rstrip("\n"), Colors.ERROR) + "\n")
        sys.stderr.flush()

    def skip_prompts(self, text):
        while text.startswith(REPL_PROMPT):
            self.out(REPL_PROMPT)
            text = text[len(REPL_PROMPT):]
        return text

    def line(self, text):
        text = self.skip_prompts(text)
        if text.startswith(Colors.ERRORS):
            self.err(text)
        else:
            self.out(text)

    def feed(self, text):
        lines = (self.pending + text.replace("\r\n", "\n")).split("\n")
        self.pending = lines.pop()
        for line in lines:
            self.line(line + "\n")
        # Show an unfinished line, such as the prompt, unless it may become an error.
        self.pending = self.skip_prompts(self.pending)
        if not any(e.startswith(self.pending) or self.pending.startswith(e)
                   for e in Colors.ERRORS):
            self.out(self.pending)
            self.pending = ""

    def close(self):
        self.line(self.pending)
        self.pending = ""


def run_piped(sock, color, greeting):
    """Sends stdin as it is and prints the replies: what nc does."""
    splitter = Splitter(Colors(color))
    splitter.feed(greeting)
    reader = threading.Thread(target=pump, args=(sock, splitter.feed))
    reader.daemon = True
    reader.start()
    try:
        source = sys.stdin.buffer
        while True:
            data = source.read1(4096) if hasattr(source, "read1") else source.read(4096)
            if not data:
                break
            sock.sendall(data)
        # Closing our side ends the session, and the robot then closes its side.
        sock.shutdown(socket.SHUT_WR)
    except OSError:
        pass
    reader.join()
    splitter.close()
    sys.stdout.write("\n")


def run_interactive(sock, history, color, greeting):
    closed = threading.Event()

    def send(text):
        try:
            sock.sendall(text.encode("utf-8"))
        except OSError:
            closed.set()

    keys = WindowsKeys() if WINDOWS else PosixKeys()
    editor = Editor(history, send)

    def receive():
        pump(sock, editor.output)
        closed.set()

    reader = threading.Thread(target=receive)
    reader.daemon = True
    with keys:
        editor.colors = Colors(color and keys.ansi)
        editor.output(greeting)
        reader.start()
        try:
            while not closed.is_set():
                key = keys.key(0.1)
                if key is not None and not editor.handle(key):
                    break
        finally:
            editor.finish()
    if closed.is_set():
        print(editor.colors.paint("Connection closed.", Colors.NOTE))
    sock.close()


# ---------------------------------------------------------------------------
# Web REPL


WEB_PORT = 4188
WEB_HOSTS = ("localhost", "127.0.0.1")
WEB_PING_SECONDS = 15


class WebServer:
    """Serves a notebook-style REPL page at http://localhost:4188.

    Each browser tab gets its own connection to the robot, as a second
    terminal would, so it shares the definitions but not the prompt. The
    robot's output reaches the page through Server-Sent Events, which every
    browser supports without any script library.

    It listens on 127.0.0.1 only: anyone who can reach it can move the robot.
    """

    def __init__(self, host, port, intro, history):
        self.robot = (host, port)
        self.intro = intro
        self.history = history
        self.lock = threading.Lock()
        self.sessions = {}   # session id -> socket to the robot

    def start(self):
        """Starts serving in the background; raises OSError if the port is taken."""
        httpd = WebHTTPServer(("127.0.0.1", WEB_PORT), WebHandler)
        httpd.web = self
        thread = threading.Thread(target=httpd.serve_forever)
        thread.daemon = True
        thread.start()

    def stream(self, handler):
        """Connects to the robot and relays what it sends until either side leaves."""
        handler.send_response(200)
        handler.send_header("Content-Type", "text/event-stream")
        handler.send_header("Cache-Control", "no-cache")
        handler.end_headers()

        def event(name, data):
            handler.wfile.write(("event: %s\ndata: %s\n\n" % (name, json.dumps(data))).encode("utf-8"))
            handler.wfile.flush()

        try:
            try:
                sock = socket.create_connection(self.robot, timeout=5)
            except OSError as e:
                event("closed", "Could not connect to %s:%d: %s" % (self.robot[0], self.robot[1], e))
                return
            session = secrets.token_hex(16)
            with self.lock:
                self.sessions[session] = sock
            try:
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                # Waking up now and then is how a closed browser tab gets noticed.
                sock.settimeout(WEB_PING_SECONDS)
                event("session", {"id": session, "intro": self.intro})
                decoder = codecs.getincrementaldecoder("utf-8")("replace")
                while True:
                    try:
                        data = sock.recv(4096)
                    except socket.timeout:
                        handler.wfile.write(b": ping\n\n")
                        handler.wfile.flush()
                        continue
                    except OSError:
                        data = b""
                    if not data:
                        event("closed", "Connection closed.")
                        return
                    text = decoder.decode(data)
                    if text:
                        event("output", text)
            finally:
                with self.lock:
                    self.sessions.pop(session, None)
                sock.close()
        except OSError:
            pass  # the browser went away

    def evaluate(self, session, code):
        """Sends code to the robot for session; False if that session is gone."""
        with self.lock:
            sock = self.sessions.get(session)
            if sock is None:
                return False
        self.history.add(code)
        try:
            sock.sendall((code + "\n").encode("utf-8"))
        except OSError:
            return False
        return True

    def history_entries(self):
        with self.history.lock:
            return list(self.history.entries)


class WebHTTPServer(http.server.ThreadingHTTPServer):
    daemon_threads = True
    # On Windows, SO_REUSEADDR lets a second secondloop.py bind the same port
    # without an error, and then either one may answer the browser.
    allow_reuse_address = not WINDOWS


class WebHandler(http.server.BaseHTTPRequestHandler):
    server_version = "SecondLoop"

    def log_message(self, format, *args):
        pass  # the terminal belongs to the REPL

    def allowed(self):
        # Checking Host stops other web sites from reaching in by DNS rebinding.
        host = self.headers.get("Host", "")
        if host.rsplit(":", 1)[0] not in WEB_HOSTS:
            self.reply(403, "text/plain", b"Forbidden")
            return False
        return True

    def reply(self, status, content_type, body):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if not self.allowed():
            return
        web = self.server.web
        path = self.path.split("?")[0]
        if path == "/":
            self.reply(200, "text/html; charset=utf-8", WEB_PAGE.encode("utf-8"))
        elif path == "/history":
            self.reply(200, "application/json", json.dumps(web.history_entries()).encode("utf-8"))
        elif path == "/events":
            web.stream(self)
        else:
            self.reply(404, "text/plain", b"Not found")

    def do_POST(self):
        if not self.allowed():
            return
        path, _, query = self.path.partition("?")
        length = int(self.headers.get("Content-Length") or 0)
        code = self.rfile.read(length).decode("utf-8", "replace")
        session = urllib.parse.parse_qs(query).get("session", [""])[0]
        if path != "/eval":
            self.reply(404, "text/plain", b"Not found")
        elif self.server.web.evaluate(session, code):
            self.reply(200, "text/plain", b"OK")
        else:
            self.reply(410, "text/plain", b"Not connected")


WEB_PAGE = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Second Loop</title>
<style>
:root {
  --bg: #fafafa; --fg: #1f2328; --muted: #6e7781; --cell: #fff; --border: #d0d7de;
  --accent: #1a7f37; --error: #cf222e; --focus: #0969da;
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #0d1117; --fg: #e6edf3; --muted: #8b949e; --cell: #161b22; --border: #30363d;
    --accent: #3fb950; --error: #f85149; --focus: #58a6ff;
  }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--fg);
  font: 15px/1.45 system-ui, -apple-system, "Segoe UI", sans-serif; }
header { position: sticky; top: 0; z-index: 1; display: flex; align-items: center; gap: 12px;
  padding: 10px 16px; background: var(--bg); border-bottom: 1px solid var(--border); }
header h1 { margin: 0; font-size: 17px; }
#status { flex: 1; color: var(--muted); font-size: 13px; white-space: pre-line; }
#status.closed { color: var(--error); }
button { font: inherit; font-size: 13px; padding: 4px 12px; border-radius: 6px; cursor: pointer;
  color: var(--fg); background: var(--cell); border: 1px solid var(--border); }
button:hover { border-color: var(--muted); }
main { max-width: 960px; margin: 0 auto; padding: 16px; }
pre, textarea { font: 14px/1.45 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; margin: 0; }
pre { white-space: pre-wrap; word-break: break-word; }
.cell { display: grid; grid-template-columns: 52px 1fr; column-gap: 8px; margin-bottom: 12px; }
.label { color: var(--muted); text-align: right; padding-top: 7px;
  font: 13px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.code, textarea { background: var(--cell); border: 1px solid var(--border); border-radius: 6px;
  padding: 6px 10px; color: var(--fg); }
.code { cursor: pointer; }
.code:hover { border-color: var(--muted); }
.out { grid-column: 2; padding: 6px 10px 0; }
.out:empty { display: none; }
.err { color: var(--error); }
.note .out { color: var(--muted); padding-top: 0; }
textarea { display: block; width: 100%; resize: none; overflow: hidden; outline: none; }
textarea:focus { border-color: var(--focus); box-shadow: 0 0 0 1px var(--focus); }
#prompt .label { color: var(--accent); }
.hint { grid-column: 2; color: var(--muted); font-size: 12px; padding: 4px 2px; }
</style>
</head>
<body>
<header>
  <h1>Second Loop</h1>
  <div id="status">Connecting…</div>
  <button id="reconnect" hidden>Reconnect</button>
  <button id="clear" title="Remove all cells">Clear</button>
</header>
<main>
  <div id="notebook"></div>
  <div class="cell" id="prompt">
    <div class="label">[ ]:</div>
    <textarea id="input" rows="1" spellcheck="false" autocomplete="off" autofocus></textarea>
    <div class="hint">Enter runs a complete form · Shift+Enter adds a line · Up/Down recall history · click a cell to copy it</div>
  </div>
</main>
<script>
"use strict";
var PROMPT = "user=> ";
var ERRORS = ["Syntax error", "Execution error", "Future failed."];
var notebook = document.getElementById("notebook");
var input = document.getElementById("input");
var statusLine = document.getElementById("status");
var reconnectButton = document.getElementById("reconnect");
var session = null, source = null, intro = "";
var count = 0, last = null;          // last: the cell that receives output
var past = [], index = 0, draft = "";

// Same rule as the terminal: every bracket and string closed.
function isComplete(text) {
  var depth = 0, i = 0, n = text.length;
  while (i < n) {
    var c = text[i];
    if (c === ";") {
      while (i < n && text[i] !== "\n") i++;
    } else if (c === '"') {
      i++;
      while (i < n && text[i] !== '"') { if (text[i] === "\\") i++; i++; }
      if (i >= n) return false;
    } else if ("([{".indexOf(c) >= 0) {
      depth++;
    } else if (")]}".indexOf(c) >= 0) {
      if (--depth < 0) return true;
    }
    i++;
  }
  return depth <= 0;
}

// The robot's output without its prompts, including one still arriving.
function clean(raw) {
  var s = raw.replace(/\r\n/g, "\n").replace(/^(user=> )+/gm, "");
  for (var k = PROMPT.length - 1; k > 0; k--) {
    var tail = PROMPT.slice(0, k);
    if (s.slice(-k) === tail && (s.length === k || s[s.length - k - 1] === "\n")) {
      s = s.slice(0, -k);
      break;
    }
  }
  return s.replace(/\n$/, "");
}

function paint(pre, raw) {
  pre.textContent = "";
  var text = clean(raw);
  if (!text) return;
  text.split("\n").forEach(function (line, i) {
    if (i) pre.appendChild(document.createTextNode("\n"));
    var isError = ERRORS.some(function (e) { return line.indexOf(e) === 0; });
    if (isError) {
      var span = document.createElement("span");
      span.className = "err";
      span.textContent = line;
      pre.appendChild(span);
    } else {
      pre.appendChild(document.createTextNode(line));
    }
  });
}

function nearBottom() {
  return window.innerHeight + window.scrollY >= document.body.scrollHeight - 80;
}

function scrollDown() {
  window.scrollTo(0, document.body.scrollHeight);
}

function receive(text) {
  var follow = nearBottom();
  if (!last) addNote();
  last.raw += text;
  paint(last.out, last.raw);
  if (last.running && last.raw.slice(-PROMPT.length) === PROMPT) {
    last.running = false;
    last.label.textContent = "[" + last.number + "]:";
  }
  if (follow) scrollDown();
}

function addCell(code) {
  var cell = document.createElement("div");
  cell.className = "cell";
  var label = document.createElement("div");
  label.className = "label";
  label.textContent = "[*]:";
  var pre = document.createElement("pre");
  pre.className = "code";
  pre.textContent = code;
  pre.title = "Copy to the input";
  pre.onclick = function () { setInput(code); input.focus(); };
  var out = document.createElement("pre");
  out.className = "out";
  cell.appendChild(label);
  cell.appendChild(pre);
  cell.appendChild(out);
  notebook.appendChild(cell);
  count++;
  last = { number: count, label: label, out: out, raw: "", running: true };
}

// Holds output that comes before any cell, such as the greeting.
function addNote() {
  var cell = document.createElement("div");
  cell.className = "cell note";
  var out = document.createElement("pre");
  out.className = "out";
  cell.appendChild(out);
  notebook.appendChild(cell);
  last = { out: out, raw: "", running: false };
}

function setStatus(text, closed, asHTML) {
  if (asHTML) statusLine.innerHTML = text; else statusLine.textContent = text;
  statusLine.className = closed ? "closed" : "";
  reconnectButton.hidden = !closed;
}

function run() {
  var code = input.value.replace(/\s+$/, "");
  if (!code.trim()) return;
  if (!session) { setStatus("Not connected to the robot.", true); return; }
  addCell(code);
  if (past[past.length - 1] !== code) past.push(code);
  index = past.length;
  draft = "";
  setInput("");
  scrollDown();
  fetch("/eval?session=" + session, { method: "POST", body: code }).then(function (r) {
    if (!r.ok) receive("Execution error: not connected to the robot\n");
  }, function () {
    receive("Execution error: secondloop.py is not running\n");
  });
}

function setInput(text) {
  input.value = text;
  input.selectionStart = input.selectionEnd = text.length;
  resize();
}

function resize() {
  input.style.height = "auto";
  input.style.height = input.scrollHeight + 2 + "px";
}

function recall(step) {
  var i = index + step;
  if (i < 0 || i > past.length) return;
  if (index === past.length) draft = input.value;
  index = i;
  setInput(i === past.length ? draft : past[i]);
}

input.addEventListener("input", resize);
input.addEventListener("keydown", function (e) {
  var before = input.value.slice(0, input.selectionStart);
  var after = input.value.slice(input.selectionEnd);
  if (e.key === "Enter" && !e.shiftKey) {
    if (isComplete(input.value)) { e.preventDefault(); run(); }
  } else if (e.key === "Tab" && !e.shiftKey) {
    e.preventDefault();
    document.execCommand("insertText", false, "  ");
  } else if (e.key === "ArrowUp" && before.indexOf("\n") < 0) {
    e.preventDefault(); recall(-1);
  } else if (e.key === "ArrowDown" && after.indexOf("\n") < 0) {
    e.preventDefault(); recall(1);
  }
});

function connect() {
  setStatus("Connecting…", false);
  source = new EventSource("/events");
  source.addEventListener("session", function (e) {
    var data = JSON.parse(e.data);
    session = data.id;
    intro = data.intro;
    setStatus(intro || "Connected.", false, !!intro);
  });
  source.addEventListener("output", function (e) { receive(JSON.parse(e.data)); });
  source.addEventListener("closed", function (e) {
    disconnect(JSON.parse(e.data));
  });
  source.onerror = function () {
    if (source) disconnect("Lost contact with secondloop.py.");
  };
}

function disconnect(message) {
  source.close();
  source = null;
  session = null;
  if (last && last.running) { last.running = false; last.label.textContent = "[" + last.number + "]:"; }
  setStatus(message, true);
}

reconnectButton.onclick = function () {
  last = null;
  connect();
};
document.getElementById("clear").onclick = function () {
  notebook.textContent = "";
  last = null;
  input.focus();
};

// History is shared with the terminal, so fetch it again on coming back to
// the tab, unless an entry is being recalled right now.
function loadHistory() {
  fetch("/history").then(function (r) { return r.json(); }).then(function (entries) {
    if (index !== past.length) return;
    past = entries;
    index = past.length;
  }, function () {});
}
window.addEventListener("focus", loadHistory);
document.addEventListener("visibilitychange", function () {
  if (!document.hidden) loadHistory();
});
loadHistory();
resize();
connect();
</script>
</body>
</html>
"""


def bold_html(text, style):
    """HTML for text, in bold for the WiFi Direct name and the configuration."""
    text = html.escape(text)
    return "<b>%s</b>" % text if style in (Colors.DEVICE, Colors.CONFIG) else text


def open_browser(url):
    """Opens url in the default web browser, without letting the browser
    write over the terminal."""
    try:
        if sys.platform.startswith("linux") and shutil.which("xdg-open"):
            subprocess.Popen(["xdg-open", url], stdin=subprocess.DEVNULL,
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                             start_new_session=True)
        else:
            webbrowser.open(url)
    except (OSError, webbrowser.Error):
        pass


def start_web(host, port, rc_info, history, colors, browser=True):
    intro = describe_rc(rc_info, bold_html) if rc_info else ""
    try:
        WebServer(host, port, intro, history).start()
    except OSError as e:
        print(colors.paint("No web REPL: port %d: %s" % (WEB_PORT, e.strerror or e), Colors.NOTE))
        return
    url = "http://localhost:%d" % WEB_PORT
    print("Web REPL at %s" % colors.paint(url, Colors.DEVICE))
    if browser:
        threading.Thread(target=open_browser, args=(url,), daemon=True).start()


def main():
    parser = argparse.ArgumentParser(description="Connect to the Second Loop REPL on a robot.")
    parser.add_argument("host", nargs="?", default=None,
                        help="robot address, optionally HOST:PORT (default %s)"
                        % ", then ".join(DEFAULT_HOSTS))
    parser.add_argument("port", nargs="?", type=int, default=None,
                        help="port (default %d)" % DEFAULT_PORT)
    parser.add_argument("--history", default=HISTORY_FILE,
                        help="history file (default %(default)s); empty to keep none")
    parser.add_argument("--no-color", action="store_true",
                        help="never use colors (they are also off when NO_COLOR is set)")
    parser.add_argument("--no-web", action="store_true",
                        help="don't serve the web REPL at http://localhost:%d" % WEB_PORT)
    parser.add_argument("--no-browser", action="store_true",
                        help="don't open the web REPL in the default web browser")
    args = parser.parse_args()

    host, port = args.host, args.port
    if port is None:
        port = DEFAULT_PORT
        if host is not None and host.count(":") == 1:
            host, _, text = host.partition(":")
            try:
                port = int(text)
            except ValueError:
                parser.error("bad port: %s" % text)

    # The robot controller answers even before the REPL runs, so its details
    # are shown first, also when the REPL turns out to be unreachable.
    interactive = sys.stdin.isatty() and sys.stdout.isatty()
    if interactive:
        info_out = sys.stdout
        color = not args.no_color and color_wanted() and enable_ansi()
    else:
        info_out = sys.stderr  # stdout is kept for what the robot sends
        color = not args.no_color and not WINDOWS and sys.stderr.isatty() and color_wanted()
    errors = []
    for candidate in ([host] if host is not None else DEFAULT_HOSTS):
        try:
            rc_info = fetch_rc_info(candidate)
        except Unreachable as e:
            errors.append(str(e))
            continue
        host = candidate
        break
    else:
        sys.exit("\n".join(errors))
    if rc_info:
        info_out.write(describe_rc(rc_info, Colors(color).paint) + "\n")
        info_out.flush()
    history = History(args.history) if interactive else None
    if interactive and not args.no_web:
        start_web(host, port, rc_info, history, Colors(color), not args.no_browser)

    sock = connect(host, port, Colors(color and sys.stderr.isatty()))
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    # Kept for checking compatibility with the robot later; nothing uses it yet.
    greeting, robot_version = fetch_version(sock)
    if interactive:
        run_interactive(sock, history, color, greeting)
    else:
        run_piped(sock, color, greeting)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
