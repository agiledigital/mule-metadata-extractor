(ns mule-preview.tools.image-extractor.copying
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.xml :as xml]
            [mule-preview.tools.zip-utils :as zu]
            [clj-xpath.core :as xp])
  (:import [java.util.zip ZipInputStream]
           [java.io FileOutputStream])
  (:use [mule-preview.tools.shared])
  (:gen-class))

(def images-jar-url-prefix
  "The prefix of a file URL that designates that the file should be read from the shared
   images jar, rather than from the plugin itself."
  "images:/")

(defn copy-file-from-images-jar [image-jar image-url target-file]
  "In Anypoint Studio 7, plugins can use image paths prefixed with 'images/'.
   These paths refer to a specific jar that contains shared image files.
   This function extracts images from that jar.
   Will try to extract the high DPI version if available."
  (let [path-in-jar (string/replace-first image-url images-jar-url-prefix "images/")
        high-res-path (string/replace path-in-jar #"\.png$" "@2x.png")]
    (cond (zu/zip-contains-file image-jar high-res-path) (zu/copy-file-from-zip image-jar high-res-path target-file)
          (zu/zip-contains-file image-jar path-in-jar) (zu/copy-file-from-zip image-jar path-in-jar target-file)
          :else (println "Warning: Could not find [" high-res-path "] or [ " high-res-path "] in shared images jar [" image-jar "]"))))

(defn copy-image-from-plugin [plugin-path sub-path read-fn copy-fn]
  "Given a path to a plugin, extract the mapping for a particular sub path of the plugin
   Uses the given read-fn to extract the XML data"
  (let [target-file-contents (read-fn plugin-path sub-path)
        parsed-xml (xml-string-to-xml target-file-contents)
        widgets (:content parsed-xml)
        filtered-widgets (filter #(and
                                   (mule-widget-tags (:tag %))
                                   (not-empty (-> % :attrs :image))) widgets)]
    (map #(copy-fn plugin-path (-> % :attrs :image)) filtered-widgets)))

(defn copy-images-from-plugin [plugin-path sub-paths read-fn copy-fn]
  "Given a path to a plugin, extract the mapping for a list of sub paths
   Uses the given read-fn to extract the XML data"
  (println "Copying images from valid plugin [" (.getAbsolutePath plugin-path) "]")
  (flatten (map #(copy-image-from-plugin plugin-path % read-fn copy-fn) sub-paths)))

(defn process-plugins [file-list read-fn copy-fn]
  "Processes a list of files and returns a merged map of widget definitions"
  (println "Scanning plugins...")
  (let
   [extracted-definitions (map #(assoc {}
                                       :file %
                                       :definitions (extract-widget-definition (read-fn % "plugin.xml"))) file-list)
    valid-definitions (filter #(not (empty? (:definitions %))) extracted-definitions)]
    (flatten (map #(copy-images-from-plugin
                    (-> % :file)
                    (map extract-subpaths-from-definition (-> % :definitions))
                    read-fn copy-fn) valid-definitions))))

(defn zip-copy-fn [image-jar target-directory jar-path sub-path]
  "A read function for use with process-plugins and jar files"
  (let [target-file (io/file target-directory (filename sub-path))]
    (cond
      (string/starts-with? sub-path images-jar-url-prefix) (copy-file-from-images-jar image-jar sub-path target-file)
      (zu/zip-contains-file jar-path sub-path) (zu/copy-file-from-zip jar-path sub-path target-file)
      :else (println "Warning: Image file [" sub-path "] not found in [" jar-path "]"))))

(defn raw-copy-fn [image-jar target-dirctory base-path sub-path]
  "A read function for use with process-plugins and raw (already extracted) plugins"
  (let [source-file (concat-paths base-path sub-path)
        target-file (io/file target-dirctory (filename sub-path))]
    (cond
      (string/starts-with? sub-path images-jar-url-prefix) (copy-file-from-images-jar image-jar sub-path target-file)
      (.exists source-file) (io/copy (concat-paths base-path sub-path) target-file)
      :else (println "Warning: Image file [" sub-path "] not found at [" source-file "]"))))

(defn find-image-jar [root-dir]
  "Finds the highest version images jar (if any) in the root dir and returns it"
  (let [[images-jars] (scan-for-files root-dir [images-jar-regex])
        highest-version-jar (sort-by #(.getName %) images-jars)]
    (last highest-version-jar)))

(defn scan-directory-for-plugins [root-dir output-dir]
  "Walks the given root dir looking for valid Mule widget plugins,
  for each valid plugin, it will copy all it's associated images into the output dir"
  (let [image-jar (find-image-jar root-dir)
        [raw-plugins jars] (scan-for-files root-dir [raw-filename-regex jar-filename-regex])
        jars-with-plugins (filter #(zu/zip-contains-file % "plugin.xml") jars)
        jar-scan-output (process-plugins jars-with-plugins zip-read-fn #(zip-copy-fn image-jar output-dir %1 %2))
        raw-scan-output (process-plugins raw-plugins raw-read-fn #(raw-copy-fn image-jar output-dir %1 %2))]
    (doall jar-scan-output)
    (doall raw-scan-output)
    (println "Successfully wrote images to [" output-dir "]")))
