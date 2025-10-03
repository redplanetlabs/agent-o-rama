(ns doc-tools.public-api
  (:require
   [clj-kondo.core :as kondo]
   [clojure.pprint :as pprint]
   [clojure.string :as str]))

(defn- analyse
  []
  (:analysis
   (kondo/run!
    {:lint   ["src/clj/com/rpl/agent_o_rama.clj"
              "src/java/com/rpl/agentorama/"]
     :config {:skip-lint    true
              :debug        true
              :repro        true
              :copy-configs false
              :parallel     false
              :analysis     {:arglists        true
                             :var-definitions {:shallow true}
                             :var-usages      false
                             :java-class-definitions true
                             :java-member-definitions true}}})))

(defn- public-vars
  [analysis]
  (->> (:var-definitions analysis)
       (filterv (complement :private))
       (mapv #(select-keys % [:ns :name :defined-by :arglist-strs]))))

(defn- public-syms
  [public-vars]
  (-> (group-by :ns public-vars)
      (update-vals #(mapv :name %))))

(defn- agent-o-rama-package?
  [package]
  (= 'com.rpl.agentorama package))

(defn- package-and-class
  [class-name]
  (->> (str/split class-name #"\.")
       ((juxt (comp #(str/join "." %) butlast) last))
       (mapv symbol)
       (zipmap [:package :class])))

(defn- public-java-classes
  [analysis]
  (->> (:java-class-definitions analysis)
       (filterv
        #(or ((:flags %) :public)
             (str/starts-with? (last (str/split (:class %) #"\.")) "I")))
       (mapv (comp package-and-class :class))
       (filterv (comp agent-o-rama-package? :package))))

(defn- public-java-class-syms
  [java-classes]
  (-> (group-by :package java-classes)
      (update-vals #(mapv :class %))))

(defn- public-java-members
  [analysis]
  (->> (:java-member-definitions analysis)
       (mapv #(merge % ((comp package-and-class :class) %)))
       (filterv #(or ((:flags %) :public)
                     (str/starts-with? (name (:class %)) "I")))
       (filterv (comp agent-o-rama-package? :package))
       (mapv
        #(select-keys % [:package :class :name :parameters :type :flags]))))

(defn- public-java-member-syms
  [java-members]
  (-> (group-by (juxt :package :class) java-members)
      (update-vals #(mapv
                     (fn [m]
                       (let [flags (:flags m)]
                         (cond-> (dissoc m :package :class :flags)
                           (flags :field) (assoc :type :field)
                           (flags :method) (assoc :type :method)
                           (flags :static) (assoc :static? true))))
                     %))))


(defn- ppr-str
  [x]
  (with-out-str (pprint/write x :dispatch pprint/code-dispatch)))

(defn dump-public-syms
  []
  (let [analysis (analyse)]
    (spit
     "dev/clojure-public-syms.edn"
     (-> analysis
         public-vars
         public-syms
         ppr-str))
    (spit
     "dev/java-public-classes.edn"
     (-> analysis
         public-java-classes
         public-java-class-syms
         ppr-str))
    (spit
     "dev/java-public-members.edn"
     (-> analysis
         public-java-members
         public-java-member-syms
         ppr-str))))

(comment

  (def analysis (analyse))
  (def publics (public-vars analysis))
  (def public-syms (public-syms publics))
  (def public-syms (public-syms publics))

  (def java-defs
    (select-keys analysis [:java-class-definitions :java-member-definitions]))

  (def public-classes (public-java-classes analysis))
  (def public-class-syms (public-java-class-syms public-classes))

  (def public-members (public-java-members analysis))
  (def public-member-syms (public-java-member-syms public-members))


  (dump-public-syms)
  ;; End of comment
)
