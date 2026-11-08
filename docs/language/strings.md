# Strings

Strings are mostly for the Driver Hub: a label next to a value, a formatted
number, or a message when something goes wrong.

## str

`str` joins its arguments with no separator:

```clojure
user=> (str "power " 0.54)
"power 0.54"
user=> (str 1 2 3)
"1.02.03.0"
```

It uses the same text `println` shows, so `nil` becomes the empty string:

```clojure
user=> (str "x" nil)
"x"
```

## println, pr-str and prn

`println` prints its arguments separated by spaces and adds a newline. The text
appears without quotes because `println` prints it rather than returning a
string:

```clojure
user=> (println "power" 0.54)
power 0.54
nil
```

`pr-str` builds the readable form, with quotes around strings, and `prn` prints
that form. Use them when you want to see exactly what a value is:

```clojure
user=> (pr-str "Bolt")
"\"Bolt\""
user=> (prn {:a 1})
{:a 1.0}
nil
user=> (str {:a 1})
"{:a 1.0}"
```

## format

`format` takes a Java-style pattern and fills in the values, which is the way to
control how many decimals a number shows:

```clojure
user=> (format "%.2f" 1.234)
"1.23"
user=> (format "x=%.1f y=%.1f" 1 2)
"x=1.0 y=2.0"
```

All numbers are doubles, so `%d` is an error. Use `%.0f` for whole numbers, and
`%s` for anything else:

```clojure
user=> (format "%d" 3)
Execution error: format: d != java.lang.Double (all numbers are doubles, so use %.0f instead of %d)
user=> (format "%.0f" 3)
"3"
```

`format` returns a string; `println` or `telemetry/add-line` puts it on screen.

## parse-double

`parse-double` turns text into a number, and returns `nil` when the text is not
a number, so a bad sensor line does not crash the loop:

```clojure
user=> (parse-double "3.5")
3.5
user=> (parse-double "3.5x")
nil
```

## subs

`subs` takes part of a string, given a start index and an optional end index:

```clojure
user=> (subs "Second Loop" 7)
"Loop"
user=> (subs "Second Loop" 0 6)
"Second"
```

## str/ functions

These are the string helpers robot code tends to need. There are no regular
expressions, so `str/split` and `str/replace` match plain text.

```clojure
user=> (str/join ", " ["a" "b"])
"a, b"
user=> (str/join ["a" "b"])
"ab"
user=> (str/split "a,b,c" ",")
["a" "b" "c"]
user=> (str/replace "left motor" "left" "right")
"right motor"
```

`str/replace` replaces every occurrence. `str/split` returns a vector and drops
trailing empty parts, so `(str/split "a,b," ",")` is `["a" "b"]`.

Tests and small edits:

```clojure
user=> (str/trim "  x  ")
"x"
user=> (str/blank? "  ")
true
user=> (str/upper-case "ab")
"AB"
user=> (str/lower-case "AB")
"ab"
user=> (str/includes? "drive" "ive")
true
user=> (str/starts-with? "drive" "dr")
true
user=> (str/ends-with? "drive" "ve")
true
```

`str/blank?` is also true for `nil`, which makes it safe on a missing reading.
