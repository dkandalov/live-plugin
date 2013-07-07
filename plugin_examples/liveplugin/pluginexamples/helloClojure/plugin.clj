(ns plugin.clj
  (:use clojure.contrib.import-static)
  (:require [clojure.string :as string])
  (:import [liveplugin PluginUtil])
  (:import [groovy.lang Closure]))
(import-static liveplugin.PluginUtil show registerAction runDocumentWriteAction currentEditorIn)

(defn as-groovy-closure [f]
  (proxy [Closure] [""]
    (call [arg] (f arg))))

(defn insert-new-line [project document]
  (let [caret-model (. (currentEditorIn project) getCaretModel)
        offset (.. caret-model getOffset)
        current-line (.. caret-model getLogicalPosition line)
        line-start-offset (. document getLineStartOffset current-line)]
    (. document insertString line-start-offset "\n")
    (. caret-model moveToOffset (+ offset 1))))

(defn insert-new-line-write-action [action-event]
  (let [project (. action-event getProject)]
    (runDocumentWriteAction project (as-groovy-closure #(insert-new-line project %)))))

(defn insert-new-line-action [] (as-groovy-closure insert-new-line-write-action))

(registerAction "InsertNewLineAbove" "alt shift ENTER" (insert-new-line-action))
(show "Loaded 'InsertNewLineAbove' action<br/>Use 'Alt+Shift+Enter' to run it")


(show (string/join "" ["Implicit variables:<br/>"
                       "project = " *project* "<br/>"
                       "isIdeStartup = " *isIdeStartup* "<br/>"
                       "pluginPath = " *pluginPath*]))