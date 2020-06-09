(ns map-repo.core
  (:require [tentacles.repos :as r]
            [tentacles.core :refer [with-defaults]]
            [tentacles.pulls :refer [create-pull]]
            [clojure.java.shell :refer [sh]]
            [clojure.string :refer [split]]))

(def alphabet "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")

(defn gen-id []
  (apply str (take 8 (shuffle (seq alphabet)))))

(defn raise-failure [result]
  (if (pos? (:exit result))
    (throw (Exception. (:err result)))
    result))

(defn sh-raise [& args]
  (raise-failure (apply sh args)))

(defn response-raise [response]
  (if-not (<= 200 (:status response 200) 299)
    (throw
      (Exception. (format "HTTP %d: %s" (:status response) (:message (:body response)))))
    response))

(defn get-repos [org]
  ["asfd"])

(defn repo-url [org repo-name]
  (str "git@github.com:" org "/" repo-name ".git"))

(defn repo-directory [run-id repo-name]
  (str "/tmp/" run-id "/" repo-name))

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

(defn process-repo [run-id org command repo-name]
  (merge
    {:repo-name repo-name}
    (try
      (let [directory (repo-directory run-id repo-name)
            url (repo-url org repo-name)
            branch-name (str "map-repo-" run-id)]
        (clone-repo url directory)
        (run-command directory command)
        (if (has-changes? directory)
          (do (branch directory branch-name)
              (commit directory "Automatic commit from map-repo")
              (push directory branch-name)
              (response-raise
                (create-pull
                  org repo-name "Automatic PR from map-repo" "master" branch-name {:body "Body..."}))
              {:changes? true
               :success? true})
          {:changes? false}))
      (catch Exception e {:success false :message (.getMessage e)}))))


(comment

  (format "hey %d" nil)

    (with-defaults {:oauth-token ":trollface:"}
      (response-raise (r/user-repos "pb-")))

    (with-defaults {:oauth-token ":trollface:"}
      (process-repo (gen-id) "pb-" "rm README.md" "map-repo"))

  )

(defn run [run-id org repo-pattern command]
  (let [repos ["map-repo"]]))

(comment
  (run (gen-id) "pb-" ".*" "rm README.md"))

(defn -main []
  (println (map :full_name (r/org-repos "asdf"))))
