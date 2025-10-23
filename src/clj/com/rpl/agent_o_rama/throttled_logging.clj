(ns com.rpl.agent-o-rama.throttled-logging
  "Throttled logging utilities.
  
  This namespace provides rate-limited logging macros that prevent log overflow
  during high-frequency operations. Throttled logging automatically limits the number of
  log messages emitted per callsite within a time window.
  
  Key features:
  - Rate limiting per callsite to prevent log spam
  - Automatic throttling based on configurable thresholds
  - Preserves critical messages while filtering repetitive ones
  - Transparent integration with standard logging levels
  
  Example:
    (require '[com.rpl.agent-o-rama.throttled-logging :as tl])
    
    ;; In an agent node function
    (fn [agent-node input]
      (dotimes [i 1000]
        ;; This will be throttled after the rate limit is reached
        (tl/info ::processing-loop (str \"Processing item \" i)))
      (aor/result! agent-node :completed))"
  (:require
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.rama.throttled-logging :as throttled-logging]))

(defmacro logp
  "Logs a message with the specified level using throttled logging.
  
  This is the base throttled logging macro that all other level-specific
  macros delegate to. It provides fine-grained control over log levels
  while maintaining rate limiting per callsite.
  
  Args:
    callsite-id - Keyword identifying the callsite for rate limiting (recommended: namespaced keyword unique to that callsite)
    level - Log level keyword (:debug, :info, :warn, :error, :fatal)
    throwable - Optional Throwable instance
    message - Log message string
    
  Example:
    (tl/logp ::data-processing :info \"Processing batch of 1000 items\")
    (tl/logp ::error-handling :error ex \"Failed to process request\")"
  [callsite-id & args]
  `(throttled-logging/logp (po/log-throttler) ~callsite-id ~@args))

(defmacro debug
  "Logs a debug message using throttled logging.
  
  Debug messages are typically used for detailed diagnostic information
  that is only of interest when debugging problems. These messages are
  usually filtered out in production environments.
  
  Args:
    callsite-id - Keyword identifying the callsite for rate limiting (recommended: namespaced keyword unique to that callsite)
    message - Debug message string
    
  Example:
    (tl/debug ::data-validation \"Validating input parameters\")
    (tl/debug ::cache-lookup (str \"Cache hit for key: \" cache-key))"
  [callsite-id & args]
  `(throttled-logging/debug (po/log-throttler) ~callsite-id ~@args))

(defmacro info
  "Logs an informational message using throttled logging.
  
  Info messages provide general information about the application's
  execution flow. They are typically used to track important events
  and state changes.
  
  Args:
    callsite-id - Keyword identifying the callsite for rate limiting (recommended: namespaced keyword unique to that callsite)
    message - Informational message string
    
  Example:
    (tl/info ::agent-start \"Agent execution started\")
    (tl/info ::data-processing (str \"Processed \" count \" items successfully\"))"
  [callsite-id & args]
  `(throttled-logging/info (po/log-throttler) ~callsite-id ~@args))

(defmacro warn
  "Logs a warning message using throttled logging.
  
  Warning messages indicate potentially harmful situations or unusual
  conditions that don't prevent the application from continuing but
  may indicate problems that should be investigated.
  
  Args:
    callsite-id - Keyword identifying the callsite for rate limiting (recommended: namespaced keyword unique to that callsite)
    throwable - Optional Throwable instance
    message - Warning message string
    
  Example:
    (tl/warn ::rate-limit ex \"API rate limit hit\")
    (tl/warn ::deprecated-usage \"Using deprecated function, consider upgrading\")"
  [callsite-id & args]
  `(throttled-logging/warn (po/log-throttler) ~callsite-id ~@args))

(defmacro error
  "Logs an error message using throttled logging.
  
  Error messages indicate serious problems that prevent the application
  from performing a function but allow it to continue running. These
  are typically exceptions or unexpected conditions that require attention.
  
  Args:
    callsite-id - Keyword identifying the callsite for rate limiting (recommended: namespaced keyword unique to that callsite)
    throwable - Optional Throwable instance
    message - Error message string
    
  Example:
    (tl/error ::tool-exec-error ex \"Tool execution failed\")
    (tl/error ::data-validation \"Invalid input data received\")"
  [callsite-id & args]
  `(throttled-logging/error (po/log-throttler) ~callsite-id ~@args))

(defmacro fatal
  "Logs a fatal message using throttled logging.
  
  Fatal messages indicate very severe errors that will presumably lead
  to the application aborting. These are the highest priority log messages
  and should be used sparingly for critical system failures.
  
  Args:
    callsite-id - Keyword identifying the callsite for rate limiting (recommended: namespaced keyword unique to that callsite)
    throwable - Optional Throwable instance
    message - Fatal error message string
    
  Example:
    (tl/fatal ::system-failure ex \"Critical system component failed\")
    (tl/fatal ::resource-exhaustion \"Out of memory, shutting down\")"
  [callsite-id & args]
  `(throttled-logging/fatal (po/log-throttler) ~callsite-id ~@args))
