(ns com.rpl.agent-o-rama.throttled-logging
  (:require
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.rama.throttled-logging :as throttled-logging]))

(defmacro logp
  [callsite-id & args]
  `(throttled-logging/logp (po/log-throttler) ~callsite-id ~@args))

(defmacro debug
  [callsite-id & args]
  `(throttled-logging/debug (po/log-throttler) ~callsite-id ~@args))

(defmacro info
  [callsite-id & args]
  `(throttled-logging/info (po/log-throttler) ~callsite-id ~@args))

(defmacro warn
  [callsite-id & args]
  `(throttled-logging/warn (po/log-throttler) ~callsite-id ~@args))

(defmacro error
  [callsite-id & args]
  `(throttled-logging/error (po/log-throttler) ~callsite-id ~@args))

(defmacro fatal
  [callsite-id & args]
  `(throttled-logging/fatal (po/log-throttler) ~callsite-id ~@args))
