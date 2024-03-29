(ns app.sudoku
  #?(:clj (:import [de.sfuhrm.sudoku Creator]))

  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [clojure.string :as string]
            #?(:clj [duratom.core :refer [duratom]])))


;;; logic
;;;;;;;;;

(defn map2dv [f]
  (mapv #(mapv (partial f %) (range 9)) (range 9)))

#?(:clj (defn make-sudoku [clear-count]
          ;   VERY_EASY: more than 50 given numbers, remove less than 31 numbers
          ;   EASY: 36-49 given numbers, remove 32-45 numbers
          ;   MEDIUM: 32-35 given numbers, remove 46-49 numbers
          ;   HARD: 28-31 given numbers, remove 50-53 numbers
          ;   EXPERT: 22-27 given numbers, remove 54-59 numbers
          (let [matrix (Creator/createFull)
                riddle (Creator/createRiddle matrix clear-count)]
            (map2dv (fn [x y]
                      (if (.getWritable riddle y x) #{}
                          (str (.get riddle y x))))))))

(defn toggle-in-set [v n]
  (cond
    (not (set? v)) #{n}
    (contains? v n) (disj v n)
    :else (conj v n)))

(defn set-notes [v]
  (if (number? v) #{v} v))
  ; (and (not notes) (set? v) (= 1 (count v))) (first v)


;;; app state
;;;;;;;;;;;;;

; dynamic def for sharing state between server/client
(e/def ^:dynamic state)
(e/def ^:dynamic code)

#?(:clj (defn make-atom [init-val]
          (let [state-file-path (System/getenv "SUDOKU_STATE_FILE")]
            (if state-file-path
              (do
                (println "storing state in" state-file-path)
                (duratom :local-file
                         :file-path state-file-path
                         :init init-val))
              (atom init-val)))))

; server-side atom for actual storage
(def !state #?(:clj (make-atom {})))

(defmacro swap-state! [& args]
  `(swap! !state update-in [(e/client code)] ~@args))


;;; view stuff
;;;;;;;;;;;;;;

(defn i->num [v]
  (inc (+ v (quot v 3))))

(e/defn Cell [pos f !f]
  (let [val (get-in state pos)
        typ (cond
              (string? val) :fixed
              (number? val) :single
              (empty? val) :empty
              :else :multi)]
    (dom/div (dom/props {:tabIndex (case typ :fixed -1 0)
                         :class ["cell" (str "cell-" (name typ)) (when (= f pos) "focused")]
                         :style {:grid-column (i->num (first pos))
                                 :grid-row (i->num (last pos))}})
             (dom/text (case typ
                        :fixed val
                        :single val
                        (string/join " " (sort val))))
             (when-not (= typ :fixed)
               (dom/on "focus"
                 (e/fn [_] (reset! !f pos)))
               (dom/on "click"
                 (e/fn [e] (.stopPropagation e)))
               (dom/on "keydown"
                 (e/fn [e]
                   (cond
                     (contains? #{"Backspace" "Clear" "Delete"} (.-key e))
                     (e/server (swap-state! update-in pos (constantly #{})))

                     (string/starts-with? (.-code e) "Digit")
                     (let [n (-> e .-code last int)]
                       (when (<= 1 n 9)
                         (.preventDefault e)
                         (if (.-shiftKey e)
                           (e/server (swap-state! update-in pos toggle-in-set n))
                           (e/server (swap-state! update-in pos (constantly n)))))))))))))

(e/defn Keyboard [pos]
  (let [v (get-in state pos)
        !notes (atom (and (set? v) (< 0 (count v))))
        notes (e/watch !notes)]
    (dom/div
      (dom/props {:class "keyboard"})
      (dom/on "click"
         (e/fn [e] (.stopPropagation e)))

      (e/for [n (range 1 10)]
        (ui/button (e/fn []
                     (e/server (if notes
                                 (swap-state! update-in pos toggle-in-set n)
                                 (swap-state! update-in pos (constantly n)))))
                   (dom/text n)))
      (ui/button (e/fn [] (e/server (swap-state! update-in pos (constantly #{}))))
                 (dom/props {:class "clear"})
                 (dom/text "clear"))

      (ui/button
        (e/fn []
          (when-not notes (e/server (swap-state! update-in pos set-notes)))
          (swap! !notes not))
        (dom/props {:class ["notes" (when notes "notes-active")]})
        (dom/text "notes")))))

(e/defn show-login []
  (let [!password (atom "")
        !out (atom (let [path (. js/location -pathname)]
                     (when (> (count path) 1) (subs path 1))))
        out (e/watch !out)]
    (let [path (str "/" out)]
      (. js/history pushState path "" path))
    (if out
      (dom/div
        (dom/span
          (dom/text "game code: ")
          (dom/code (dom/text out))
          (dom/text " "))

        (ui/button
          (e/fn [] (reset! !out nil))
          (dom/text "leave")))
      (dom/form
        (dom/on "submit" (e/fn [e] (.preventDefault e)))
        (dom/p (dom/text "enter a game code to join or create a game:"))
        (ui/input (e/watch !password) (e/fn [v] (reset! !password v)))
        (ui/button
          (e/fn []
            (when-not (empty? @!password)
              (reset! !out @!password)))
          (dom/text "start"))))
    out))

(e/defn Game []
  (let [!focus (atom nil)
        focus (e/watch !focus)]
    (dom/on "click"
      (e/fn [_] (reset! !focus nil)))

    (dom/p (dom/text "click a cell and use the keypad or use the number keys and shift to enter numbers and notes."))
    (ui/button
      (e/fn []
        (when (.confirm js/window "Really clear this game and restart?")
          (e/server (swap-state! (constantly nil)))))

      (dom/text "regenerate"))

    (dom/div
      (dom/props {:class "sudoku"})
      (e/for [y (range 9) x (range 9)]
        (Cell. [x y] focus !focus)))

    (when focus
      (dom/hr)
      (Keyboard. focus))))

(e/defn NewGame []
  (let [!difficulty (atom 31)
        difficulty (e/watch !difficulty)]

    (dom/div
      (dom/label (dom/props {:for "difficulty"}) (dom/text "difficulty:"))
      (ui/range difficulty (e/fn [v] (reset! !difficulty v))
                (dom/props {:id "difficulty" :min 1 :max 59})))

    (ui/button (e/fn []
                 (e/server (swap-state! (constantly (make-sudoku difficulty)))))
               (dom/text "generate"))))

(e/defn App []
  (e/client
    (dom/link (dom/props {:rel :stylesheet :href "/app.css"}))
    (dom/h1 (dom/text "multiplayer sudoku game"))
    (let [game-code (show-login.)]
      (when game-code
        (binding [state (e/server (get (e/watch !state) game-code))
                  code game-code]
          (if state
            (Game.)
            (NewGame.)))))))
