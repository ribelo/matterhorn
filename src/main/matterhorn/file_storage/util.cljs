(ns matterhorn.file-storage.util
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [ribelo.doxa :as dx]
   [matterhorn.transit :refer [write-transit read-transit]]
   ["process" :as process]
   ["fs" :as fs]))

(def ^:dynamic *data-path* (enc/path (-> process .-env .-HOME) ".config" "matterhorn" "_data"))

(defn- keyword->string [nk]
  (let [ns (namespace nk)
        k  (name nk)]
    (str ns "_" k)))

(defn- store->file-name [store]
  (enc/cond
    (keyword? store) (enc/format "%s/%s.transit" *data-path* (keyword->string store))
    (vector?  store) (enc/format "%s/%s.transit" *data-path*
                                 (enc/str-join-once "__" (mapv keyword->string store)))))

(defn -write-to-file [file-name data]
  (when-not (.existsSync fs *data-path*) (.mkdirSync fs *data-path* #js {:recursive true}))
  (.writeFile fs file-name (write-transit data)
              (fn [err]
                (if err
                  (timbre/error err)
                  (timbre/infof "successful write %s" file-name)))))


(defn -read-file [file-name]
  (if (.existsSync fs file-name)
    (read-transit (.readFileSync fs file-name))
    (timbre/errorf "file %s does not exist" file-name)))

(defn -read-store-from-file [store]
  (-read-file (store->file-name store)))

(defn freeze-dx [k db]
  (if db
    (let [file-name (store->file-name k)]
      (-write-to-file file-name db))
    (timbre/warnf "can't freeze dx: %s, db is empty" k)))

(defn freeze-store [& ks]
  (doseq [k ks]
    (freeze-dx k (some-> (dx/get-dx k) deref))))

(defn thaw-store [& ks]
  (doseq [k ks]
    (if-let [data (-read-store-from-file k)]
      (dx/with-dx! [db_ k]
        (swap! db_ (fn [] data)))
      (timbre/errorf "can't thaw %s" k))))
