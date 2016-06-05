(ns iwaswhere-web.store-keepalive-test
  "Here, we test the keepalive handler functions."
  (:require [clojure.test :refer [deftest testing is]]
            [matthiasn.systems-toolbox.component :as stc]
            [iwaswhere-web.store :as s]
            [iwaswhere-web.client-store :as cs]
            [iwaswhere-web.store-test :as st]
            [iwaswhere-web.keepalive :as k]))

(deftest backend-keepalive-test
  "The keepalive mechanism consists of two parts:
    1) Connected clients send frequent :cmd/keep-alive messages. These are handled by the
       keepalive-fn, which resets the :last-seen key for a particular client.
       To emulate this, we create a component state with a query, and then call the keepalive-fn
       with the same connection ID.
    2) The backend sends :cmd/query-gc messages to the store every so often, which are handled
       by the query-gc-fn. This checks which queries were seen too long ago, and removes those.
       Here, we can use the state from the previous step, with the max-age redefined as -1
       so that any query would always be too old. With that, we expect the query created in the
       previous step to be removed."
  (let [test-ts (stc/now)
        sente-uid (stc/make-uuid)
        current-state (:current-state (st/mk-test-state test-ts))
        w-query (:new-state (s/state-get-fn {:current-state current-state
                                             :msg-payload   st/simple-query
                                             :msg-meta      {:sente-uid sente-uid}}))
        {:keys [new-state emit-msg]} (k/keepalive-fn {:current-state w-query
                                                      :msg-meta      {:sente-uid sente-uid}})]
    (testing "component state has recent last-seen timestamp for query"
      (is (< (- (get-in new-state [:client-queries sente-uid :last-seen]) test-ts) 1000)))
    (testing "handler emits keep-live-res message, with connection UUID"
      (is (= emit-msg [:cmd/keep-alive-res]))
      (is (= (:sente-uid (meta emit-msg)) sente-uid)))
    (testing "client query removed when :last-seen too long ago"
      (with-redefs [k/max-age -1]
        (let [new-state (k/query-gc-fn {:current-state new-state})]
          (is (not (get-in new-state [:client-queries sente-uid]))))))))


(deftest frontend-keepalive-test
  "The keepalive mechanism consists of two parts:
    1) When a response to a :cmd/keep-alive message is received, the :last-alive timestamp is set
    2) Every so often, the scheduler sends a message to check if :last-alive timestamp is too long
       ago. In that case, we expect the component state to be reset to empty."
  (let [current-state @(:state (cs/initial-state-fn #()))
        new-state (:new-state (k/set-alive-fn {:current-state current-state}))]

    (testing ":last-alive timestamp set"
      (is (< (- (stc/now) (:last-alive new-state)) 10)))

    (testing "client state reset when :last-alive too long ago"
      (with-redefs [k/max-age -1]
        (let [new-state (:new-state (k/reset-fn {:current-state new-state}))]
          (is (empty? new-state)))))))

