(defproject mule-metadata-extractor "1.0.13"
  :description "Command line tools for processing Mule XML files"
  :url "https://github.com/NoxHarmonium/mule-preview"
  :license {:name "Apache License"
            :url  "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.cli "0.4.2"]
                 [com.github.kyleburton/clj-xpath "1.4.11"]
                 [cheshire "5.8.1"]]
  :main ^:skip-aot mule-preview.tools.core
  :target-path "target/%s"
  :plugins [[io.taylorwood/lein-native-image "0.3.1"]
            [lein-project-version "0.1.0"]]
  :native-image {:opts ["--no-server"
                        "--report-unsupported-elements-at-runtime"
                        "--initialize-at-build-time"
                        "--verbose"]}
  :profiles {:uberjar {:aot :all
                       :native-image {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}})
