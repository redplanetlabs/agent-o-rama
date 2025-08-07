(ns com.rpl.agent_o_rama.impl.ui.sente.adapter
  "A custom, WebSocket-only Sente server adapter specifically for ring-jetty-adapter v1.9.x."
  #_(:require
     [taoensso.sente.interfaces :as i])
  (:import
   [org.eclipse.jetty.websocket.api Session]
   ;; Note: We are NOT requiring ring.websocket, but using the specific types
   ;; from the Jetty library that ring-jetty-adapter exposes.
   ))

;;;; Step 1: IServerChan implementation for Jetty's `Session`
;; Sente needs a wrapper around the native WebSocket object. For this version of
;; the Jetty adapter, that object is an `org.eclipse.jetty.websocket.api.Session`.

(deftype JettyWsChan [^Session ws-session]
  i/IServerChan
  (sch-open? [_]
    (.isOpen ws-session))
  (sch-close! [_]
    ;; Per Jetty docs, this closes the connection.
    (.close ws-session 1000 "Normal Sente closure"))
  (sch-send! [_ _websocket? msg]
    ;; The Jetty remote endpoint handles the actual sending.
    (try
      (-> ws-session .getRemote (.sendString ^String msg))
      true
      (catch Exception e
        (trove/log! {:level :error, :id :sente.adapter.jetty/send-error, :error e})
        false))))

;;;; Step 2: The Main Adapter Implementation
;; This uses the websocket API provided directly by ring-jetty-adapter's body handler.

(deftype JettySenteAdapter []
  i/IServerChanAdapter
  (ring-req->server-ch-resp [_ ring-req callbacks]
    (let [{:keys [on-open on-close on-msg on-error]} callbacks]
      ;; The Ring spec for this version of the Jetty adapter handles websocket
      ;; upgrades by returning a special map of callbacks in the response body.
      ;; The ring-jetty-adapter middleware detects this and performs the upgrade.
      {;; status 101 is the HTTP "Switching Protocols" code for WebSocket upgrades
       :status 101
       :headers {}

       ;; The body contains the handler map.
       :body
       {:on-connect
        (fn [ws-session]
          (try
            ;; Create our wrapper and notify Sente that a channel is open.
            (on-open (->JettyWsChan ws-session) true)
            (catch Exception e (on-error (->JettyWsChan ws-session) true e))))

        :on-text
        (fn [ws-session message]
          (try
            (on-msg (->JettyWsChan ws-session) true message)
            (catch Exception e (on-error (->JettyWsChan ws-session) true e))))

        :on-close
        (fn [ws-session status-code reason]
          (try
            (on-close (->JettyWsChan ws-session) true {:status status-code :reason reason})
            (catch Exception e (on-error (->JettyWsChan ws-session) true e))))

        :on-error
        (fn [ws-session throwable]
          (on-error (->JettyWsChan ws-session) true throwable))
        
        ;; Sente doesn't use these, but they are part of the protocol
        :on-bytes (fn [_ _ _ _])
        :on-ping (fn [_ _])
        :on-pong (fn [_ _])
        }})))

;;;; Step 3: Constructor Function
(defn get-sch-adapter
  "Returns a new instance of our custom WebSocket-only adapter for Ring-Jetty 1.9.x."
  []
  (->JettySenteAdapter))
