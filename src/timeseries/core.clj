(ns timeseries.core
  (:use [clojure.string :only [join]]))

(defn- format-args
  [args]
  (join " " (map #(if (nil? %) "nil" %) args)))

(defn- error-string
  [e fn-name args]
  (let [arg-string (format-args args)]
    (str (.getMessage e) "\n"
	 (format "Try calling your function like: (%s %s)" fn-name arg-string))))

(defn- empty-trace
  [args rval])

(defn example-trace
  "An example trace function for ts-seq."
  [args rval]
  (println (format "(fn %s) => %s" (format-args args) rval)))

(defn- wrap-trace [trace-fn]
  (fn [f & args]
    (let [rval (apply f args)]
      (binding [*out* *err*]
	(trace-fn args rval)
	rval))))
  
(defn- create-step-fn
  [f n-steps trace-fn]
  (let [trace (wrap-trace trace-fn)]
    (fn [[current-state previous-states]]
      [(try
	 (condp = n-steps
	     0 (trace f)
	     1 (trace f current-state)
	     (apply trace f current-state previous-states))
	 (catch Exception e
	   (throw (Exception. (error-string e "f"
					    (cons current-state previous-states))))))
       (take (dec n-steps) (cons current-state (take n-steps previous-states)))])))

(defn ts-seq
  "Create a timeseries sequence given an initial state, a
   stop condition, a function for generating the next value.
   The length of initial-state determines the number of values
   passed to the function for generating the next value. The stop-fn
   is called with only the current value in the sequence."
  ([initial-state stop-fn next-fn]
     (ts-seq initial-state stop-fn next-fn empty-trace))
  ([initial-state stop-fn next-fn trace-fn]
     (let [step-fn (create-step-fn next-fn (count initial-state) trace-fn)]
       (take-while #(not (stop-fn %))
		   (map first (iterate step-fn [(first initial-state)
						(rest initial-state)]))))))

(defn payment-model
  "Return a function for a given periodic interest and payment
   against a loan."
  [interest payment]
  (fn [amount]
    (- (+ amount (* amount interest))
       payment)))

(defn value-at-n
  "Find the value of a timeseries sequence at n."
  [ts n]
  (first (drop n ts)))

(defn negam?
  "Determine if a timeseries sequence has negative amortization
   over a specified period."
  [ts n]
  (every? identity (map (fn [[x y]] (< x y))
			(partition 2 1 ts))))

(defn- pairs-till-divergence
  [& more-ts]
  (let [ts-count (count more-ts)]
    (take-while
     #(apply = %)
     (partition ts-count ts-count
		(if (< 1 ts-count)
		  (apply interleave more-ts)
		  (first more-ts))))))

(defn ts-compare
  "Takes an initial state, a stop condition, and N step functions.
   Makes a timeseries sequence for each step function and
   compares their values until divergence or the stop condition
   is reached, which ever comes first. If any of the functions
   diverged then the number of steps required for divergence is returned.
   If the stop condition is met then nil is returned."
  [initial-state stop-fn fn1 fn2 & more-fns]
  (let [fns (cons fn1 (cons fn2 more-fns))
	all-ts (map #(ts-seq initial-state stop-fn %) fns)
	pairs (apply pairs-till-divergence all-ts)
	last-pairs (last pairs)]
    (if (not (apply = (map #(%1 %2) fns last-pairs)))
      {:steps (dec (count pairs))
       :value (first last-pairs)})))
