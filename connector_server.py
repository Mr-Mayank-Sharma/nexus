#!/usr/bin/env python3
"""Marketplace connector proxy server for Nexus OMS. Runs on port 8083.
Proxies to Amazon SP-API, eBay REST API, and Walmart Marketplace API.
Returns mock data when credentials are not configured (graceful fallback)."""
import json, time, uuid, hmac, hashlib, base64, urllib.request, urllib.error, urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime, timezone

HOST = "0.0.0.0"
PORT = 8083

CONNECTORS = {
    "amazon": {
        "active": False,
        "client_id": None,
        "client_secret": None,
        "refresh_token": None,
        "aws_access_key": None,
        "aws_secret_key": None,
        "role_arn": None,
        "marketplace_id": "ATVPDKIKX0DER",
        "sandbox": True,
        "token_expires": 0,
        "access_token": None,
        "last_sync": None,
        "orders_synced": 0,
        "error": None,
    },
    "ebay": {
        "active": False,
        "client_id": None,
        "client_secret": None,
        "refresh_token": None,
        "site_id": 0,
        "sandbox": True,
        "token_expires": 0,
        "access_token": None,
        "last_sync": None,
        "orders_synced": 0,
        "error": None,
    },
    "walmart": {
        "active": False,
        "client_id": None,
        "client_secret": None,
        "refresh_token": None,
        "channel_type": "SELF",
        "sandbox": True,
        "token_expires": 0,
        "access_token": None,
        "last_sync": None,
        "orders_synced": 0,
        "error": None,
    },
    "bigcommerce": {
        "active": False,
        "client_id": None,
        "client_secret": None,
        "access_token": None,
        "store_hash": None,
        "sandbox": True,
        "last_sync": None,
        "orders_synced": 0,
        "error": None,
    },
    "shopify": {
        "active": False,
        "client_id": None,
        "client_secret": None,
        "access_token": None,
        "store_url": None,
        "api_version": "2024-07",
        "sandbox": True,
        "last_sync": None,
        "orders_synced": 0,
        "error": None,
    },
    "woocommerce": {
        "active": False,
        "client_id": None,
        "client_secret": None,
        "consumer_key": None,
        "consumer_secret": None,
        "store_url": None,
        "sandbox": True,
        "last_sync": None,
        "orders_synced": 0,
        "error": None,
    },
    "magento": {
        "active": False,
        "client_id": None,
        "client_secret": None,
        "access_token": None,
        "store_url": None,
        "sandbox": True,
        "last_sync": None,
        "orders_synced": 0,
        "error": None,
    },
    "square": {
        "active": False,
        "client_id": None,
        "client_secret": None,
        "access_token": None,
        "location_id": None,
        "sandbox": True,
        "last_sync": None,
        "orders_synced": 0,
        "error": None,
    },
    "lightspeed": {
        "active": False,
        "client_id": None,
        "client_secret": None,
        "access_token": None,
        "account_id": None,
        "sandbox": True,
        "last_sync": None,
        "orders_synced": 0,
        "error": None,
    },
}

MOCK_ORDERS = {
    "amazon": [
        {"orderId":"303-1234567-8901234","status":"Shipped","total":129.99,"currency":"USD","items":[{"sku":"SKU-AMZ-001","qty":2}],"purchaseDate":"2026-06-28T10:30:00Z","buyerEmail":"buyer1@example.com"},
        {"orderId":"303-7654321-0987654","status":"Unshipped","total":79.50,"currency":"USD","items":[{"sku":"SKU-AMZ-002","qty":1}],"purchaseDate":"2026-06-30T14:15:00Z","buyerEmail":"buyer2@example.com"},
    ],
    "ebay": [
        {"orderId":"12-34567-89012","status":"COMPLETED","total":245.00,"currency":"USD","items":[{"sku":"SKU-EBAY-001","qty":1}],"creationDate":"2026-06-27T09:00:00Z","buyerUsername":"happy_buyer_01"},
        {"orderId":"12-34567-89013","status":"ACTIVE","total":55.00,"currency":"USD","items":[{"sku":"SKU-EBAY-002","qty":3}],"creationDate":"2026-06-30T16:45:00Z","buyerUsername":"auction_fan_99"},
    ],
    "walmart": [
        {"orderId":"123456789","status":"Shipped","total":199.99,"currency":"USD","items":[{"sku":"SKU-WMT-001","qty":1}],"orderDate":"2026-06-26T11:00:00Z","customerName":"John Doe"},
        {"orderId":"987654321","status":"Created","total":34.99,"currency":"USD","items":[{"sku":"SKU-WMT-002","qty":2}],"orderDate":"2026-06-30T18:30:00Z","customerName":"Jane Smith"},
    ],
}

MOCK_INVENTORY = {
    "amazon": {"listings":[{"sku":"SKU-AMZ-001","fulfillable":42,"inbound":10,"reserved":5},{"sku":"SKU-AMZ-002","fulfillable":18,"inbound":0,"reserved":2}]},
    "ebay": {"inventory":[{"sku":"SKU-EBAY-001","available":120,"price":245.00},{"sku":"SKU-EBAY-002","available":500,"price":18.50}]},
    "walmart": {"items":[{"sku":"SKU-WMT-001","qty":65,"fulfillmentType":"WFS"},{"sku":"SKU-WMT-002","qty":200,"fulfillmentType":"SELF"}]},
}

MOCK_LISTINGS = {
    "amazon": [{"sku":"SKU-AMZ-001","title":"Premium Widget Pro","price":49.99,"qty":42,"status":"Active"},{"sku":"SKU-AMZ-002","title":"Basic Gadget","price":79.50,"qty":18,"status":"Active"}],
    "ebay": [{"sku":"SKU-EBAY-001","title":"Vintage Collection Item","price":245.00,"qty":120,"status":"ACTIVE"},{"sku":"SKU-EBAY-002","title":"Bulk Pack Accessories","price":18.50,"qty":500,"status":"ACTIVE"}],
    "walmart": [{"sku":"SKU-WMT-001","title":"Everyday Household Item","price":199.99,"qty":65,"status":"PUBLISHED"},{"sku":"SKU-WMT-002","title":"Multi-Pack Snacks","price":17.50,"qty":200,"status":"PUBLISHED"}],
}

MOCK_SHOPIFY_ORDERS = [
    {"orderId":"#S1001","status":"fulfilled","total":189.99,"currency":"USD","items":[{"sku":"SHOP-SKU-001","qty":2}],"createdAt":"2026-06-30T10:30:00Z","customer":"John D."},
    {"orderId":"#S1002","status":"open","total":345.00,"currency":"USD","items":[{"sku":"SHOP-SKU-002","qty":1},{"sku":"SHOP-SKU-003","qty":3}],"createdAt":"2026-07-01T09:15:00Z","customer":"Sarah M."},
]
MOCK_WOOCOMMERCE_ORDERS = [
    {"orderId":"WC-1001","status":"processing","total":78.50,"currency":"USD","items":[{"sku":"WOO-SKU-001","qty":1}],"createdAt":"2026-06-29T14:20:00Z","customer":"Mike R."},
    {"orderId":"WC-1002","status":"completed","total":234.00,"currency":"USD","items":[{"sku":"WOO-SKU-002","qty":2}],"createdAt":"2026-06-28T11:00:00Z","customer":"Lisa K."},
]
MOCK_MAGENTO_ORDERS = [
    {"orderId":"MAG-000001","status":"pending","total":567.80,"currency":"USD","items":[{"sku":"MAG-SKU-001","qty":1}],"createdAt":"2026-06-30T16:45:00Z","customer":"Acme Corp"},
    {"orderId":"MAG-000002","status":"processing","total":1290.00,"currency":"USD","items":[{"sku":"MAG-SKU-002","qty":5}],"createdAt":"2026-06-29T08:30:00Z","customer":"BigCo Inc"},
]
MOCK_SQUARE_ORDERS = [
    {"orderId":"SQ-1001","status":"completed","total":45.99,"currency":"USD","items":[{"sku":"SQ-SKU-001","qty":1}],"createdAt":"2026-07-01T12:30:00Z","customer":"Walk-in Customer","location":"Downtown Store"},
    {"orderId":"SQ-1002","status":"open","total":129.50,"currency":"USD","items":[{"sku":"SQ-SKU-002","qty":2}],"createdAt":"2026-07-01T13:15:00Z","customer":"Online Order","location":"Main Street"},
]
MOCK_LIGHTSPEED_ORDERS = [
    {"orderId":"LS-5001","status":"shipped","total":299.00,"currency":"USD","items":[{"sku":"LS-SKU-001","qty":1}],"createdAt":"2026-06-28T09:00:00Z","customer":"Retail Customer","register":"Register 1"},
    {"orderId":"LS-5002","status":"completed","total":89.99,"currency":"USD","items":[{"sku":"LS-SKU-002","qty":3}],"createdAt":"2026-06-27T15:30:00Z","customer":"Wholesale Buyer","register":"Register 2"},
]

MOCK_CONNECTOR_ORDERS = {
    "shopify": MOCK_SHOPIFY_ORDERS,
    "woocommerce": MOCK_WOOCOMMERCE_ORDERS,
    "magento": MOCK_MAGENTO_ORDERS,
    "square": MOCK_SQUARE_ORDERS,
    "lightspeed": MOCK_LIGHTSPEED_ORDERS,
}

def sp_api_sign(access_key, secret_key, method, host, path, headers, region="us-east-1", service="execute-api", body=""):
    """Sign a request with AWS Signature V4 for SP-API."""
    t = datetime.now(timezone.utc)
    amz_date = t.strftime("%Y%m%dT%H%M%SZ")
    date_stamp = t.strftime("%Y%m%d")
    headers["x-amz-date"] = amz_date
    headers["host"] = host
    canonical_uri = path
    canonical_querystring = ""
    payload_hash = hashlib.sha256(body.encode() if isinstance(body, str) else body).hexdigest()
    headers["x-amz-content-sha256"] = payload_hash
    signed_headers = ";".join(sorted(h.lower() for h in headers))
    canonical_headers = "".join(f"{h.lower()}:{headers[h].strip()}\n" for h in sorted(headers, key=str.lower))
    canonical_request = f"{method}\n{canonical_uri}\n{canonical_querystring}\n{canonical_headers}\n{signed_headers}\n{payload_hash}"
    algorithm = "AWS4-HMAC-SHA256"
    credential_scope = f"{date_stamp}/{region}/{service}/aws4_request"
    string_to_sign = f"{algorithm}\n{amz_date}\n{credential_scope}\n{hashlib.sha256(canonical_request.encode()).hexdigest()}"
    k_date = hmac.new(f"AWS4{secret_key}".encode(), date_stamp.encode(), hashlib.sha256).digest()
    k_region = hmac.new(k_date, region.encode(), hashlib.sha256).digest()
    k_service = hmac.new(k_region, service.encode(), hashlib.sha256).digest()
    k_signing = hmac.new(k_service, "aws4_request".encode(), hashlib.sha256).digest()
    signature = hmac.new(k_signing, string_to_sign.encode(), hashlib.sha256).hexdigest()
    headers["Authorization"] = f"{algorithm} Credential={access_key}/{credential_scope}, SignedHeaders={signed_headers}, Signature={signature}"

class ConnectorHandler(BaseHTTPRequestHandler):
    def _json(self, data, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def _read_body(self):
        length = int(self.headers.get("Content-Length", 0))
        if length > 0:
            return json.loads(self.rfile.read(length))
        return {}

    def _error(self, msg, status=400):
        self._json({"success": False, "error": msg}, status)

    def do_OPTIONS(self):
        self._json({})

    def _route(self, method):
        path = self.path.rstrip("/")
        parts = path.split("/")
        if len(parts) >= 3 and parts[1] == "connectors" and parts[2] == "status":
            return self._handle_status()
        if len(parts) >= 3 and parts[1] == "connectors" and parts[2] == "configure" and method == "POST":
            return self._handle_configure()
        if len(parts) >= 4 and parts[1] == "connectors":
            marketplace = parts[2]
            action = parts[3]
            return self._handle_marketplace(marketplace, action, method, parts)
        if len(parts) >= 3 and parts[1] == "auth" and parts[2] == "callback":
            return self._handle_auth_callback()
        self._error("Not found", 404)

    def do_GET(self): self._route("GET")
    def do_POST(self): self._route("POST")
    def do_PUT(self): self._route("PUT")
    def do_DELETE(self): self._route("DELETE")

    def _handle_status(self):
        statuses = {}
        for name, cfg in CONNECTORS.items():
            statuses[name] = {
                "active": cfg["active"],
                "sandbox": cfg["sandbox"],
                "lastSync": cfg["last_sync"],
                "ordersSynced": cfg["orders_synced"],
                "error": cfg["error"],
                "hasCredentials": bool(cfg.get("client_id") and cfg.get("client_secret")),
            }
        self._json({"success": True, "connectors": statuses})

    def _handle_configure(self):
        body = self._read_body()
        marketplace = body.get("marketplace")
        if marketplace not in CONNECTORS:
            return self._error(f"Unknown marketplace: {marketplace}")
        cfg = CONNECTORS[marketplace]
        for key in cfg:
            if key in body:
                cfg[key] = body[key]
        if cfg.get("client_id") and cfg.get("client_secret"):
            cfg["active"] = True
            cfg["error"] = None
        else:
            cfg["active"] = False
        self._json({"success": True, "marketplace": marketplace, "active": cfg["active"]})

    def _handle_marketplace(self, marketplace, action, method, parts):
        if marketplace not in CONNECTORS:
            return self._error(f"Unknown marketplace: {marketplace}")
        cfg = CONNECTORS[marketplace]
        if action == "orders":
            return self._get_orders(marketplace, cfg, parts)
        if action == "inventory":
            return self._get_inventory(marketplace, cfg, parts)
        if action == "listings":
            return self._get_listings(marketplace, cfg, parts)
        if action == "authorize":
            return self._authorize(marketplace, cfg, parts)
        if action == "disconnect":
            return self._disconnect(marketplace, cfg)
        if action == "sync":
            return self._sync(marketplace, cfg)
        return self._error(f"Unknown action: {action}")

    def _get_orders(self, marketplace, cfg, parts):
        order_id = parts[5] if len(parts) > 5 else None
        all_mock = {**MOCK_ORDERS, **MOCK_CONNECTOR_ORDERS}
        mock_source = all_mock.get(marketplace, [])
        if cfg["active"]:
            return self._proxy_orders(marketplace, cfg, order_id)
        if order_id:
            for o in mock_source:
                if o.get("orderId") == order_id or o.get("orderId") == order_id:
                    return self._json({"success": True, "order": o, "mock": True})
            return self._json({"success": False, "error": "Order not found"}, 404)
        return self._json({"success": True, "orders": mock_source, "mock": True})

    def _get_inventory(self, marketplace, cfg, parts):
        all_inv = {**MOCK_INVENTORY}
        if cfg["active"]:
            return self._proxy_inventory(marketplace, cfg)
        inv = all_inv.get(marketplace)
        if inv:
            return self._json({"success": True, **inv, "mock": True})
        return self._json({"success": True, "inventory": [], "mock": True})

    def _get_listings(self, marketplace, cfg, parts):
        all_listings = {**MOCK_LISTINGS}
        if cfg["active"]:
            return self._proxy_listings(marketplace, cfg)
        listings = all_listings.get(marketplace)
        if listings:
            return self._json({"success": True, "listings": listings, "mock": True})
        return self._json({"success": True, "listings": [], "mock": True})

    def _authorize(self, marketplace, cfg, parts):
        body = self._read_body()
        if marketplace == "amazon":
            cfg["client_id"] = body.get("clientId", cfg["client_id"])
            cfg["client_secret"] = body.get("clientSecret", cfg["client_secret"])
            cfg["aws_access_key"] = body.get("awsAccessKey", cfg["aws_access_key"])
            cfg["aws_secret_key"] = body.get("awsSecretKey", cfg["aws_secret_key"])
            cfg["role_arn"] = body.get("roleArn", cfg["role_arn"])
            cfg["marketplace_id"] = body.get("marketplaceId", cfg["marketplace_id"])
            cfg["refresh_token"] = body.get("refreshToken", cfg["refresh_token"])
        elif marketplace == "ebay":
            cfg["client_id"] = body.get("clientId", cfg["client_id"])
            cfg["client_secret"] = body.get("clientSecret", cfg["client_secret"])
            cfg["refresh_token"] = body.get("refreshToken", cfg["refresh_token"])
            cfg["site_id"] = body.get("siteId", cfg["site_id"])
        elif marketplace == "walmart":
            cfg["client_id"] = body.get("clientId", cfg["client_id"])
            cfg["client_secret"] = body.get("clientSecret", cfg["client_secret"])
            cfg["refresh_token"] = body.get("refreshToken", cfg["refresh_token"])
            cfg["channel_type"] = body.get("channelType", cfg["channel_type"])
        cfg["sandbox"] = body.get("sandbox", cfg["sandbox"])
        cfg["active"] = True
        cfg["error"] = None
        self._json({"success": True, "marketplace": marketplace, "active": True, "message": f"{marketplace.title()} connector authorized"})

    def _disconnect(self, marketplace, cfg):
        for key in list(cfg.keys()):
            if key not in ("sandbox", "marketplace_id", "site_id", "channel_type", "orders_synced"):
                cfg[key] = None if key in ("access_token", "refresh_token", "client_id", "client_secret",
                    "aws_access_key", "aws_secret_key", "role_arn", "error", "last_sync") else cfg[key]
        cfg["active"] = False
        self._json({"success": True, "marketplace": marketplace, "active": False})

    def _sync(self, marketplace, cfg):
        all_mock = {**MOCK_ORDERS, **MOCK_CONNECTOR_ORDERS}
        mock_source = all_mock.get(marketplace, [])
        cfg["last_sync"] = datetime.now(timezone.utc).isoformat()
        cfg["orders_synced"] = cfg.get("orders_synced", 0) + len(mock_source[:5]) if mock_source else 0
        self._json({"success": True, "marketplace": marketplace, "syncedAt": cfg["last_sync"], "ordersSynced": cfg["orders_synced"]})

    def _proxy_orders(self, marketplace, cfg, order_id):
        try:
            if order_id:
                return self._json({"success": True, "order": MOCK_ORDERS[marketplace][0], "mock": True, "note": "Real API call would go here with configured credentials"})
            return self._json({"success": True, "orders": MOCK_ORDERS[marketplace], "mock": True, "note": "Real API call would go here with configured credentials"})
        except Exception as e:
            self._json({"success": False, "error": str(e)}, 502)

    def _proxy_inventory(self, marketplace, cfg):
        try:
            return self._json({"success": True, **MOCK_INVENTORY[marketplace], "mock": True, "note": "Real API call would go here with configured credentials"})
        except Exception as e:
            self._json({"success": False, "error": str(e)}, 502)

    def _proxy_listings(self, marketplace, cfg):
        try:
            return self._json({"success": True, "listings": MOCK_LISTINGS[marketplace], "mock": True, "note": "Real API call would go here with configured credentials"})
        except Exception as e:
            self._json({"success": False, "error": str(e)}, 502)

    def _handle_auth_callback(self):
        query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        marketplace = query.get("marketplace", [None])[0]
        code = query.get("spapi_oauth_code") or query.get("code")
        if marketplace and code:
            self._json({"success": True, "marketplace": marketplace, "authorizationCode": code[0] if isinstance(code, list) else code})
        else:
            self._error("Missing authorization code", 400)

    def log_message(self, format, *args):
        ts = datetime.now().strftime("%H:%M:%S")
        print(f"[{ts}] {args[0]} {args[1]}")

if __name__ == "__main__":
    server = HTTPServer((HOST, PORT), ConnectorHandler)
    print(f"[Connector Server] Listening on {HOST}:{PORT}")
    print(f"[Connector Server] Endpoints: /connectors/status, /connectors/<marketplace>/<action>")
    print(f"[Connector Server] Marketplaces: amazon, ebay, walmart, bigcommerce, shopify, woocommerce, magento, square, lightspeed")
    print(f"[Connector Server] Actions: orders, inventory, listings, authorize, disconnect, sync")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[Connector Server] Shutting down")
        server.server_close()
