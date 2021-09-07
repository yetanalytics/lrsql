(ns lrsql.admin.interceptors.ui
  (:require [ring.util.response :as resp]))

(defn admin-ui-redirect
  "Handler function to redirect to the admin ui"
  [_]
  (resp/redirect "/admin/index.html"))
