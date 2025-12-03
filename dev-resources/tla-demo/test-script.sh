API_USER=my_key
API_PASS=my_secret

BASE_STATEMENT=$(cat <<'EOF'
{
  "actor": { "mbox": "mailto:learner@example.com" },
  "verb": { "id": "http://adlnet.gov/expapi/verbs/experienced" },
  "object": { "id": "http://example.com/activities/sample" }
}
EOF
)

for PORT in 8081 8082 8083; do
  echo "Posting to LRS on port $PORT"
  curl -s -o /dev/null -w "%{http_code}\n" \
    -u "$API_USER:$API_PASS" \
    -H "X-Experience-API-Version: 1.0.3" \
    -H "Content-Type: application/json" \
    -d "$BASE_STATEMENT" \
    "http://localhost:${PORT}/xapi/statements"
done

echo "Statement ID: $STATEMENT_ID"
