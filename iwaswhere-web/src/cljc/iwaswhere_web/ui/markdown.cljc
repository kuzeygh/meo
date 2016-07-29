(ns iwaswhere-web.ui.markdown
  "This namespace holds the fucntions for rendering the text (markdown) content of a journal entry.
  This includes both a properly styled element for static content and the edit-mode view, with
  autosuggestions for tags and mentions."
  (:require [markdown.core :as md]
            [clojure.string :as s]
            #?(:cljs [reagent.core :as rc])))

(def tag-char-class "[\\w\\-\\u00C0-\\u017F]")

(defn hashtags-replacer
  "Replaces hashtags in entry text. Depending on show-hashtags? switch either displays
  the hashtag or not. Creates link for each hashtag, which opens iWasWhere in new tab,
  with the filter set to the clicked hashtag."
  [show-hashtags?]
  (fn [acc hashtag]
    (let [f-hashtag (if show-hashtags? hashtag (subs hashtag 1))
          with-link (str " <a href='/#" hashtag "'>" f-hashtag "</a>")]
      (s/replace acc (re-pattern (str "[^*]" hashtag "(?!" tag-char-class ")(?![`)])")) with-link))))

(defn mentions-replacer
  "Replaces mentions in entry text."
  [show-hashtags?]
  (fn [acc mention]
    (let [f-mention (if show-hashtags? mention (subs mention 1))
          with-link (str " <a class='mention-link' href='/#" mention "'>" f-mention "</a>")]
      (s/replace acc (re-pattern (str mention "(?!" tag-char-class ")")) with-link))))

(defn reducer
  "Generic reducer, allows calling specified function for each item in the collection."
  [text coll fun]
  (reduce fun text coll))

(defn markdown-render
  "Renders a markdown div using :dangerouslySetInnerHTML. Not that dangerous
   here since application is only running locally, so in doubt we could only
   harm ourselves. Returns nil when entry does not contain markdown text."
  [entry show-hashtags?]
  (let [first-line-only #?(:clj  (atom true)
                           :cljs (rc/atom true))]
    (fn [entry show-hashtags?]
      (when-let [md-string (:md entry)]
        (let [is-comment? (:comment-for entry)
              lines (s/split-lines md-string)
              shortened? (and @first-line-only is-comment? (> (count lines) 1))
              md-string (if shortened? (first lines) md-string)
              formatted-md
              (-> md-string
                  (reducer (:tags entry) (hashtags-replacer show-hashtags?))
                  (reducer (:mentions entry) (mentions-replacer show-hashtags?)))
              html #?(:clj  (md/md-to-html-string formatted-md)
                      :cljs (md/md->html formatted-md))]
          [:div
           [:div {:dangerouslySetInnerHTML {:__html html}}]
           (when shortened?
             [:span.fa.fa-ellipsis-h.more
              {:on-mouse-enter #(swap! first-line-only not)}])])))))
