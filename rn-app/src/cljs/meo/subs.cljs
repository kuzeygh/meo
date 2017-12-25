(ns meo.subs
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub :stats (fn [db _] (:stats db)))
(reg-sub :entries (fn [db _] (:entries db)))
