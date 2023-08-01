(ns repley.browser-test
  (:require [wally.main :as w]
            [repley.main :as main]
            [repley.repl :as repl]
            [clojure.test :as t :refer [deftest is testing]]
            [clojure.string :as str]))

(defn with-repl [f]
  (let [stop-server (main/start {:http {:port 4444}})]
    (w/with-page (w/make-page {:headless true})
      (try
        (w/navigate "http://localhost:4444/")
        (f)
        (finally
          (repl/clear!)
          (repl/disable-tap-listener!)
          (stop-server))))))

(t/use-fixtures :each with-repl)

(defn eval-in-repl [& code]
  (let [js (str/replace (apply str code) "'" "\\'")]
    (.evaluate (w/get-page) (str "() => _eval('" js "')"))
    (Thread/sleep 10)))

(def sample-csv-url "https://media.githubusercontent.com/media/datablist/sample-csv-files/main/files/customers/customers-100.csv")

(deftest csv-table-test
  (eval-in-repl "(def url \"" sample-csv-url "\")")
  (is (= (-> 'user ns-publics (get 'url) deref) sample-csv-url))
  (eval-in-repl "(require '[clojure.data.csv :as csv]) (def customers (csv/read-csv (clojure.java.io/reader url)))")
  (Thread/sleep 1000) ;; wait for HTTP load and CSV parsing to take place
  (let [customers (-> 'user ns-publics (get 'customers) deref)]
    (is (= 101 (count customers))))
  (w/click (w/find-one-by-text :.tab "Table"))
  (w/wait "table.table")
  (is (= 20 (w/count* (w/query "table.table tbody tr"))))
  (w/fill [:evaluation "input"] "Fiji")
  (Thread/sleep 500)
  (is (= 1 (w/count* (w/query "table.table tbody tr")))))

(defn breadcrumbs []
  (Thread/sleep 100)
  (.evaluate (w/get-page) "() => { var s = ''; document.querySelectorAll('div.breadcrumbs li').forEach(e=>s+=e.innerText+';'); return s; }"))

(deftest breadcrumbs-test
  ;; evaluate a nested map structure
  (eval-in-repl "{:hello {:there {:my [\"friend\" {:name \"Rich\"} \"!\"]}}}")

  ;; descend deeper into result
  (w/click (w/find-one-by-text "td" ":hello"))
  (is (= (breadcrumbs) ";:hello;"))
  (w/click (w/find-one-by-text "td" ":there"))
  (is (= (breadcrumbs) ";:hello;:there;"))
  (w/click (w/find-one-by-text "td" ":my"))
  (is (= (breadcrumbs) ";:hello;:there;:my;"))
  ;;(w/click "") ;; drill down from table
  (w/click (w/find-one-by-text "span" "\"friend\""))
  (is (= (breadcrumbs) ";:hello;:there;:my;0;"))

  ;; drill back to :hello
  (w/click (w/find-one-by-text "li" ":hello"))
  (is (= (breadcrumbs) ";:hello;"))

  ;; drill back to home, breadcrumbs disappear
  (w/click "div.breadcrumbs li:nth-child(1)")
  (is (= (breadcrumbs) "")))

(deftest table-data-escaped
  (eval-in-repl "{:foo 1 :bar \"<script>alert(2)</script>\"}")
  (w/click (w/find-one-by-text :.tab "Table"))
  ;;(.pause (w/get-page))
  (is (w/find-one-by-text "td" "<script>alert(2)</script>")))

(defn evaluation-count []
  (w/count* (w/-query "div.evaluation")))

(deftest tap-listen-option
  (w/click ".options")
  (is (not (.isChecked (w/-query ".tap-listener"))))
  (testing "tap> does not send result to REPL"
    (tap> 1)
    (Thread/sleep 50)
    (is (zero? (evaluation-count))))
  (testing "enabling tap> listener"
    (w/click ".tap-listener")
    (Thread/sleep 50)
    (tap> 2)
    (Thread/sleep 50)
    (is (= 1 (evaluation-count)))
    (is (= "2" (w/text-content "div.evaluation div.edn"))))
  (testing "disabling again"
    (w/click ".tap-listener")
    (Thread/sleep 50)
    (tap> 3)
    (Thread/sleep 50)
    (is (= 1 (evaluation-count)))))

(deftest clear-results
  (eval-in-repl "1")
  (eval-in-repl "2")
  (eval-in-repl "3")
  (is (= 3 (evaluation-count)))
  (w/click ".options")
  (w/click ".clear-results")
  (Thread/sleep 10)
  (is (zero? (evaluation-count))))
