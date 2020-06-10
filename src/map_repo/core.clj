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
  (map :name (response-raise (org-repos org {:all-pages true}))))

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

(defn process-repo [run-id org message command repo-name]
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
              (commit directory message)
              (push directory branch-name)
              {:changes? true
               :success? true
               :link (:html_url
                       (response-raise
                         (create-pull
                           org repo-name message "master" branch-name {:body message})))})
          {:changes? false
           :success? true}))
      (catch Exception e {:success? false :message (.getMessage e)}))))

(defn format-results [results]
  (->> results
       (sort-by :repo-name)
       (sort-by :changes?)
       (sort-by #(not (:success? %)))
       (map #(str (:repo-name %)
                  ": "
                  (cond
                    (:changes? %) (str  "PR created - " (:link %))
                    (:success? %) "no changes"
                    :else (str  "failed: " (:message %)))))
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn usage [options-summary]
  (->> ["Apply a command to a sequence of Github repositories and turn changes into pull requests."
        ""
        "Usage: map-repo [OPTIONS] COMMAND..."
        ""
        "Available OPTIONS:"
        options-summary
        ""
        "COMMAND... will be executed in a shell."]
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
      (and (not-empty arguments)
           (every? (set (keys options)) [:org :pattern :message]))
      {:command arguments :options options}
      ; Summary
      :else
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn run [run-id org repo-pattern message command]
  (let [repos (filter-repos (get-repos org) repo-pattern)
        results (map (partial process-repo run-id org message command) repos)]
    (println (format-results results))
    (shutdown-agents)))

(defn -main [& args]
  (let [{:keys [command options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (let [token (System/getenv "GITHUB_TOKEN")
            run-id (gen-id)]
        (if-not token
          (exit 1 "Please set the GITHUB_TOKEN environment variable")
          (with-defaults {:oauth-token token}
            (println "Run id" run-id)
            (run run-id (:org options) (:pattern options) (:message options) command)))))))
