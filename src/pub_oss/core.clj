; extracts open source code out of source directory generated
; by sourcepkg class of Poky / Open Embedded Linux Disribution
;
; main function, stand-alone app
;
; by Otto Linnemann
; (C) 2014, GNU General Public Licence

(ns pub-oss.core
  (:require
   [clojure.tools.cli :refer [parse-opts]]
   [swank.swank])
  (:use [pub-oss.spdx]
        [pub-oss.source-dir-utils]
        [pub-oss.file-handling]
        [pub-oss.package-addon-info]
        [utils.xml-utils]
        [utils.gen-utils])
  (:gen-class))


(swank.swank/start-server :port 4005)


(defn- create-target-directories
  "helper function to initially create directory structure"
  [target-root-dir-name spdx-sub-dir-name target-source-sub-dir-name]
  (let [target-root-dir (java.io.File. target-root-dir-name)]
    (if-not (.mkdir target-root-dir)
      (println "could not create target directory "
               (.getCanonicalPath target-root-dir) " error, remove it first!")
      (let [spdx-dir-name (str target-root-dir-name "/" spdx-sub-dir-name)
            target-dir-name (str target-root-dir-name "/" target-source-sub-dir-name)]
        (if-not (.mkdir (java.io.File. spdx-dir-name))
          (println (str "could not create directory " spdx-dir-name " error!"))
          (if-not (.mkdir (java.io.File. target-dir-name))
            (println (str "could not create directory " target-dir-name " error!"))
            true))))))


(defn mainloop
  "walks through all package directories within 'oss-main-directory-name'
   and generates and copies spdx container files to '<target-directory-name>/spdx'
   and sources and patch files to appropriate subdirectories."
  [oe-main-directory-name  oss-sub-directory-name  target-sub-directory-name
   & {:keys [blacklist whitelist]}]
  (let [oss-main-directory-name (str oe-main-directory-name "/" oss-sub-directory-name)
        target-directory-name (str oe-main-directory-name "/" target-sub-directory-name)
        packages (get-all-os-packages oe-main-directory-name oss-main-directory-name)
        packages (if blacklist (reduce-pcks-by-blacklist packages blacklist) packages)
        packages (if whitelist (reduce-pcks-by-whitelist-fuzzy packages whitelist) packages)
        spdx-sub-dir-name "spdx"
        target-source-sub-dir-name "sources"
        spx-full-qual-dir-name (str target-directory-name "/" spdx-sub-dir-name)
        target-source-full-qual-dir-name (str target-directory-name "/" target-source-sub-dir-name)]
    (when (create-target-directories
           target-directory-name spdx-sub-dir-name target-source-sub-dir-name)
      (doseq [package packages]
        (let [package-name (:package-name package)
              spdx-full-qual-file-name (str spx-full-qual-dir-name "/" package-name ".spdx")
              sources (read-all-files-within (:source-dir package))
              pkg-veri-code (package-verification-code sources)
              sources-meta (extract-source-dir-meta-data sources)
              pkg-add-on-info (pkg-add-on-info package-name)
              package (merge package sources-meta pkg-add-on-info {:package-verification-code pkg-veri-code})
              spdx-content (apply-with-keywords create-spdx package)]
          (if sources-meta
            (let [package-dir-name (str target-source-full-qual-dir-name "/" package-name)]
              (println "writing package data for " package-name)
              (when-not pkg-add-on-info
                (println "\t\tmissing add on information!"))
              (if (.mkdir (java.io.File. package-dir-name))
                (do
                  (dorun (write-all-files-within sources package-dir-name))
                  (write-xml spdx-full-qual-file-name spdx-content))
                (println "could not write package data for package " package-name " error!")))
            (println "package data " package-name " is malformed error!")))))))


(comment "usage"

  (mainloop
   "../../apps_proc/oe-core"
   "build/tmp-eglibc/deploy/sources/arm-oe-linux-gnueabi"
   "build/tmp-eglibc/deploy/published-oss-image-dir"
   :whitelist "whitelist")


  (def r (get-all-os-packages "oe-main-dir" "../../apps_proc/oe-core/build/tmp-eglibc/deploy/sources/arm-oe-linux-gnueabi"))

  (println r)

  )


; command line interface (leiningen)

(def cli-options
  [["-o" "--oe DIR" "open embedded main directory"
    :default "../../apps_proc/oe-core"
    :validate [#(.isDirectory (java.io.File. %)) "must be directory"]]
   ["-s" "--source-pkg DIR" "directory generated by oe archiver class"
    :default "build/tmp-eglibc/deploy/sources/arm-oe-linux-gnueabi"
    :validate [#(.isDirectory (java.io.File. %)) "must be directory"]]
   ["-p" "--pub DIR" "publishing target url or directory"
    :default "build/tmp-eglibc/deploy/published-oss-image-dir"
    :validate [#(not (.exists (java.io.File. %))) "file or directory must not exist"]]
   ["-b" "--blacklist FILE" "blacklist file with package not to be published"]
   ["-w" "--whitelist FILE" "whitelist file with packages exclusively to be published"]
   ["-h" "--help" "this help string"]
   ])


(defn -main [& args]
  (let [opts (parse-opts args cli-options)
        options (:options opts)
        arguments (:arguments opts)
        summary (:summary opts)
        errors (:errors opts)
        oe-main-dir (:oe options)
        sources-dir (:source-pkg options)
        pub-dir (:pub options)
        blacklist (:blacklist options)
        whitelist (:whitelist options)
        invalid-opts (not-empty errors)
        title-str (str
                   "pub-oss: extracts open source code out of source directory generated\n"
                   "         by sourcepkg class of Poky / Open Embedded Linux Disribution\n")
        start-msg-fmt (str
                       "starting application with\n"
                       "\t      open embedded main directory: %s\n"
                       "\t  directory generated by sourcepkg: %s\n"
                       "\tpublishing target url or directory: %s\n"
                       "\tblacklist: %s\n"
                       "\twhitelist: %s\n")
        start-msg (format start-msg-fmt oe-main-dir sources-dir pub-dir blacklist whitelist)]
    (println title-str)
    (when (or (:help options) invalid-opts)
      (println "  Invocation:\n")
      (println summary)
      (System/exit -1))
    (if invalid-opts
      (println errors)
      (do
        (println start-msg)
        (mainloop oe-main-dir sources-dir pub-dir
                  :blacklist blacklist
                  :whitelist whitelist)
        (System/exit -1)))))

