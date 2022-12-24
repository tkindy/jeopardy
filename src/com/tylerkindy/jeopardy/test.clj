(ns com.tylerkindy.jeopardy.test
  (:require [compojure.core :refer [defroutes GET]]
            [org.httpkit.server :refer [as-channel send!]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5]]
            [com.tylerkindy.jeopardy.jservice :refer [random-clue]]
            [cheshire.core :as json]
            [clojure.string :as str]
            [com.tylerkindy.jeopardy.common :refer [scripts]]))

(defn answer-form [response]
  [:form#answer-form {:ws-send "", :hx-swap-oob "morph"}
   [:input {:name :type, :value :answer, :hidden ""}]
   [:input#answer {:name "answer", :autocomplete :off}]
   [:button "Submit"]
   [:p response]])

(defn test-page []
  (html5
   {:lang :en}
   [:body
    [:h1 "Jeopardy"]

    [:div {:hx-ext "ws,morph", :ws-connect "/test"}
     [:div#clue "Loading..."]
     (answer-form "")
     [:form {:ws-send ""}
      [:input {:name :type, :value :new-question, :hidden ""}]
      [:button "New question"]]]

    scripts]))

(defn test-page-response []
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (test-page)})

(defonce clue (atom nil))

(defn render-question [{:keys [category question]}]
  (html
   [:div#clue
    [:p.category (:title category)]
    [:p.question question]]))

(defn send-clue [ch]
  (send! ch (render-question @clue)))

(defn new-clue [ch]
  (let [new (random-clue)]
    (reset! clue new)
    (send-clue ch)))

; TODO: implement edit distance
; TODO: strip HTML (I've seen <a>, <i>)
(defn check-answer [{:keys [answer]}]
  (let [clue @clue
        response (if (= (str/lower-case (:answer clue))
                        (str/lower-case answer))
                   "That's right!"
                   "Incorrect.")]
    (html (answer-form (str response " " (:answer clue))))))

(defn receive-message [ch message]
  (let [{:keys [type] :as message} (json/parse-string message keyword)]
    (case (keyword type)
      :answer (send! ch (check-answer message))
      :new-question (new-clue ch))))

(defn test-websocket [req]
  (as-channel req
              {:on-open new-clue
               :on-receive receive-message}))

(defn handle-test-request [req]
  (if (:websocket? req)
    (test-websocket req)
    (test-page-response)))

(defroutes test-routes
  (GET "/test" req (handle-test-request req)))
