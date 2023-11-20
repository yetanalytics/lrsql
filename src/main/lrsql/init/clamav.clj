(ns lrsql.init.clamav
  "ClamAV virus scanning"
  (:require [clojure.string :as cs]
            [clojure.java.io :as io])
  (:import [xyz.capybara.clamav ClamavClient]
           [xyz.capybara.clamav.commands.scan.result ScanResult ScanResult$OK]))

(defn init-file-scanner
  "Given ClamAV config, creates a client and returns a function that reads in
  the input and scans it with ClamAV.
  Compatible with build-routes' :file-scanner argument"
  [{:keys [clamav-host
           clamav-port]
    :or {clamav-host "localhost"
         clamav-port 3310}}]
  (let [client (new ClamavClient clamav-host clamav-port)]
    (fn [input]
      (with-open [in (io/input-stream input)]
        (let [^ScanResult scan-result (.scan client in)]
          (when-not (instance? ScanResult$OK scan-result)
            (let [virus-list (-> scan-result
                                 bean ;; FIXME: Normal property access?
                                 :foundViruses
                                 (get "stream")
                                 (->> (into [])))]
              {:message (format "Submitted file failed scan. Found: %s"
                                (cs/join ", " virus-list))})))))))
