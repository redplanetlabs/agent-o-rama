(ns com.rpl.agent-o-rama.ui.server
  (:require
   [ring.middleware.file :as ring-file]
   [ring.middleware.file-info :as ring-file-info]
   [reitit.ring :as ring]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.ring.middleware.exception :as exception]
   [muuntaja.core :as m]
   [ring.util.response :as resp]
   [ring.middleware.resource :as resource]
   [ring.middleware.content-type :as content-type]
   [ring.middleware.not-modified :as not-modified]
   [reitit.coercion.malli :as rcm]
   [reitit.ring.coercion :as rrc]
   [malli.core :as mc]

   [com.rpl.agent-o-rama.ui.agents :as agents]
   [com.rpl.agent-o-rama.ui.datasets :as datasets]))
