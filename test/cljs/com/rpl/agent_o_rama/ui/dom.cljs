(ns com.rpl.agent-o-rama.ui.dom
  "DOM setup for ClojureScript tests using jsdom.
  This namespace should be required before any tests that need DOM access.")

(defn setup-dom!
  "Initialize jsdom to provide a DOM environment for tests."
  []
  (let [jsdom (js/require "jsdom")
        JSDOM (.-JSDOM jsdom)
        dom (JSDOM. "<!DOCTYPE html><html><head></head><body></body></html>")]
    (set! js/window (.-window dom))
    (set! js/document (.-document js/window))
    (set! js/navigator (.-navigator js/window))
    (set! js/global js/window)))

(setup-dom!)
