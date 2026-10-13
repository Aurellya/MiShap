#!/usr/bin/env bash
# usage: scripts/failure-tests.sh <v1|v3>
# Needs the 3 generated services running (Order 8081, Payment 8082, Inventory 8083).

set -u
ORDER=localhost:8081; PAY=localhost:8082; INV=localhost:8083
fails=0

reset() {
  for h in $ORDER $PAY $INV; do curl -s -X POST $h/admin/reset > /dev/null; done
  sleep 1
}

field() { curl -s "$1/state" | grep -o "\"$2\":\"\?[A-Za-z0-9_]*" | head -1 | sed 's/.*://; s/"//'; }

expect() {  # name actual expected
  if [ "$2" = "$3" ]; then echo "PASS  $1 ($2)"; else echo "FAIL  $1: got $2, expected $3"; fails=1; fi
}

case "${1:-}" in
  v1) want_charges=2; want_pay=CHARGED ;;
  v3) want_charges=1; want_pay=REFUNDED ;;
  *) echo "usage: $0 <v1|v3>"; exit 2 ;;
esac

# Test 1: duplicate Charge (mishap: NoDoubleCharge)
reset
curl -s -X POST $ORDER/messages/Start > /dev/null; sleep 2
curl -s -X POST $PAY/messages/Charge  > /dev/null; sleep 1
expect "duplicate Charge -> charges" "$(field $PAY charges)" "$([ $1 = v1 ] && echo 2 || echo 1)"

# Test 2: late PaymentOk, then timeout (mishap: NoPaidCancelledWithoutRefund)
reset
curl -s -X POST $ORDER/admin/drop/PaymentOk > /dev/null # the reply never reaches Order
curl -s -X POST $ORDER/messages/Start > /dev/null; sleep 2
curl -s -X POST $ORDER/admin/timeout  > /dev/null; sleep 2
expect "late reply + timeout -> Order"   "$(field $ORDER state)" "CANCELLED"
expect "late reply + timeout -> Payment" "$(field $PAY state)"   "$want_pay"

exit $fails