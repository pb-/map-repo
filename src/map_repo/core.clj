(ns map-repo.core
  (:require [tentacles.repos :as r]
            [clojure.java.shell :refer [sh]]
            [clojure.string :refer [split]]))

(def ^:dynamic *run-id* nil)

(defn raise-failure [result]
  (if (pos? (:exit result))
    (throw (Exception. (:err result)))
    result))

(defn get-repos [org]
  ["asfd"])

(defn sh-raise [& args]
  (raise-failure (apply sh args)))

(defn clone-repo [url directory]
  (sh-raise "git" "clone" "--depth" "1" "--quiet" url directory))

(defn has-changes? [repo]
  (pos? (:exit (sh "git" "diff" "--exit-code" :dir repo))))

(defn branch [repo branch-name]
  (sh-raise "git" "checkout" "-b" branch-name :dir repo))

(defn commit [repo message]
  (sh-raise "git" "commit" "-am" message :dir repo))

(defn push [repo branch-name]
  (sh-raise "git" "push" "origin" branch-name :dir repo))

(defn run-command [directory command]
  (apply sh-raise (concat (split command #" ") [:dir directory])))

(defn process-repo [repo-url directory branch-name command]
    (clone-repo repo-url directory)
    (run-command directory command)
    (when (has-changes? directory)
      (branch directory branch-name)
      (commit directory "Automatic commit from map-repo")
      (push directory branch-name))
    )
        ; push here

(comment

  (process-repo "https://github.com/pb-/map-repo" "/tmp/a1" "some-branch" "echo hello >> README.md")

  )

(defn run [run-id org repo-pattern command]
  (let []))

(defn -main []
  (println (map :full_name (r/org-repos "asdf"))))
