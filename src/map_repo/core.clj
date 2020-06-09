(ns map-repo.core
  (:require [tentacles.repos :refer [org-repos]]
            [tentacles.core :refer [with-defaults]]
            [tentacles.pulls :refer [create-pull]]
            [clojure.java.shell :refer [sh]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]))

(def cli-options
  [["-h" "--help"]
   ["-o" "--org ORGANIZATION" "Required. The Github organization to operate on."]
   ["-p" "--pattern PATTERN" "Required. Regular expression (Java) against which repositories are matched."
    :parse-fn re-pattern]
   ["-m" "--message MESSAGE" "Required. Message to be used for commits/PRs."]])

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
  (map :name (org-repos org {:all-pages true})))

(defn filter-repos [repos pattern]
  (filter (partial re-find pattern) repos))

(defn repo-url [org repo-name]
  (str "git@github.com:" org "/" repo-name ".git"))

(defn repo-directory [run-id repo-name]
  (str "/tmp/map-repo-" run-id "/" repo-name))

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
  (apply sh-raise (concat command [:dir directory])))

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

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn usage [options-summary]
  (->> ["Apply a command to a sequence of Github repositories and turn changes into pull requests."
        ""
        "Usage: map-repo [options] command"
        ""
        "Options:"
        options-summary
        ""
        "command will be executed in a shell."]
       (string/join \newline)))

(defn validate-args [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      ; Help
      (:help options)
      {:exit-message (usage summary) :ok? true}
      ; Errors
      errors
      {:exit-message (error-msg errors)}
      ; Extra validation
      (every? (set (keys options)) [:org :pattern :message])
      {:action (first arguments) :options options}
      ; Summary
      :else
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(comment

  (format "hey %d" nil)

    (with-defaults {:oauth-token ":trollface:"}
      (response-raise (r/user-repos "pb-")))

    (with-defaults {:oauth-token ":trollface:"}
      (process-repo (gen-id) "pb-" "rm README.md" "map-repo"))

    (with-defaults {:oauth-token ":trollface:"}
      (process-repo (gen-id) "pb-" "ls" "map-repo"))

    (run "123" "asdf" #"^a" "ls")

    (validate-args ["-o" "o" "-p" "p" "asdf"])

  )

(defn run [run-id org repo-pattern command]
  (let [repos (filter-repos (get-repos org) repo-pattern)]
    (println repos)))

(comment
  (run (gen-id) "pb-" ".*" "rm README.md"))

(defn -main [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (println options))))
