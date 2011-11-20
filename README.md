# Timeseries

A small framework for time series generation.

## simple time series generation

```clojure
(use 'timeseries.core)
(set! *print-length* 10)
```

This will be our stop criteria. When it returns true, the sequence will end.

```clojure
(defn simple-stop [x]
  (< x 0))
```

Next we'll define a step function to represent an annual interest rate
of 12 percent, with a periodic payment of 400 dollars.

```clojure
(defn example1-step [x]
  (let [p (payment-model (/ 0.12 12) 400)]
    (p x)))
```

Now we'll make a timeseries with an initial state of [10000], and
use the stop and step functions defined above.

```clojure
(def example1-ts
  (ts-seq [10000] simple-stop example1-step))

example1-ts
;; (10000 9700.0 9397.0 9090.97 ...)
```

## slightly more complicated time series generation

Let's make a slightly more complicated but unrealistic payment
model with an interest rate that changes based on the principal.
If the principal is over 5000, the we have 12 percent interest
annually. If the principal is 5000 or less, then we have 10
percent interest annually.

```clojure
(defn example2-step [x]
  (let [p1 (payment-model (/ 0.12 12) 400)
	p2 (payment-model (/ 0.10 12) 400)]
    (if (< 5000 x)
      (p1 x)
      (p2 x))))
```

Now let's compare the time series from example 1 with example 2
and look for the interest rate change in output. We can see
that once the balance drops below 5000, the interest rate
changes.

```clojure
(ts-seq [5400] simple-stop example1-step)
;; (5400 5054.0 4704.54 4351.5854 ...)
(ts-seq [5400] simple-stop example2-step)
;; (5400 5054.0 4704.54 4343.7445 ...)

```

## exception handling

What happens if you make a mistake in a step function?
How will you find it? Let's see. We'll make a step function
that works fine until the balance drops below 5000.

```clojure
(defn example3-step [x]
  (let [p1 (payment-model (/ 0.12 12) 400)
	p2 (payment-model (/ 0.10 12) 400)]
    (if (< 5000 x)
      (p1 x)
      (/ 1 0))))

(ts-seq [5400] simple-stop example3-step)
;; ... You should see an exception thrown that contains enough
;; information to duplicate the error ...
;; Divide by zero
;; Try calling your function like: (f 4704.54)
```

## tracing

Maybe you'd like to see a little more of the process. The
ts-seq function also takes a trace function. A sample trace
function is provided called "example-trace". Let's see what
the output looks like, and then try writing our own trace.
ts-seq returns it's normal value, but anything printed
in example-trace is sent to standard error.

```clojure
(ts-seq [10000] simple-stop example1-step example-trace)
;; (fn 10000) => 9700.0
;; (fn 9700.0) => 9397.0
;; ...
;; (10000 9700.0 9397.0 9090.97 ...)
```

The output might be a little too verbose for you, or you might
just want something a little different. The trace function
needs to take 2 arguments. The first argument will be a list of all
the arguments given to the step function, the second argument will
be the return value. Let's write a function that will just print
the return value.

```clojure
(defn simple-trace
  [args rval]
  (println rval))

(ts-seq [10000] simple-stop example1-step simple-trace)
;; 9700.0
;; 9397.0
;; 9090.97
;; ...
;; (10000 9700.0 9397.0 9090.97 ...)
```

You're not seeing all the results you say? These are lazy-seqs, so
we need to ask for all the results if we're interested.
```clojure
(doall (ts-seq [10000] simple-stop example1-step example-trace))
;; ...
;; (10000 9700.0 9397.0 9090.97 ...)
```

## previous state

Now that we know about trace functions, let's show how we can pass
more than just one previous state into the step function. First we
need a step function that can take more than one argument, right?
I couldn't think of any remotely realistic examples, so we'll just
use the first argument, and pass it to the example1-step function
we defined above.

The size of the first argument determines how much state is kept
and passed along to the next step function. The trace output should
make exactly what happens more clear. 

```clojure
(defn example4-step
  [x & more]
  (example1-step x))

(ts-seq [10000 10000] simple-stop example4-step example-trace)
;; (fn 10000 10000) => 9700.0
;; (fn 9700.0 10000) => 9397.0
;; (fn 9397.0 9700.0) => 9090.97
;; ...
;; (10000 9700.0 9397.0 9090.97 ...)
```

So that example was a bit contrived. Let's use the n, and n-1 to
compute fibonacci sequence just to show we really do have access
to previous values. This one should be easy.

```clojure
(ts-seq [0 1] not #(+ %1 %2))
;; (0 1 1 2 3 5 8 13 21 34 ...)
```

## comparing step functions

One more function that should make it easier to compare multiple
step functions at the same time is ts-compare. Let's take a close
look. ts-compare takes an initial state, a stop condition, and as
many step functions as you'd like.

```clojure
(ts-compare [10000] simple-stop example1-step example2-step)
;; {:steps 16, :value 4822.640652289042}
```

The return value may or not be clear. It took 16 steps for divergence
and the value at that step was 4822.640652289042.
Let's compare a step function with itself. No divergence should return
nil.

```clojure
(ts-compare [10000] simple-stop example1-step example1-step)
;; nil
```

Now let's compare more than just two step functions. We'll just compare
example1-step and example2-step, so the results should be the same
as above.

```clojure
(ts-compare [10000] simple-stop example1-step example1-step example1-step example2-step)
;; {:steps 16, :value 4822.640652289042}
```
And that's it!
