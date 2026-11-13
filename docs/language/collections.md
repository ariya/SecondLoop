# Collections

Lists, vectors, maps and sets are values. Every function below returns a new
collection and leaves its arguments alone, so a snapshot you took earlier never
changes.

Literals are the usual way to build one; the functions are for when you build
one at run time:

```clojure
user=> [1 2 3]
[1.0 2.0 3.0]
user=> {:name "Bolt" :speed 0.5}
{:name "Bolt", :speed 0.5}
user=> #{1 2 3}
#{1.0 2.0 3.0}
user=> (list 1 2 3)
(1.0 2.0 3.0)
```

A set literal with a repeated value is a syntax error; use `set` to remove
duplicates from an existing collection.

`list`, `vector`, `hash-map` and `hash-set` do the same from values you computed.
`vec` and `set` convert a collection, and `range` builds a list of numbers:

```clojure
user=> (vec (range 3))
[0.0 1.0 2.0]
user=> (set [1 1 2])
#{1.0 2.0}
user=> (range 5)
(0.0 1.0 2.0 3.0 4.0)
user=> (range 0 10 3)
(0.0 3.0 6.0 9.0)
```

`range` needs an end: nothing is lazy here, so every element is built at once.

## Reading

```clojure
user=> (count [1 2 3])
3.0
user=> (first [1 2 3])
1.0
user=> (rest [1 2 3])
(2.0 3.0)
user=> (nth [10 20 30] 1)
20.0
user=> (get {:a 1} :a)
1.0
user=> (get {:a 1} :b :missing)
:missing
user=> (keys {:a 1 :b 2})
(:a :b)
user=> (vals {:a 1 :b 2})
(1.0 2.0)
```

`first`, `second` and `last` return `nil` for an empty collection; `rest`
returns an empty list and `next` returns `nil`. `contains?` asks about a key for
a map or set, and about an index for a vector:

```clojure
user=> (contains? {:a 1} :a)
true
user=> (contains? [10 20] 1)
true
user=> (empty? [])
true
user=> (seq [])
nil
user=> (seq [1])
(1.0)
```

`seq` turns any collection into a list, or `nil` when it is empty, which is why
it works as an emptiness test in `if`.

`get-in` reads a path, and `peek` looks at the end of a vector or the front of a
list:

```clojure
user=> (get-in {:arm {:angle 45}} [:arm :angle])
45.0
user=> (peek [1 2 3])
3.0
user=> (peek (list 1 2 3))
1.0
```

## Changing

`conj` adds to the natural end: the back of a vector, the front of a list, or a
`[key value]` pair to a map. `cons` always adds to the front of a sequence:

```clojure
user=> (conj [1 2] 3)
[1.0 2.0 3.0]
user=> (conj (list 1 2) 3)
(3.0 1.0 2.0)
user=> (conj {:a 1} [:b 2])
{:a 1.0, :b 2.0}
user=> (cons 1 [2])
(1.0 2.0)
user=> (pop [1 2 3])
[1.0 2.0]
```

`assoc` sets a key or index, `update` changes a value through a function, and
`update-in` does the same at a path. `dissoc` and `disj` remove:

```clojure
user=> (assoc {:a 1} :b 2)
{:a 1.0, :b 2.0}
user=> (assoc [1 2] 0 9)
[9.0 2.0]
user=> (update {:power 0.5} :power * 2)
{:power 1.0}
user=> (update-in {:arm {:angle 30}} [:arm :angle] + 15)
{:arm {:angle 45.0}}
user=> (assoc-in {} [:a :b] 1)
{:a {:b 1.0}}
user=> (dissoc {:a 1 :b 2} :a)
{:b 2.0}
user=> (disj #{1 2} 1)
#{2.0}
```

`assoc-in` and `update-in` create missing levels on the way down, which makes
them handy for nested configuration maps.

`into` pours one collection into another and `merge` combines maps:

```clojure
user=> (into [1] [2 3])
[1.0 2.0 3.0]
user=> (into {} [[:a 1]])
{:a 1.0}
user=> (merge {:a 1} {:b 2 :a 3})
{:a 3.0, :b 2.0}
```

## Sequences

`map`, `filter`, `reduce`, `some` and `every?` take a function, and the usual
anonymous function is `fn`:

```clojure
user=> (map inc [1 2 3])
(2.0 3.0 4.0)
user=> (filter even? [1 2 3 4])
(2.0 4.0)
user=> (reduce + [1 2 3])
6.0
user=> (reduce + 10 [1 2 3])
16.0
user=> (some even? [1 2 3])
true
user=> (every? pos? [1 2 3])
true
```

`concat` and `reverse` do what they say:

```clojure
user=> (concat [1] [2 3])
(1.0 2.0 3.0)
user=> (reverse [1 2 3])
(3.0 2.0 1.0)
```

`sort` puts numbers, strings or keywords in order. Give it a function such as
`>` to choose another order. A map is sorted by its keys:

```clojure
user=> (sort [3 -1 2])
(-1.0 2.0 3.0)
user=> (sort > [3 -1 2])
(3.0 2.0 -1.0)
user=> (sort {:right 0.4 :left 0.5})
([:left 0.5] [:right 0.4])
```

Because keywords are functions, `map` can pull one key out of every map. This
is the usual way to summarise robot state:

```clojure
user=> (map :name [{:name "Bolt"} {:name "Dash"}])
("Bolt" "Dash")
```

## Predicates

`list?`, `seq?`, `vector?`, `map?`, `set?` and `coll?` test the type of a value:

```clojure
user=> (vector? [1])
true
user=> (seq? [1])
false
user=> (coll? {:a 1})
true
```

## Lists and vectors

A list and a vector print differently, `(1 2 3)` and `[1 2 3]`, and both work
as sequences. They differ in where `conj` adds, as shown above, and in that a
vector can be indexed with `nth` or by calling it.
