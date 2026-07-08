#!/usr/bin/env python3
"""Backend API server for Nexus OMS. Runs on port 8080.
Comprehensive replacement for the stale Java JAR — serves all /api/v1/*
endpoints with JWT auth, CORS, and realistic in-memory data."""
import json, time, uuid, hmac, hashlib, base64, random, threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime, timezone, timedelta
from urllib.parse import urlparse, parse_qs

HOST = "0.0.0.0"
PORT = 8080
JWT_SECRET = "***REMOVED***"
JWT_ALGO = "HS256"

# ──────────────── Users ────────────────
USERS = {
    "admin": {"password": "Test1234!", "role": "ADMIN", "name": "Admin User", "email": "admin@nexusoms.com", "permissions": ["*"], "securityGroups": ["ADMIN"]},
    "john.smith": {"password": "picker123", "role": "PICKER", "name": "John Smith", "email": "john.smith@nexusoms.com", "permissions": ["*"], "securityGroups": ["ADMIN"]},
    "sarah.manager": {"password": "manager123", "role": "OPS_MANAGER", "name": "Sarah Manager", "email": "sarah@nexusoms.com", "permissions": ["*"], "securityGroups": ["ADMIN"]},
    "mike.warehouse": {"password": "warehouse123", "role": "WAREHOUSE_MANAGER", "name": "Mike Warehouse", "email": "mike@nexusoms.com", "permissions": ["*"], "securityGroups": ["ADMIN"]},
    "lisa.finance": {"password": "finance123", "role": "FINANCE", "name": "Lisa Finance", "email": "lisa@nexusoms.com", "permissions": ["*"], "securityGroups": ["ADMIN"]},
    "tom.ceo": {"password": "ceo1234!", "role": "CEO", "name": "Tom CEO", "email": "tom@nexusoms.com", "permissions": ["*"], "securityGroups": ["ADMIN"]},
}

# ──────────────── Seed Data ────────────────
PRODUCTS = [
    {"id":"P001","sku":"SKU-ACE-SKN-001","name":"Radiance Renewal Serum","price":89.00,"cost":32.00,"category":"Skincare","qty":420,"unit":"EA","active":True},
    {"id":"P002","sku":"SKU-ACE-SKN-002","name":"HydraGlow Moisturizer","price":65.00,"cost":24.00,"category":"Skincare","qty":580,"unit":"EA","active":True},
    {"id":"P003","sku":"SKU-ACE-SKN-003","name":"Vitamin C Brightening Cream","price":55.00,"cost":20.00,"category":"Skincare","qty":340,"unit":"EA","active":True},
    {"id":"P004","sku":"SKU-ACE-SKN-004","name":"Retinol Night Treatment","price":78.00,"cost":29.00,"category":"Skincare","qty":260,"unit":"EA","active":True},
    {"id":"P005","sku":"SKU-ACE-SKN-005","name":"SPF 50 Daily Shield","price":45.00,"cost":16.00,"category":"Skincare","qty":1200,"unit":"EA","active":True},
    {"id":"P006","sku":"SKU-ACE-MKP-001","name":"Luminous Foundation - Fair","price":52.00,"cost":21.00,"category":"Makeup","qty":310,"unit":"EA","active":True},
    {"id":"P007","sku":"SKU-ACE-MKP-002","name":"Velvet Matte Lipstick - Rose","price":28.00,"cost":10.00,"category":"Makeup","qty":890,"unit":"EA","active":True},
    {"id":"P008","sku":"SKU-ACE-MKP-003","name":"Precision Eyeliner - Black","price":22.00,"cost":8.00,"category":"Makeup","qty":1500,"unit":"EA","active":True},
    {"id":"P009","sku":"SKU-ACE-MKP-004","name":"Volume Mascara - Noir","price":32.00,"cost":12.00,"category":"Makeup","qty":720,"unit":"EA","active":True},
    {"id":"P010","sku":"SKU-ACE-MKP-005","name":"Contour Palette Pro","price":48.00,"cost":19.00,"category":"Makeup","qty":180,"unit":"EA","active":True},
    {"id":"P011","sku":"SKU-ACE-HAIR-001","name":"Keratin Repair Shampoo","price":38.00,"cost":14.00,"category":"Haircare","qty":640,"unit":"EA","active":True},
    {"id":"P012","sku":"SKU-ACE-HAIR-002","name":"Argan Oil Hair Mask","price":42.00,"cost":16.00,"category":"Haircare","qty":370,"unit":"EA","active":True},
    {"id":"P013","sku":"SKU-ACE-HAIR-003","name":"Volumizing Dry Shampoo","price":26.00,"cost":9.00,"category":"Haircare","qty":1100,"unit":"EA","active":True},
    {"id":"P014","sku":"SKU-ACE-HAIR-004","name":"Heat Protectant Spray","price":22.00,"cost":8.00,"category":"Haircare","qty":950,"unit":"EA","active":True},
    {"id":"P015","sku":"SKU-ACE-HAIR-005","name":"Scalp Treatment Serum","price":44.00,"cost":17.00,"category":"Haircare","qty":230,"unit":"EA","active":True},
    {"id":"P016","sku":"SKU-ACE-FRAG-001","name":"Bloom Eau de Parfum","price":120.00,"cost":48.00,"category":"Fragrance","qty":190,"unit":"EA","active":True},
    {"id":"P017","sku":"SKU-ACE-FRAG-002","name":"Midnight Orchid Cologne","price":95.00,"cost":38.00,"category":"Fragrance","qty":140,"unit":"EA","active":True},
    {"id":"P018","sku":"SKU-ACE-FRAG-003","name":"Ocean Breeze Body Mist","price":35.00,"cost":12.00,"category":"Fragrance","qty":780,"unit":"EA","active":True},
    {"id":"P019","sku":"SKU-ACE-TOOL-001","name":"Professional Hair Dryer","price":180.00,"cost":75.00,"category":"Tools","qty":85,"unit":"EA","active":True},
    {"id":"P020","sku":"SKU-ACE-TOOL-002","name":"Curling Wand Pro","price":65.00,"cost":28.00,"category":"Tools","qty":120,"unit":"EA","active":True},
    {"id":"P021","sku":"SKU-ACE-TOOL-003","name":"Facial Cleansing Brush","price":89.00,"cost":35.00,"category":"Tools","qty":210,"unit":"EA","active":True},
    {"id":"P022","sku":"SKU-ACE-TOOL-004","name":"LED Lighted Makeup Mirror","price":55.00,"cost":22.00,"category":"Tools","qty":160,"unit":"EA","active":True},
    {"id":"P023","sku":"SKU-ACE-SET-001","name":"Skincare Starter Kit","price":149.00,"cost":65.00,"category":"Sets","qty":95,"unit":"EA","active":True},
    {"id":"P024","sku":"SKU-ACE-SET-002","name":"Makeup Artist Collection","price":199.00,"cost":88.00,"category":"Sets","qty":55,"unit":"EA","active":True},
    {"id":"P025","sku":"SKU-ACE-SET-003","name":"Luxury Self-Care Bundle","price":275.00,"cost":120.00,"category":"Sets","qty":45,"unit":"EA","active":True},
]

CUSTOMERS = [
    {"id":"C001","name":"Glow & Co. Boutique","email":"orders@glowandco.com","phone":"+1-212-555-0101","orders":18,"totalSpent":28450.00,"status":"vip","company":"Glow & Co. Boutique","channel":"b2b","since":"2024-03-15"},
    {"id":"C002","name":"Luxury Spa Collection","email":"procurement@luxuryspa.com","phone":"+1-305-555-0102","orders":12,"totalSpent":18920.00,"status":"vip","company":"Luxury Spa Collection","channel":"b2b","since":"2024-06-01"},
    {"id":"C003","name":"Beauty Bliss Distributors","email":"info@beautyblissdist.com","phone":"+1-312-555-0103","orders":8,"totalSpent":42150.00,"status":"active","company":"Beauty Bliss Distributors","channel":"b2b","since":"2025-01-10"},
    {"id":"C004","name":"Sophia Martinez","email":"sophia.m@email.com","phone":"+1-415-555-0104","orders":5,"totalSpent":1245.00,"status":"active","company":"","channel":"dtc","since":"2025-09-20"},
    {"id":"C005","name":"James Chen","email":"james.chen@email.com","phone":"+1-206-555-0105","orders":3,"totalSpent":689.00,"status":"active","company":"","channel":"dtc","since":"2026-02-14"},
    {"id":"C006","name":"Aria Beauty Supply","email":"sales@ariabeauty.com","phone":"+1-713-555-0106","orders":7,"totalSpent":15340.00,"status":"active","company":"Aria Beauty Supply","channel":"b2b","since":"2025-04-22"},
    {"id":"C007","name":"Emma Davis","email":"emma.davis@email.com","phone":"+1-617-555-0107","orders":9,"totalSpent":3870.00,"status":"vip","company":"","channel":"dtc","since":"2024-11-05"},
    {"id":"C008","name":"Olivia Thompson","email":"olivia.t@email.com","phone":"+1-310-555-0108","orders":2,"totalSpent":375.00,"status":"active","company":"","channel":"dtc","since":"2026-05-30"},
    {"id":"C009","name":"Luxe Beauty Lounge","email":"hello@luxebeautylounge.com","phone":"+1-702-555-0109","orders":6,"totalSpent":9870.00,"status":"active","company":"Luxe Beauty Lounge","channel":"b2b","since":"2025-07-15"},
    {"id":"C010","name":"Noah Williams","email":"noah.w@email.com","phone":"+1-212-555-0110","orders":1,"totalSpent":149.00,"status":"new","company":"","channel":"dtc","since":"2026-06-28"},
    {"id":"C011","name":"Prestige Spa & Salon","email":"orders@prestigespa.com","phone":"+1-404-555-0111","orders":15,"totalSpent":31200.00,"status":"vip","company":"Prestige Spa & Salon","channel":"b2b","since":"2024-08-01"},
    {"id":"C012","name":"Mia Rodriguez","email":"mia.r@email.com","phone":"+1-512-555-0112","orders":4,"totalSpent":1095.00,"status":"active","company":"","channel":"dtc","since":"2026-01-12"},
    {"id":"C013","name":"Urban Glam Cosmetics","email":"info@urbanglamcos.com","phone":"+1-323-555-0113","orders":0,"totalSpent":0.00,"status":"on-hold","company":"Urban Glam Cosmetics","channel":"b2b","since":"2026-06-01"},
    {"id":"C014","name":"Liam Patel","email":"liam.p@email.com","phone":"+1-312-555-0114","orders":2,"totalSpent":520.00,"status":"active","company":"","channel":"dtc","since":"2026-04-05"},
    {"id":"C015","name":"Coastal Beauty Distributors","email":"sales@coastalbeauty.com","phone":"+1-858-555-0115","orders":5,"totalSpent":8700.00,"status":"active","company":"Coastal Beauty Distributors","channel":"b2b","since":"2025-11-20"},
]

ORDER_STATUSES = ["PENDING","CONFIRMED","ALLOCATED","PARTIALLY_ALLOCATED","PICKING","PACKING","SHIPPED","DELIVERED","CANCELLED","RETURNED"]
STATUS_WEIGHTS = [0.05, 0.10, 0.15, 0.05, 0.10, 0.10, 0.25, 0.10, 0.05, 0.05]

ORDERS = [
    {"id":"ORD-10000","orderNumber":"NEX-10000","customerId":"C001","customerName":"Glow & Co. Boutique","customerEmail":"orders@glowandco.com","items":[{"sku":"SKU-ACE-SKN-001","name":"Radiance Renewal Serum","qty":6,"price":89.00},{"sku":"SKU-ACE-SKN-002","name":"HydraGlow Moisturizer","qty":6,"price":65.00},{"sku":"SKU-ACE-SKN-005","name":"SPF 50 Daily Shield","qty":12,"price":45.00}],"subtotal":1524.00,"shipping":15.00,"tax":121.92,"total":1660.92,"currency":"USD","status":"CONFIRMED","paymentStatus":"paid","fulfillmentStatus":"in_progress","shippingMethod":"STANDARD","carrierId":"FEDEX","trackingNumber":None,"shippingAddress":"245 Madison Ave, New York, NY 10016","notes":"Wholesale order - bulk packaging","channel":"b2b-portal","createdAt":"2026-07-02T08:30:00Z","updatedAt":"2026-07-02T08:30:00Z","shipBy":"2026-07-05T08:30:00Z","itemsCount":24,"tags":["wholesale"]},
    {"id":"ORD-10001","orderNumber":"NEX-10001","customerId":"C001","customerName":"Glow & Co. Boutique","customerEmail":"orders@glowandco.com","items":[{"sku":"SKU-ACE-MKP-005","name":"Contour Palette Pro","qty":10,"price":48.00},{"sku":"SKU-ACE-MKP-002","name":"Velvet Matte Lipstick - Rose","qty":24,"price":28.00}],"subtotal":1152.00,"shipping":12.00,"tax":92.16,"total":1256.16,"currency":"USD","status":"ALLOCATED","paymentStatus":"paid","fulfillmentStatus":"in_progress","shippingMethod":"STANDARD","carrierId":"FEDEX","trackingNumber":None,"shippingAddress":"245 Madison Ave, New York, NY 10016","notes":"","channel":"b2b-portal","createdAt":"2026-07-01T14:00:00Z","updatedAt":"2026-07-01T14:00:00Z","shipBy":"2026-07-04T14:00:00Z","itemsCount":34,"tags":["wholesale","gift"]},
    {"id":"ORD-10002","orderNumber":"NEX-10002","customerId":"C001","customerName":"Glow & Co. Boutique","customerEmail":"orders@glowandco.com","items":[{"sku":"SKU-ACE-SET-001","name":"Skincare Starter Kit","qty":5,"price":149.00},{"sku":"SKU-ACE-SET-003","name":"Luxury Self-Care Bundle","qty":2,"price":275.00}],"subtotal":1295.00,"shipping":18.00,"tax":103.60,"total":1416.60,"currency":"USD","status":"PENDING","paymentStatus":"pending","fulfillmentStatus":"pending","shippingMethod":"STANDARD","carrierId":"UPS","trackingNumber":None,"shippingAddress":"245 Madison Ave, New York, NY 10016","notes":"Pending credit check","channel":"b2b-portal","createdAt":"2026-07-03T10:15:00Z","updatedAt":"2026-07-03T10:15:00Z","shipBy":"2026-07-06T10:15:00Z","itemsCount":7,"tags":[]},
    {"id":"ORD-10003","orderNumber":"NEX-10003","customerId":"C002","customerName":"Luxury Spa Collection","customerEmail":"procurement@luxuryspa.com","items":[{"sku":"SKU-ACE-SKN-001","name":"Radiance Renewal Serum","qty":12,"price":89.00},{"sku":"SKU-ACE-SKN-004","name":"Retinol Night Treatment","qty":12,"price":78.00},{"sku":"SKU-ACE-FRAG-001","name":"Bloom Eau de Parfum","qty":6,"price":120.00}],"subtotal":3108.00,"shipping":25.00,"tax":248.64,"total":3381.64,"currency":"USD","status":"SHIPPED","paymentStatus":"paid","fulfillmentStatus":"SHIPPED","shippingMethod":"EXPEDITED","carrierId":"UPS","trackingNumber":"TN-87234910563","shippingAddress":"1200 Collins Ave, Miami Beach, FL 33139","notes":"","channel":"b2b-portal","createdAt":"2026-06-30T09:00:00Z","updatedAt":"2026-07-01T14:30:00Z","shipBy":"2026-07-02T09:00:00Z","itemsCount":30,"tags":["fragile"]},
    {"id":"ORD-10004","orderNumber":"NEX-10004","customerId":"C002","customerName":"Luxury Spa Collection","customerEmail":"procurement@luxuryspa.com","items":[{"sku":"SKU-ACE-TOOL-001","name":"Professional Hair Dryer","qty":4,"price":180.00},{"sku":"SKU-ACE-TOOL-002","name":"Curling Wand Pro","qty":6,"price":65.00}],"subtotal":1110.00,"shipping":22.00,"tax":88.80,"total":1220.80,"currency":"USD","status":"CANCELLED","paymentStatus":"refunded","fulfillmentStatus":"CANCELLED","shippingMethod":"STANDARD","carrierId":"FEDEX","trackingNumber":None,"shippingAddress":"1200 Collins Ave, Miami Beach, FL 33139","notes":"Customer cancelled - ordered wrong items","channel":"b2b-portal","createdAt":"2026-06-28T11:00:00Z","updatedAt":"2026-06-29T09:00:00Z","shipBy":"2026-07-01T11:00:00Z","itemsCount":10,"tags":[]},
    {"id":"ORD-10005","orderNumber":"NEX-10005","customerId":"C004","customerName":"Sophia Martinez","customerEmail":"sophia.m@email.com","items":[{"sku":"SKU-ACE-SKN-001","name":"Radiance Renewal Serum","qty":1,"price":89.00},{"sku":"SKU-ACE-MKP-002","name":"Velvet Matte Lipstick - Rose","qty":2,"price":28.00}],"subtotal":145.00,"shipping":8.50,"tax":11.60,"total":165.10,"currency":"USD","status":"PICKING","paymentStatus":"paid","fulfillmentStatus":"in_progress","shippingMethod":"STANDARD","carrierId":"USPS","trackingNumber":None,"shippingAddress":"742 Lombard St, San Francisco, CA 94133","notes":"Leave at front desk","channel":"dtc-website","createdAt":"2026-07-01T16:45:00Z","updatedAt":"2026-07-01T16:45:00Z","shipBy":"2026-07-04T16:45:00Z","itemsCount":3,"tags":[]},
    {"id":"ORD-10006","orderNumber":"NEX-10006","customerId":"C004","customerName":"Sophia Martinez","customerEmail":"sophia.m@email.com","items":[{"sku":"SKU-ACE-HAIR-002","name":"Argan Oil Hair Mask","qty":2,"price":42.00},{"sku":"SKU-ACE-HAIR-005","name":"Scalp Treatment Serum","qty":1,"price":44.00}],"subtotal":128.00,"shipping":7.50,"tax":10.24,"total":145.74,"currency":"USD","status":"SHIPPED","paymentStatus":"paid","fulfillmentStatus":"SHIPPED","shippingMethod":"STANDARD","carrierId":"USPS","trackingNumber":"TN-45902387101","shippingAddress":"742 Lombard St, San Francisco, CA 94133","notes":"","channel":"dtc-website","createdAt":"2026-06-29T12:30:00Z","updatedAt":"2026-06-30T08:00:00Z","shipBy":"2026-07-02T12:30:00Z","itemsCount":3,"tags":[]},
    {"id":"ORD-10007","orderNumber":"NEX-10007","customerId":"C005","customerName":"James Chen","customerEmail":"james.chen@email.com","items":[{"sku":"SKU-ACE-SKN-003","name":"Vitamin C Brightening Cream","qty":1,"price":55.00},{"sku":"SKU-ACE-SKN-005","name":"SPF 50 Daily Shield","qty":1,"price":45.00},{"sku":"SKU-ACE-SKN-002","name":"HydraGlow Moisturizer","qty":1,"price":65.00}],"subtotal":165.00,"shipping":6.99,"tax":13.20,"total":185.19,"currency":"USD","status":"ALLOCATED","paymentStatus":"paid","fulfillmentStatus":"in_progress","shippingMethod":"STANDARD","carrierId":"UPS","trackingNumber":None,"shippingAddress":"1500 4th Ave, Seattle, WA 98101","notes":"","channel":"dtc-website","createdAt":"2026-07-02T07:00:00Z","updatedAt":"2026-07-02T07:00:00Z","shipBy":"2026-07-05T07:00:00Z","itemsCount":3,"tags":[]},
    {"id":"ORD-10008","orderNumber":"NEX-10008","customerId":"C005","customerName":"James Chen","customerEmail":"james.chen@email.com","items":[{"sku":"SKU-ACE-HAIR-001","name":"Keratin Repair Shampoo","qty":1,"price":38.00},{"sku":"SKU-ACE-HAIR-003","name":"Volumizing Dry Shampoo","qty":2,"price":26.00}],"subtotal":90.00,"shipping":6.99,"tax":7.20,"total":104.19,"currency":"USD","status":"CANCELLED","paymentStatus":"refunded","fulfillmentStatus":"CANCELLED","shippingMethod":"STANDARD","carrierId":"USPS","trackingNumber":None,"shippingAddress":"1500 4th Ave, Seattle, WA 98101","notes":"Customer changed mind","channel":"dtc-website","createdAt":"2026-06-27T15:00:00Z","updatedAt":"2026-06-27T16:30:00Z","shipBy":"2026-06-30T15:00:00Z","itemsCount":3,"tags":[]},
    {"id":"ORD-10009","orderNumber":"NEX-10009","customerId":"C003","customerName":"Beauty Bliss Distributors","customerEmail":"info@beautyblissdist.com","items":[{"sku":"SKU-ACE-MKP-001","name":"Luminous Foundation - Fair","qty":24,"price":52.00},{"sku":"SKU-ACE-MKP-003","name":"Precision Eyeliner - Black","qty":48,"price":22.00},{"sku":"SKU-ACE-MKP-004","name":"Volume Mascara - Noir","qty":24,"price":32.00}],"subtotal":3744.00,"shipping":35.00,"tax":299.52,"total":4078.52,"currency":"USD","status":"ALLOCATED","paymentStatus":"paid","fulfillmentStatus":"in_progress","shippingMethod":"EXPEDITED","carrierId":"FEDEX","trackingNumber":None,"shippingAddress":"233 S Wacker Dr, Chicago, IL 60606","notes":"Distribute to 3 locations","channel":"amazon","createdAt":"2026-07-01T10:00:00Z","updatedAt":"2026-07-01T10:00:00Z","shipBy":"2026-07-03T10:00:00Z","itemsCount":96,"tags":["wholesale"]},
    {"id":"ORD-10010","orderNumber":"NEX-10010","customerId":"C003","customerName":"Beauty Bliss Distributors","customerEmail":"info@beautyblissdist.com","items":[{"sku":"SKU-ACE-HAIR-001","name":"Keratin Repair Shampoo","qty":36,"price":38.00},{"sku":"SKU-ACE-HAIR-004","name":"Heat Protectant Spray","qty":24,"price":22.00}],"subtotal":1896.00,"shipping":30.00,"tax":151.68,"total":2077.68,"currency":"USD","status":"SHIPPED","paymentStatus":"paid","fulfillmentStatus":"SHIPPED","shippingMethod":"STANDARD","carrierId":"FEDEX","trackingNumber":"TN-98234712345","shippingAddress":"233 S Wacker Dr, Chicago, IL 60606","notes":"","channel":"shopify","createdAt":"2026-06-28T13:00:00Z","updatedAt":"2026-06-29T10:00:00Z","shipBy":"2026-07-01T13:00:00Z","itemsCount":60,"tags":[]},
    {"id":"ORD-10011","orderNumber":"NEX-10011","customerId":"C006","customerName":"Aria Beauty Supply","customerEmail":"sales@ariabeauty.com","items":[{"sku":"SKU-ACE-FRAG-001","name":"Bloom Eau de Parfum","qty":8,"price":120.00},{"sku":"SKU-ACE-FRAG-002","name":"Midnight Orchid Cologne","qty":6,"price":95.00}],"subtotal":1530.00,"shipping":20.00,"tax":122.40,"total":1672.40,"currency":"USD","status":"PACKING","paymentStatus":"paid","fulfillmentStatus":"in_progress","shippingMethod":"EXPEDITED","carrierId":"UPS","trackingNumber":None,"shippingAddress":"4567 Bellaire Blvd, Houston, TX 77401","notes":"Fragile - glass bottles","channel":"bigcommerce","createdAt":"2026-07-01T11:30:00Z","updatedAt":"2026-07-01T11:30:00Z","shipBy":"2026-07-03T11:30:00Z","itemsCount":14,"tags":["fragile"]},
    {"id":"ORD-10012","orderNumber":"NEX-10012","customerId":"C006","customerName":"Aria Beauty Supply","customerEmail":"sales@ariabeauty.com","items":[{"sku":"SKU-ACE-TOOL-002","name":"Curling Wand Pro","qty":5,"price":65.00},{"sku":"SKU-ACE-TOOL-003","name":"Facial Cleansing Brush","qty":5,"price":89.00},{"sku":"SKU-ACE-TOOL-004","name":"LED Lighted Makeup Mirror","qty":3,"price":55.00}],"subtotal":1070.00,"shipping":18.00,"tax":85.60,"total":1173.60,"currency":"USD","status":"ALLOCATED","paymentStatus":"paid","fulfillmentStatus":"in_progress","shippingMethod":"STANDARD","carrierId":"FEDEX","trackingNumber":None,"shippingAddress":"4567 Bellaire Blvd, Houston, TX 77401","notes":"","channel":"bigcommerce","createdAt":"2026-07-02T09:45:00Z","updatedAt":"2026-07-02T09:45:00Z","shipBy":"2026-07-05T09:45:00Z","itemsCount":13,"tags":[]},
    {"id":"ORD-10013","orderNumber":"NEX-10013","customerId":"C007","customerName":"Emma Davis","customerEmail":"emma.davis@email.com","items":[{"sku":"SKU-ACE-SKN-004","name":"Retinol Night Treatment","qty":2,"price":78.00},{"sku":"SKU-ACE-MKP-005","name":"Contour Palette Pro","qty":1,"price":48.00}],"subtotal":204.00,"shipping":7.99,"tax":16.32,"total":228.31,"currency":"USD","status":"SHIPPED","paymentStatus":"paid","fulfillmentStatus":"SHIPPED","shippingMethod":"EXPEDITED","carrierId":"UPS","trackingNumber":"TN-72910458236","shippingAddress":"200 Boylston St, Boston, MA 02116","notes":"","channel":"dtc-website","createdAt":"2026-06-29T18:00:00Z","updatedAt":"2026-06-30T09:00:00Z","shipBy":"2026-07-01T18:00:00Z","itemsCount":3,"tags":["priority"]},
    {"id":"ORD-10014","orderNumber":"NEX-10014","customerId":"C007","customerName":"Emma Davis","customerEmail":"emma.davis@email.com","items":[{"sku":"SKU-ACE-FRAG-001","name":"Bloom Eau de Parfum","qty":1,"price":120.00},{"sku":"SKU-ACE-FRAG-003","name":"Ocean Breeze Body Mist","qty":2,"price":35.00}],"subtotal":190.00,"shipping":7.99,"tax":15.20,"total":213.19,"currency":"USD","status":"CONFIRMED","paymentStatus":"paid","fulfillmentStatus":"in_progress","shippingMethod":"STANDARD","carrierId":"USPS","trackingNumber":None,"shippingAddress":"200 Boylston St, Boston, MA 02116","notes":"Gift wrap","channel":"dtc-website","createdAt":"2026-07-02T14:00:00Z","updatedAt":"2026-07-02T14:00:00Z","shipBy":"2026-07-05T14:00:00Z","itemsCount":3,"tags":["gift"]},
    {"id":"ORD-10015","orderNumber":"NEX-10015","customerId":"C008","customerName":"Olivia Thompson","customerEmail":"olivia.t@email.com","items":[{"sku":"SKU-ACE-MKP-002","name":"Velvet Matte Lipstick - Rose","qty":1,"price":28.00},{"sku":"SKU-ACE-MKP-004","name":"Volume Mascara - Noir","qty":1,"price":32.00},{"sku":"SKU-ACE-MKP-003","name":"Precision Eyeliner - Black","qty":1,"price":22.00}],"subtotal":82.00,"shipping":5.99,"tax":6.56,"total":94.55,"currency":"USD","status":"PENDING","paymentStatus":"pending","fulfillmentStatus":"pending","shippingMethod":"STANDARD","carrierId":"USPS","trackingNumber":None,"shippingAddress":"456 Sunset Blvd, Los Angeles, CA 90028","notes":"","channel":"shopify","createdAt":"2026-07-03T09:00:00Z","updatedAt":"2026-07-03T09:00:00Z","shipBy":"2026-07-06T09:00:00Z","itemsCount":3,"tags":[]},
    {"id":"ORD-10016","orderNumber":"NEX-10016","customerId":"C009","customerName":"Luxe Beauty Lounge","customerEmail":"hello@luxebeautylounge.com","items":[{"sku":"SKU-ACE-SKN-002","name":"HydraGlow Moisturizer","qty":10,"price":65.00},{"sku":"SKU-ACE-SKN-005","name":"SPF 50 Daily Shield","qty":15,"price":45.00},{"sku":"SKU-ACE-MKP-001","name":"Luminous Foundation - Fair","qty":6,"price":52.00}],"subtotal":1637.00,"shipping":20.00,"tax":130.96,"total":1787.96,"currency":"USD","status":"PACKING","paymentStatus":"paid","fulfillmentStatus":"in_progress","shippingMethod":"STANDARD","carrierId":"FEDEX","trackingNumber":None,"shippingAddress":"3300 Las Vegas Blvd S, Las Vegas, NV 89109","notes":"","channel":"b2b-portal","createdAt":"2026-07-01T13:00:00Z","updatedAt":"2026-07-01T13:00:00Z","shipBy":"2026-07-04T13:00:00Z","itemsCount":31,"tags":["wholesale"]},
    {"id":"ORD-10017","orderNumber":"NEX-10017","customerId":"C009","customerName":"Luxe Beauty Lounge","customerEmail":"hello@luxebeautylounge.com","items":[{"sku":"SKU-ACE-TOOL-001","name":"Professional Hair Dryer","qty":2,"price":180.00},{"sku":"SKU-ACE-TOOL-002","name":"Curling Wand Pro","qty":3,"price":65.00}],"subtotal":555.00,"shipping":15.00,"tax":44.40,"total":614.40,"currency":"USD","status":"SHIPPED","paymentStatus":"paid","fulfillmentStatus":"SHIPPED","shippingMethod":"EXPEDITED","carrierId":"FEDEX","trackingNumber":"TN-56102938475","shippingAddress":"3300 Las Vegas Blvd S, Las Vegas, NV 89109","notes":"","channel":"b2b-portal","createdAt":"2026-06-30T10:00:00Z","updatedAt":"2026-07-01T08:00:00Z","shipBy":"2026-07-02T10:00:00Z","itemsCount":5,"tags":[]},
    {"id":"ORD-10018","orderNumber":"NEX-10018","customerId":"C010","customerName":"Noah Williams","customerEmail":"noah.w@email.com","items":[{"sku":"SKU-ACE-SET-001","name":"Skincare Starter Kit","qty":1,"price":149.00}],"subtotal":149.00,"shipping":6.99,"tax":11.92,"total":167.91,"currency":"USD","status":"PENDING","paymentStatus":"pending","fulfillmentStatus":"pending","shippingMethod":"STANDARD","carrierId":"UPS","trackingNumber":None,"shippingAddress":"350 5th Ave, New York, NY 10118","notes":"New customer","channel":"shopify","createdAt":"2026-07-03T11:00:00Z","updatedAt":"2026-07-03T11:00:00Z","shipBy":"2026-07-06T11:00:00Z","itemsCount":1,"tags":["new"]},
    {"id":"ORD-10019","orderNumber":"NEX-10019","customerId":"C011","customerName":"Prestige Spa & Salon","customerEmail":"orders@prestigespa.com","items":[{"sku":"SKU-ACE-SKN-004","name":"Retinol Night Treatment","qty":15,"price":78.00},{"sku":"SKU-ACE-SKN-003","name":"Vitamin C Brightening Cream","qty":10,"price":55.00},{"sku":"SKU-ACE-HAIR-005","name":"Scalp Treatment Serum","qty":8,"price":44.00}],"subtotal":2072.00,"shipping":25.00,"tax":165.76,"total":2262.76,"currency":"USD","status":"SHIPPED","paymentStatus":"paid","fulfillmentStatus":"SHIPPED","shippingMethod":"EXPEDITED","carrierId":"UPS","trackingNumber":"TN-39482756102","shippingAddress":"100 Peachtree St, Atlanta, GA 30303","notes":"","channel":"b2b-portal","createdAt":"2026-06-28T08:00:00Z","updatedAt":"2026-06-29T12:00:00Z","shipBy":"2026-07-01T08:00:00Z","itemsCount":33,"tags":["wholesale","fragile"]},
    {"id":"ORD-10020","orderNumber":"NEX-10020","customerId":"C011","customerName":"Prestige Spa & Salon","customerEmail":"orders@prestigespa.com","items":[{"sku":"SKU-ACE-TOOL-003","name":"Facial Cleansing Brush","qty":8,"price":89.00}],"subtotal":712.00,"shipping":20.00,"tax":56.96,"total":788.96,"currency":"USD","status":"CANCELLED","paymentStatus":"refunded","fulfillmentStatus":"CANCELLED","shippingMethod":"STANDARD","carrierId":"FEDEX","trackingNumber":None,"shippingAddress":"100 Peachtree St, Atlanta, GA 30303","notes":"Out of stock - cancelled at customer request","channel":"b2b-portal","createdAt":"2026-06-26T09:00:00Z","updatedAt":"2026-06-26T14:00:00Z","shipBy":"2026-06-29T09:00:00Z","itemsCount":8,"tags":[]},
    {"id":"ORD-10021","orderNumber":"NEX-10021","customerId":"C012","customerName":"Mia Rodriguez","customerEmail":"mia.r@email.com","items":[{"sku":"SKU-ACE-FRAG-003","name":"Ocean Breeze Body Mist","qty":1,"price":35.00},{"sku":"SKU-ACE-HAIR-003","name":"Volumizing Dry Shampoo","qty":1,"price":26.00},{"sku":"SKU-ACE-HAIR-004","name":"Heat Protectant Spray","qty":1,"price":22.00}],"subtotal":83.00,"shipping":5.99,"tax":6.64,"total":95.63,"currency":"USD","status":"ALLOCATED","paymentStatus":"paid","fulfillmentStatus":"in_progress","shippingMethod":"STANDARD","carrierId":"USPS","trackingNumber":None,"shippingAddress":"800 Congress Ave, Austin, TX 78701","notes":"","channel":"shopify","createdAt":"2026-07-02T15:30:00Z","updatedAt":"2026-07-02T15:30:00Z","shipBy":"2026-07-05T15:30:00Z","itemsCount":3,"tags":[]},
    {"id":"ORD-10022","orderNumber":"NEX-10022","customerId":"C014","customerName":"Liam Patel","customerEmail":"liam.p@email.com","items":[{"sku":"SKU-ACE-SET-002","name":"Makeup Artist Collection","qty":1,"price":199.00},{"sku":"SKU-ACE-MKP-003","name":"Precision Eyeliner - Black","qty":2,"price":22.00}],"subtotal":243.00,"shipping":6.99,"tax":19.44,"total":269.43,"currency":"USD","status":"CONFIRMED","paymentStatus":"paid","fulfillmentStatus":"in_progress","shippingMethod":"STANDARD","carrierId":"UPS","trackingNumber":None,"shippingAddress":"233 S Michigan Ave, Chicago, IL 60604","notes":"","channel":"amazon","createdAt":"2026-07-02T10:00:00Z","updatedAt":"2026-07-02T10:00:00Z","shipBy":"2026-07-05T10:00:00Z","itemsCount":3,"tags":[]},
    {"id":"ORD-10023","orderNumber":"NEX-10023","customerId":"C015","customerName":"Coastal Beauty Distributors","customerEmail":"sales@coastalbeauty.com","items":[{"sku":"SKU-ACE-MKP-001","name":"Luminous Foundation - Fair","qty":12,"price":52.00},{"sku":"SKU-ACE-MKP-005","name":"Contour Palette Pro","qty":6,"price":48.00},{"sku":"SKU-ACE-MKP-004","name":"Volume Mascara - Noir","qty":18,"price":32.00}],"subtotal":1464.00,"shipping":25.00,"tax":117.12,"total":1606.12,"currency":"USD","status":"PICKING","paymentStatus":"paid","fulfillmentStatus":"in_progress","shippingMethod":"STANDARD","carrierId":"FEDEX","trackingNumber":None,"shippingAddress":"101 W Broadway, San Diego, CA 92101","notes":"","channel":"amazon","createdAt":"2026-07-01T07:30:00Z","updatedAt":"2026-07-01T07:30:00Z","shipBy":"2026-07-04T07:30:00Z","itemsCount":36,"tags":["wholesale"]},
    {"id":"ORD-10024","orderNumber":"NEX-10024","customerId":"C015","customerName":"Coastal Beauty Distributors","customerEmail":"sales@coastalbeauty.com","items":[{"sku":"SKU-ACE-HAIR-001","name":"Keratin Repair Shampoo","qty":12,"price":38.00},{"sku":"SKU-ACE-HAIR-004","name":"Heat Protectant Spray","qty":12,"price":22.00}],"subtotal":720.00,"shipping":20.00,"tax":57.60,"total":797.60,"currency":"USD","status":"SHIPPED","paymentStatus":"paid","fulfillmentStatus":"SHIPPED","shippingMethod":"STANDARD","carrierId":"FEDEX","trackingNumber":"TN-67583920184","shippingAddress":"101 W Broadway, San Diego, CA 92101","notes":"","channel":"amazon","createdAt":"2026-06-30T14:00:00Z","updatedAt":"2026-07-01T10:00:00Z","shipBy":"2026-07-02T14:00:00Z","itemsCount":24,"tags":[]},
]

WAREHOUSES = [
    {"id":"WH-01","name":"Mumbai Fulfillment Center","location":"Mumbai, MH","capacity":50000,"used":38750,"zones":6,"status":"active"},
    {"id":"WH-02","name":"Delhi Logistics Hub","location":"Delhi, DL","capacity":35000,"used":22100,"zones":4,"status":"active"},
    {"id":"WH-03","name":"Bangalore Tech Warehouse","location":"Bangalore, KA","capacity":28000,"used":18900,"zones":3,"status":"active"},
    {"id":"WH-04","name":"Chennai Port Warehouse","location":"Chennai, TN","capacity":42000,"used":31500,"zones":5,"status":"active"},
    {"id":"WH-05","name":"Kolkata Distribution Center","location":"Kolkata, WB","capacity":22000,"used":9800,"zones":3,"status":"active"},
]

INVENTORY_BY_WAREHOUSE = {}
for wh in WAREHOUSES:
    INVENTORY_BY_WAREHOUSE[wh["id"]] = {p["sku"]: random.randint(5, 200) for p in PRODUCTS}

RETURNS = [
    {"id":"RMA-10001","orderId":"ORD-10002","customer":"Sophia Martinez","sku":"SKU-ACE-SKN-001","product":"Radiance Renewal Serum","qty":1,"reason":"Defective","condition":"Damaged","status":"pending","disposition":"pending","createdAt":"2026-06-28T10:30:00Z","refund":89.00},
    {"id":"RMA-10002","orderId":"ORD-10005","customer":"Emma Davis","sku":"SKU-ACE-MKP-005","product":"Contour Palette Pro","qty":1,"reason":"Not as described","condition":"Like New","status":"approved","disposition":"restock","createdAt":"2026-06-27T14:15:00Z","refund":48.00},
    {"id":"RMA-10003","orderId":"ORD-10007","customer":"James Chen","sku":"SKU-ACE-SKN-003","product":"Vitamin C Brightening Cream","qty":1,"reason":"Wrong item","condition":"Open Box","status":"inspected","disposition":"restock","createdAt":"2026-06-26T09:00:00Z","refund":55.00},
    {"id":"RMA-10004","orderId":"ORD-10022","customer":"Liam Patel","sku":"SKU-ACE-HAIR-002","product":"Argan Oil Hair Mask","qty":1,"reason":"No longer needed","condition":"Like New","status":"approved","disposition":"restock","createdAt":"2026-06-25T16:45:00Z","refund":42.00},
    {"id":"RMA-10005","orderId":"ORD-10021","customer":"Mia Rodriguez","sku":"SKU-ACE-FRAG-002","product":"Midnight Orchid Cologne","qty":1,"reason":"Defective","condition":"Damaged","status":"pending","disposition":"pending","createdAt":"2026-06-24T11:30:00Z","refund":95.00},
    {"id":"RMA-10006","orderId":"ORD-10020","customer":"Olivia Thompson","sku":"SKU-ACE-TOOL-003","product":"Facial Cleansing Brush","qty":1,"reason":"Quality issue","condition":"Fair","status":"inspected","disposition":"refurbish","createdAt":"2026-06-23T08:00:00Z","refund":89.00},
]

WAVE_PLANS = [
    {"id":"WAVE-001","name":"Morning Wave","priority":"high","status":"active","orders":["ORD-10000","ORD-10001","ORD-10002","ORD-10003","ORD-10004"],"zone":"A1","targetTime":"10:00","progress":60,"createdAt":"2026-07-01T06:00:00Z"},
    {"id":"WAVE-002","name":"Express Fulfillment","priority":"urgent","status":"active","orders":["ORD-10005","ORD-10006"],"zone":"B2","targetTime":"09:30","progress":100,"createdAt":"2026-07-01T07:00:00Z"},
    {"id":"WAVE-003","name":"Bulk Orders","priority":"normal","status":"pending","orders":["ORD-10007","ORD-10008","ORD-10009","ORD-10010","ORD-10011","ORD-10012"],"zone":"C3","targetTime":"14:00","progress":0,"createdAt":"2026-07-01T08:00:00Z"},
    {"id":"WAVE-004","name":"International Priority","priority":"high","status":"completed","orders":["ORD-10013","ORD-10014","ORD-10015"],"zone":"A1","targetTime":"08:00","progress":100,"createdAt":"2026-06-30T05:00:00Z"},
    {"id":"WAVE-005","name":"Afternoon Wave","priority":"normal","status":"pending","orders":["ORD-10016","ORD-10017","ORD-10018"],"zone":"B2","targetTime":"16:00","progress":0,"createdAt":"2026-07-01T12:00:00Z"},
]

EMPLOYEES = [
    {"id":"E001","name":"Raj Patel","role":"Picker","status":"active","efficiency":98,"hoursToday":6.5,"shift":"morning","zone":"A1"},
    {"id":"E002","name":"Priya Sharma","role":"Packer","status":"active","efficiency":96,"hoursToday":6.0,"shift":"morning","zone":"A1"},
    {"id":"E003","name":"Amit Singh","role":"Picker","status":"active","efficiency":88,"hoursToday":7.0,"shift":"morning","zone":"B2"},
    {"id":"E004","name":"Deepa Kumar","role":"Packer","status":"break","efficiency":94,"hoursToday":4.5,"shift":"afternoon","zone":"B2"},
    {"id":"E005","name":"Vikram Joshi","role":"Loader","status":"active","efficiency":92,"hoursToday":5.5,"shift":"morning","zone":"C3"},
    {"id":"E006","name":"Ananya Reddy","role":"Picker","status":"active","efficiency":85,"hoursToday":3.0,"shift":"afternoon","zone":"C3"},
    {"id":"E007","name":"Suresh Nair","role":"Supervisor","status":"active","efficiency":100,"hoursToday":7.5,"shift":"morning","zone":"A1"},
    {"id":"E008","name":"Meera Iyer","role":"Packer","status":"off","efficiency":91,"hoursToday":0.0,"shift":"afternoon","zone":"A1"},
    {"id":"E009","name":"Rohan Desai","role":"Picker","status":"active","efficiency":79,"hoursToday":4.0,"shift":"afternoon","zone":"A1"},
    {"id":"E010","name":"Kavita Gupta","role":"Loader","status":"active","efficiency":95,"hoursToday":6.0,"shift":"morning","zone":"B2"},
]

CARRIERS = [
    {"id":"FEDEX","name":"FedEx","services":["GROUND","2DAY","OVERNIGHT","INTERNATIONAL"]},
    {"id":"UPS","name":"UPS","services":["GROUND","2ND_DAY","NEXT_DAY","WORLDWIDE"]},
    {"id":"USPS","name":"USPS","services":["PRIORITY","EXPRESS","FIRST_CLASS","MEDIA"]},
    {"id":"DHL","name":"DHL","services":["EXPRESS","ECONOMY","FREIGHT"]},
]

PAYMENTS = []
for i in range(min(25, len(ORDERS))):
    order = ORDERS[i]
    fees = round(order["total"] * 0.029 + 0.30, 2)
    PAYMENTS.append({
        "id":f"PAY-{20000+i}","orderId":order["id"],"amount":order["total"],"currency":"USD",
        "method":random.choice(["credit_card","debit_card","paypal","bank_transfer","wallet"]),
        "status":random.choice(["captured","settled","pending","refunded"]),
        "fee":fees,"net":round(order["total"]-fees,2),
        "createdAt":order["createdAt"],"refundedAt":None,
    })

INVOICES = []
for i in range(min(25, len(ORDERS))):
    order = ORDERS[i]
    INVOICES.append({
        "id":f"INV-{30000+i}","orderId":order["id"],"number":f"INV-2026-{10000+i}",
        "customerName":order["customerName"],"amount":order["total"],"currency":"USD",
        "status":random.choice(["paid","pending","overdue","cancelled"]),
        "issuedAt":order["createdAt"],"dueAt":(datetime.fromisoformat(order["createdAt"])+timedelta(days=30)).isoformat(),
        "paidAt":order["createdAt"] if random.random()>0.3 else None,
    })

RECONCILIATION = []
for i in range(20):
    r = random.choice(PAYMENTS)
    inv = random.choice(INVOICES)
    diff = round(random.uniform(-5, 5), 2)
    RECONCILIATION.append({
        "id":f"REC-{40000+i}","paymentId":r["id"],"invoiceId":inv["id"],
        "orderId":r["orderId"],"paymentAmount":r["amount"],"invoiceAmount":inv["amount"],
        "difference":diff,"status":"matched" if abs(diff)<0.01 else "mismatch" if abs(diff)<2 else "unmatched",
    })

CARRIER_RATES = [
    {"carrier":"FedEx","service":"Ground","estimatedDays":"1-5","rate":8.99,"cutoff":"16:00"},
    {"carrier":"FedEx","service":"2Day","estimatedDays":"2","rate":14.99,"cutoff":"15:00"},
    {"carrier":"FedEx","service":"Overnight","estimatedDays":"1","rate":29.99,"cutoff":"14:00"},
    {"carrier":"UPS","service":"Ground","estimatedDays":"1-6","rate":7.99,"cutoff":"17:00"},
    {"carrier":"UPS","service":"2nd Day Air","estimatedDays":"2","rate":15.99,"cutoff":"15:30"},
    {"carrier":"UPS","service":"Next Day Air","estimatedDays":"1","rate":34.99,"cutoff":"13:00"},
    {"carrier":"USPS","service":"Priority Mail","estimatedDays":"1-3","rate":7.50,"cutoff":"16:30"},
    {"carrier":"USPS","service":"Express Mail","estimatedDays":"1-2","rate":26.95,"cutoff":"15:00"},
    {"carrier":"DHL","service":"Express","estimatedDays":"2-5","rate":22.50,"cutoff":"14:00"},
    {"carrier":"DHL","service":"Economy","estimatedDays":"5-10","rate":12.00,"cutoff":"16:00"},
]

MANIFESTS = [
    {"id":"MAN-50001","carrier":"FedEx","status":"closed","shipments":47,"totalWeight":1250.5,"totalCost":847.25,"createdAt":"2026-07-01T08:00:00Z","closedAt":"2026-07-01T18:30:00Z","bol":"BOL-FX-20260701"},
    {"id":"MAN-50002","carrier":"UPS","status":"draft","shipments":23,"totalWeight":680.0,"totalCost":395.80,"createdAt":"2026-07-01T09:00:00Z","closedAt":None,"bol":None},
    {"id":"MAN-50003","carrier":"USPS","status":"submitted","shipments":156,"totalWeight":890.25,"totalCost":1125.00,"createdAt":"2026-07-01T07:00:00Z","closedAt":"2026-07-01T17:00:00Z","bol":"BOL-USPS-20260701"},
    {"id":"MAN-50004","carrier":"FedEx","status":"draft","shipments":12,"totalWeight":340.75,"totalCost":215.60,"createdAt":"2026-07-02T06:00:00Z","closedAt":None,"bol":None},
    {"id":"MAN-50005","carrier":"DHL","status":"closed","shipments":8,"totalWeight":120.0,"totalCost":180.00,"createdAt":"2026-06-30T10:00:00Z","closedAt":"2026-06-30T19:00:00Z","bol":"BOL-DHL-20260630"},
]

LABELS_HISTORY = []
for i in range(min(25, len(ORDERS))):
    order = ORDERS[i]
    LABELS_HISTORY.append({
        "id":f"LBL-{60000+i}","orderId":order["id"],"carrier":order["carrierId"],
        "service":order["shippingMethod"],"trackingNumber":order["trackingNumber"] or f"TN-LBL-{random.randint(10000000000,99999999999)}",
        "weight":round(random.uniform(0.5,25.0),2),"dimensions":f'{random.randint(5,24)}x{random.randint(5,18)}x{random.randint(2,12)}',
        "status":random.choice(["generated","printed","voided"]),"createdAt":order["createdAt"],
    })

TASK_QUEUES = [
    {"id":"TQ-001","type":"swap","orders":["ORD-10002","ORD-10006"],"count":2,"description":"Products need to be swapped","priority":"high"},
    {"id":"TQ-002","type":"bad_address","orders":["ORD-10008"],"count":1,"description":"Invalid shipping address","priority":"high"},
    {"id":"TQ-003","type":"fraud_review","orders":["ORD-10011","ORD-10017","ORD-10022"],"count":3,"description":"Orders flagged for fraud review","priority":"urgent"},
    {"id":"TQ-004","type":"payment_hold","orders":["ORD-10000"],"count":1,"description":"Payment verification required","priority":"medium"},
    {"id":"TQ-005","type":"missing_info","orders":["ORD-10005","ORD-10013","ORD-10019","ORD-10024"],"count":4,"description":"Missing required customer information","priority":"medium"},
    {"id":"TQ-006","type":"quality_check","orders":["ORD-10003"],"count":1,"description":"Quality check required before shipment","priority":"low"},
]

SETTINGS = {
    "companyName":"Nexus OMS","timezone":"UTC","currency":"USD",
    "dateFormat":"YYYY-MM-DD","autoAllocate":True,"autoShip":False,
    "defaultCarrier":"FEDEX","pickMethod":"wave",
    "labelFormat":"ZPL","labelSize":"4x6"
}

NOTIFICATIONS = [
    {"id":"N-001","type":"order","title":"New Order #NEX-10000","message":"Order placed by Glow & Co. Boutique","read":False,"createdAt":"2026-07-01T09:30:00Z"},
    {"id":"N-002","type":"alert","title":"Low Stock Alert","message":"SKU-ACE-TOOL-001 Professional Hair Dryer has only 12 units remaining","read":False,"createdAt":"2026-07-01T09:00:00Z"},
    {"id":"N-003","type":"system","title":"Wave Completed","message":"Morning Wave completed successfully","read":True,"createdAt":"2026-07-01T08:45:00Z"},
    {"id":"N-004","type":"error","title":"Carrier API Error","message":"FedEx rate request failed — retrying","read":False,"createdAt":"2026-07-01T08:30:00Z"},
    {"id":"N-005","type":"return","title":"Return Request RMA-10001","message":"Sophia Martinez requested return for Radiance Renewal Serum","read":False,"createdAt":"2026-07-01T08:00:00Z"},
]

SHIFTS = [
    {"id":"S-001","employeeId":"E001","date":"2026-07-01","shift":"morning","start":"06:00","end":"14:00","status":"active"},
    {"id":"S-002","employeeId":"E002","date":"2026-07-01","shift":"morning","start":"06:00","end":"14:00","status":"active"},
    {"id":"S-003","employeeId":"E003","date":"2026-07-01","shift":"morning","start":"06:00","end":"14:00","status":"active"},
    {"id":"S-004","employeeId":"E004","date":"2026-07-01","shift":"afternoon","start":"14:00","end":"22:00","status":"active"},
    {"id":"S-005","employeeId":"E005","date":"2026-07-01","shift":"morning","start":"06:00","end":"14:00","status":"active"},
    {"id":"S-006","employeeId":"E006","date":"2026-07-01","shift":"afternoon","start":"14:00","end":"22:00","status":"active"},
    {"id":"S-007","employeeId":"E007","date":"2026-07-01","shift":"morning","start":"06:00","end":"14:00","status":"active"},
    {"id":"S-008","employeeId":"E008","date":"2026-07-01","shift":"afternoon","start":"14:00","end":"22:00","status":"scheduled"},
    {"id":"S-009","employeeId":"E009","date":"2026-07-01","shift":"afternoon","start":"14:00","end":"22:00","status":"active"},
    {"id":"S-010","employeeId":"E010","date":"2026-07-01","shift":"morning","start":"06:00","end":"14:00","status":"active"},
]

PICK_LISTS = [
    {"id":"PL-001","waveId":"WAVE-001","zone":"A1","items":[{"sku":"SKU-ACE-SKN-001","name":"Radiance Renewal Serum","qty":3,"location":"A1-01-B"},{"sku":"SKU-ACE-SKN-002","name":"HydraGlow Moisturizer","qty":5,"location":"A1-03-C"}],"status":"in_progress","pickerId":"E001","createdAt":"2026-07-01T06:30:00Z"},
    {"id":"PL-002","waveId":"WAVE-002","zone":"B2","items":[{"sku":"SKU-ACE-MKP-001","name":"Luminous Foundation - Fair","qty":1,"location":"B2-02-A"},{"sku":"SKU-ACE-MKP-004","name":"Volume Mascara - Noir","qty":2,"location":"B2-01-D"}],"status":"completed","pickerId":"E003","createdAt":"2026-07-01T07:00:00Z"},
    {"id":"PL-003","waveId":"WAVE-001","zone":"A1","items":[{"sku":"SKU-ACE-SKN-004","name":"Retinol Night Treatment","qty":2,"location":"A1-02-A"}],"status":"pending","pickerId":None,"createdAt":"2026-07-01T06:30:00Z"},
]

# ──────────────── RBAC Seed Data ────────────────
RBAC_PERMISSIONS = [
    {"id":"RP-001","role":"ADMIN","resource":"*","action":"*","granted":True},
    {"id":"RP-002","role":"OPS_MANAGER","resource":"orders","action":"*","granted":True},
    {"id":"RP-003","role":"OPS_MANAGER","resource":"products","action":"*","granted":True},
    {"id":"RP-004","role":"OPS_MANAGER","resource":"inventory","action":"*","granted":True},
    {"id":"RP-005","role":"WAREHOUSE_MANAGER","resource":"orders","action":"read","granted":True},
    {"id":"RP-006","role":"WAREHOUSE_MANAGER","resource":"inventory","action":"*","granted":True},
    {"id":"RP-007","role":"FINANCE","resource":"invoices","action":"*","granted":True},
    {"id":"RP-008","role":"FINANCE","resource":"payments","action":"*","granted":True},
    {"id":"RP-009","role":"PICKER","resource":"orders","action":"read","granted":True},
    {"id":"RP-010","role":"PICKER","resource":"inventory","action":"read","granted":True},
]
RBAC_USER_ROLES = [
    {"id":"UR-001","username":"admin","role":"ADMIN","assignedAt":"2026-01-01T00:00:00Z"},
    {"id":"UR-002","username":"john.smith","role":"PICKER","assignedAt":"2026-01-15T00:00:00Z"},
    {"id":"UR-003","username":"sarah.manager","role":"OPS_MANAGER","assignedAt":"2026-02-01T00:00:00Z"},
    {"id":"UR-004","username":"mike.warehouse","role":"WAREHOUSE_MANAGER","assignedAt":"2026-03-01T00:00:00Z"},
    {"id":"UR-005","username":"lisa.finance","role":"FINANCE","assignedAt":"2026-04-01T00:00:00Z"},
    {"id":"UR-006","username":"tom.ceo","role":"ADMIN","assignedAt":"2026-01-01T00:00:00Z"},
]
RBAC_TEAMS = [
    {"id":"T-001","name":"Operations","description":"Order operations team","memberCount":5,"createdAt":"2026-01-01T00:00:00Z"},
    {"id":"T-002","name":"Warehouse","description":"Warehouse staff","memberCount":12,"createdAt":"2026-01-01T00:00:00Z"},
    {"id":"T-003","name":"Finance","description":"Finance and accounting","memberCount":3,"createdAt":"2026-02-01T00:00:00Z"},
]

# ──────────────── Extended Seed Data ────────────────
SHIPMENTS = []
for i in range(min(20, len(ORDERS))):
    o = ORDERS[i]
    status = random.choice(["pending","label_created","picked_up","in_transit","out_for_delivery","delivered","exception"])
    events = [
        {"status":"label_created","location":"Origin","timestamp":o["createdAt"]},
        {"status":"picked_up","location":"Origin","timestamp":(datetime.fromisoformat(o["createdAt"])+timedelta(hours=2)).isoformat()},
    ]
    if status in ("in_transit","out_for_delivery","delivered"):
        events.append({"status":"in_transit","location":"Sort Facility","timestamp":(datetime.fromisoformat(o["createdAt"])+timedelta(hours=8)).isoformat()})
    if status in ("out_for_delivery","delivered"):
        events.append({"status":"out_for_delivery","location":"Destination Hub","timestamp":(datetime.fromisoformat(o["createdAt"])+timedelta(hours=24)).isoformat()})
    if status == "delivered":
        events.append({"status":"delivered","location":"Final Destination","timestamp":(datetime.fromisoformat(o["createdAt"])+timedelta(hours=30)).isoformat()})
    SHIPMENTS.append({
        "id":f"SHP-{80000+i}","orderId":o["id"],"orderNumber":o["orderNumber"],"carrier":o["carrierId"],
        "service":o["shippingMethod"],"trackingNumber":o.get("trackingNumber") or f"TN-{random.randint(10**10,10**11-1)}",
        "status":status,"weight":round(random.uniform(0.5,25.0),2),
        "dimensions":f'{random.randint(5,24)}x{random.randint(5,18)}x{random.randint(2,12)}',
        "cost":round(random.uniform(5,35),2),"estimatedDelivery":(datetime.fromisoformat(o["createdAt"])+timedelta(days=random.randint(1,7))).isoformat(),
        "actualDelivery":(datetime.fromisoformat(o["createdAt"])+timedelta(days=random.randint(1,7))).isoformat() if status=="delivered" else None,
        "shipDate":o["createdAt"],"shippingAddress":o["shippingAddress"],"events":events,
    })

WORKFLOWS = [
    {"id":"WF-001","name":"Standard Order Fulfillment","description":"Process orders from receipt to shipment","status":"active","category":"fulfillment","priority":1,"steps":5,"createdAt":"2026-06-01T00:00:00Z","updatedAt":"2026-07-01T00:00:00Z"},
    {"id":"WF-002","name":"Returns Processing","description":"Handle customer returns and refunds","status":"active","category":"returns","priority":2,"steps":4,"createdAt":"2026-06-01T00:00:00Z","updatedAt":"2026-07-01T00:00:00Z"},
    {"id":"WF-003","name":"Quality Assurance","description":"Quality checks for products","status":"active","category":"quality","priority":3,"steps":3,"createdAt":"2026-06-05T00:00:00Z","updatedAt":"2026-06-30T00:00:00Z"},
    {"id":"WF-004","name":"Procurement Request","description":"Handle purchase requests and approvals","status":"inactive","category":"procurement","priority":4,"steps":5,"createdAt":"2026-06-10T00:00:00Z","updatedAt":"2026-06-25T00:00:00Z"},
    {"id":"WF-005","name":"Cross-Border Shipping","description":"International shipping compliance","status":"active","category":"shipping","priority":2,"steps":6,"createdAt":"2026-06-15T00:00:00Z","updatedAt":"2026-07-01T00:00:00Z"},
]
WORKFLOW_STEPS = {
    "WF-001":[
        {"id":"WFS-101","name":"Order Received","type":"trigger","config":{"event":"order.created"},"order":1},
        {"id":"WFS-102","name":"Payment Verification","type":"condition","config":{"check":"payment_status","value":"paid"},"order":2},
        {"id":"WFS-103","name":"Inventory Allocation","type":"task","config":{"action":"allocate"},"order":3},
        {"id":"WFS-104","name":"Picking & Packing","type":"task","config":{"action":"pick_pack"},"order":4},
        {"id":"WFS-105","name":"Ship Order","type":"task","config":{"action":"ship"},"order":5},
    ],
    "WF-002":[
        {"id":"WFS-201","name":"Return Request","type":"trigger","config":{"event":"return.requested"},"order":1},
        {"id":"WFS-202","name":"Inspection","type":"task","config":{"action":"inspect"},"order":2},
        {"id":"WFS-203","name":"Approval","type":"condition","config":{"check":"inspection_passed"},"order":3},
        {"id":"WFS-204","name":"Refund Processing","type":"task","config":{"action":"refund"},"order":4},
    ],
    "WF-003":[
        {"id":"WFS-301","name":"Sample Selection","type":"task","config":{"action":"select_sample"},"order":1},
        {"id":"WFS-302","name":"Quality Test","type":"task","config":{"action":"test"},"order":2},
        {"id":"WFS-303","name":"Result Review","type":"condition","config":{"check":"test_passed"},"order":3},
    ],
    "WF-004":[],
    "WF-005":[],
}
WORKFLOW_EXECUTIONS = [
    {"id":"WFE-001","workflowId":"WF-001","status":"completed","triggeredBy":"system","startedAt":"2026-07-01T08:00:00Z","completedAt":"2026-07-01T08:30:00Z","context":{"orderId":"ORD-10000"}},
    {"id":"WFE-002","workflowId":"WF-001","status":"in_progress","triggeredBy":"system","startedAt":"2026-07-02T09:00:00Z","completedAt":None,"context":{"orderId":"ORD-10005"}},
    {"id":"WFE-003","workflowId":"WF-002","status":"completed","triggeredBy":"customer","startedAt":"2026-06-28T10:00:00Z","completedAt":"2026-06-28T11:00:00Z","context":{"returnId":"RMA-10001"}},
    {"id":"WFE-004","workflowId":"WF-002","status":"failed","triggeredBy":"customer","startedAt":"2026-06-29T14:00:00Z","completedAt":"2026-06-29T14:30:00Z","context":{"returnId":"RMA-10003"},"error":"Inspection failed"},
]

SUPPLIERS = [
    {"id":"SUP-001","name":"Raw Materials Inc","contact":"John Doe","email":"john@rawmaterials.com","phone":"+1-212-555-0201","category":"raw_materials","status":"active","rating":4.5,"paymentTerms":"net30","leadTimeDays":14,"address":"100 Industrial Blvd, Newark, NJ 07101"},
    {"id":"SUP-002","name":"Packaging Pro Ltd","contact":"Jane Smith","email":"jane@packagingpro.com","phone":"+1-312-555-0202","category":"packaging","status":"active","rating":4.2,"paymentTerms":"net45","leadTimeDays":7,"address":"200 Commerce Dr, Chicago, IL 60601"},
    {"id":"SUP-003","name":"Global Logistics Co","contact":"Bob Wilson","email":"bob@glogistics.com","phone":"+1-305-555-0203","category":"logistics","status":"active","rating":4.0,"paymentTerms":"net30","leadTimeDays":21,"address":"300 Shipping Ln, Miami, FL 33101"},
    {"id":"SUP-004","name":"Quality Parts Supply","contact":"Alice Brown","email":"alice@qps.com","phone":"+1-415-555-0204","category":"components","status":"inactive","rating":3.8,"paymentTerms":"net15","leadTimeDays":30,"address":"400 Industrial Pkwy, San Francisco, CA 94101"},
]
SUPPLIER_CONTACTS_DB = {
    "SUP-001":[
        {"id":"SC-001","name":"John Doe","email":"john@rawmaterials.com","phone":"+1-212-555-0201","role":"Primary","isPrimary":True},
        {"id":"SC-002","name":"Jane Roe","email":"jane@rawmaterials.com","phone":"+1-212-555-0205","role":"Sales","isPrimary":False},
    ],
    "SUP-002":[{"id":"SC-003","name":"Jane Smith","email":"jane@packagingpro.com","phone":"+1-312-555-0202","role":"Primary","isPrimary":True}],
    "SUP-003":[{"id":"SC-004","name":"Bob Wilson","email":"bob@glogistics.com","phone":"+1-305-555-0203","role":"Primary","isPrimary":True}],
    "SUP-004":[{"id":"SC-005","name":"Alice Brown","email":"alice@qps.com","phone":"+1-415-555-0204","role":"Primary","isPrimary":True}],
}
SUPPLIER_CONTRACTS_DB = {
    "SUP-001":[
        {"id":"SCT-001","supplierId":"SUP-001","title":"Annual Raw Materials Agreement","startDate":"2026-01-01","endDate":"2026-12-31","value":500000,"status":"active"},
    ],
    "SUP-002":[{"id":"SCT-002","supplierId":"SUP-002","title":"Packaging Supply Contract","startDate":"2026-03-01","endDate":"2027-02-28","value":200000,"status":"active"}],
    "SUP-003":[],
    "SUP-004":[{"id":"SCT-003","supplierId":"SUP-004","title":"Component Supply","startDate":"2025-06-01","endDate":"2026-06-01","value":150000,"status":"expired"}],
}
PURCHASE_REQUESTS = [
    {"id":"PR-001","title":"Bulk Raw Materials","requester":"Mike Warehouse","department":"warehouse","items":[{"sku":"SKU-ACE-SKN-001","name":"Radiance Renewal Serum","qty":500,"unitPrice":32.00,"total":16000.00}],"total":16000.00,"status":"approved","priority":"high","createdAt":"2026-06-28T10:00:00Z","updatedAt":"2026-06-29T14:00:00Z"},
    {"id":"PR-002","title":"Packaging Materials","requester":"Sarah Manager","department":"operations","items":[{"sku":"PKG-BOX-001","name":"Standard Shipping Box","qty":2000,"unitPrice":0.75,"total":1500.00}],"total":2500.00,"status":"pending","priority":"medium","createdAt":"2026-07-01T09:00:00Z","updatedAt":None},
    {"id":"PR-003","title":"Office Supplies","requester":"Lisa Finance","department":"admin","items":[],"total":500.00,"status":"draft","priority":"low","createdAt":"2026-07-01T08:00:00Z","updatedAt":None},
]
RFQS = [
    {"id":"RFQ-001","title":"Bulk Raw Materials Q2","description":"Quarterly raw materials procurement","deadline":"2026-07-15","status":"open","items":[{"sku":"SKU-ACE-SKN-001","name":"Radiance Renewal Serum","qty":1000,"unit":"EA"}],"totalEstimate":35000.00,"responsesCount":0,"createdAt":"2026-07-01T09:00:00Z"},
    {"id":"RFQ-002","title":"Packaging Q3","description":"Q3 packaging materials","deadline":"2026-07-30","status":"draft","items":[{"sku":"PKG-BOX-001","name":"Standard Box","qty":5000,"unit":"EA"}],"totalEstimate":5000.00,"responsesCount":0,"createdAt":"2026-07-01T10:00:00Z"},
]
RFQ_RESPONSES_DB = {}
PURCHASE_ORDERS = [
    {"id":"PO-001","supplierId":"SUP-001","supplierName":"Raw Materials Inc","items":[{"sku":"SKU-ACE-SKN-001","name":"Radiance Renewal Serum","qty":500,"unitPrice":30.00,"total":15000.00}],"total":15000.00,"status":"approved","paymentTerms":"net30","expectedDelivery":"2026-07-20","createdAt":"2026-07-01T08:00:00Z","updatedAt":"2026-07-01T09:00:00Z"},
    {"id":"PO-002","supplierId":"SUP-002","supplierName":"Packaging Pro Ltd","items":[{"sku":"PKG-BOX-001","name":"Standard Box","qty":2000,"unitPrice":0.70,"total":1400.00}],"total":1400.00,"status":"pending","paymentTerms":"net45","expectedDelivery":"2026-07-15","createdAt":"2026-07-02T10:00:00Z","updatedAt":None},
    {"id":"PO-003","supplierId":"SUP-003","supplierName":"Global Logistics Co","items":[],"total":0,"status":"draft","paymentTerms":"net30","expectedDelivery":None,"createdAt":"2026-07-02T11:00:00Z","updatedAt":None},
]

DOCUMENTS = [
    {"id":"DOC-001","name":"Warehouse Safety Guidelines","type":"policy","format":"pdf","size":245000,"tags":["safety","warehouse"],"createdAt":"2026-06-01T10:00:00Z","updatedAt":"2026-06-15T14:00:00Z"},
    {"id":"DOC-002","name":"Product Catalog Q3","type":"catalog","format":"xlsx","size":5200000,"tags":["products","catalog"],"createdAt":"2026-06-20T08:00:00Z","updatedAt":"2026-06-20T08:00:00Z"},
    {"id":"DOC-003","name":"Employee Onboarding Checklist","type":"hr","format":"pdf","size":180000,"tags":["hr","onboarding"],"createdAt":"2026-05-01T09:00:00Z","updatedAt":"2026-05-15T11:00:00Z"},
    {"id":"DOC-004","name":"Shipping Label Template","type":"template","format":"zpl","size":4500,"tags":["shipping","labels"],"createdAt":"2026-06-10T13:00:00Z","updatedAt":"2026-06-10T13:00:00Z"},
    {"id":"DOC-005","name":"Audit Report June 2026","type":"report","format":"pdf","size":1800000,"tags":["audit","finance"],"createdAt":"2026-07-01T16:00:00Z","updatedAt":"2026-07-01T16:00:00Z"},
]
DOCUMENT_VERSIONS_DB = {
    "DOC-001":[
        {"id":"DV-001","documentId":"DOC-001","version":1,"note":"Initial version","size":245000,"uploadedBy":"Admin","uploadedAt":"2026-06-01T10:00:00Z"},
        {"id":"DV-002","documentId":"DOC-001","version":2,"note":"Updated safety protocols","size":248000,"uploadedBy":"Admin","uploadedAt":"2026-06-15T14:00:00Z"},
    ],
    "DOC-002":[{"id":"DV-003","documentId":"DOC-002","version":1,"note":"Initial catalog","size":5200000,"uploadedBy":"Admin","uploadedAt":"2026-06-20T08:00:00Z"}],
    "DOC-003":[],
    "DOC-004":[{"id":"DV-004","documentId":"DOC-004","version":1,"note":"Initial template","size":4500,"uploadedBy":"Admin","uploadedAt":"2026-06-10T13:00:00Z"}],
    "DOC-005":[],
}

EDI_DOCUMENTS = [
    {"id":"EDI-001","type":"850","direction":"inbound","partnerId":"EP-001","partnerName":"Retail Partner A","status":"received","filename":"PO_850_20260701.edi","createdAt":"2026-07-01T08:30:00Z","processedAt":None,"errors":[],"orderCount":3},
    {"id":"EDI-002","type":"856","direction":"outbound","partnerId":"EP-002","partnerName":"Distributor B","status":"generated","filename":"ASN_856_20260701.edi","createdAt":"2026-07-01T09:00:00Z","processedAt":"2026-07-01T09:05:00Z","errors":[],"orderCount":5},
    {"id":"EDI-003","type":"810","direction":"outbound","partnerId":"EP-001","partnerName":"Retail Partner A","status":"error","filename":"INV_810_20260701.edi","createdAt":"2026-07-01T10:00:00Z","processedAt":None,"errors":["Missing tax ID in header"],"orderCount":0},
    {"id":"EDI-004","type":"850","direction":"inbound","partnerId":"EP-003","partnerName":"Wholesale Chain C","status":"pending","filename":"PO_850_20260702.edi","createdAt":"2026-07-02T07:00:00Z","processedAt":None,"errors":[],"orderCount":0},
]
EDI_PARTNERS_DB = [
    {"id":"EP-001","name":"Retail Partner A","ediId":"RETAILA01","qualifier":"01","type":"retail","status":"active","version":"4010","createdAt":"2026-01-15T00:00:00Z"},
    {"id":"EP-002","name":"Distributor B","ediId":"DISTB02","qualifier":"01","type":"distributor","status":"active","version":"4010","createdAt":"2026-02-01T00:00:00Z"},
    {"id":"EP-003","name":"Wholesale Chain C","ediId":"WHOLEC03","qualifier":"01","type":"wholesale","status":"active","version":"5010","createdAt":"2026-03-01T00:00:00Z"},
]

EMAIL_PARSED_ORDERS = [
    {"id":"EPO-001","subject":"New Order #1001","sender":"customer@email.com","receivedAt":"2026-07-01T08:00:00Z","items":[{"sku":"SKU-ACE-SKN-001","name":"Radiance Renewal Serum","qty":2}],"status":"pending","confidence":0.92},
    {"id":"EPO-002","subject":"Re: Order Status","sender":"vendor@supply.com","receivedAt":"2026-07-01T09:30:00Z","items":[{"sku":"SKU-ACE-HAIR-002","name":"Argan Oil Hair Mask","qty":5}],"status":"approved","confidence":0.85},
    {"id":"EPO-003","subject":"Fwd: Purchase Request","sender":"manager@company.com","receivedAt":"2026-07-01T10:00:00Z","items":[],"status":"rejected","confidence":0.45,"error":"Could not parse order details"},
    {"id":"EPO-004","subject":"Order Update Needed","sender":"client@biz.com","receivedAt":"2026-07-02T07:00:00Z","items":[{"sku":"SKU-ACE-TOOL-001","name":"Professional Hair Dryer","qty":3}],"status":"pending","confidence":0.78},
]

AI_MODELS = [
    {"id":"AI-M-001","name":"Demand Forecaster Pro","description":"Predicts product demand using historical sales","version":"2.1.0","category":"forecasting","status":"active","accuracy":94.2,"latencyMs":145,"lastTrained":"2026-06-28T10:00:00Z","tenantId":"nexus-main","isGlobal":True},
    {"id":"AI-M-002","name":"Smart Allocator","description":"Optimal inventory allocation across warehouses","version":"1.5.0","category":"allocation","status":"active","accuracy":91.8,"latencyMs":210,"lastTrained":"2026-06-25T08:00:00Z","tenantId":"nexus-main","isGlobal":True},
    {"id":"AI-M-003","name":"Carrier Optimizer","description":"Selects best carrier based on cost and delivery","version":"3.0.0","category":"routing","status":"active","accuracy":96.5,"latencyMs":85,"lastTrained":"2026-07-01T12:00:00Z","tenantId":"nexus-main","isGlobal":True},
    {"id":"AI-M-004","name":"Fraud Detector","description":"Identifies potentially fraudulent orders","version":"1.2.0","category":"security","status":"active","accuracy":88.3,"latencyMs":320,"lastTrained":"2026-06-20T09:00:00Z","tenantId":"nexus-main","isGlobal":True},
    {"id":"AI-M-005","name":"Returns Predictor","description":"Predicts likelihood of returns","version":"1.0.0","category":"returns","status":"training","accuracy":None,"latencyMs":None,"lastTrained":None,"tenantId":"nexus-main","isGlobal":False},
    {"id":"AI-M-006","name":"Custom Picker Optimizer","description":"Tenant-specific picking route optimization","version":"1.0.0","category":"picking","status":"active","accuracy":87.1,"latencyMs":175,"lastTrained":"2026-06-30T14:00:00Z","tenantId":"nexus-demo","isGlobal":False},
]
AI_MODEL_VERSIONS_DB = {
    "AI-M-001":[{"id":"AIV-001","modelId":"AI-M-001","version":"1.0.0","description":"Initial release","status":"archived","createdAt":"2026-01-15T10:00:00Z"},{"id":"AIV-002","modelId":"AI-M-001","version":"2.0.0","description":"Improved accuracy","status":"archived","createdAt":"2026-03-20T10:00:00Z"},{"id":"AIV-003","modelId":"AI-M-001","version":"2.1.0","description":"Current production","status":"active","createdAt":"2026-06-28T10:00:00Z"}],
    "AI-M-002":[{"id":"AIV-004","modelId":"AI-M-002","version":"1.0.0","description":"Initial","status":"archived","createdAt":"2026-02-01T10:00:00Z"},{"id":"AIV-005","modelId":"AI-M-002","version":"1.5.0","description":"Current","status":"active","createdAt":"2026-06-25T08:00:00Z"}],
    "AI-M-003":[{"id":"AIV-006","modelId":"AI-M-003","version":"1.0.0","description":"Initial","status":"archived","createdAt":"2026-03-01T10:00:00Z"},{"id":"AIV-007","modelId":"AI-M-003","version":"2.0.0","description":"Improved","status":"archived","createdAt":"2026-05-01T10:00:00Z"},{"id":"AIV-008","modelId":"AI-M-003","version":"3.0.0","description":"Current","status":"active","createdAt":"2026-07-01T12:00:00Z"}],
    "AI-M-004":[{"id":"AIV-009","modelId":"AI-M-004","version":"1.0.0","description":"Initial","status":"archived","createdAt":"2026-04-01T10:00:00Z"},{"id":"AIV-010","modelId":"AI-M-004","version":"1.2.0","description":"Current","status":"active","createdAt":"2026-06-20T09:00:00Z"}],
    "AI-M-005":[],
    "AI-M-006":[{"id":"AIV-011","modelId":"AI-M-006","version":"1.0.0","description":"Initial","status":"active","createdAt":"2026-06-30T14:00:00Z"}],
}
AI_FEATURES = [
    {"id":"AIF-001","name":"sales_history_days","group":"demand","type":"numeric","description":"Number of days of sales history","defaultValue":90},
    {"id":"AIF-002","name":"seasonality_factor","group":"demand","type":"numeric","description":"Seasonal adjustment factor","defaultValue":1.0},
    {"id":"AIF-003","name":"warehouse_proximity","group":"routing","type":"categorical","description":"Distance to nearest warehouse","defaultValue":"near"},
    {"id":"AIF-004","name":"carrier_reliability","group":"routing","type":"numeric","description":"Historical carrier reliability score","defaultValue":0.95},
    {"id":"AIF-005","name":"order_value_tier","group":"security","type":"categorical","description":"Order value risk tier","defaultValue":"standard"},
]
AI_TRAINING_JOBS = [
    {"id":"AITJ-001","modelId":"AI-M-001","status":"completed","progress":100,"startedAt":"2026-06-28T08:00:00Z","completedAt":"2026-06-28T10:00:00Z","accuracy":94.2,"datasetSize":50000},
    {"id":"AITJ-002","modelId":"AI-M-003","status":"completed","progress":100,"startedAt":"2026-07-01T10:00:00Z","completedAt":"2026-07-01T12:00:00Z","accuracy":96.5,"datasetSize":35000},
    {"id":"AITJ-003","modelId":"AI-M-005","status":"running","progress":45,"startedAt":"2026-07-02T06:00:00Z","completedAt":None,"accuracy":None,"datasetSize":12000},
]
AI_INFERENCE_LOGS = [
    {"id":"AIIL-001","modelId":"AI-M-001","input":"{product: SKU-ACE-SKN-001, date: 2026-07-05}","output":"{predicted_demand: 42}","latencyMs":145,"timestamp":"2026-07-01T08:00:00Z"},
    {"id":"AIIL-002","modelId":"AI-M-001","input":"{product: SKU-ACE-MKP-002, date: 2026-07-05}","output":"{predicted_demand: 78}","latencyMs":132,"timestamp":"2026-07-01T08:05:00Z"},
    {"id":"AIIL-003","modelId":"AI-M-003","input":"{origin: Mumbai, destination: New York, weight: 5.2}","output":"{carrier: FedEx, cost: 29.99, eta: 2d}","latencyMs":85,"timestamp":"2026-07-01T08:10:00Z"},
]

INTEGRATION_STORES = [
    {"id":"IS-001","storeCode":"SHOP-001","storeName":"Glow & Co. Boutique","platform":"SHOPIFY","currency":"USD","defaultLocale":"en_US","timezone":"UTC","externalStoreId":"shopify-store-001","externalDomain":"glowandco.myshopify.com","status":"connected","lastSyncAt":"2026-07-01T12:00:00Z","settings":{}},
    {"id":"IS-002","storeCode":"AMZ-001","storeName":"Beauty Bliss Amazon Store","platform":"AMAZON","currency":"USD","defaultLocale":"en_US","timezone":"America/Chicago","externalStoreId":"amz-store-001","externalDomain":"amazon.com","status":"connected","lastSyncAt":"2026-07-01T11:00:00Z","settings":{}},
    {"id":"IS-003","storeCode":"BC-001","storeName":"Aria Beauty BigCommerce","platform":"BIGCOMMERCE","currency":"USD","defaultLocale":"en_US","timezone":"America/Chicago","externalStoreId":"bc-store-001","externalDomain":"ariabeauty.mybigcommerce.com","status":"disconnected","lastSyncAt":"2026-06-30T15:00:00Z","settings":{}},
    {"id":"IS-004","storeCode":"EBAY-001","storeName":"Luxe Beauty eBay","platform":"EBAY","currency":"USD","defaultLocale":"en_US","timezone":"America/New_York","externalStoreId":"ebay-store-001","externalDomain":"ebay.com","status":"error","lastSyncAt":"2026-06-29T10:00:00Z","settings":{}},
]
INTEGRATION_STORE_SETTINGS_DB = {
    "IS-001":[{"key":"api_key","value":"sk_shopify_xxxx","encrypted":True},{"key":"webhook_url","value":"https://nexusoms.com/webhooks/shopify","encrypted":False}],
    "IS-002":[{"key":"aws_access_key","value":"AKIAXXXX","encrypted":True},{"key":"marketplace_id","value":"ATVPDKIKX0DER","encrypted":False}],
    "IS-003":[{"key":"api_token","value":"bc_token_xxxx","encrypted":True}],
    "IS-004":[],
}

ROUTING_RULES = [
    {"id":"RR-001","name":"West Coast Orders","description":"Route west coast orders to Bangalore","priority":1,"conditions":[{"field":"shipping_state","operator":"in","value":["CA","OR","WA","NV","AZ"]}],"action":{"warehouse":"WH-03","carrier":"UPS"},"status":"active","createdAt":"2026-06-01T00:00:00Z"},
    {"id":"RR-002","name":"International Priority","description":"Route international orders via DHL","priority":2,"conditions":[{"field":"shipping_country","operator":"not_equal","value":"US"}],"action":{"carrier":"DHL","service":"EXPRESS"},"status":"active","createdAt":"2026-06-01T00:00:00Z"},
    {"id":"RR-003","name":"High Value Orders","description":"Use expedited shipping for orders over $1000","priority":3,"conditions":[{"field":"order_total","operator":"greater_than","value":1000}],"action":{"carrier":"FEDEX","service":"2DAY"},"status":"active","createdAt":"2026-06-05T00:00:00Z"},
    {"id":"RR-004","name":"B2B Partner Route","description":"Route B2B orders to nearest warehouse","priority":4,"conditions":[{"field":"channel","operator":"in","value":["b2b-portal","amazon"]}],"action":{"warehouse":"WH-01"},"status":"inactive","createdAt":"2026-06-10T00:00:00Z"},
]
ORDER_ALLOCATIONS = [
    {"id":"OA-001","orderId":"ORD-10000","warehouseId":"WH-01","carrier":"FEDEX","service":"GROUND","estimatedDelivery":"2026-07-06T08:30:00Z","status":"active","createdAt":"2026-07-02T08:30:00Z"},
    {"id":"OA-002","orderId":"ORD-10005","warehouseId":"WH-01","carrier":"USPS","service":"PRIORITY","estimatedDelivery":"2026-07-03T16:45:00Z","status":"active","createdAt":"2026-07-01T16:45:00Z"},
    {"id":"OA-003","orderId":"ORD-10009","warehouseId":"WH-02","carrier":"FEDEX","service":"GROUND","estimatedDelivery":"2026-07-05T10:00:00Z","status":"active","createdAt":"2026-07-01T10:00:00Z"},
]
ROUTING_EXCEPTIONS = [
    {"id":"RE-001","orderId":"ORD-10008","type":"address_validation","description":"Invalid ZIP code","severity":"high","status":"open","createdAt":"2026-06-27T15:00:00Z"},
    {"id":"RE-002","orderId":"ORD-10003","type":"carrier_restriction","description":"Fragile items - carrier restriction","severity":"medium","status":"resolved","createdAt":"2026-06-30T09:00:00Z","resolvedAt":"2026-06-30T11:00:00Z"},
    {"id":"RE-003","orderId":"ORD-10020","type":"insufficient_inventory","description":"Not enough stock for allocation","severity":"critical","status":"escalated","createdAt":"2026-06-28T09:00:00Z","resolvedAt":None},
]

WAREHOUSE_ZONES_DB = []
for wh in WAREHOUSES:
    for z in range(wh["zones"]):
        WAREHOUSE_ZONES_DB.append({"id":f"{wh['id']}-Z{z+1}","warehouseId":wh["id"],"warehouse":wh["name"],"code":f"Z{z+1}","status":"active","capacity":random.randint(2000,8000),"used":random.randint(500,7500)})
WAREHOUSE_BINS_DB = [{"id":f"{wh['id']}-B{b+1}","warehouseId":wh["id"],"zoneCode":f"Z{b%3+1}","code":f"{['A','B','C','D'][b%4]}{b+1}-{'01' if b%2==0 else '02'}-{['A','B','C','D'][b%4]}","status":random.choice(["occupied","empty","reserved"]),"capacity":random.randint(10,100),"used":random.randint(0,80)} for wh in WAREHOUSES[:3] for b in range(6)]
WAREHOUSE_STAFF_DB = [{"id":f"WS-{i}","name":f"Staff Member {i}","role":random.choice(["Picker","Packer","Loader","Supervisor"]),"status":random.choice(["active","break","off"]),"warehouseId":random.choice(WAREHOUSES)["id"],"zone":random.choice(["A1","B2","C3","D4"]),"efficiency":random.randint(70,100)} for i in range(1,13)]
WAREHOUSE_EQUIPMENT_DB = [{"id":f"WE-{i}","name":f"{random.choice(['Forklift','Pallet Jack','Scanner','Conveyor Belt','Dolly'])} #{i}","type":random.choice(["forklift","pallet_jack","scanner","conveyor","dolly"]),"status":random.choice(["available","in_use","maintenance"]),"warehouseId":random.choice(WAREHOUSES)["id"]} for i in range(1,13)]

NOTIFICATION_TEMPLATES = [
    {"id":"NT-001","name":"Order Confirmation","subject":"Your order #{{orderNumber}} has been confirmed","body":"Dear {{customerName}}, your order has been confirmed.","type":"email","variables":["orderNumber","customerName"],"createdAt":"2026-01-01T00:00:00Z"},
    {"id":"NT-002","name":"Shipping Update","subject":"Your order #{{orderNumber}} has shipped","body":"Your order is on its way! Tracking: {{trackingNumber}}","type":"email","variables":["orderNumber","trackingNumber"],"createdAt":"2026-01-01T00:00:00Z"},
    {"id":"NT-003","name":"Low Stock Alert","subject":"LOW STOCK: {{productName}}","body":"Product {{productName}} has only {{qty}} units remaining.","type":"in_app","variables":["productName","qty"],"createdAt":"2026-01-01T00:00:00Z"},
]
NOTIFICATION_LOGS = [
    {"id":"NL-001","templateId":"NT-001","recipient":"orders@glowandco.com","channel":"email","status":"sent","sentAt":"2026-07-02T08:35:00Z"},
    {"id":"NL-002","templateId":"NT-002","recipient":"sophia.m@email.com","channel":"email","status":"sent","sentAt":"2026-07-01T16:50:00Z"},
    {"id":"NL-003","templateId":"NT-003","recipient":"admin@nexusoms.com","channel":"in_app","status":"failed","sentAt":"2026-07-01T09:05:00Z","error":"Rate limit exceeded"},
]
ALERT_RULES = [
    {"id":"AR-001","name":"Low Stock","condition":"inventory.qty < 20","severity":"warning","enabled":True,"channels":["in_app","email"],"createdAt":"2026-01-01T00:00:00Z"},
    {"id":"AR-002","name":"Order Anomaly","condition":"order.total > 5000","severity":"critical","enabled":True,"channels":["in_app","sms"],"createdAt":"2026-01-01T00:00:00Z"},
    {"id":"AR-003","name":"Carrier Delay","condition":"shipment.delay > 24h","severity":"warning","enabled":False,"channels":["in_app"],"createdAt":"2026-01-01T00:00:00Z"},
]

INVENTORY_RECEIPTS = [
    {"id":"IR-001","sku":"SKU-ACE-SKN-001","name":"Radiance Renewal Serum","qty":200,"expectedDate":"2026-07-03","status":"pending","supplier":"Raw Materials Inc","poNumber":"PO-001","notes":"","createdAt":"2026-07-01T08:00:00Z"},
    {"id":"IR-002","sku":"SKU-ACE-HAIR-001","name":"Keratin Repair Shampoo","qty":500,"expectedDate":"2026-07-05","status":"in_transit","supplier":"Global Logistics Co","poNumber":"PO-003","notes":"Multiple pallets","createdAt":"2026-07-01T09:00:00Z"},
    {"id":"IR-003","sku":"SKU-ACE-MKP-002","name":"Velvet Matte Lipstick - Rose","qty":1000,"expectedDate":"2026-07-02","status":"received","supplier":"Packaging Pro Ltd","poNumber":"PO-002","notes":"","createdAt":"2026-06-30T10:00:00Z"},
]
CYCLE_COUNTS_DB = [
    {"id":"CC-001","zone":"A1","sku":"SKU-ACE-SKN-001","name":"Radiance Renewal Serum","expected":420,"actual":418,"status":"completed","countedBy":"Raj Patel","countedAt":"2026-07-01T08:00:00Z","discrepancy":-2},
    {"id":"CC-002","zone":"B2","sku":"SKU-ACE-SKN-003","name":"Vitamin C Brightening Cream","expected":340,"actual":340,"status":"completed","countedBy":"Amit Singh","countedAt":"2026-07-01T09:00:00Z","discrepancy":0},
    {"id":"CC-003","zone":"A1","sku":"SKU-ACE-MKP-005","name":"Contour Palette Pro","expected":180,"actual":176,"status":"in_progress","countedBy":None,"countedAt":None,"discrepancy":None},
]

# ──────────────── JWT Helpers ────────────────
def create_jwt(username, role, name, email):
    header = base64.urlsafe_b64encode(json.dumps({"alg":"HS256","typ":"JWT"}).encode()).rstrip(b"=").decode()
    payload = base64.urlsafe_b64encode(json.dumps({
        "sub":username,"role":role,"name":name,"email":email,
        "iat":int(time.time()),"exp":int(time.time())+86400
    }).encode()).rstrip(b"=").decode()
    sig = base64.urlsafe_b64encode(hmac.new(JWT_SECRET.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest()).rstrip(b"=").decode()
    return f"{header}.{payload}.{sig}"

def verify_jwt(token):
    try:
        parts = token.split(".")
        if len(parts) != 3: return None
        expected = base64.urlsafe_b64encode(hmac.new(JWT_SECRET.encode(), f"{parts[0]}.{parts[1]}".encode(), hashlib.sha256).digest()).rstrip(b"=").decode()
        if parts[2] != expected: return None
        payload = json.loads(base64.urlsafe_b64decode(parts[1] + "=="))
        if payload["exp"] < time.time(): return None
        return payload
    except: return None

# ──────────────── Request Handler ────────────────
class NexusAPIHandler(BaseHTTPRequestHandler):
    def _json(self, data, status=200):
        self.send_response(status)
        self.send_header("Content-Type","application/json")
        self.send_header("Access-Control-Allow-Origin","*")
        self.send_header("Access-Control-Allow-Headers","Content-Type, Authorization")
        self.send_header("Access-Control-Allow-Methods","GET, POST, PUT, PATCH, DELETE, OPTIONS")
        self.end_headers()
        self.wfile.write(json.dumps(data, default=str).encode())

    def _read_body(self):
        try:
            length = int(self.headers.get("Content-Length", 0))
        except (ValueError, TypeError):
            length = 0
        if length > 0:
            try:
                return json.loads(self.rfile.read(length))
            except json.JSONDecodeError:
                return {}
        return {}

    def _error(self, msg, status=400):
        self._json({"success": False, "error": msg}, status)

    def _ok(self, data):
        self._json({"success": True, **data})

    SUB_RESOURCE_NAMES = frozenset([
        "zones","bins","staff","equipment","summary","kpis","rates","lists","queues",
        "models","features","experiments","templates","logs","alerts","unread-count",
        "analytics","reasons","dashboard","entity-types","formats","modes","history",
        "empty","groups","jobs",
    ])

    def _is_resource_id(self, s):
        if not s or len(s) > 64: return False
        if s in self.SUB_RESOURCE_NAMES: return False
        return True

    def _auth(self):
        auth = self.headers.get("Authorization","")
        if not auth.startswith("Bearer "): return None
        return verify_jwt(auth[7:])

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Content-Type","application/json")
        self.send_header("Access-Control-Allow-Origin","*")
        self.send_header("Access-Control-Allow-Headers","Content-Type, Authorization")
        self.send_header("Access-Control-Allow-Methods","GET, POST, PUT, PATCH, DELETE, OPTIONS")
        self.end_headers()
        self.wfile.write(b"{}")

    def _require_auth(self, roles=None):
        user = self._auth()
        if not user:
            self._error("Unauthorized", 401)
            return None
        if roles and user.get("role") not in roles:
            self._error("Forbidden", 403)
            return None
        return user

    def _parse_path(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/")
        qs = parse_qs(parsed.query)
        parts = [p for p in path.split("/") if p]
        return path, parts, qs

    # ──────────────── Router ────────────────
    def do_GET(self):
        path, parts, qs = self._parse_path()
        try:
            if parts[:3] == ["api","v1","health"]: return self._json({"success": True, "status": "healthy", "timestamp": datetime.now(timezone.utc).isoformat(), "version": "1.0.0"})
            if parts[:3] == ["api","v1","auth"]: return self._handle_auth_get(parts)
            user = self._require_auth()
            if not user: return
            if parts[:3] == ["api","v1","orders"]: return self._handle_orders_get(parts, qs)
            if parts[:3] == ["api","v1","products"]: return self._handle_products_get(parts, qs)
            if parts[:3] == ["api","v1","customers"]: return self._handle_customers_get(parts, qs)
            if parts[:3] == ["api","v1","inventory"]: return self._handle_inventory_get(parts, qs)
            if parts[:3] == ["api","v1","warehouse"]: return self._handle_warehouse_get(parts)
            if parts[:3] == ["api","v1","wave-plans"]: return self._handle_waves_get(parts, qs)
            if parts[:3] == ["api","v1","labor"]: return self._handle_labor_get(parts, qs)
            if parts[:3] == ["api","v1","picking"]: return self._handle_picking_get(parts, qs)
            if parts[:3] == ["api","v1","packing"]: return self._handle_packing_get(parts)
            if parts[:3] == ["api","v1","carriers"]: return self._handle_carriers_get(parts, qs)
            if parts[:3] == ["api","v1","labels"]: return self._handle_labels_get(parts)
            if parts[:3] == ["api","v1","manifests"]: return self._handle_manifests_get(parts)
            if parts[:3] == ["api","v1","returns"]: return self._handle_returns_get(parts, qs)
            if parts[:3] == ["api","v1","payments"]: return self._handle_payments_get(parts, qs)
            if parts[:3] == ["api","v1","invoices"]: return self._handle_invoices_get(parts, qs)
            if parts[:3] == ["api","v1","reconciliation"]: return self._handle_reconciliation_get(parts)
            if parts[:3] == ["api","v1","reports"]: return self._handle_reports_get(parts, qs)
            if parts[:3] == ["api","v1","task-queues"]: return self._handle_task_queues_get(parts)
            if parts[:3] == ["api","v1","notifications"]: return self._handle_notifications_get(parts)
            if parts[:3] == ["api","v1","integrations"]: return self._handle_integrations_get(parts)
            if parts[:3] == ["api","v1","settings"]: return self._handle_settings_get(parts)
            if parts[:3] == ["api","v1","dashboard"]: return self._handle_dashboard_get()
            if parts[:3] == ["api","v1","rbac"]: return self._handle_rbac_get(parts, qs)
            if parts[:3] == ["api","v1","ai"]: return self._handle_ai_get(parts, qs)
            if parts[:3] == ["api","v1","shipments"]: return self._handle_shipments_get(parts, qs)
            if parts[:3] == ["api","v1","shipping"]: return self._handle_shipping_get(parts, qs)
            if parts[:3] == ["api","v1","workflows"]: return self._handle_workflows_get(parts, qs)
            if parts[:3] == ["api","v1","procurement"]: return self._handle_procurement_get(parts, qs)
            if parts[:3] == ["api","v1","documents"]: return self._handle_documents_get(parts, qs)
            if parts[:3] == ["api","v1","edi"]: return self._handle_edi_get(parts, qs)
            if parts[:3] == ["api","v1","email-parser"]: return self._handle_email_parser_get(parts, qs)
            if parts[:3] == ["api","v1","integration-stores"]: return self._handle_integration_stores_get(parts, qs)
            if parts[:3] == ["api","v1","routing-rules"]: return self._handle_routing_rules_get(parts, qs)
            if parts[:3] == ["api","v1","order-routing"]: return self._handle_order_routing_get(parts, qs)
            if parts[:3] == ["api","v1","rate-shopping"]: return self._handle_rate_shopping_get(parts, qs)
            if parts[:3] == ["api","v1","integration-platform"]: return self._handle_integration_platform_get(parts, qs)
            if parts[:3] == ["api","v1","integration-hub"]: return self._handle_integration_hub_get(parts, qs)
            if parts[:3] == ["api","v1","inventory-receipts"]: return self._handle_inventory_receipts_get(parts, qs)
            if parts[:3] == ["api","v1","cycle-counts"]: return self._handle_cycle_counts_get(parts, qs)
            if parts[:3] == ["api","v1","import"]: return self._handle_import_get(parts, qs)
            self._error("Not Found", 404)
        except Exception as e:
            self._error(str(e), 500)

    def do_POST(self):
        path, parts, qs = self._parse_path()
        try:
            if parts[:3] == ["api","v1","auth"]: return self._handle_auth_post(parts)
            user = self._require_auth()
            if not user: return
            if parts[:3] == ["api","v1","orders"]: return self._handle_orders_post(parts, user)
            if parts[:3] == ["api","v1","products"]: return self._handle_products_post(parts)
            if parts[:3] == ["api","v1","customers"]: return self._handle_customers_post(parts)
            if parts[:3] == ["api","v1","inventory"]: return self._handle_inventory_post(parts)
            if parts[:3] == ["api","v1","wave-plans"]: return self._handle_waves_post(parts)
            if parts[:3] == ["api","v1","labor"]: return self._handle_labor_post(parts)
            if parts[:3] == ["api","v1","picking"]: return self._handle_picking_post(parts)
            if parts[:3] == ["api","v1","packing"]: return self._handle_packing_post(parts)
            if parts[:3] == ["api","v1","labels"]: return self._handle_labels_post(parts)
            if parts[:3] == ["api","v1","manifests"]: return self._handle_manifests_post(parts)
            if parts[:3] == ["api","v1","carriers"]: return self._handle_carriers_post(parts)
            if parts[:3] == ["api","v1","returns"]: return self._handle_returns_post(parts)
            if parts[:3] == ["api","v1","invoices"]: return self._handle_invoices_post(parts)
            if parts[:3] == ["api","v1","reports"]: return self._handle_reports_post(parts)
            if parts[:3] == ["api","v1","rbac"]: return self._handle_rbac_post(parts)
            if parts[:3] == ["api","v1","ai"]: return self._handle_ai_post(parts)
            if parts[:3] == ["api","v1","shipments"]: return self._handle_shipments_post(parts)
            if parts[:3] == ["api","v1","workflows"]: return self._handle_workflows_post(parts)
            if parts[:3] == ["api","v1","procurement"]: return self._handle_procurement_post(parts)
            if parts[:3] == ["api","v1","documents"]: return self._handle_documents_post(parts)
            if parts[:3] == ["api","v1","edi"]: return self._handle_edi_post(parts)
            if parts[:3] == ["api","v1","email-parser"]: return self._handle_email_parser_post(parts)
            if parts[:3] == ["api","v1","integration-stores"]: return self._handle_integration_stores_post(parts)
            if parts[:3] == ["api","v1","routing-rules"]: return self._handle_routing_rules_post(parts)
            if parts[:3] == ["api","v1","order-routing"]: return self._handle_order_routing_post(parts)
            if parts[:3] == ["api","v1","rate-shopping"]: return self._handle_rate_shopping_post(parts)
            if parts[:3] == ["api","v1","integration-platform"]: return self._handle_integration_platform_post(parts)
            if parts[:3] == ["api","v1","integration-hub"]: return self._handle_integration_hub_post(parts)
            if parts[:3] == ["api","v1","inventory-receipts"]: return self._handle_inventory_receipts_post(parts)
            if parts[:3] == ["api","v1","warehouse"]: return self._handle_warehouse_post(parts)
            if parts[:3] == ["api","v1","cycle-counts"]: return self._handle_cycle_counts_post(parts)
            if parts[:3] == ["api","v1","import"]: return self._handle_import_post(parts)
            if parts[:3] == ["api","v1","notifications"]: return self._handle_notifications_post(parts)
            if parts[:3] == ["api","v1","integrations"]: return self._handle_integrations_post(parts)
            if parts[:3] == ["api","v1","shopify"]: return self._ok({"success": True, "syncResult": {"status": "completed", "synced": 10, "errors": 0}})
            self._error("Not Found", 404)
        except Exception as e:
            self._error(str(e), 500)

    def do_PUT(self):
        path, parts, qs = self._parse_path()
        try:
            user = self._require_auth()
            if not user: return
            if parts[:3] == ["api","v1","orders"]: return self._handle_orders_put(parts)
            if parts[:3] == ["api","v1","products"]: return self._handle_products_put(parts)
            if parts[:3] == ["api","v1","customers"]: return self._handle_customers_put(parts)
            if parts[:3] == ["api","v1","settings"]: return self._handle_settings_put(parts)
            if parts[:3] == ["api","v1","shipments"]: return self._handle_shipments_put(parts)
            if parts[:3] == ["api","v1","workflows"]: return self._handle_workflows_put(parts)
            if parts[:3] == ["api","v1","procurement"]: return self._handle_procurement_put(parts)
            if parts[:3] == ["api","v1","documents"]: return self._handle_documents_put(parts)
            if parts[:3] == ["api","v1","carriers"]: return self._handle_carriers_put(parts)
            if parts[:3] == ["api","v1","warehouse"]: return self._handle_warehouse_put(parts)
            if parts[:3] == ["api","v1","notifications"]: return self._handle_notifications_put(parts)
            if parts[:3] == ["api","v1","integration-stores"]: return self._handle_integration_stores_put(parts)
            if parts[:3] == ["api","v1","routing-rules"]: return self._handle_routing_rules_put(parts)
            self._error("Not Found", 404)
        except Exception as e:
            self._error(str(e), 500)

    def do_PATCH(self):
        path, parts, qs = self._parse_path()
        try:
            user = self._require_auth()
            if not user: return
            if parts[:3] == ["api","v1","orders"]: return self._handle_orders_patch(parts, user)
            if parts[:3] == ["api","v1","wave-plans"]: return self._handle_waves_patch(parts)
            if parts[:3] == ["api","v1","picking"]: return self._handle_picking_patch(parts)
            if parts[:3] == ["api","v1","returns"]: return self._handle_returns_patch(parts)
            if parts[:3] == ["api","v1","invoices"]: return self._handle_invoices_patch(parts)
            if parts[:3] == ["api","v1","manifests"]: return self._handle_manifests_patch(parts)
            if parts[:3] == ["api","v1","task-queues"]: return self._handle_task_queues_patch(parts)
            if parts[:3] == ["api","v1","documents"]: return self._handle_documents_patch(parts)
            if parts[:3] == ["api","v1","settings"]: return self._handle_settings_put(parts)
            self._error("Not Found", 404)
        except Exception as e:
            self._error(str(e), 500)

    def do_DELETE(self):
        path, parts, qs = self._parse_path()
        try:
            user = self._require_auth()
            if not user: return
            if len(parts) >= 4:
                id = parts[3]
                if parts[:3] == ["api","v1","orders"]:
                    item = next((o for o in ORDERS if o["id"] == id), None)
                    if not item: return self._error("Order not found", 404)
                    ORDERS.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","products"]:
                    item = next((p for p in PRODUCTS if p["id"] == id), None)
                    if not item: return self._error("Product not found", 404)
                    PRODUCTS.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","customers"]:
                    item = next((c for c in CUSTOMERS if c["id"] == id), None)
                    if not item: return self._error("Customer not found", 404)
                    CUSTOMERS.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","returns"]:
                    item = next((r for r in RETURNS if r["id"] == id), None)
                    if not item: return self._error("Return not found", 404)
                    RETURNS.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","invoices"]:
                    item = next((i for i in INVOICES if i["id"] == id), None)
                    if not item: return self._error("Invoice not found", 404)
                    INVOICES.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","manifests"]:
                    item = next((m for m in MANIFESTS if m["id"] == id), None)
                    if not item: return self._error("Manifest not found", 404)
                    MANIFESTS.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","task-queues"]:
                    item = next((t for t in TASK_QUEUES if t["id"] == id), None)
                    if not item: return self._error("Task queue not found", 404)
                    TASK_QUEUES.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","notifications"]:
                    item = next((n for n in NOTIFICATIONS if n["id"] == id), None)
                    if not item: return self._error("Notification not found", 404)
                    NOTIFICATIONS.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","labels"]:
                    item = next((l for l in LABELS_HISTORY if l["id"] == id), None)
                    if not item: return self._error("Label not found", 404)
                    LABELS_HISTORY.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","wave-plans"]:
                    item = next((w for w in WAVE_PLANS if w["id"] == id), None)
                    if not item: return self._error("Wave plan not found", 404)
                    WAVE_PLANS.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","inventory"]:
                    item = next((p for p in PRODUCTS if p["sku"] == id), None)
                    if not item: return self._error("Inventory item not found", 404)
                    PRODUCTS.remove(item)
                    for wh in INVENTORY_BY_WAREHOUSE:
                        INVENTORY_BY_WAREHOUSE[wh].pop(id, None)
                    return self._ok({"data": {"deleted": True, "sku": id}})
                if parts[:3] == ["api","v1","shipments"]:
                    item = next((s for s in SHIPMENTS if s["id"] == id), None)
                    if not item: return self._error("Shipment not found", 404)
                    SHIPMENTS.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","documents"]:
                    item = next((d for d in DOCUMENTS if d["id"] == id), None)
                    if not item: return self._error("Document not found", 404)
                    DOCUMENTS.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","carriers"]:
                    return self._handle_carriers_delete(parts)
                if parts[:3] == ["api","v1","warehouse"]:
                    wh = next((w for w in WAREHOUSES if w["id"] == id), None)
                    if not wh: return self._error("Warehouse not found", 404)
                    WAREHOUSES.remove(wh)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","procurement"] and len(parts) >= 5 and parts[3] == "suppliers":
                    item = next((s for s in SUPPLIERS if s["id"] == id), None)
                    if not item: return self._error("Supplier not found", 404)
                    SUPPLIERS.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","integration-stores"]:
                    item = next((s for s in INTEGRATION_STORES if s["id"] == id), None)
                    if not item: return self._error("Store not found", 404)
                    INTEGRATION_STORES.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","routing-rules"]:
                    item = next((r for r in ROUTING_RULES if r["id"] == id), None)
                    if not item: return self._error("Rule not found", 404)
                    ROUTING_RULES.remove(item)
                    return self._ok({"data": {"deleted": True, "id": id}})
                if parts[:3] == ["api","v1","rbac"] and len(parts) >= 5:
                    return self._handle_rbac_delete(parts)
            self._error("Not Found", 404)
        except Exception as e:
            self._error(str(e), 500)

    # ──────────────── Auth Handlers ────────────────
    def _handle_auth_get(self, parts):
        if len(parts) >= 4 and parts[3] in ("profile", "me"):
            user = self._auth()
            if not user: return self._error("Unauthorized", 401)
            if parts[3] == "me":
                return self._ok({"data": {"id": user.get("sub",""), "username": user.get("sub",""), "email": user.get("email",""), "fullName": user.get("name",""), "role": user.get("role","VIEWER"), "permissions": ["*"], "avatar": None}})
            return self._ok({"user": user})
        if len(parts) >= 4 and parts[3] == "tenants":
            return self._ok({"data": [{"id": "nexus-main", "name": "Nexus OMS", "domain": "nexusoms.com"}, {"id": "nexus-demo", "name": "Nexus Demo", "domain": "demo.nexusoms.com"}]})
        if len(parts) >= 5 and parts[3] == "sso" and parts[4] == "providers":
            return self._ok({"data": ["google", "microsoft", "okta", "auth0"]})
        self._error("Not Found", 404)

    def _handle_auth_post(self, parts):
        if len(parts) >= 4 and parts[3] == "login":
            body = self._read_body()
            u = USERS.get(body.get("username",""))
            if not u or u["password"] != body.get("password",""):
                return self._error("Invalid credentials", 401)
            token = create_jwt(body["username"], u["role"], u["name"], u["email"])
            return self._ok({"data": {
                "accessToken": token,
                "tokenType": "Bearer",
                "expiresIn": 86400,
                "username": body["username"],
                "role": u["role"],
                "fullName": u["name"],
                "email": u["email"],
                "tenantId": u.get("tenantId", "nexus-main"),
                "tenantName": u.get("tenantName", "Nexus OMS"),
                "permissions": u.get("permissions", []),
                "securityGroups": u.get("securityGroups", []),
                "mfaRequired": False,
                "passwordResetRequired": False
            }})
        if len(parts) >= 5 and parts[3] == "mfa" and parts[4] == "verify":
            return self._ok({"data": {"verified": True, "message": "MFA code verified"}})
        if len(parts) >= 4 and parts[3] == "sso":
            return self._ok({"data": {"accessToken": create_jwt("sso_user", "OPS_MANAGER", "SSO User", "sso@nexusoms.com"), "tokenType": "Bearer", "expiresIn": 86400, "username": "sso_user", "role": "OPS_MANAGER", "fullName": "SSO User", "email": "sso@nexusoms.com", "tenantId": "nexus-main", "tenantName": "Nexus OMS", "permissions": ["*"], "securityGroups": ["OPS"], "mfaRequired": False, "passwordResetRequired": False}})
        if len(parts) >= 4 and parts[3] == "forgot-password":
            return self._ok({"data": {"message": "Password reset email sent", "resetToken": "mock-reset-token"}})
        if len(parts) >= 4 and parts[3] == "reset-password":
            return self._ok({"data": {"message": "Password has been reset successfully"}})
        if len(parts) >= 4 and parts[3] == "refresh":
            return self._ok({"data": {"accessToken": create_jwt("admin", "ADMIN", "Admin User", "admin@nexusoms.com"), "tokenType": "Bearer", "expiresIn": 86400}})
        self._error("Not Found", 404)

    # ──────────────── Orders ────────────────
    def _handle_orders_get(self, parts, qs):
        if len(parts) == 3:
            page = int(qs.get("page", [1])[0])
            limit = int(qs.get("limit", [20])[0])
            status_filter = qs.get("status", [None])[0]
            search = qs.get("search", [None])[0]
            filtered = ORDERS
            if status_filter: filtered = [o for o in filtered if o["status"] == status_filter]
            if search: filtered = [o for o in filtered if search.lower() in o["customerName"].lower() or search in o["orderNumber"]]
            start = (page-1)*limit
            return self._ok({"orders": filtered[start:start+limit], "total": len(filtered), "page": page, "limit": limit})
        if len(parts) >= 4 and parts[3] == "stats":
            status_counts = {}
            for o in ORDERS:
                status_counts[o["status"]] = status_counts.get(o["status"], 0) + 1
            total_revenue = sum(o["total"] for o in ORDERS if o["status"] in ["SHIPPED","DELIVERED"])
            return self._ok({"stats": {
                "total": len(ORDERS), "revenue": round(total_revenue, 2),
                "byStatus": status_counts,
                "today": len([o for o in ORDERS if o["createdAt"].startswith(datetime.now(timezone.utc).strftime("%Y-%m-%d"))]),
                "pending": len([o for o in ORDERS if o["status"] in ["PENDING","CONFIRMED"]])
            }})
        if len(parts) >= 4:
            order = next((o for o in ORDERS if o["id"] == parts[3]), None)
            if not order: return self._error("Order not found", 404)
            return self._ok({"order": order})
        self._error("Not Found", 404)

    def _handle_orders_post(self, parts, user):
        if len(parts) == 3:
            body = self._read_body()
            new_id = f"ORD-{10000+len(ORDERS)}"
            order = {
                "id":new_id,"orderNumber":f"NEX-{10000+len(ORDERS)}",
                "customerId":body.get("customerId","C001"),
                "customerName":body.get("customerName","New Customer"),
                "customerEmail":body.get("customerEmail",""),
                "items":body.get("items",[]),
                "subtotal":body.get("subtotal",0),
                "shipping":body.get("shipping",0),
                "tax":body.get("tax",0),
                "total":body.get("total",0),
                "currency":"USD","status":"PENDING","paymentStatus":"pending",
                "fulfillmentStatus":"pending","shippingMethod":body.get("shippingMethod","STANDARD"),
                "carrierId":body.get("carrierId","FEDEX"),"trackingNumber":None,
                "shippingAddress":body.get("shippingAddress",""),
                "notes":body.get("notes",""),"channel":"direct",
                "createdAt":datetime.now(timezone.utc).isoformat(),
                "updatedAt":datetime.now(timezone.utc).isoformat(),
                "shipBy":(datetime.now(timezone.utc)+timedelta(days=3)).isoformat(),
                "itemsCount":sum(i.get("qty",0) for i in body.get("items",[])),
                "tags":[],
            }
            ORDERS.insert(0, order)
            return self._ok({"order": order})
        self._error("Not Found", 404)

    def _handle_orders_put(self, parts):
        if len(parts) >= 4:
            order = next((o for o in ORDERS if o["id"] == parts[3]), None)
            if not order: return self._error("Order not found", 404)
            body = self._read_body()
            for k,v in body.items():
                if k in order and k not in ("id","orderNumber","createdAt"):
                    order[k] = v
            order["updatedAt"] = datetime.now(timezone.utc).isoformat()
            return self._ok({"order": order})
        self._error("Not Found", 404)

    def _handle_orders_patch(self, parts, user):
        if len(parts) < 5: return self._error("Not Found", 404)
        order = next((o for o in ORDERS if o["id"] == parts[3]), None)
        if not order: return self._error("Order not found", 404)
        action = parts[4]
        transitions = {"confirm":"CONFIRMED","allocate":"ALLOCATED","ship":"SHIPPED","cancel":"CANCELLED"}
        if action in transitions:
            order["status"] = transitions[action]
            order["updatedAt"] = datetime.now(timezone.utc).isoformat()
            if action == "ship":
                order["trackingNumber"] = f"TN-{random.randint(10000000000,99999999999)}"
                order["fulfillmentStatus"] = "SHIPPED"
                order["paymentStatus"] = "paid"
            return self._ok({"order": order})
        self._error(f"Unknown action: {action}")

    # ──────────────── Products ────────────────
    def _handle_products_get(self, parts, qs):
        if len(parts) == 3:
            search = qs.get("search", [None])[0]
            filtered = PRODUCTS
            if search: filtered = [p for p in filtered if search.lower() in p["name"].lower() or search.lower() in p["sku"].lower()]
            return self._ok({"products": filtered, "total": len(filtered)})
        if len(parts) >= 4:
            prod = next((p for p in PRODUCTS if p["id"] == parts[3] or p["sku"] == parts[3]), None)
            if not prod: return self._error("Product not found", 404)
            return self._ok({"product": prod})
        self._error("Not Found", 404)

    def _handle_products_post(self, parts):
        if len(parts) == 3:
            body = self._read_body()
            new_id = f"P{len(PRODUCTS)+1:03d}"
            prod = {"id":new_id,"sku":body.get("sku",f"SKU-{uuid.uuid4().hex[:8].upper()}"),
                    "name":body.get("name","New Product"),"price":body.get("price",0),
                    "cost":body.get("cost",0),"category":body.get("category","General"),
                    "qty":body.get("qty",0),"unit":"EA","active":True}
            PRODUCTS.append(prod)
            return self._ok({"product": prod})
        self._error("Not Found", 404)

    def _handle_products_put(self, parts):
        if len(parts) >= 4:
            prod = next((p for p in PRODUCTS if p["id"] == parts[3] or p["sku"] == parts[3]), None)
            if not prod: return self._error("Product not found", 404)
            body = self._read_body()
            for k,v in body.items():
                if k in prod: prod[k] = v
            return self._ok({"product": prod})
        self._error("Not Found", 404)

    # ──────────────── Customers ────────────────
    def _handle_customers_get(self, parts, qs):
        if len(parts) == 3:
            search = qs.get("search", [None])[0]
            filtered = CUSTOMERS
            if search: filtered = [c for c in filtered if search.lower() in c["name"].lower() or search.lower() in c["email"].lower()]
            return self._ok({"customers": filtered, "total": len(filtered)})
        if len(parts) >= 4:
            cust = next((c for c in CUSTOMERS if c["id"] == parts[3]), None)
            if not cust: return self._error("Customer not found", 404)
            return self._ok({"customer": cust})
        self._error("Not Found", 404)

    def _handle_customers_post(self, parts):
        if len(parts) == 3:
            body = self._read_body()
            new_id = f"C{len(CUSTOMERS)+1:03d}"
            cust = {"id":new_id,"name":body.get("name","New Customer"),"email":body.get("email",""),
                    "phone":body.get("phone",""),"orders":0,"totalSpent":0,"status":"new",
                    "since":datetime.now().strftime("%Y-%m-%d")}
            CUSTOMERS.append(cust)
            return self._ok({"customer": cust})
        self._error("Not Found", 404)

    def _handle_customers_put(self, parts):
        if len(parts) >= 4:
            cust = next((c for c in CUSTOMERS if c["id"] == parts[3]), None)
            if not cust: return self._error("Customer not found", 404)
            body = self._read_body()
            for k,v in body.items():
                if k in cust: cust[k] = v
            return self._ok({"customer": cust})
        self._error("Not Found", 404)

    # ──────────────── Inventory ────────────────
    def _handle_inventory_get(self, parts, qs):
        if len(parts) >= 4 and parts[3] == "enhanced":
            data = []
            for wh in WAREHOUSES:
                wh_inv = INVENTORY_BY_WAREHOUSE[wh["id"]]
                items = [{"sku":p["sku"],"name":p["name"],"qty":wh_inv.get(p["sku"],0)} for p in PRODUCTS[:10]]
                data.append({"warehouse": wh, "items": items, "usage": round(wh["used"]/wh["capacity"]*100, 1)})
            return self._ok({"warehouses": data})
        if len(parts) >= 4 and parts[3] == "receiving":
            receipts = [{"id":f"RECV-{i}","sku":"SKU-NXS-001","name":"Ergonomic Mouse Pad","qty":random.randint(50,500),"expected":f"2026-07-{random.randint(1,5):02d}","status":random.choice(["pending","in_transit","received"])} for i in range(8)]
            return self._ok({"receipts": receipts})
        if len(parts) >= 4 and parts[3] == "cycle-counts":
            counts = [{"id":f"CC-{i}","zone":random.choice(["A1","B2","C3","D4"]),"sku":random.choice(PRODUCTS)["sku"],"expected":random.randint(10,200),"actual":random.randint(10,200),"status":random.choice(["pending","in_progress","completed"])} for i in range(12)]
            return self._ok({"counts": counts})
        if len(parts) >= 4:
            item = next((p for p in PRODUCTS if p["sku"] == parts[3]), None)
            if not item: return self._error("Item not found", 404)
            wh_data = {wh["id"]: {"name": wh["name"], "qty": INVENTORY_BY_WAREHOUSE[wh["id"]].get(parts[3], 0)} for wh in WAREHOUSES}
            return self._ok({"item": item, "byWarehouse": wh_data, "total": sum(w["qty"] for w in wh_data.values())})
        sku_filter = qs.get("sku", [None])[0]
        filtered_inv = PRODUCTS
        if sku_filter:
            filtered_inv = [p for p in filtered_inv if p["sku"] == sku_filter]
        low_stock = [p for p in filtered_inv if p["qty"] < 20]
        return self._ok({"inventory": filtered_inv, "lowStock": low_stock, "total": len(filtered_inv)})

    def _handle_inventory_post(self, parts):
        if len(parts) >= 4 and parts[3] == "adjust":
            body = self._read_body()
            sku = body.get("sku")
            wh_id = body.get("warehouseId", "WH-01")
            qty = body.get("qty", 0)
            reason = body.get("reason", "manual")
            if sku and wh_id in INVENTORY_BY_WAREHOUSE:
                INVENTORY_BY_WAREHOUSE[wh_id][sku] = INVENTORY_BY_WAREHOUSE[wh_id].get(sku, 0) + qty
                prod = next((p for p in PRODUCTS if p["sku"] == sku), None)
                if prod: prod["qty"] += qty
                return self._ok({"success": True, "newQty": INVENTORY_BY_WAREHOUSE[wh_id][sku]})
            return self._error("Invalid SKU or warehouse")
        self._error("Not Found", 404)

    # ──────────────── Waves ────────────────
    def _handle_waves_get(self, parts, qs):
        return self._ok({"waves": WAVE_PLANS})

    def _handle_waves_post(self, parts):
        body = self._read_body()
        new_wave = {"id":f"WAVE-{len(WAVE_PLANS)+1:03d}","name":body.get("name","New Wave"),
                    "priority":body.get("priority","normal"),"status":"pending",
                    "orders":body.get("orders",[]),"zone":body.get("zone","A1"),
                    "targetTime":body.get("targetTime","14:00"),"progress":0,
                    "createdAt":datetime.now(timezone.utc).isoformat()}
        WAVE_PLANS.insert(0, new_wave)
        return self._ok({"wave": new_wave})

    def _handle_waves_patch(self, parts):
        if len(parts) >= 4:
            wave = next((w for w in WAVE_PLANS if w["id"] == parts[3]), None)
            if not wave: return self._error("Wave not found", 404)
            body = self._read_body()
            for k,v in body.items():
                if k in wave: wave[k] = v
            return self._ok({"wave": wave})
        self._error("Not Found", 404)

    # ──────────────── Labor ────────────────
    def _handle_labor_get(self, parts, qs):
        if len(parts) >= 4 and parts[3] == "shifts":
            date = qs.get("date", [datetime.now().strftime("%Y-%m-%d")])[0]
            day_shifts = [s for s in SHIFTS if s["date"] == date]
            return self._ok({"shifts": day_shifts})
        return self._ok({"employees": EMPLOYEES})

    def _handle_labor_post(self, parts):
        if len(parts) >= 4 and parts[3] == "tasks":
            body = self._read_body()
            task = {"id":f"T-{uuid.uuid4().hex[:8]}","employeeId":body.get("employeeId"),
                    "type":body.get("type","picking"),"zone":body.get("zone"),
                    "status":"assigned","createdAt":datetime.now(timezone.utc).isoformat()}
            return self._ok({"task": task})
        self._error("Not Found", 404)

    # ──────────────── Picking ────────────────
    def _handle_picking_get(self, parts, qs):
        if len(parts) >= 4 and parts[3] == "user-staff":
            username = qs.get("username", [None])[0]
            if not username:
                return self._error("username required", 400)
            staff = next((s for s in WAREHOUSE_STAFF_DB if s.get("name","").lower() == username.lower()), None)
            if staff:
                return self._ok({"success": True, "data": {"staffId": staff["id"]}})
            return self._ok({"success": True, "data": {"staffId": None}})
        if len(parts) >= 6 and parts[3] == "lists" and parts[5] == "items":
            pl = next((p for p in PICK_LISTS if p["id"] == parts[4]), None)
            if not pl: return self._error("Pick list not found", 404)
            return self._ok({"items": pl.get("items", [])})
        if len(parts) >= 5 and parts[3] == "lists":
            pl = next((p for p in PICK_LISTS if p["id"] == parts[4]), None)
            if not pl: return self._error("Pick list not found", 404)
            return self._ok({"list": pl})
        if len(parts) >= 4 and parts[3] == "lists":
            return self._ok({"lists": PICK_LISTS})
        return self._ok({"lists": PICK_LISTS})

    def _handle_picking_post(self, parts):
        if len(parts) >= 6 and parts[3] == "items":
            return self._ok({"success": True, "message": f"Item {parts[5]} picked"})
        if len(parts) >= 6 and parts[3] == "lists" and parts[5] == "assign":
            pl = next((p for p in PICK_LISTS if p["id"] == parts[4]), None)
            if not pl: return self._error("Pick list not found", 404)
            body = self._read_body()
            pl["assigneeId"] = body.get("pickerId") or body.get("staffId")
            pl["status"] = "OPEN"
            return self._ok({"list": pl})
        if len(parts) >= 6 and parts[3] == "lists" and parts[5] == "complete":
            pl = next((p for p in PICK_LISTS if p["id"] == parts[4]), None)
            if not pl: return self._error("Pick list not found", 404)
            pl["status"] = "COMPLETED"
            pl["completedAt"] = datetime.now(timezone.utc).isoformat()
            return self._ok({"list": pl})
        if len(parts) >= 6 and parts[3] == "lists" and parts[5] == "cancel":
            pl = next((p for p in PICK_LISTS if p["id"] == parts[4]), None)
            if not pl: return self._error("Pick list not found", 404)
            pl["status"] = "CANCELLED"
            return self._ok({"list": pl})
        if len(parts) >= 6 and parts[3] == "lists" and parts[5] == "start":
            pl = next((p for p in PICK_LISTS if p["id"] == parts[4]), None)
            if not pl: return self._error("Pick list not found", 404)
            pl["status"] = "IN_PROGRESS"
            pl["startedAt"] = datetime.now(timezone.utc).isoformat()
            return self._ok({"list": pl})
        if len(parts) >= 4 and parts[3] == "seed-items":
            body = self._read_body()
            picklistId = body.get("picklistId")
            orderId = body.get("orderId")
            pl = next((p for p in PICK_LISTS if p["id"] == picklistId), None)
            if not pl: return self._error("Pick list not found", 404)
            order = next((o for o in ORDERS if o["id"] == orderId), None)
            if not order: return self._error("Order not found", 404)
            items = order.get("items", [])
            seeded = []
            for i, item in enumerate(items):
                seeded.append({
                    "id": f"{picklistId}-ITEM-{i+1:03d}",
                    "sku": item.get("sku", f"SKU-{i+1:03d}"),
                    "productName": item.get("productName", item.get("name", f"Product {i+1}")),
                    "quantity": item.get("quantity", item.get("qty", 1)),
                    "pickedQuantity": 0,
                    "status": "PENDING",
                    "fromLocation": f"A-{i+1:02d}",
                    "fromBinId": f"BIN-{i+1:03d}",
                })
            pl.setdefault("items", []).extend(seeded)
            pl["totalItems"] = (pl.get("totalItems", 0) or 0) + len(seeded)
            return self._ok({"success": True, "items": seeded})
        if len(parts) >= 4 and parts[3] == "lists":
            body = self._read_body()
            orderIds = body.get("orderIds", "[]")
            if isinstance(orderIds, str):
                orderIds = json.loads(orderIds)
            pl = {
                "id": f"PL-{len(PICK_LISTS)+1:03d}",
                "name": body.get("name", f"Picklist {len(PICK_LISTS)+1}"),
                "waveType": body.get("waveType", "SINGLE_ORDER"),
                "priority": body.get("priority", "NORMAL"),
                "status": "OPEN",
                "items": [],
                "totalItems": 0,
                "pickedItems": 0,
                "assigneeId": None,
                "orderIds": orderIds or [],
                "createdAt": datetime.now(timezone.utc).isoformat(),
                "startedAt": None,
                "completedAt": None,
                "notes": body.get("notes", ""),
            }
            PICK_LISTS.insert(0, pl)
            return self._ok({"list": pl, "success": True, "id": pl["id"]})
        self._error("Not Found", 404)

    def _handle_picking_patch(self, parts):
        if len(parts) >= 4:
            pl = next((p for p in PICK_LISTS if p["id"] == parts[3]), None)
            if not pl: return self._error("Pick list not found", 404)
            body = self._read_body()
            for k,v in body.items():
                if k in pl: pl[k] = v
            return self._ok({"list": pl})
        self._error("Not Found", 404)

    # ──────────────── Packing ────────────────
    def _handle_packing_get(self, parts):
        if len(parts) >= 5 and parts[3] == "queues":
            queues = [{"id":f"PKQ-{i}","orderId":o["id"],"customer":o["customerName"],
                       "items":o["items"],"status":random.choice(["pending","in_progress","completed"]),
                       "assignedTo":random.choice([None,"E002","E004","E008"])} for i,o in enumerate(ORDERS[:12])]
            q = next((q for q in queues if q["id"] == parts[4]), None)
            if not q: return self._error("Queue not found", 404)
            return self._ok({"queue": q})
        if len(parts) >= 4 and parts[3] == "queues":
            queues = [{"id":f"PKQ-{i}","orderId":o["id"],"customer":o["customerName"],
                       "items":o["items"],"status":random.choice(["pending","in_progress","completed"]),
                       "assignedTo":random.choice([None,"E002","E004","E008"])} for i,o in enumerate(ORDERS[:12])]
            return self._ok({"queues": queues})
        return self._ok({"queues": []})

    def _handle_packing_post(self, parts):
        if len(parts) >= 6 and parts[3] == "packages":
            return self._ok({"success": True, "message": f"Package {parts[4]} {parts[5]}"})
        if len(parts) >= 4 and parts[3] == "complete":
            return self._ok({"success": True, "message": "Packing completed"})
        self._error("Not Found", 404)

    # ──────────────── Carriers ────────────────
    def _handle_carriers_get(self, parts, qs):
        if len(parts) >= 4 and parts[3] == "kpis":
            total_shipments = len(SHIPMENTS)
            return self._ok({"kpis": {
                "totalCarriers": len(CARRIERS), "activeCarriers": len([c for c in CARRIERS if c.get("active", True)]),
                "totalShipments": total_shipments, "avgCost": round(sum(s.get("cost", 0) for s in SHIPMENTS) / max(total_shipments, 1), 2),
                "onTimeRate": 94.5, "active": len([c for c in CARRIERS if c.get("active", True)]),
            }})
        if len(parts) >= 4 and parts[3] == "rates":
            return self._ok({"rates": CARRIER_RATES})
        if len(parts) >= 4 and self._is_resource_id(parts[3]):
            c = next((c for c in CARRIERS if c["id"] == parts[3]), None)
            if not c: return self._error("Carrier not found", 404)
            return self._ok({"carrier": c})
        return self._ok({"carriers": CARRIERS})

    def _handle_carriers_post(self, parts):
        body = self._read_body()
        c = {"id":f"CARR-{len(CARRIERS)+1:03d}","name":body.get("name","New Carrier"),
             "code":body.get("code","NEW"),"active":body.get("active",True),
             "services":body.get("services",["GROUND"]),"type":body.get("type","carrier")}
        CARRIERS.append(c)
        return self._ok({"carrier": c})

    def _handle_carriers_put(self, parts):
        if len(parts) >= 4 and self._is_resource_id(parts[3]):
            c = next((c for c in CARRIERS if c["id"] == parts[3]), None)
            if not c: return self._error("Carrier not found", 404)
            body = self._read_body()
            for k,v in body.items():
                if k in c: c[k] = v
            return self._ok({"carrier": c})
        self._error("Not Found", 404)

    def _handle_carriers_delete(self, parts):
        if len(parts) >= 4 and self._is_resource_id(parts[3]):
            c = next((c for c in CARRIERS if c["id"] == parts[3]), None)
            if not c: return self._error("Carrier not found", 404)
            CARRIERS.remove(c)
            return self._ok({"success": True})
        self._error("Not Found", 404)

    # ──────────────── Labels ────────────────
    def _handle_labels_get(self, parts):
        return self._ok({"labels": LABELS_HISTORY})

    def _handle_labels_post(self, parts):
        body = self._read_body()
        order_id = body.get("orderId", f"ORD-{10000+random.randint(0,84)}")
        carrier = body.get("carrier", "FEDEX")
        service = body.get("service", "GROUND")
        lbl = {"id":f"LBL-{60000+len(LABELS_HISTORY)}","orderId":order_id,"carrier":carrier,
               "service":service,"trackingNumber":f"TN-LBL-{random.randint(10000000000,99999999999)}",
               "weight":body.get("weight",random.uniform(0.5,25)),"dimensions":body.get("dimensions","12x10x6"),
               "status":"generated","createdAt":datetime.now(timezone.utc).isoformat()}
        LABELS_HISTORY.insert(0, lbl)
        if body.get("bulk"):
            labels = [lbl]
            for _ in range(body.get("count", 1)-1):
                lbl2 = dict(lbl)
                lbl2["id"] = f"LBL-{60000+len(LABELS_HISTORY)}"
                lbl2["orderId"] = f"ORD-{10000+random.randint(0,84)}"
                lbl2["trackingNumber"] = f"TN-LBL-{random.randint(10000000000,99999999999)}"
                LABELS_HISTORY.insert(0, lbl2)
                labels.append(lbl2)
            return self._ok({"labels": labels, "bulk": True, "count": len(labels)})
        return self._ok({"label": lbl})

    # ──────────────── Manifests ────────────────
    def _handle_manifests_get(self, parts):
        return self._ok({"manifests": MANIFESTS})

    def _handle_manifests_post(self, parts):
        body = self._read_body()
        carrier = body.get("carrier", "FEDEX")
        mnf = {"id":f"MAN-{50000+len(MANIFESTS)}","carrier":carrier,"status":"draft",
               "shipments":body.get("shipments",0),"totalWeight":body.get("totalWeight",0),
               "totalCost":body.get("totalCost",0),"createdAt":datetime.now(timezone.utc).isoformat(),
               "closedAt":None,"bol":None}
        MANIFESTS.insert(0, mnf)
        return self._ok({"manifest": mnf})

    def _handle_manifests_patch(self, parts):
        if len(parts) >= 4:
            m = next((m for m in MANIFESTS if m["id"] == parts[3]), None)
            if not m: return self._error("Manifest not found", 404)
            body = self._read_body()
            for k,v in body.items():
                if k in m: m[k] = v
            return self._ok({"manifest": m})
        self._error("Not Found", 404)

    # ──────────────── Returns ────────────────
    def _handle_returns_get(self, parts, qs):
        if len(parts) >= 4 and parts[3] == "analytics":
            reasons = {r["reason"]: len([x for x in RETURNS if x["reason"]==r["reason"]]) for r in RETURNS}
            return self._ok({"analytics": {
                "total": len(RETURNS), "pending": len([r for r in RETURNS if r["status"]=="pending"]),
                "approved": len([r for r in RETURNS if r["status"]=="approved"]),
                "totalRefund": sum(r["refund"] for r in RETURNS),
                "reasons": reasons, "byStatus": {s: len([r for r in RETURNS if r["status"]==s]) for s in set(r["status"] for r in RETURNS)},
                "recoveryValue": sum(r["refund"]*0.6 for r in RETURNS if r["disposition"] in ["restock","refurbish"]),
            }})
        if len(parts) >= 4 and parts[3] == "reasons":
            reasons_list = {}
            for r in RETURNS:
                reason = r.get("reason", "Other")
                reasons_list[reason] = reasons_list.get(reason, 0) + 1
            return self._ok({"reasons": [{"reason": k, "count": v} for k, v in reasons_list.items()]})
        if len(parts) >= 4 and self._is_resource_id(parts[3]):
            r = next((r for r in RETURNS if r["id"] == parts[3]), None)
            if not r: return self._error("Return not found", 404)
            return self._ok({"return": r})
        return self._ok({"returns": RETURNS})

    def _handle_returns_post(self, parts):
        body = self._read_body()
        rma_id = f"RMA-{10000+len(RETURNS)+1}"
        rma = {"id":rma_id,"orderId":body.get("orderId",""),"customer":body.get("customer",""),
               "sku":body.get("sku",""),"product":body.get("product",""),
               "qty":body.get("qty",1),"reason":body.get("reason",""),
               "condition":body.get("condition","Open Box"),"status":"pending",
               "disposition":"pending","createdAt":datetime.now(timezone.utc).isoformat(),
               "refund":body.get("refund",0)}
        RETURNS.insert(0, rma)
        return self._ok({"return": rma})

    def _handle_returns_patch(self, parts):
        if len(parts) >= 4:
            rma = next((r for r in RETURNS if r["id"] == parts[3]), None)
            if not rma: return self._error("Return not found", 404)
            body = self._read_body()
            for k,v in body.items():
                if k in rma: rma[k] = v
            return self._ok({"return": rma})
        self._error("Not Found", 404)

    # ──────────────── Payments ────────────────
    def _handle_payments_get(self, parts, qs):
        status = qs.get("status", [None])[0]
        filtered = PAYMENTS
        if status: filtered = [p for p in filtered if p["status"] == status]
        return self._ok({"payments": filtered, "total": len(filtered)})

    # ──────────────── Invoices ────────────────
    def _handle_invoices_get(self, parts, qs):
        status = qs.get("status", [None])[0]
        filtered = INVOICES
        if status: filtered = [i for i in filtered if i["status"] == status]
        return self._ok({"invoices": filtered, "total": len(filtered)})

    def _handle_invoices_post(self, parts):
        body = self._read_body()
        inv = {"id":f"INV-{30000+len(INVOICES)}","orderId":body.get("orderId",""),
               "number":f"INV-2026-{10000+len(INVOICES)}","customerName":body.get("customerName",""),
               "amount":body.get("amount",0),"currency":"USD","status":"pending",
               "issuedAt":datetime.now(timezone.utc).isoformat(),
               "dueAt":(datetime.now(timezone.utc)+timedelta(days=30)).isoformat(),"paidAt":None}
        INVOICES.insert(0, inv)
        return self._ok({"invoice": inv})

    def _handle_invoices_patch(self, parts):
        if len(parts) >= 4:
            inv = next((i for i in INVOICES if i["id"] == parts[3]), None)
            if not inv: return self._error("Invoice not found", 404)
            body = self._read_body()
            for k,v in body.items():
                if k in inv: inv[k] = v
            return self._ok({"invoice": inv})
        self._error("Not Found", 404)

    # ──────────────── Reconciliation ────────────────
    def _handle_reconciliation_get(self, parts):
        return self._ok({"items": RECONCILIATION})

    # ──────────────── Reports ────────────────
    def _handle_reports_get(self, parts, qs):
        if len(parts) >= 4 and parts[3] == "dashboard":
            today_revenue = sum(o["total"] for o in ORDERS if o["createdAt"].startswith(datetime.now(timezone.utc).strftime("%Y-%m-%d")) and o["status"] in ["SHIPPED","DELIVERED"])
            return self._ok({"widgets": [
                {"id":"w1","type":"kpi","title":"Total Revenue","value":f"${sum(o['total'] for o in ORDERS):,.2f}","change":12.5,"period":"MTD"},
                {"id":"w2","type":"kpi","title":"Orders","value":str(len(ORDERS)),"change":8.3,"period":"MTD"},
                {"id":"w3","type":"kpi","title":"Avg Order Value","value":f"${sum(o['total'] for o in ORDERS)/len(ORDERS):.2f}","change":-2.1,"period":"MTD"},
                {"id":"w4","type":"kpi","title":"Fulfillment Rate","value":"94.2%","change":1.8,"period":"MTD"},
                {"id":"w5","type":"chart","title":"Orders by Status","labels":ORDER_STATUSES,"values":[len([o for o in ORDERS if o["status"]==s]) for s in ORDER_STATUSES]},
                {"id":"w6","type":"chart","title":"Revenue Trend","labels":["Week 1","Week 2","Week 3","Week 4"],"values":[random.randint(10000,50000) for _ in range(4)]},
            ]})
        if len(parts) >= 4 and parts[3] == "scheduled":
            scheduled = [{"id":"SR-1","name":"Daily Operations Report","frequency":"daily","format":"PDF","recipients":["ops@nexusoms.com"],"lastRun":"2026-07-01T06:00:00Z","nextRun":"2026-07-02T06:00:00Z","active":True}]
            return self._ok({"reports": scheduled})
        templates = [{"id":"RT-1","name":"Inventory Summary","category":"Inventory","description":"Current stock levels across all warehouses"},{"id":"RT-2","name":"Order Fulfillment","category":"Operations","description":"Order processing times and fulfillment metrics"},{"id":"RT-3","name":"Financial Summary","category":"Finance","description":"Revenue, costs, and payment reconciliation"},{"id":"RT-4","name":"Returns Analysis","category":"Returns","description":"Return reasons, trends, and recovery value"}]
        return self._ok({"templates": templates})

    def _handle_reports_post(self, parts):
        if len(parts) >= 4 and parts[3] == "generate":
            body = self._read_body()
            return self._ok({"report": {"id":f"RPT-{uuid.uuid4().hex[:8]}","template":body.get("templateId"),
                                        "format":body.get("format","PDF"),"status":"generated",
                                        "url":f"/reports/download/{uuid.uuid4().hex}.pdf",
                                        "generatedAt":datetime.now(timezone.utc).isoformat()}})
        if len(parts) >= 4 and parts[3] == "scheduled":
            body = self._read_body()
            sr = {"id":f"SR-{uuid.uuid4().hex[:8]}","name":body.get("name","Scheduled Report"),
                  "frequency":body.get("frequency","daily"),"format":body.get("format","PDF"),
                  "recipients":body.get("recipients",[]),"active":True,
                  "lastRun":None,"nextRun":datetime.now(timezone.utc).isoformat()}
            return self._ok({"report": sr})
        self._error("Not Found", 404)

    # ──────────────── Task Queues ────────────────
    def _handle_task_queues_get(self, parts):
        return self._ok({"queues": TASK_QUEUES})

    def _handle_task_queues_patch(self, parts):
        if len(parts) >= 4:
            tq = next((t for t in TASK_QUEUES if t["id"] == parts[3]), None)
            if not tq: return self._error("Task queue not found", 404)
            body = self._read_body()
            for k,v in body.items():
                if k in tq: tq[k] = v
            return self._ok({"queue": tq})
        self._error("Not Found", 404)

    # ──────────────── Settings ────────────────
    def _handle_settings_get(self, parts):
        if len(parts) >= 5 and parts[3] == "keys":
            key = parts[4]
            value = SETTINGS.get(key)
            if value is None:
                return self._ok({"key": key, "value": None, "default": None})
            return self._ok({"key": key, "value": value})
        return self._ok({"settings": SETTINGS})

    def _handle_settings_put(self, parts):
        body = self._read_body()
        for k, v in body.items():
            SETTINGS[k] = v
        return self._ok({"settings": SETTINGS, "message": "Settings saved"})

    # ──────────────── Dashboard ────────────────
    def _handle_dashboard_get(self):
        today_orders = len([o for o in ORDERS if o["createdAt"].startswith(datetime.now(timezone.utc).strftime("%Y-%m-%d"))])
        return self._ok({
            "kpis": {
                "totalOrders": len(ORDERS), "revenue": round(sum(o["total"] for o in ORDERS if o["status"] in ["SHIPPED","DELIVERED"]), 2),
                "pendingOrders": len([o for o in ORDERS if o["status"] not in ["SHIPPED","DELIVERED","CANCELLED"]]),
                "todayOrders": today_orders, "activePickers": len([e for e in EMPLOYEES if e["status"]=="active"]),
                "lowStock": len([p for p in PRODUCTS if p["qty"]<20]), "returns": len(RETURNS),
            }
        })

    # ──────────────── AI Endpoints ────────────────
    def _handle_ai_get(self, parts, qs):
        if len(parts) < 4: return self._error("Not Found", 404)
        section = parts[3]
        if section == "models":
            if len(parts) >= 5 and parts[4] == "summary":
                return self._ok({"summary":{"totalModels":len(AI_MODELS),"activeModels":len([m for m in AI_MODELS if m["status"]=="active"]),"globalModels":len([m for m in AI_MODELS if m.get("isGlobal")]),"tenantModels":len([m for m in AI_MODELS if not m.get("isGlobal")]),"modelsInTraining":len([m for m in AI_MODELS if m["status"]=="training"])}})
            if len(parts) >= 6 and parts[5] == "versions":
                return self._ok({"versions": AI_MODEL_VERSIONS_DB.get(parts[4], [])})
            if len(parts) >= 6 and parts[5] == "inference-logs":
                logs = [l for l in AI_INFERENCE_LOGS if l["modelId"] == parts[4]]
                return self._ok({"logs": logs})
            if len(parts) >= 5:
                m = next((m for m in AI_MODELS if m["id"] == parts[4]), None)
                if not m: return self._error("Model not found", 404)
                return self._ok({"model": m})
            category = qs.get("category", [None])[0]
            status = qs.get("status", [None])[0]
            filtered = AI_MODELS
            if category: filtered = [m for m in filtered if m["category"] == category]
            if status: filtered = [m for m in filtered if m["status"] == status]
            page = int(qs.get("page", [0])[0]); size = int(qs.get("size", [20])[0])
            start = page*size
            return self._ok({"models": filtered, "content": filtered[start:start+size], "totalElements": len(filtered)})
        if section == "features":
            if len(parts) >= 5 and parts[4] == "groups":
                groups = {}
                for f in AI_FEATURES:
                    groups[f["group"]] = groups.get(f["group"], 0) + 1
                return self._ok({"groups": [{"group":k,"count":v} for k,v in groups.items()]})
            if len(parts) >= 4:
                group = qs.get("featureGroup", [None])[0]
                filtered = AI_FEATURES
                if group: filtered = [f for f in filtered if f["group"] == group]
                return self._ok({"features": filtered, "content": filtered, "totalElements": len(filtered)})
        if section == "training" and len(parts) >= 5 and parts[4] == "jobs":
            if len(parts) >= 6:
                j = next((j for j in AI_TRAINING_JOBS if j["id"] == parts[5]), None)
                if not j: return self._error("Job not found", 404)
                return self._ok({"job": j})
            model_id = qs.get("modelId", [None])[0]
            status = qs.get("status", [None])[0]
            filtered = AI_TRAINING_JOBS
            if model_id: filtered = [j for j in filtered if j["modelId"] == model_id]
            if status: filtered = [j for j in filtered if j["status"] == status]
            return self._ok({"jobs": filtered, "content": filtered, "totalElements": len(filtered)})
        if section == "monitoring":
            if len(parts) >= 6 and parts[4] == "models":
                return self._ok({"health":{"status":"healthy","uptime":"99.8%","avgLatency":"156ms","errorRate":"0.02%"}})
            if len(parts) >= 5 and parts[4] == "dashboard":
                return self._ok({"dashboard":{"totalRequests":1247,"avgLatency":"142ms","errorRate":"0.03%","modelsActive":4}})
        if section == "analytics" and len(parts) >= 5 and parts[4] == "dashboard":
            return self._ok({"dashboard":{"totalPredictions":50000,"avgConfidence":0.94,"topModel":"Demand Forecaster"}})
        if section == "experiments":
            if len(parts) >= 5:
                return self._ok({"experiment":{"id":parts[4],"name":"A/B Test","status":"running","createdAt":"2026-07-01T00:00:00Z"}})
            return self._ok({"experiments":[{"id":"EXP-001","name":"Carrier Selection A/B","status":"running","createdAt":"2026-07-01T00:00:00Z"},{"id":"EXP-002","name":"Packing Algorithm Test","status":"completed","createdAt":"2026-06-15T00:00:00Z"}],"total":2})
        if section == "fallbacks" and len(parts) >= 5:
            return self._ok({"fallbacks":[{"id":"FB-001","modelId":parts[4],"type":"fallback","priority":1,"condition":"latency > 500ms","action":"use_rule_based"}]})
        if section == "briefing":
            return self._ok({"kpis":{"totalOrders":len(ORDERS),"revenue":f"₹{sum(o['total'] for o in ORDERS)*83:,.0f}","fulfillmentRate":"94.2%","aiAccuracy":"96.8%"},
                "insights":[
                    {"type":"positive","title":"Order volume up 23%","description":"Month-over-month growth driven by Amazon channel","icon":"trending_up"},
                    {"type":"warning","title":"Low stock on 5 SKUs","description":"SKU-NXS-005 has only 5 units remaining","icon":"alert"},
                    {"type":"info","title":"AI override rate at 2.7%","description":"Well below industry average of 8%","icon":"info"},
                ],
                "risks":[{"title":"Carrier capacity","description":"FedEx Ground at 94% capacity","severity":"medium","probability":0.65},{"title":"Supplier delay","description":"Electronic components delayed by 2 weeks","severity":"high","probability":0.4}],
                "recommendations":[
                    {"id":"rec-1","title":"Restock Noise Cancelling Headphones","description":"Current stock of 5 is below reorder point of 20","impact":"$25.5K potential revenue at risk","confidence":0.94,"type":"inventory","status":"pending"},
                    {"id":"rec-2","title":"Enable dynamic carrier routing","description":"Switch 30% of FedEx volume to UPS to reduce costs","impact":"Save $1,247/month","confidence":0.88,"type":"operations","status":"pending"},
                ],
                "forecast":[{"month":"Jul","revenue":52400},{"month":"Aug","revenue":56800},{"month":"Sep","revenue":61200}]})
        if section == "routing":
            return self._ok({"agents":[
                {"name":"Order Verification","status":"active","accuracy":99.2,"decisions24h":847,"modelVersion":"2.1.0"},
                {"name":"Order Prioritization","status":"active","accuracy":94.5,"decisions24h":612,"modelVersion":"1.8.3"},
                {"name":"Inventory Allocation","status":"active","accuracy":96.8,"decisions24h":423,"modelVersion":"3.0.1"},
            ],"queue":[
                {"orderId":"ORD-10031","confidence":0.92,"decision":"auto_approve","reason":"All checks passed","score":0.92,"agent":"Order Verification"},
                {"orderId":"ORD-10042","confidence":0.67,"decision":"flagged","reason":"High-value order, requires review","score":0.67,"agent":"Order Verification"},
                {"orderId":"ORD-10055","confidence":0.45,"decision":"flagged","reason":"Suspicious shipping address","score":0.45,"agent":"Fraud Detection"},
            ],"stats":{"autoApproved":1247,"flagged":34,"overrideRate":2.7}})
        if section == "packing":
            def _item_weight(items):
                total = 0.0
                for it in items:
                    prod = next((p for p in PRODUCTS if p["sku"] == it["sku"]), None)
                    if prod and "price" in prod:
                        total += prod["price"] * it.get("qty",1)
                return round(total * 0.1, 1)
            orders_data = [{"orderId":ORDERS[i]["id"],"items":ORDERS[i]["items"],"aiBoxPlan":{"boxType":"Medium Box","dimensions":"18x14x10","weight":_item_weight(ORDERS[i]["items"]),"fillRate":random.randint(75,98),"materials":["Box","Bubble Wrap","Tape"],"confidence":random.uniform(0.85,0.99)}} for i in range(min(5,len(ORDERS)))]
            return self._ok({"orders": orders_data})
        if section == "loading":
            return self._ok({"trucks":[
                {"id":"TRUCK-001","type":"20ft","capacity":2000,"used":1820,"stops":3,"status":"loading","departure":"2026-07-01T14:00:00Z"},
                {"id":"TRUCK-002","type":"40ft","capacity":4000,"used":0,"stops":5,"status":"pending","departure":"2026-07-01T16:00:00Z"},
                {"id":"TRUCK-003","type":"20ft","capacity":2000,"used":2000,"stops":2,"status":"ready","departure":"2026-07-01T12:00:00Z"},
            ]})
        if section == "audit":
            decisions = [{"id":f"audit-{i}","orderId":random.choice(ORDERS)["id"],"agent":random.choice(["Order Verification","Fraud Detection","Routing","Packing","Loading"]),"decision":random.choice(["auto_approved","overridden","fallback"]),"confidence":round(random.uniform(0.55,0.99),2),"timestamp":f"2026-07-01T{random.randint(6,18):02d}:{random.randint(0,59):02d}:00Z","overriddenBy":None,"processingTime":f"{random.randint(50,500)}ms"} for i in range(25)]
            stats_d = {"total":1247,"autoApproved":1189,"overridden":34,"fallback":24,"avgConfidence":0.94}
            return self._ok({"decisions": decisions, "stats": stats_d})
        if section == "forecasting":
            months = ["Jun","Jul","Aug","Sep","Oct","Nov"]
            return self._ok({"demand":[
                {"month":m,"orders":random.randint(300,800),"revenue":random.randint(20000,65000),"growth":round(random.uniform(-5,15),1)} for m in months
            ],"supplierRisk":[
                {"supplier":"TechComponents Inc","riskScore":72,"status":"at_risk","leadTime":random.randint(14,45),"onTime":random.randint(70,95)},
                {"supplier":"PackPlus Ltd","riskScore":92,"status":"reliable","leadTime":random.randint(3,7),"onTime":98},
                {"supplier":"Global Logistics Co","riskScore":45,"status":"critical","leadTime":random.randint(30,60),"onTime":55},
            ],"recommendations":[
                {"id":"frec-1","title":"Increase safety stock for electronics","reason":"Supplier risk score dropped to 45","impact":"Avoid $15K in lost sales","confidence":0.91},
                {"id":"frec-2","title":"Diversify packaging supplier","reason":"Single-source risk for corrugated boxes","impact":"Reduce supply chain risk","confidence":0.85},
            ]})
        if section == "recommendations":
            suggestions = [
                {"id":"sr-1","actionType":"confirm","label":"Confirm Order ORD-10031","description":"Order passes all validation checks","confidence":0.95,"context":"order"},
                {"id":"sr-2","actionType":"allocate","label":"Allocate Inventory","description":"All products in stock, allocate immediately","confidence":0.92,"context":"inventory"},
                {"id":"sr-3","actionType":"expedite","label":"Expedite Order ORD-10042","description":"Premium customer, priority handling recommended","confidence":0.65,"context":"order"},
            ]
            return self._ok({"suggestions": suggestions})
        self._error("Not Found", 404)

    # ──────────────── RBAC ────────────────
    def _handle_rbac_get(self, parts, qs):
        sub = parts[3] if len(parts) >= 4 else ""
        if sub == "permissions":
            if len(parts) >= 5:
                role_perms = [p for p in RBAC_PERMISSIONS if p["role"] == parts[4]]
                return self._ok({"permissions": role_perms})
            return self._ok({"permissions": RBAC_PERMISSIONS, "total": len(RBAC_PERMISSIONS)})
        if sub == "user-roles":
            return self._ok({"userRoles": RBAC_USER_ROLES, "total": len(RBAC_USER_ROLES)})
        if sub == "teams":
            return self._ok({"teams": RBAC_TEAMS, "total": len(RBAC_TEAMS)})
        if sub == "check-permission":
            role = qs.get("role", [None])[0]
            resource = qs.get("resource", [None])[0]
            action = qs.get("action", [None])[0]
            granted = any(p["role"] == role and (p["resource"] == "*" or p["resource"] == resource) and (p["action"] == "*" or p["action"] == action) for p in RBAC_PERMISSIONS)
            return self._ok({"granted": granted})
        self._error("Not Found", 404)

    def _handle_rbac_post(self, parts):
        sub = parts[3] if len(parts) >= 4 else ""
        body = self._read_body()
        if sub == "permissions":
            new_id = f"RP-{len(RBAC_PERMISSIONS)+1:03d}"
            perm = {"id": new_id, "role": body.get("role","VIEWER"), "resource": body.get("resource","*"), "action": body.get("action","read"), "granted": body.get("granted",True)}
            RBAC_PERMISSIONS.append(perm)
            return self._ok({"permission": perm})
        if sub == "user-roles":
            new_id = f"UR-{len(RBAC_USER_ROLES)+1:03d}"
            ur = {"id": new_id, "username": body.get("username","unknown"), "role": body.get("role","VIEWER"), "assignedAt": datetime.now(timezone.utc).isoformat()}
            RBAC_USER_ROLES.append(ur)
            return self._ok({"userRole": ur})
        if sub == "teams":
            new_id = f"T-{len(RBAC_TEAMS)+1:03d}"
            team = {"id": new_id, "name": body.get("name","New Team"), "description": body.get("description",""), "memberCount": 0, "createdAt": datetime.now(timezone.utc).isoformat()}
            RBAC_TEAMS.append(team)
            return self._ok({"team": team})
        self._error("Not Found", 404)

    def _handle_rbac_delete(self, parts):
        sub = parts[3] if len(parts) >= 4 else ""
        id = parts[4] if len(parts) >= 5 else ""
        if sub == "user-roles":
            item = next((u for u in RBAC_USER_ROLES if u["id"] == id), None)
            if not item: return self._error("User role not found", 404)
            RBAC_USER_ROLES.remove(item)
            return self._ok({"data": {"deleted": True, "id": id}})
        self._error("Not Found", 404)

    def _handle_ai_post(self, parts):
        if len(parts) >= 6 and parts[3] == "recommendations" and parts[5] == "respond":
            body = self._read_body()
            return self._ok({"success": True, "action": body.get("action","approved"), "id": parts[4]})
        if len(parts) >= 4 and parts[3] == "models":
            body = self._read_body()
            new_id = f"AI-M-{len(AI_MODELS)+1:03d}"
            m = {"id":new_id,"name":body.get("name","New Model"),"description":body.get("description",""),"version":"1.0.0","category":body.get("category","general"),"status":"inactive","accuracy":None,"latencyMs":None,"lastTrained":None,"tenantId":"nexus-main","isGlobal":False}
            AI_MODELS.append(m)
            return self._ok({"model": m})
        if len(parts) >= 6 and parts[3] == "models" and parts[5] == "versions":
            body = self._read_body()
            new_id = f"AIV-{len(sum(AI_MODEL_VERSIONS_DB.values(),[]))+1:03d}"
            mv = {"id":new_id,"modelId":parts[4],"version":body.get("version","1.0.0"),"description":body.get("description",""),"status":"active","createdAt":datetime.now(timezone.utc).isoformat()}
            AI_MODEL_VERSIONS_DB.setdefault(parts[4], []).append(mv)
            return self._ok({"version": mv})
        if len(parts) >= 6 and parts[3] == "models" and parts[5] in ("deploy","rollback"):
            return self._ok({"success":True,"message":f"Version {parts[5]=='deploy' and 'deployed' or 'rolled back'} successfully"})
        if len(parts) >= 4 and parts[3] == "features":
            body = self._read_body()
            new_id = f"AIF-{len(AI_FEATURES)+1:03d}"
            f = {"id":new_id,"name":body.get("name","new_feature"),"group":body.get("group","general"),"type":body.get("type","numeric"),"description":body.get("description",""),"defaultValue":body.get("defaultValue",0)}
            AI_FEATURES.append(f)
            return self._ok({"feature": f})
        if len(parts) >= 5 and parts[3] == "training" and parts[4] == "jobs":
            body = self._read_body()
            new_id = f"AITJ-{len(AI_TRAINING_JOBS)+1:03d}"
            j = {"id":new_id,"modelId":body.get("modelId",""),"status":"pending","progress":0,"startedAt":None,"completedAt":None,"accuracy":None,"datasetSize":body.get("datasetSize",0)}
            AI_TRAINING_JOBS.append(j)
            return self._ok({"job": j})
        if len(parts) >= 6 and parts[3] == "training" and parts[4] == "jobs":
            j = next((j for j in AI_TRAINING_JOBS if j["id"] == parts[5]), None)
            if not j: return self._error("Job not found", 404)
            action = parts[6] if len(parts) >= 7 else ""
            if action == "start": j["status"] = "running"; j["startedAt"] = datetime.now(timezone.utc).isoformat()
            elif action == "complete": j["status"] = "completed"; j["progress"] = 100; j["completedAt"] = datetime.now(timezone.utc).isoformat()
            elif action == "fail": j["status"] = "failed"; j["completedAt"] = datetime.now(timezone.utc).isoformat()
            return self._ok({"job": j})
        if len(parts) >= 4 and parts[3] == "experiments":
            body = self._read_body()
            new_id = f"EXP-{random.randint(1000,9999)}"
            exp = {"id":new_id,"name":body.get("name","New Experiment"),"status":"draft","createdAt":datetime.now(timezone.utc).isoformat(),"description":body.get("description","")}
            self._ok({"experiment": exp})
            return
        self._error("Not Found", 404)

    # ──────────────── Shipments ────────────────
    def _handle_shipments_get(self, parts, qs):
        if len(parts) >= 4:
            s = next((s for s in SHIPMENTS if s["id"] == parts[3]), None)
            if not s: return self._error("Shipment not found", 404)
            return self._ok({"shipment": s})
        status_filter = qs.get("status", [None])[0]
        filtered = SHIPMENTS
        if status_filter: filtered = [s for s in filtered if s["status"] == status_filter]
        return self._ok({"shipments": filtered, "total": len(filtered)})

    def _handle_shipments_post(self, parts):
        if len(parts) >= 5 and parts[4] in ("ship","deliver","void"):
            s = next((s for s in SHIPMENTS if s["id"] == parts[3]), None)
            if not s: return self._error("Shipment not found", 404)
            s["status"] = {"ship":"in_transit","deliver":"delivered","void":"voided"}[parts[4]]
            s["events"].append({"status":s["status"],"location":"System","timestamp":datetime.now(timezone.utc).isoformat()})
            return self._ok({"shipment": s})
        body = self._read_body()
        new_id = f"SHP-{len(SHIPMENTS)+80001}"
        s = {"id":new_id,"orderId":body.get("orderId",""),"orderNumber":body.get("orderNumber",""),"carrier":body.get("carrier","FEDEX"),"service":body.get("service","GROUND"),"trackingNumber":f"TN-{random.randint(10**10,10**11-1)}","status":"pending","weight":body.get("weight",1.0),"dimensions":body.get("dimensions","12x12x12"),"cost":body.get("cost",0),"estimatedDelivery":None,"actualDelivery":None,"shipDate":datetime.now(timezone.utc).isoformat(),"shippingAddress":body.get("shippingAddress",""),"events":[{"status":"label_created","location":"Origin","timestamp":datetime.now(timezone.utc).isoformat()}]}
        SHIPMENTS.insert(0, s)
        return self._ok({"shipment": s})

    def _handle_shipments_put(self, parts):
        if len(parts) >= 5 and parts[4] == "tracking":
            s = next((s for s in SHIPMENTS if s["id"] == parts[3]), None)
            if not s: return self._error("Shipment not found", 404)
            body = self._read_body()
            s["trackingNumber"] = body.get("trackingNumber", s["trackingNumber"])
            return self._ok({"shipment": s})
        self._error("Not Found", 404)

    def _handle_shipping_get(self, parts, qs):
        if len(parts) >= 4 and parts[3] == "kpis":
            total = len(SHIPMENTS)
            delivered = len([s for s in SHIPMENTS if s["status"] == "delivered"])
            in_transit = len([s for s in SHIPMENTS if s["status"] in ("in_transit","out_for_delivery")])
            on_time = len([s for s in SHIPMENTS if s["status"] == "delivered"])  # simplified
            avg_cost = round(sum(s["cost"] for s in SHIPMENTS) / total, 2) if total else 0
            return self._ok({"kpis":{"total":total,"delivered":delivered,"inTransit":in_transit,"onTime":on_time,"avgCost":avg_cost,"carrierBreakdown":{}}})
        self._error("Not Found", 404)

    # ──────────────── Workflows ────────────────
    def _handle_workflows_get(self, parts, qs):
        if len(parts) >= 5 and parts[3] and parts[4] == "steps":
            steps = WORKFLOW_STEPS.get(parts[3], [])
            return self._ok({"steps": steps})
        if len(parts) >= 5 and parts[3] and parts[4] == "executions":
            execs = [e for e in WORKFLOW_EXECUTIONS if e["workflowId"] == parts[3]]
            return self._ok({"executions": execs})
        if len(parts) >= 4:
            w = next((w for w in WORKFLOWS if w["id"] == parts[3]), None)
            if not w: return self._error("Workflow not found", 404)
            return self._ok({"workflow": w})
        return self._ok({"workflows": WORKFLOWS})

    def _handle_workflows_post(self, parts):
        if len(parts) >= 5 and parts[4] in ("execute","toggle"):
            wf_id = parts[3]
            if parts[4] == "toggle":
                w = next((w for w in WORKFLOWS if w["id"] == wf_id), None)
                if not w: return self._error("Workflow not found", 404)
                w["status"] = "inactive" if w["status"] == "active" else "active"
                return self._ok({"workflow": w})
            exec_id = f"WFE-{len(WORKFLOW_EXECUTIONS)+1:04d}"
            ex = {"id":exec_id,"workflowId":wf_id,"status":"in_progress","triggeredBy":"manual","startedAt":datetime.now(timezone.utc).isoformat(),"completedAt":None,"context":{}}
            WORKFLOW_EXECUTIONS.insert(0, ex)
            return self._ok({"execution": ex})
        body = self._read_body()
        new_id = f"WF-{len(WORKFLOWS)+1:03d}"
        w = {"id":new_id,"name":body.get("name","New Workflow"),"description":body.get("description",""),"status":"inactive","category":body.get("category","general"),"priority":body.get("priority",5),"steps":0,"createdAt":datetime.now(timezone.utc).isoformat(),"updatedAt":datetime.now(timezone.utc).isoformat()}
        WORKFLOWS.insert(0, w)
        return self._ok({"workflow": w})

    def _handle_workflows_put(self, parts):
        if len(parts) >= 5 and parts[4] == "status":
            w = next((w for w in WORKFLOWS if w["id"] == parts[3]), None)
            if not w: return self._error("Workflow not found", 404)
            body = self._read_body()
            w["status"] = body.get("status", w["status"])
            w["updatedAt"] = datetime.now(timezone.utc).isoformat()
            return self._ok({"workflow": w})
        self._error("Not Found", 404)

    # ──────────────── Procurement ────────────────
    def _handle_procurement_get(self, parts, qs):
        sub = parts[3] if len(parts) >= 4 else ""
        if sub == "suppliers":
            if len(parts) >= 5:
                s = next((s for s in SUPPLIERS if s["id"] == parts[4]), None)
                if not s: return self._error("Supplier not found", 404)
                if len(parts) >= 6 and parts[5] == "contacts":
                    return self._ok({"contacts": SUPPLIER_CONTACTS_DB.get(s["id"], [])})
                if len(parts) >= 6 and parts[5] == "contracts":
                    return self._ok({"contracts": SUPPLIER_CONTRACTS_DB.get(s["id"], [])})
                return self._ok({"supplier": s})
            page = int(qs.get("page", [1])[0]); size = int(qs.get("size", [20])[0])
            start = (page-1)*size
            return self._ok({"content": SUPPLIERS[start:start+size], "totalElements": len(SUPPLIERS), "totalPages": max(1, (len(SUPPLIERS)+size-1)//size)})
        if sub == "requests":
            if len(parts) >= 5:
                r = next((r for r in PURCHASE_REQUESTS if r["id"] == parts[4]), None)
                if not r: return self._error("Request not found", 404)
                return self._ok({"request": r})
            page = int(qs.get("page", [1])[0]); size = int(qs.get("size", [20])[0])
            start = (page-1)*size
            return self._ok({"content": PURCHASE_REQUESTS[start:start+size], "totalElements": len(PURCHASE_REQUESTS), "totalPages": max(1, (len(PURCHASE_REQUESTS)+size-1)//size)})
        if sub == "rfqs":
            if len(parts) >= 5:
                r = next((r for r in RFQS if r["id"] == parts[4]), None)
                if not r: return self._error("RFQ not found", 404)
                if len(parts) >= 6 and parts[5] == "responses":
                    return self._ok({"responses": RFQ_RESPONSES_DB.get(r["id"], [])})
                return self._ok({"rfq": r})
            page = int(qs.get("page", [1])[0]); size = int(qs.get("size", [20])[0])
            start = (page-1)*size
            return self._ok({"content": RFQS[start:start+size], "totalElements": len(RFQS), "totalPages": max(1, (len(RFQS)+size-1)//size)})
        if sub == "purchase-orders":
            if len(parts) >= 5:
                po = next((p for p in PURCHASE_ORDERS if p["id"] == parts[4]), None)
                if not po: return self._error("Purchase order not found", 404)
                return self._ok({"purchaseOrder": po})
            page = int(qs.get("page", [1])[0]); size = int(qs.get("size", [20])[0])
            start = (page-1)*size
            return self._ok({"content": PURCHASE_ORDERS[start:start+size], "totalElements": len(PURCHASE_ORDERS), "totalPages": max(1, (len(PURCHASE_ORDERS)+size-1)//size)})
        self._error("Not Found", 404)

    def _handle_procurement_post(self, parts):
        sub = parts[3] if len(parts) >= 4 else ""
        body = self._read_body()
        if sub == "suppliers":
            new_id = f"SUP-{len(SUPPLIERS)+1:03d}"
            s = {"id":new_id,"name":body.get("name","New Supplier"),"contact":body.get("contact",""),"email":body.get("email",""),"phone":body.get("phone",""),"category":body.get("category","general"),"status":"active","rating":body.get("rating",3.0),"paymentTerms":body.get("paymentTerms","net30"),"leadTimeDays":body.get("leadTimeDays",14),"address":body.get("address","")}
            SUPPLIERS.insert(0, s)
            return self._ok({"supplier": s})
        if sub == "supplier-contacts":
            c = {"id":f"SC-{len(SUPPLIER_CONTACTS_DB.get(body.get('supplierId',''),[]))+1:03d}","supplierId":body.get("supplierId",""),"name":body.get("name",""),"email":body.get("email",""),"phone":body.get("phone",""),"role":body.get("role",""),"isPrimary":body.get("isPrimary",False)}
            SUPPLIER_CONTACTS_DB.setdefault(c["supplierId"], []).append(c)
            return self._ok({"contact": c})
        if sub == "supplier-contracts":
            ct = {"id":f"SCT-{len(SUPPLIER_CONTRACTS_DB.get(body.get('supplierId',''),[]))+1:03d}","supplierId":body.get("supplierId",""),"title":body.get("title",""),"startDate":body.get("startDate",""),"endDate":body.get("endDate",""),"value":body.get("value",0),"status":"active"}
            SUPPLIER_CONTRACTS_DB.setdefault(ct["supplierId"], []).append(ct)
            return self._ok({"contract": ct})
        if sub == "requests":
            if len(parts) >= 5 and parts[4] in ("submit","approve"):
                r = next((r for r in PURCHASE_REQUESTS if r["id"] == parts[3]), None)
                if not r: return self._error("Request not found", 404)
                r["status"] = {"submit":"pending","approve":"approved"}[parts[4]]
                r["updatedAt"] = datetime.now(timezone.utc).isoformat()
                return self._ok({"request": r})
            new_id = f"PR-{len(PURCHASE_REQUESTS)+1:03d}"
            r = {"id":new_id,"title":body.get("title","New Request"),"requester":body.get("requester",""),"department":body.get("department",""),"items":body.get("items",[]),"total":body.get("total",0),"status":"draft","priority":body.get("priority","medium"),"createdAt":datetime.now(timezone.utc).isoformat(),"updatedAt":None}
            PURCHASE_REQUESTS.insert(0, r)
            return self._ok({"request": r})
        if sub == "rfqs":
            if len(parts) >= 6 and parts[4] == "submit":
                rfq = next((r for r in RFQS if r["id"] == parts[3]), None)
                if not rfq: return self._error("RFQ not found", 404)
                rfq["status"] = "open"
                return self._ok({"rfq": rfq})
            new_id = f"RFQ-{len(RFQS)+1:03d}"
            rfq = {"id":new_id,"title":body.get("title","New RFQ"),"description":body.get("description",""),"deadline":body.get("deadline",""),"status":"draft","items":body.get("items",[]),"totalEstimate":body.get("totalEstimate",0),"responsesCount":0,"createdAt":datetime.now(timezone.utc).isoformat()}
            RFQS.insert(0, rfq)
            return self._ok({"rfq": rfq})
        if sub == "purchase-orders":
            if len(parts) >= 6 and parts[4] in ("approve","receive"):
                po = next((p for p in PURCHASE_ORDERS if p["id"] == parts[3]), None)
                if not po: return self._error("Purchase order not found", 404)
                if parts[4] == "approve": po["status"] = "approved"
                elif parts[4] == "receive": po["status"] = "received"
                po["updatedAt"] = datetime.now(timezone.utc).isoformat()
                return self._ok({"purchaseOrder": po})
            new_id = f"PO-{len(PURCHASE_ORDERS)+1:03d}"
            po = {"id":new_id,"supplierId":body.get("supplierId",""),"supplierName":body.get("supplierName",""),"items":body.get("items",[]),"total":body.get("total",0),"status":"draft","paymentTerms":body.get("paymentTerms","net30"),"expectedDelivery":body.get("expectedDelivery",None),"createdAt":datetime.now(timezone.utc).isoformat(),"updatedAt":None}
            PURCHASE_ORDERS.insert(0, po)
            return self._ok({"purchaseOrder": po})
        self._error("Not Found", 404)

    def _handle_procurement_put(self, parts):
        sub = parts[3] if len(parts) >= 4 else ""
        body = self._read_body()
        if sub == "suppliers":
            s = next((s for s in SUPPLIERS if s["id"] == parts[4] if len(parts) >= 5), None)
            if not s: return self._error("Supplier not found", 404)
            for k,v in body.items(): s[k] = v
            return self._ok({"supplier": s})
        if sub == "requests" and len(parts) >= 6 and parts[5] == "status":
            r = next((r for r in PURCHASE_REQUESTS if r["id"] == parts[4]), None)
            if not r: return self._error("Request not found", 404)
            r["status"] = body.get("status", r["status"])
            r["updatedAt"] = datetime.now(timezone.utc).isoformat()
            return self._ok({"request": r})
        if sub == "purchase-orders" and len(parts) >= 6 and parts[5] == "status":
            po = next((p for p in PURCHASE_ORDERS if p["id"] == parts[4]), None)
            if not po: return self._error("Purchase order not found", 404)
            po["status"] = body.get("status", po["status"])
            po["updatedAt"] = datetime.now(timezone.utc).isoformat()
            return self._ok({"purchaseOrder": po})
        self._error("Not Found", 404)

    # ──────────────── Documents ────────────────
    def _handle_documents_get(self, parts, qs):
        if len(parts) >= 6 and parts[3] == "by-entity":
            return self._ok({"documents": [d for d in DOCUMENTS if d.get("entityId") == qs.get("entityId",[None])[0] or d.get("entityType") == qs.get("entityType",[None])[0]]})
        if len(parts) >= 5 and parts[4] == "versions":
            return self._ok({"versions": DOCUMENT_VERSIONS_DB.get(parts[3], [])})
        if len(parts) >= 4:
            d = next((d for d in DOCUMENTS if d["id"] == parts[3]), None)
            if not d: return self._error("Document not found", 404)
            return self._ok({"document": d})
        entity_type = qs.get("entityType", [None])[0]
        entity_id = qs.get("entityId", [None])[0]
        filtered = DOCUMENTS
        if entity_type: filtered = [d for d in filtered if d.get("entityType") == entity_type]
        if entity_id: filtered = [d for d in filtered if d.get("entityId") == entity_id]
        return self._ok({"documents": filtered})

    def _handle_documents_post(self, parts):
        if len(parts) >= 5 and parts[4] == "versions":
            d = next((d for d in DOCUMENTS if d["id"] == parts[3]), None)
            if not d: return self._error("Document not found", 404)
            vers = DOCUMENT_VERSIONS_DB.setdefault(d["id"], [])
            new_id = f"DV-{len(vers)+1:03d}"
            v = {"id":new_id,"documentId":d["id"],"version":len(vers)+1,"note":"New version uploaded","size":0,"uploadedBy":"Admin","uploadedAt":datetime.now(timezone.utc).isoformat()}
            vers.append(v)
            return self._ok({"version": v})
        body = self._read_body()
        new_id = f"DOC-{len(DOCUMENTS)+1:03d}"
        d = {"id":new_id,"name":body.get("name","Untitled"),"type":body.get("type","general"),"format":body.get("format","pdf"),"size":body.get("size",0),"tags":body.get("tags",[]),"createdAt":datetime.now(timezone.utc).isoformat(),"updatedAt":datetime.now(timezone.utc).isoformat()}
        DOCUMENTS.insert(0, d)
        return self._ok({"document": d})

    def _handle_documents_put(self, parts):
        if len(parts) >= 4:
            d = next((d for d in DOCUMENTS if d["id"] == parts[3]), None)
            if not d: return self._error("Document not found", 404)
            body = self._read_body()
            for k,v in body.items(): d[k] = v
            d["updatedAt"] = datetime.now(timezone.utc).isoformat()
            return self._ok({"document": d})
        self._error("Not Found", 404)

    def _handle_documents_patch(self, parts):
        return self._handle_documents_put(parts)

    # ──────────────── EDI ────────────────
    def _handle_edi_get(self, parts, qs):
        sub = parts[3] if len(parts) >= 4 else ""
        if sub == "kpis":
            return self._ok({"kpis":{"total":len(EDI_DOCUMENTS),"inbound":len([d for d in EDI_DOCUMENTS if d["direction"]=="inbound"]),"outbound":len([d for d in EDI_DOCUMENTS if d["direction"]=="outbound"]),"error":len([d for d in EDI_DOCUMENTS if d["status"]=="error"]),"partners":len(EDI_PARTNERS_DB)}})
        if sub == "partners":
            return self._ok({"partners": EDI_PARTNERS_DB})
        if len(parts) >= 4:
            d = next((d for d in EDI_DOCUMENTS if d["id"] == parts[3]), None)
            if not d: return self._error("EDI document not found", 404)
            return self._ok({"document": d})
        page = int(qs.get("page", [1])[0]); size = int(qs.get("size", [20])[0])
        start = (page-1)*size
        return self._ok({"content": EDI_DOCUMENTS[start:start+size], "totalElements": len(EDI_DOCUMENTS)})

    def _handle_edi_post(self, parts):
        body = self._read_body()
        if len(parts) >= 5 and parts[4] == "reprocess":
            d = next((d for d in EDI_DOCUMENTS if d["id"] == parts[3]), None)
            if not d: return self._error("EDI document not found", 404)
            d["status"] = "pending"; d["errors"] = []; d["processedAt"] = None
            return self._ok({"document": d})
        if len(parts) >= 4 and parts[3] == "parse":
            return self._ok({"parsed": {"orderCount": 1, "items": [{"sku":"SKU-PARSED","name":"Parsed Item","qty":1}]}})
        if len(parts) >= 4 and parts[3] == "upload":
            new_id = f"EDI-{len(EDI_DOCUMENTS)+1:04d}"
            d = {"id":new_id,"type":body.get("type","850"),"direction":"inbound","partnerId":body.get("partnerId",""),"partnerName":body.get("partnerName",""),"status":"received","filename":body.get("filename","upload.edi"),"createdAt":datetime.now(timezone.utc).isoformat(),"processedAt":None,"errors":[],"orderCount":0}
            EDI_DOCUMENTS.insert(0, d)
            return self._ok({"document": d})
        if len(parts) >= 4 and parts[3] == "partners":
            new_id = f"EP-{len(EDI_PARTNERS_DB)+1:04d}"
            p = {"id":new_id,"name":body.get("name","New Partner"),"ediId":body.get("ediId",""),"qualifier":"01","type":body.get("type","retail"),"status":"active","version":"4010","createdAt":datetime.now(timezone.utc).isoformat()}
            EDI_PARTNERS_DB.append(p)
            return self._ok({"partner": p})
        self._error("Not Found", 404)

    # ──────────────── Email Parser ────────────────
    def _handle_email_parser_get(self, parts, qs):
        if len(parts) >= 4 and parts[3] == "kpis":
            return self._ok({"kpis":{"total":len(EMAIL_PARSED_ORDERS),"pending":len([e for e in EMAIL_PARSED_ORDERS if e["status"]=="pending"]),"approved":len([e for e in EMAIL_PARSED_ORDERS if e["status"]=="approved"]),"rejected":len([e for e in EMAIL_PARSED_ORDERS if e["status"]=="rejected"])}})
        if len(parts) >= 4:
            e = next((e for e in EMAIL_PARSED_ORDERS if e["id"] == parts[3]), None)
            if not e: return self._error("Email not found", 404)
            return self._ok({"order": e})
        page = int(qs.get("page", [1])[0]); size = int(qs.get("size", [20])[0])
        start = (page-1)*size
        return self._ok({"content": EMAIL_PARSED_ORDERS[start:start+size], "totalElements": len(EMAIL_PARSED_ORDERS)})

    def _handle_email_parser_post(self, parts):
        body = self._read_body()
        if len(parts) >= 5 and parts[4] in ("approve","reject"):
            e = next((e for e in EMAIL_PARSED_ORDERS if e["id"] == parts[3]), None)
            if not e: return self._error("Email not found", 404)
            e["status"] = {"approve":"approved","reject":"rejected"}[parts[4]]
            return self._ok({"order": e})
        if len(parts) >= 4 and parts[3] in ("parse","parse-csv"):
            new_id = f"EPO-{len(EMAIL_PARSED_ORDERS)+1:04d}"
            e = {"id":new_id,"subject":body.get("subject","Parsed Order"),"sender":body.get("sender",""),"receivedAt":datetime.now(timezone.utc).isoformat(),"items":body.get("items",[]),"status":"pending","confidence":body.get("confidence",0.85)}
            EMAIL_PARSED_ORDERS.insert(0, e)
            return self._ok({"order": e})
        self._error("Not Found", 404)

    # ──────────────── Integration Stores ────────────────
    def _handle_integration_stores_get(self, parts, qs):
        if len(parts) >= 5 and parts[4] == "settings":
            return self._ok({"settings": INTEGRATION_STORE_SETTINGS_DB.get(parts[3], [])})
        if len(parts) >= 5 and parts[4] == "sync-status":
            return self._ok({"status":{"lastSync":"2026-07-01T12:00:00Z","status":"synced","types":[{"type":"orders","status":"completed","lastSync":"2026-07-01T12:00:00Z"},{"type":"products","status":"completed","lastSync":"2026-07-01T12:00:00Z"},{"type":"inventory","status":"in_progress","lastSync":None}]}})
        if len(parts) >= 4:
            s = next((s for s in INTEGRATION_STORES if s["id"] == parts[3]), None)
            if not s: return self._error("Store not found", 404)
            return self._ok({"store": s})
        platform_filter = qs.get("platform", [None])[0]
        filtered = INTEGRATION_STORES
        if platform_filter: filtered = [s for s in filtered if s["platform"] == platform_filter]
        return self._ok({"stores": filtered})

    def _handle_integration_stores_post(self, parts):
        body = self._read_body()
        new_id = f"IS-{len(INTEGRATION_STORES)+1:03d}"
        s = {"id":new_id,"storeCode":body.get("storeCode",""),"storeName":body.get("storeName","New Store"),"platform":body.get("platform","SHOPIFY"),"currency":body.get("currency","USD"),"defaultLocale":body.get("defaultLocale","en_US"),"timezone":body.get("timezone","UTC"),"externalStoreId":body.get("externalStoreId",""),"externalDomain":body.get("externalDomain",""),"status":"connected","lastSyncAt":None,"settings":body.get("settings",{})}
        INTEGRATION_STORES.insert(0, s)
        return self._ok({"store": s})

    def _handle_integration_stores_put(self, parts):
        if len(parts) >= 4:
            s = next((s for s in INTEGRATION_STORES if s["id"] == parts[3]), None)
            if not s: return self._error("Store not found", 404)
            body = self._read_body()
            for k,v in body.items(): s[k] = v
            return self._ok({"store": s})
        self._error("Not Found", 404)

    # ──────────────── Routing Rules ────────────────
    def _handle_routing_rules_get(self, parts, qs):
        if len(parts) >= 4:
            r = next((r for r in ROUTING_RULES if r["id"] == parts[3]), None)
            if not r: return self._error("Rule not found", 404)
            return self._ok({"rule": r})
        return self._ok({"rules": ROUTING_RULES})

    def _handle_routing_rules_post(self, parts):
        if len(parts) >= 4 and parts[3] == "reorder":
            body = self._read_body()
            return self._ok({"success": True})
        body = self._read_body()
        new_id = f"RR-{len(ROUTING_RULES)+1:03d}"
        r = {"id":new_id,"name":body.get("name","New Rule"),"description":body.get("description",""),"priority":body.get("priority",5),"conditions":body.get("conditions",[]),"action":body.get("action",{}),"status":"active","createdAt":datetime.now(timezone.utc).isoformat()}
        ROUTING_RULES.insert(0, r)
        return self._ok({"rule": r})

    def _handle_routing_rules_put(self, parts):
        if len(parts) >= 4:
            r = next((r for r in ROUTING_RULES if r["id"] == parts[3]), None)
            if not r: return self._error("Rule not found", 404)
            body = self._read_body()
            for k,v in body.items(): r[k] = v
            return self._ok({"rule": r})
        self._error("Not Found", 404)

    # ──────────────── Order Routing ────────────────
    def _handle_order_routing_get(self, parts, qs):
        if len(parts) >= 5 and parts[3] == "allocations" and parts[4]:
            allocs = [a for a in ORDER_ALLOCATIONS if a["orderId"] == parts[4]]
            return self._ok({"allocations": allocs})
        if len(parts) >= 5 and parts[3] == "exceptions":
            if len(parts) >= 5:
                e = next((e for e in ROUTING_EXCEPTIONS if e["id"] == parts[4]), None)
                if e: return self._ok({"exception": e})
            return self._ok({"exceptions": ROUTING_EXCEPTIONS})
        if len(parts) >= 4 and parts[3] == "kpis":
            return self._ok({"kpis":{"totalAllocations":len(ORDER_ALLOCATIONS),"activeAllocations":len([a for a in ORDER_ALLOCATIONS if a["status"]=="active"]),"exceptions":len(ROUTING_EXCEPTIONS),"openExceptions":len([e for e in ROUTING_EXCEPTIONS if e["status"]=="open"])}})
        self._error("Not Found", 404)

    def _handle_order_routing_post(self, parts):
        body = self._read_body()
        if len(parts) >= 4 and parts[3] == "allocate":
            new_id = f"OA-{len(ORDER_ALLOCATIONS)+1:04d}"
            a = {"id":new_id,"orderId":body.get("orderId",""),"warehouseId":body.get("warehouseId","WH-01"),"carrier":body.get("carrier","FEDEX"),"service":body.get("service","GROUND"),"estimatedDelivery":(datetime.now(timezone.utc)+timedelta(days=3)).isoformat(),"status":"active","createdAt":datetime.now(timezone.utc).isoformat()}
            ORDER_ALLOCATIONS.append(a)
            return self._ok({"allocation": a})
        if len(parts) >= 4 and parts[3] == "simulate":
            return self._ok({"simulation":{"warehouse":"WH-01","carrier":"FEDEX","cost":15.99,"eta":"3 days"}})
        if len(parts) >= 4 and parts[3] == "reallocate":
            return self._ok({"success":True,"message":"Reallocation complete"})
        if len(parts) >= 6 and parts[3] == "exceptions" and parts[5] in ("resolve","escalate"):
            e = next((e for e in ROUTING_EXCEPTIONS if e["id"] == parts[4]), None)
            if not e: return self._error("Exception not found", 404)
            e["status"] = {"resolve":"resolved","escalate":"escalated"}[parts[5]]
            e["resolvedAt"] = datetime.now(timezone.utc).isoformat()
            return self._ok({"exception": e})
        self._error("Not Found", 404)

    # ──────────────── Rate Shopping ────────────────
    def _handle_rate_shopping_get(self, parts, qs):
        self._error("Not Found", 404)

    def _handle_rate_shopping_post(self, parts):
        body = self._read_body()
        if len(parts) >= 4 and parts[3] in ("shop","best"):
            rates = [{"carrier":c["name"],"service":random.choice(c["services"]),"rate":round(random.uniform(5,50),2),"transitDays":random.randint(1,7)} for c in CARRIERS for _ in range(2)]
            if parts[3] == "best":
                return self._ok({"best": min(rates, key=lambda r: r["rate"])})
            return self._ok({"rates": rates})
        self._error("Not Found", 404)

    # ──────────────── Integration Platform ────────────────
    def _handle_integration_platform_get(self, parts, qs):
        sub = parts[3] if len(parts) >= 4 else ""
        if sub == "dashboard":
            return self._ok({"dashboard":{"activeFlows":3,"totalEndpoints":12,"failedJobs":1,"pendingJobs":4,"recentActivity":[]}})
        if sub == "endpoints":
            if len(parts) >= 5:
                return self._ok({"endpoint":{"id":parts[4],"name":"API Endpoint","type":"REST","url":"https://api.example.com","status":"active"}})
            return self._ok({"endpoints":[{"id":"EP-001","name":"Shopify API","type":"REST","url":"https://shopify.com/admin","status":"active"},{"id":"EP-002","name":"Amazon SP-API","type":"REST","url":"https://sellingpartnerapi.amazon.com","status":"active"},{"id":"EP-003","name":"BigCommerce API","type":"REST","url":"https://api.bigcommerce.com","status":"error"}],"total":3})
        if sub == "flows":
            if len(parts) >= 5:
                if len(parts) >= 6 and parts[5] == "steps":
                    return self._ok({"steps":[{"id":"FS-001","name":"Fetch Orders","type":"source","config":{}},{"id":"FS-002","name":"Transform","type":"transform","config":{}},{"id":"FS-003","name":"Load to OMS","type":"destination","config":{}}]})
                return self._ok({"flow":{"id":parts[4],"name":"Shopify Order Sync","status":"active","lastRun":"2026-07-01T12:00:00Z"}})
            return self._ok({"flows":[{"id":"FL-001","name":"Shopify Order Sync","status":"active","lastRun":"2026-07-01T12:00:00Z"},{"id":"FL-002","name":"Amazon Inventory Sync","status":"active","lastRun":"2026-07-01T11:30:00Z"},{"id":"FL-003","name":"BigCommerce Product Sync","status":"paused","lastRun":"2026-06-30T15:00:00Z"}],"total":3})
        if sub == "mappings":
            if len(parts) >= 5:
                return self._ok({"mapping":{"id":parts[4],"name":"Order Mapping","source":"shopify","target":"nexus","fields":[{"source":"order_number","target":"orderNumber"}]}})
            return self._ok({"mappings":[{"id":"MP-001","name":"Shopify to Nexus Order","source":"SHOPIFY","target":"NEXUS","status":"active"},{"id":"MP-002","name":"Amazon to Nexus Product","source":"AMAZON","target":"NEXUS","status":"active"}],"total":2})
        if sub == "validation-rules":
            return self._ok({"rules":[{"id":"VR-001","name":"Order Total Validation","field":"total","condition":"greater_than","value":0,"severity":"error"}]})
        if sub in ("imports","exports","cdc","dlq"):
            return self._ok({f"{sub}":[],"total":0})
        if sub == "audit":
            entity = qs.get("entity", [None])[0]
            if entity:
                return self._ok({"logs":[{"id":"AUD-001","entity":entity,"action":"created","timestamp":"2026-07-01T08:00:00Z","user":"Admin"}]})
            return self._ok({"logs":[{"id":"AUD-001","entity":"order","entityId":"ORD-10000","action":"created","timestamp":"2026-07-01T08:00:00Z","user":"Admin"},{"id":"AUD-002","entity":"product","entityId":"P001","action":"updated","timestamp":"2026-07-01T09:00:00Z","user":"Admin"}]})
        self._error("Not Found", 404)

    def _handle_integration_platform_post(self, parts):
        body = self._read_body()
        sub = parts[3] if len(parts) >= 4 else ""
        if sub in ("endpoints","flows","mappings","validation-rules","imports","exports"):
            return self._ok({"success":True,"id":f"{sub.upper()}-{random.randint(100,999)}"})
        if len(parts) >= 5 and parts[4] in ("test","activate","pause","process","retry","cancel","replay","ignore","transform","validate"):
            return self._ok({"success":True})
        if sub == "cdc" and len(parts) >= 5 and parts[4]:
            return self._ok({"success":True,"processed":True})
        self._error("Not Found", 404)

    # ──────────────── Integration Hub ────────────────
    def _handle_integration_hub_get(self, parts, qs):
        sub = parts[3] if len(parts) >= 4 else ""
        if sub == "platforms":
            return self._ok({"platforms":[{"id":"shopify","name":"Shopify","icon":"shopify","connected":True},{"id":"amazon","name":"Amazon","icon":"amazon","connected":True},{"id":"bigcommerce","name":"BigCommerce","icon":"bigcommerce","connected":False},{"id":"ebay","name":"eBay","icon":"ebay","connected":False},{"id":"walmart","name":"Walmart","icon":"walmart","connected":False}]})
        if sub == "connectors":
            if len(parts) >= 5:
                return self._ok({"connector":{"id":parts[4],"platform":"shopify","name":"Shopify Store","status":"connected"}})
            return self._ok({"connectors":[{"id":"CON-001","platform":"shopify","name":"Glow & Co. Shopify","status":"connected"},{"id":"CON-002","platform":"amazon","name":"Amazon Store","status":"connected"},{"id":"CON-003","platform":"bigcommerce","name":"Aria BigCommerce","status":"disconnected"}]})
        if sub == "jobs":
            if len(parts) >= 5:
                return self._ok({"job":{"id":parts[4],"type":"sync","status":"completed","progress":100}})
            return self._ok({"jobs":[],"total":0})
        self._error("Not Found", 404)

    def _handle_integration_hub_post(self, parts):
        body = self._read_body()
        sub = parts[3] if len(parts) >= 4 else ""
        if sub == "connectors":
            new_id = f"CON-{len(INTEGRATION_STORES)+1:04d}"
            c = {"id":new_id,"platform":body.get("platform","shopify"),"name":body.get("name","New Connector"),"status":"connected"}
            return self._ok({"connector": c})
        if len(parts) >= 6 and parts[5] in ("authorize","disconnect","sync","test","webhooks"):
            return self._ok({"success":True})
        self._error("Not Found", 404)

    # ──────────────── Inventory Receipts ────────────────
    def _handle_inventory_receipts_get(self, parts, qs):
        if len(parts) >= 4:
            r = next((r for r in INVENTORY_RECEIPTS if r["id"] == parts[3]), None)
            if not r: return self._error("Receipt not found", 404)
            return self._ok({"receipt": r})
        return self._ok({"receipts": INVENTORY_RECEIPTS})

    def _handle_inventory_receipts_post(self, parts):
        if len(parts) >= 5 and parts[4] == "receive":
            r = next((r for r in INVENTORY_RECEIPTS if r["id"] == parts[3]), None)
            if not r: return self._error("Receipt not found", 404)
            r["status"] = "received"
            return self._ok({"receipt": r})
        body = self._read_body()
        new_id = f"IR-{len(INVENTORY_RECEIPTS)+1:03d}"
        r = {"id":new_id,"sku":body.get("sku",""),"name":body.get("name","New Item"),"qty":body.get("qty",0),"expectedDate":body.get("expectedDate",""),"status":"pending","supplier":body.get("supplier",""),"poNumber":body.get("poNumber",""),"notes":body.get("notes",""),"createdAt":datetime.now(timezone.utc).isoformat()}
        INVENTORY_RECEIPTS.insert(0, r)
        return self._ok({"receipt": r})

    # ──────────────── Cycle Counts ────────────────
    def _handle_cycle_counts_get(self, parts, qs):
        if len(parts) >= 4:
            cc = next((c for c in CYCLE_COUNTS_DB if c["id"] == parts[3]), None)
            if not cc: return self._error("Cycle count not found", 404)
            return self._ok({"cycleCount": cc})
        return self._ok({"cycleCounts": CYCLE_COUNTS_DB})

    def _handle_cycle_counts_post(self, parts):
        if len(parts) >= 5 and parts[4] == "count":
            cc = next((c for c in CYCLE_COUNTS_DB if c["id"] == parts[3]), None)
            if not cc: return self._error("Cycle count not found", 404)
            body = self._read_body()
            cc["actual"] = body.get("actual", cc["expected"])
            cc["discrepancy"] = cc["actual"] - cc["expected"]
            cc["status"] = "completed"
            cc["countedBy"] = body.get("countedBy", "Admin")
            cc["countedAt"] = datetime.now(timezone.utc).isoformat()
            return self._ok({"cycleCount": cc})
        body = self._read_body()
        new_id = f"CC-{len(CYCLE_COUNTS_DB)+1:03d}"
        cc = {"id":new_id,"zone":body.get("zone","A1"),"sku":body.get("sku",""),"name":body.get("name",""),"expected":body.get("expected",0),"actual":None,"status":"pending","countedBy":None,"countedAt":None,"discrepancy":None}
        CYCLE_COUNTS_DB.insert(0, cc)
        return self._ok({"cycleCount": cc})

    # ──────────────── BigCommerce / Shopify Integration ────────────────
    def _handle_integrations_get(self, parts):
        if len(parts) >= 5 and parts[3] == "bigcommerce":
            if parts[4] == "config":
                return self._ok({"config": {"storeHash":"abc123","apiKey":"****","apiSecret":"****","status":"connected","lastSync":"2026-07-01T10:00:00Z"}})
            if parts[4] == "sync-logs":
                return self._ok({"logs": [{"id":"LOG-1","type":"orders","status":"completed","synced":25,"errors":0,"startedAt":"2026-07-01T10:00:00Z","completedAt":"2026-07-01T10:05:00Z"}]})
        self._error("Not Found", 404)

    def _handle_integrations_post(self, parts):
        body = self._read_body()
        if len(parts) >= 5 and parts[3] == "bigcommerce":
            if parts[4] == "config":
                return self._ok({"config": {"storeHash":"abc123","apiKey":"****","apiSecret":"****","status":"connected"}})
            if parts[4] == "sync":
                return self._ok({"syncResult": {"status":"completed","synced":random.randint(5,50),"errors":0}})
            if parts[4] == "webhooks" and len(parts) >= 6 and parts[5] == "register":
                return self._ok({"success": True, "webhooks": ["order/created","product/updated"]})
        self._error("Not Found", 404)

    # ──────────────── Import / Sample Data ────────────────
    def _handle_import_get(self, parts, qs):
        sub = parts[3] if len(parts) >= 4 else ""
        if sub == "entity-types":
            return self._ok({"types":["orders","products","customers","inventory","returns","invoices"]})
        if sub == "formats":
            return self._ok({"formats":["csv","json","xlsx","xml"]})
        if sub == "modes":
            return self._ok({"modes":["create","update","upsert"]})
        if sub == "history":
            if len(parts) >= 6 and parts[5] in ("logs","download"):
                return self._ok({"data":[{"line":1,"status":"success","message":"Row imported"}]})
            if len(parts) >= 5:
                return self._ok({"import":{"id":parts[4],"entityType":"orders","status":"completed","totalRows":100,"successRows":98,"errorRows":2,"createdAt":"2026-07-01T10:00:00Z"}})
            return self._ok({"imports":[{"id":"IMP-001","entityType":"orders","filename":"orders_july.csv","status":"completed","totalRows":100,"successRows":98,"errorRows":2,"createdAt":"2026-07-01T10:00:00Z"},{"id":"IMP-002","entityType":"products","filename":"products_update.csv","status":"processing","totalRows":50,"successRows":0,"errorRows":0,"createdAt":"2026-07-02T08:00:00Z"}],"total":2})
        if sub == "sample-data":
            if len(parts) >= 5 and parts[4] == "entity-types":
                return self._ok({"types":["orders","products","customers","inventory","returns","invoices"]})
            if len(parts) >= 6 and parts[4] == "generate":
                return self._ok({"data":{"generated":True,"count":25}})
        self._error("Not Found", 404)

    def _handle_import_post(self, parts):
        body = self._read_body()
        sub = parts[3] if len(parts) >= 4 else ""
        if sub in ("orders","products","customers","inventory","returns","invoices"):
            new_id = f"IMP-{len(self.__class__.__name__)+1:04d}"
            return self._ok({"import":{"id":f"IMP-{random.randint(100,999)}","status":"processing","totalRows":len(body.get("rows",[]))}})
        if sub == "history" and len(parts) >= 6 and parts[5] == "reprocess":
            return self._ok({"import":{"id":parts[4],"status":"processing"}})
        self._error("Not Found", 404)

    # ──────────────── Expanded Warehouse ────────────────
    def _handle_warehouse_get(self, parts):
        if len(parts) >= 5 and parts[4] == "zones":
            wh_id = parts[3]
            zones = [z for z in WAREHOUSE_ZONES_DB if z["warehouseId"] == wh_id]
            return self._ok({"zones": zones})
        if len(parts) >= 5 and parts[4] == "bins":
            wh_id = parts[3]
            bins = [b for b in WAREHOUSE_BINS_DB if b["warehouseId"] == wh_id]
            if len(parts) >= 6 and parts[5] == "empty":
                bins = [b for b in bins if b["status"] == "empty"]
            return self._ok({"bins": bins})
        if len(parts) >= 5 and parts[4] == "staff":
            wh_id = parts[3]
            return self._ok({"staff": [s for s in WAREHOUSE_STAFF_DB if s["warehouseId"] == wh_id]})
        if len(parts) >= 5 and parts[4] == "equipment":
            wh_id = parts[3]
            return self._ok({"equipment": [e for e in WAREHOUSE_EQUIPMENT_DB if e["warehouseId"] == wh_id]})
        if len(parts) >= 5 and parts[4] == "summary":
            wh = next((w for w in WAREHOUSES if w["id"] == parts[3]), None)
            if not wh: return self._error("Warehouse not found", 404)
            return self._ok({"summary": {"warehouse": wh, "zones": len([z for z in WAREHOUSE_ZONES_DB if z["warehouseId"] == wh["id"]]), "bins": len([b for b in WAREHOUSE_BINS_DB if b["warehouseId"] == wh["id"]]), "staff": len([s for s in WAREHOUSE_STAFF_DB if s["warehouseId"] == wh["id"]]), "equipment": len([e for e in WAREHOUSE_EQUIPMENT_DB if e["warehouseId"] == wh["id"]])}})
        if len(parts) >= 4 and parts[3] == "zones":
            return self._ok({"zones": WAREHOUSE_ZONES_DB})
        if len(parts) >= 4 and self._is_resource_id(parts[3]):
            wh = next((w for w in WAREHOUSES if w["id"] == parts[3]), None)
            if not wh: return self._error("Warehouse not found", 404)
            return self._ok({"warehouse": wh})
        return self._ok({"warehouses": WAREHOUSES})

    def _handle_warehouse_post(self, parts):
        body = self._read_body()
        if len(parts) >= 4 and parts[3] == "zones":
            zone = {"id":f"Z-{uuid.uuid4().hex[:8]}","warehouseId":body.get("warehouseId","WH-001"),
                    "name":body.get("name","New Zone"),"code":body.get("code",""),
                    "type":body.get("type","storage"),"capacity":body.get("capacity",1000),
                    "used":0,"status":"active"}
            WAREHOUSE_ZONES_DB.append(zone)
            return self._ok({"zone": zone})
        if len(parts) >= 4 and parts[3] == "bins":
            bin_rec = {"id":f"B-{uuid.uuid4().hex[:8]}","warehouseId":body.get("warehouseId","WH-001"),
                       "zoneId":body.get("zoneId",""),"code":body.get("code",""),
                       "type":body.get("type","standard"),"status":"empty",
                       "capacity":body.get("capacity",100),"used":0}
            WAREHOUSE_BINS_DB.append(bin_rec)
            return self._ok({"bin": bin_rec})
        if len(parts) >= 4 and parts[3] == "staff":
            staff = {"id":f"E-{len(WAREHOUSE_STAFF_DB)+1:03d}","warehouseId":body.get("warehouseId","WH-001"),
                     "name":body.get("name","New Staff"),"role":body.get("role","picker"),
                     "status":"active","picksToday":0,"joinedAt":datetime.now(timezone.utc).isoformat()}
            WAREHOUSE_STAFF_DB.append(staff)
            return self._ok({"staff": staff})
        if len(parts) >= 4 and parts[3] == "equipment":
            eq = {"id":f"EQ-{len(WAREHOUSE_EQUIPMENT_DB)+1:03d}","warehouseId":body.get("warehouseId","WH-001"),
                  "name":body.get("name","New Equipment"),"type":body.get("type","forklift"),
                  "status":"available","lastMaintenance":datetime.now(timezone.utc).isoformat()}
            WAREHOUSE_EQUIPMENT_DB.append(eq)
            return self._ok({"equipment": eq})
        wh_id = f"WH-{len(WAREHOUSES)+1:03d}"
        wh = {"id":wh_id,"name":body.get("name","New Warehouse"),"code":body.get("code",wh_id),
              "location":body.get("location",""),"capacity":body.get("capacity",10000),
              "used":0,"status":"active","type":body.get("type","warehouse")}
        WAREHOUSES.append(wh)
        return self._ok({"warehouse": wh})

    def _handle_warehouse_put(self, parts):
        body = self._read_body()
        if len(parts) >= 6 and parts[4] == "bins" and parts[5] == "reserve":
            b = next((b for b in WAREHOUSE_BINS_DB if b["id"] == parts[3]), None)
            if not b: return self._error("Bin not found", 404)
            b["status"] = "reserved"
            return self._ok({"bin": b})
        if len(parts) >= 6 and parts[4] == "bins" and parts[5] == "release":
            b = next((b for b in WAREHOUSE_BINS_DB if b["id"] == parts[3]), None)
            if not b: return self._error("Bin not found", 404)
            b["status"] = "empty"
            return self._ok({"bin": b})
        if len(parts) >= 6 and parts[4] == "staff" and parts[5] == "increment-picks":
            s = next((s for s in WAREHOUSE_STAFF_DB if s["id"] == parts[3]), None)
            if not s: return self._error("Staff not found", 404)
            s["picksToday"] = s.get("picksToday", 0) + 1
            return self._ok({"staff": s})
        if len(parts) >= 6 and parts[4] == "equipment" and parts[5] == "status":
            eq = next((e for e in WAREHOUSE_EQUIPMENT_DB if e["id"] == parts[3]), None)
            if not eq: return self._error("Equipment not found", 404)
            eq["status"] = body.get("status", "available")
            return self._ok({"equipment": eq})
        if len(parts) >= 4 and self._is_resource_id(parts[3]):
            wh = next((w for w in WAREHOUSES if w["id"] == parts[3]), None)
            if not wh: return self._error("Warehouse not found", 404)
            for k,v in body.items():
                if k in wh: wh[k] = v
            return self._ok({"warehouse": wh})
        self._error("Not Found", 404)

    # ──────────────── Expanded Notifications ────────────────
    def _handle_notifications_get(self, parts):
        if len(parts) >= 5 and parts[3] == "templates":
            if len(parts) >= 5:
                t = next((t for t in NOTIFICATION_TEMPLATES if t["id"] == parts[4]), None)
                if t: return self._ok({"template": t})
            return self._ok({"templates": NOTIFICATION_TEMPLATES})
        if len(parts) >= 4 and parts[3] == "logs":
            return self._ok({"logs": NOTIFICATION_LOGS})
        if len(parts) >= 4 and parts[3] == "alerts":
            return self._ok({"alerts": ALERT_RULES})
        if len(parts) >= 4 and parts[3] == "unread-count":
            return self._ok({"count": len([n for n in NOTIFICATIONS if not n["read"]])})
        return self._ok({"notifications": NOTIFICATIONS})

    def _handle_notifications_put(self, parts):
        body = self._read_body()
        if len(parts) >= 5 and parts[3] == "templates":
            t = next((t for t in NOTIFICATION_TEMPLATES if t["id"] == parts[4]), None)
            if not t: return self._error("Template not found", 404)
            for k,v in body.items():
                if k in t: t[k] = v
            return self._ok({"template": t})
        if len(parts) >= 6 and parts[3] == "alerts" and parts[5] == "toggle":
            a = next((a for a in ALERT_RULES if a["id"] == parts[4]), None)
            if not a: return self._error("Alert not found", 404)
            a["enabled"] = not a.get("enabled", True)
            return self._ok({"alert": a})
        self._error("Not Found", 404)

    def _handle_notifications_post(self, parts):
        body = self._read_body()
        if len(parts) >= 5 and parts[3] == "templates":
            t = {"id":f"NT-{len(NOTIFICATION_TEMPLATES)+1:03d}","name":body.get("name","New Template"),
                 "subject":body.get("subject",""),"body":body.get("body",""),
                 "type":body.get("type","email"),"channels":body.get("channels",["email"]),
                 "active":body.get("active",True),"createdAt":datetime.now(timezone.utc).isoformat()}
            NOTIFICATION_TEMPLATES.append(t)
            return self._ok({"template": t})
        if len(parts) >= 4 and parts[3] == "send":
            return self._ok({"success": True, "sent": 1})
        if len(parts) >= 4 and parts[3] == "alerts":
            a = {"id":f"ALERT-{len(ALERT_RULES)+1:03d}","name":body.get("name","New Alert"),
                 "condition":body.get("condition",""),"severity":body.get("severity","info"),
                 "enabled":True,"createdAt":datetime.now(timezone.utc).isoformat()}
            ALERT_RULES.append(a)
            return self._ok({"alert": a})
        self._error("Not Found", 404)

    def log_message(self, format, *args):
        ts = datetime.now().strftime("%H:%M:%S")
        print(f"[{ts}] {args[0]} {args[1]}")

if __name__ == "__main__":
    server = HTTPServer((HOST, PORT), NexusAPIHandler)
    print(f"[Backend Server] Nexus OMS API running on {HOST}:{PORT}")
    print(f"[Backend Server] Auth: POST /api/v1/auth/login | Users: admin/Test1234!, john.smith/picker123")
    print(f"[Backend Server] Endpoints: orders, products, customers, inventory, warehouse, wave-plans,")
    print(f"[Backend Server]            labor, picking, packing, carriers, labels, manifests,")
    print(f"[Backend Server]            returns, payments, invoices, reconciliation, reports,")
    print(f"[Backend Server]            task-queues, notifications, settings, dashboard, ai,")
    print(f"[Backend Server]            shipments, workflows, procurement, documents, edi,")
    print(f"[Backend Server]            email-parser, integration-stores, routing-rules,")
    print(f"[Backend Server]            order-routing, rate-shopping, integration-platform,")
    print(f"[Backend Server]            integration-hub, inventory-receipts, cycle-counts, import")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[Backend Server] Shutting down")
        server.server_close()
