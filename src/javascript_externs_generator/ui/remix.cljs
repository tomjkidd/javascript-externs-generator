(ns javascript-externs-generator.ui.remix
  "Supporting functions to enable bulk-load extern generation"
  (:require
   [clojure.string :as string]
   [javascript-externs-generator.ui.handlers :as handlers]
   [javascript-externs-generator.extern :refer [extract-loaded-remix]]
   [javascript-externs-generator.util :refer [->deferred async-pipeline]]))

(defn parse-file-list-text
  "Turn the newline separated list of files into a clean trimmed list"
  [{:keys [file-list-text] :as acc}]
  (assoc acc :file-list (remove
                         string/blank?
                         (mapv string/trim (string/split file-list-text \newline)))))

(defn load-js-files
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

(defn generate-extern
  "Create the extern string for use with cljsjs/packages"
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
  "Attempt to generate an extern given a file-list and namespace, reporting progress
  in an accumulator hash-map.

  If generation is not successful, the `:errors` key in the accumulator should
  explain why."
  [file-list-text namespace-text fin]
  (async-pipeline
   {:file-list-text file-list-text
    :namespace-text namespace-text
    :errors         []}
   [parse-file-list-text
    load-js-files
    generate-extern
    identity]
   fin))
