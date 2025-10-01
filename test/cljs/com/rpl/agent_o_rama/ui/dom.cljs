(ns com.rpl.agent-o-rama.ui.dom
  "DOM setup for ClojureScript tests using jsdom.
  This namespace should be required before any tests that need DOM access.")

(defn setup-dom!
  "Initialize jsdom to provide a DOM environment for tests."
  []
  (let [jsdom      (js/require "jsdom")
        JSDOM      (.-JSDOM jsdom)
        dom        (JSDOM.
                    "<!DOCTYPE html><html><head></head><body></body></html>")
        dom-window (.-window dom)]

    ;; Set up basic globals
    (set! js/window dom-window)
    (set! js/document (.-document dom-window))
    (set! js/navigator (.-navigator dom-window))
    (set! js/global dom-window)

    ;; Add DOM element constructors that React needs
    (set! js/HTMLElement (.-HTMLElement dom-window))
    (set! js/HTMLDivElement (.-HTMLDivElement dom-window))
    (set! js/Element (.-Element dom-window))

    ;; Add animation frame support (React often needs this)
    (when (or (not (exists? js/requestAnimationFrame))
              (not js/requestAnimationFrame))
      (set! js/requestAnimationFrame
            (fn [callback] (js/setTimeout callback 0))))
    (when-not (or (not (exists? js/cancelAnimationFrame))
                  (not js/cancelAnimationFrame))
      (set! js/cancelAnimationFrame
            (fn [id] (js/clearTimeout id))))))

(setup-dom!)
