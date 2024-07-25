(ns com.tylerkindy.jeopardy.home
  (:require [com.tylerkindy.jeopardy.common :refer [page]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]))

(defn home []
  (page
   [:body
    [:h1 "Jeopardy"]

    [:form {:action "/games"
            :method :post}
     [:div.mode
      [:div
       [:input#endless {:type :radio
                        :name :mode
                        :value :endless
                        :checked true}]
       [:label {:for :endless} "Endless"]
       [:p {:style "margin-left: 2rem"}
        [:i "An infinite stream of random questions"]]]

      [:div
       [:input#endless-categories {:type :radio
                                   :name :mode
                                   :value :endless-categories}]
       [:label {:for :endless} "Endless Categories"]
       [:p {:style "margin-left: 2rem"}
        [:i "An infinite stream of random questions, organized by category"]]]]
     (anti-forgery-field)
     [:button "New game"]]]))
