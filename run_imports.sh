#!/bin/bash
# Import all sample files through the API
set -e

SAMPLE_DIR="sample_import_files"
BASE_URL="http://localhost:8080/api/v1"
TOKEN=$(cat /tmp/admin_token.txt)

echo "========================================"
echo "  ENTERPRISE IMPORT VALIDATION"
echo "========================================"
echo ""

import_file() {
    local entity_type=$1
    local file=$2
    local mode=$3
    local format="${4:-csv}"

    echo "--- Importing $file ($entity_type, mode=$mode) ---"
    
    RESPONSE=$(curl -s --max-time 120 -X POST "$BASE_URL/import/$entity_type" \
        -H "Authorization: Bearer $TOKEN" \
        -F "file=@$SAMPLE_DIR/$file" \
        -F "format=$format" \
        -F "mode=$mode" 2>&1)
    
    local result=$(echo "$RESPONSE" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data', {})
    total = data.get('totalRecords', 0)
    success = data.get('successCount', 0)
    errors = data.get('errorCount', 0)
    skipped = data.get('skippedCount', 0)
    proc_time = data.get('processingTimeMs', 0)
    err_list = data.get('errors', [])
    print(f'Total: {total} | Success: {success} | Errors: {errors} | Skipped: {skipped} | Time: {proc_time}ms')
    if err_list:
        for e in err_list[:5]:
            print(f'  Error: {e}')
        if len(err_list) > 5:
            print(f'  ... and {len(err_list)-5} more errors')
except Exception as ex:
    print(f'PARSE ERROR: {ex}')
    print(sys.stdin.read()[:500] if hasattr(sys.stdin, 'read') else '')
" 2>&1)
    echo "$result"
    echo ""
}

import_file "customers" "customers.csv" "CONTINUE_ON_ERROR"
import_file "customers" "customers.csv" "VALIDATE_ONLY"
import_file "customers" "customers.csv" "STOP_ON_FIRST_ERROR"

import_file "products" "products.csv" "CONTINUE_ON_ERROR"

import_file "inventory" "inventory.csv" "CONTINUE_ON_ERROR"
import_file "inventory" "inventory.csv" "VALIDATE_ONLY"

import_file "orders" "orders.csv" "CONTINUE_ON_ERROR"

import_file "shipments" "shipments.csv" "CONTINUE_ON_ERROR"

import_file "returns" "returns.csv" "CONTINUE_ON_ERROR"

import_file "suppliers" "suppliers.csv" "CONTINUE_ON_ERROR"

import_file "purchase-orders" "purchase-orders.csv" "CONTINUE_ON_ERROR"

import_file "invoices" "invoices.csv" "CONTINUE_ON_ERROR"

import_file "warehouses" "warehouses.csv" "CONTINUE_ON_ERROR"

# Edge case tests
echo "========== EDGE CASE TESTS =========="
echo ""

import_file "customers" "empty.csv" "CONTINUE_ON_ERROR"
import_file "customers" "header_only.csv" "CONTINUE_ON_ERROR"
import_file "customers" "corrupted.csv" "CONTINUE_ON_ERROR"
import_file "customers" "missing_columns.csv" "CONTINUE_ON_ERROR"
import_file "customers" "extra_columns.csv" "CONTINUE_ON_ERROR"

# Invalid entity type
echo "--- Testing invalid entity type ---"
curl -s --max-time 10 -X POST "$BASE_URL/import/invalid_type" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$SAMPLE_DIR/customers.csv" 2>&1 | python3 -c "
import sys,json; d=json.load(sys.stdin); print(f'Status: success={d.get(\"success\")}, message={d.get(\"message\")[:100]}')" 2>&1
echo ""

# Test import history
echo "========== IMPORT HISTORY =========="
curl -s --max-time 10 "$BASE_URL/import/history" \
    -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json; d=json.load(sys.stdin)
data = d.get('data', {})
content = data.get('content', [])
print(f'Total imports in history: {data.get(\"totalElements\", 0)}')
for h in content[:5]:
    print(f'  {h.get(\"fileName\",\"\")} | {h.get(\"importType\",\"\")} | {h.get(\"status\",\"\")} | S:{h.get(\"successCount\",0)} E:{h.get(\"failedCount\",0)}')
" 2>&1
echo ""

echo "========================================"
echo "  IMPORT VALIDATION COMPLETE"
echo "========================================"
