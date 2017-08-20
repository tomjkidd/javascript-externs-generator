(ns javascript-externs-generator.ui.views
  (:require [re-frame.core :as rf]
            [clojure.string :as string]
            [re-com.core :as rc]
            [goog.async.Deferred]
            [javascript-externs-generator.ui.handlers :as handlers]
            [javascript-externs-generator.extern :refer [extract-loaded]]))

(defn alert-box []
  (let [alert (rf/subscribe [:alert])]
    (fn []
      (when (not-every? string/blank? [(:heading @alert) (:text @alert)])
        [rc/alert-box
         :id "alert-box"
         :alert-type :danger
         :heading (:heading @alert)
         :body (:text @alert)
         :closeable? true
         :on-close #(rf/dispatch [:close-alert])]))))

(defn extern-output []
  (let [extern (rf/subscribe [:displayed-extern])]
    (fn []
      [rc/input-textarea
       :model extern
       :on-change #()
       :width "100%"
       :rows 25])))

(defn loading-js []
  (let [loading (rf/subscribe [:loading-js])]
    (fn []
      (when @loading
        [rc/throbber :style {:margin "6px"}]))))

(defn loaded-javascripts []
  (let [loaded-urls (rf/subscribe [:loaded-urls])]
    (fn []
      (when (seq @loaded-urls)
        [:div
         [rc/title
          :label "Loaded JavaScripts:"
          :level :level3]
         [:ul
          (for [url @loaded-urls]
            [:li {:key url} url])]]))))

(defn externed-namespaces []
  (let [externed-namespaces (rf/subscribe [:externed-namespaces])
        current-namespace (rf/subscribe [:current-namespace])]
    (fn []
      (when (seq @externed-namespaces)
        [:div
         [rc/title
          :label "Externed Namespaces:"
          :level :level3]
         [rc/horizontal-tabs
          :tabs (for [namespace (keys @externed-namespaces)]
                  {:id    namespace
                   :label namespace})
          :model current-namespace
          :on-change #(rf/dispatch [:show-namespace %])]
         [extern-output]]))))

(defn load-js []
  (let [url-text (rf/subscribe [:url-text])]
    (fn []
      [:div
       [rc/title
        :label "Load your JavaScript file:"
        :level :level3]
       [rc/h-box
        :gap "5px"
        :children [[rc/input-text
                    :model url-text
                    :on-change #(rf/dispatch [:url-text-change %])
                    :placeholder "https://code.jquery.com/jquery-1.9.1.js"
                    :width "700px"
                    :attr {:id "js-url"}]
                   [loading-js]]]
       [rc/button
        :label "Load"
        :on-click #(rf/dispatch [:load-script])]])))

(defn generate-externs []
  (let [namespace-text (rf/subscribe [:namespace-text])
        loaded-urls (rf/subscribe [:loaded-urls])]
    (fn []
      (when (seq @loaded-urls)
        [:div
         [rc/title
          :label "Enter the JavaScript object you want to extern:"
          :level :level3]
         [rc/input-text
          :model namespace-text
          :on-change #(rf/dispatch [:namespace-text-change %])
          :placeholder "jQuery"
          :attr {:id "extern-namespace"}]
         [rc/button
          :label "Extern!"
          :disabled? (empty? @loaded-urls)
          :on-click #(rf/dispatch [:generate-extern])]]))))

(defn extern-generator []
  [rc/v-box
   :gap "10px"
   :margin "20px"
   :width "50%"
   :children [[rc/title
               :label "JavaScript Externs Generator"
               :underline? true
               :level :level1]

              [alert-box]

              [load-js]

              [loaded-javascripts]

              [generate-externs]

              [externed-namespaces]]])

(defn intercept
  [x]
  (.warn js/console x))

(defn ->deferred
  [x]
  (goog.async.Deferred/when
      x
    identity))

(defn display-errors
  [acc]
  (.error js/console ":errors detected")
  (.error js/console (clj->js (:errors acc)))
  (.error js/console (clj->js acc)))

(defn display-success
  [acc]
  (.log js/console ":no errors, yay")
  (.log js/console (:extern acc))
  (.log js/console (clj->js acc)))

(defn pipeline-parse-file-list-text
  [{:keys [file-list-text] :as acc}]
  (assoc acc :file-list (remove
                         string/blank?
                         (mapv string/trim (string/split file-list-text \newline)))))

(defn pipeline-load-js-files
  "Attempt to load the file-list files sequentially, capturing any load errors."
  [acc]
  (goog.async.Deferred/when
      acc
    (fn [{:keys [file-list] :as acc}]
      (let [load-js-deferreds (mapv #(handlers/load-script
                                      %
                                      (fn success [f] f)
                                      (fn error [err] err))
                                    file-list)
            load-all-js-deferred
            (reduce (fn [acc-deferred load-js-deferred]
                      (.addCallbacks
                       load-js-deferred
                       (fn success [f]
                         acc-deferred)
                       (fn error [err]
                         (goog.async.Deferred/when
                             acc-deferred
                           (fn [acc]
                             (update-in acc [:errors] conj (-> err .-message)))))))
                    (->deferred acc)
                    load-js-deferreds)]
        load-all-js-deferred))))

(defn pipeline-generate-extern
  [{:keys [namespace-text] :as acc}]
  ;; NOTE: It is assumed that pipeline-load-js-files was run with no errors
  ;; before this is run. That fn generates no tangible artifacts, and will
  ;; terminate when there are any errors.
  (try
    (assoc acc :extern (handlers/beautify (extract-loaded namespace-text)))
    (catch :default e
      (update-in acc [:errors] conj (handlers/error-string e)))))

(defn generate-extern-pipeline
  [file-list-text namespace-text]
  (goog.async.Deferred/when
      (reduce (fn [deferred-acc cur]
                (.addCallback deferred-acc
                              (fn [acc]
                                ;; wrap to ensure return a deferred acc
                                (if (seq (:errors acc))
                                  (->deferred acc)
                                  (->deferred (cur acc))))))
              (goog.async.Deferred/when
                  {:file-list-text file-list-text
                   :namespace-text namespace-text
                   :errors []}
                  identity)
              [pipeline-parse-file-list-text
               pipeline-load-js-files
               pipeline-generate-extern
               identity])
    (fn [acc]
      (if (seq (:errors acc))
        (display-errors acc)
        (display-success acc)))))

(defn tk-extern-generator []
  (let [file-list-text (rf/subscribe [:file-list-text])
        namespace-text (rf/subscribe [:namespace-text])]
    (fn []
      [rc/v-box
       :gap "10px"
       :margin "20px"
       :width "50%"
       :children [[rc/title
                   :label "JavaScript Externs Generator"
                   :underline? true
                   :level :level1]
                  [:div
                   "Enter a list of url dependencies:"
                   [rc/input-textarea
                    :model file-list-text
                    :attr {:id "file-list"}
                    :on-change #(rf/dispatch [:file-list-text-change %])]]
                  [:div
                   "Enter the JavaScript object you want to extern:"
                   [rc/input-text
                    :model namespace-text
                    :attr {:id "namespace-text"}
                    :on-change #(rf/dispatch [:namespace-text-change %])]]
                  [rc/button
                   :label "Extern!"
                   :on-click #(do
                                (.warn js/console (str "Extern! pressed!" @file-list-text @namespace-text))
                                (generate-extern-pipeline @file-list-text @namespace-text))]]])))
