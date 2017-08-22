(ns javascript-externs-generator.ui.views
  (:require [re-frame.core :as rf]
            [clojure.string :as string]
            [re-com.core :as rc]
            [goog.async.Deferred]
            [javascript-externs-generator.ui.handlers :as handlers]
            [javascript-externs-generator.extern :refer [extract-loaded-remix]]))

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

(defn copy-to-clipboard
  "Copy the textarea identified by `textarea-selector` to the clipboard
  Returns true if copied, else false"
  [textarea-selector]
  (let [textarea (.querySelector js/document textarea-selector)
        _        (.select textarea)
        succeed? (.execCommand js/document "copy")]
    (.blur textarea)
    succeed?))

(defn externed-output []
  (let [extern (rf/subscribe [:externed-output])]
    (fn []
      [:div
       [:div
        {:class "h3 instruction success"}
        "Extern Output:"]
       [:div
        {:style {:margin-bottom "10px"}}
        (when-not (string/blank? @extern)
          [rc/button
           :label "To Clipboard"
           :on-click #(.log js/console (if (copy-to-clipboard "#extern-textarea")
                                         "copied to clipboard"
                                         "not copied to clipboard"))])]
       [rc/input-textarea
        :attr {:id "extern-textarea"}
        :style {:margin-bottom "10px"}
        :model extern
        :on-change #()
        :width "100%"
        :rows 25]])))

(defn error-output []
  (let [error (rf/subscribe [:error-output])]
    (fn []
      [:div
       [:div
        {:class "h3 instruction error"}
        "Error Output:"]
       [rc/input-textarea
        :model error
        :on-change #()
        :width "100%"
        :rows 25]])))

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
  "Ensure a `goog.async.Deferred` from a possible regular value.
  Optional callback will be called when Deferred is resolved, defaults to identity"
  ([x]
   (->deferred x identity))
  ([x callback]
   (goog.async.Deferred/when x callback)))

(defn display-errors
  [acc]
  (rf/dispatch [:externed-output-change (:extern acc)])
  (rf/dispatch [:error-output-change (string/join \newline (:errors acc))])
  (.error js/console ":errors detected")
  (.error js/console (clj->js (:errors acc)))
  (.error js/console (clj->js acc)))

(defn display-success
  [acc]
  (rf/dispatch [:externed-output-change (:extern acc)])
  (rf/dispatch [:error-output-change (string/join \newline (:errors acc))])
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
  (->deferred
   acc
   (fn [{:keys [file-list] :as acc}]
     (let [load-all-js-deferred
           (reduce (fn [acc-deferred file]
                     (.then
                      acc-deferred
                      (fn [acc]
                        (handlers/load-scripts
                         [file]
                         (fn success [_]
                           (->deferred acc))
                         (fn error [err]
                           (->deferred
                            acc
                            (fn [acc]
                              (update-in acc [:errors] conj (-> err .-message))))))
                        )))
                   (->deferred acc)
                   file-list)]
       load-all-js-deferred))))

(defn pipeline-generate-extern
  [acc]
  ;; NOTE: It is assumed that pipeline-load-js-files was run with no errors
  ;; before this is run. That fn generates no tangible artifacts, and will
  ;; terminate when there are any errors.
  (->deferred
   acc
   (fn [{:keys [file-list namespace-text] :as acc}]
     (try
       (assoc acc :extern (handlers/beautify (extract-loaded-remix namespace-text file-list)))
       (catch :default e
         (update-in acc [:errors] conj (handlers/error-string e)))))))

(defn generate-extern-pipeline
  [file-list-text namespace-text]
  (->deferred
   (reduce (fn [deferred-acc cur]
             (.addCallback deferred-acc
                           (fn [acc]
                             ;; wrap to ensure return a deferred acc
                             (if (seq (:errors acc))
                               (->deferred acc)
                               (->deferred (cur acc))))))
           (->deferred
            {:file-list-text file-list-text
             :namespace-text namespace-text
             :errors         []}
            identity)
           [pipeline-parse-file-list-text
            pipeline-load-js-files
            pipeline-generate-extern
            identity])
   (fn [acc]
     (if (seq (:errors acc))
       (display-errors acc)
       (display-success acc)))))

(defn extern-generator-remix
  "This is an alternate take on the original UI that is optimized for inputing a series
  of files in dependency order all at once."
  []
  (let [file-list-text (rf/subscribe [:file-list-text])
        namespace-text (rf/subscribe [:namespace-text])
        error (rf/subscribe [:error-output])]
    (fn []
      [rc/v-box
       :gap "10px"
       :margin "20px auto"
       :width "70%"
       :max-width "600px"
       :children [[rc/title
                   :label "JavaScript Externs Generator"
                   :underline? true
                   :level :level1]
                  [:div
                   [:div
                    {:class "h3 instruction"}
                    "Enter a list of url dependencies:"]
                   [rc/input-textarea
                    :model file-list-text
                    :attr {:id "file-list"}
                    :on-change #(rf/dispatch [:file-list-text-change %])
                    :width "100%"
                    :rows 7]]
                  [:div
                   [:div
                    {:class "h3 instruction"}
                    "Enter the JavaScript object you want to extern:"]
                   [rc/input-text
                    :model namespace-text
                    :attr {:id "namespace-text"}
                    :on-change #(rf/dispatch [:namespace-text-change %])
                    :width "100%"]]
                  [rc/button
                   :label "Extern!"
                   :on-click #(generate-extern-pipeline @file-list-text @namespace-text)]
                  (if (string/blank? @error)
                    [externed-output]
                    [error-output])
                  ]])))
