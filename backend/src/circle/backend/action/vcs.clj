(ns circle.backend.action.vcs
  "build actions for checking out code."
  (:require [clj-url.core :as url])
  (:use [circle.util.except :only (throw-if-not)])
  (:use [circle.backend.action :only (defaction)])
  (:use [clojure.tools.logging :only (infof)])
  (:require [circle.backend.action.bash :as bash])
  (:require [circle.sh :as sh])
  (:use [circle.backend.action.bash :only (remote-bash-build)])
  (:require [circle.backend.build :as build])
  (:require fs)
  (:use midje.sweet)
  (:use [circle.backend.action.user :only (home-dir)]))

(defn vcs-type
  "returns the VCS type for a given url. Returns one of :git, :hg, :svn or nil, if unknown"
  [url]
  (letfn [(match [re]
            (re-find re url))]
    (cond
     (match #"^https://github.com") :git
     (match #"^git@github.com") :git
     (= (-> url url/parse :protocol) "git") :git
     :else nil)))

(defmulti checkout-impl (fn [{:keys [vcs url path]}]
                          vcs))

(defmethod checkout-impl :git [{:keys [build url path revision]}]
  (throw-if-not (pos? (.length url)) "url must be non-empty")
  (throw-if-not (pos? (.length path)) "path must be non-empty")
  (println "checking out" url " to " path)
  (if revision
    (remote-bash-build build (sh/quasiquote
                              (git clone ~url ~path --no-checkout)
                              (cd ~path)
                              (git checkout ~revision)))
    (remote-bash-build build (sh/quasiquote
                              (git clone ~url ~path --depth 1)))))

(defmethod checkout-impl :default [{:keys [vcs]}]
  (throw (Exception. "don't know how to check out code of type" vcs)))

(defn checkout-dir [b]
  (fs/join (home-dir b) (build/build-name b)))

(defaction checkout []
  {:name "checkout"}
  (fn [build]
    (let [dir (checkout-dir build)
          result (-> (checkout-impl {:build build
                                     :url (-> @build :vcs-url)
                                     :path dir
                                     :vcs (-> @build :vcs-url vcs-type)
                                     :revision (-> @build :vcs-revision)}))]
      result)))