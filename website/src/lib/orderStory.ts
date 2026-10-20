/** Shared by the order film's page script and its 3D world (kept apart so the page does not load three.js early). */
export const CHAPTERS = [
  { key: 'order', from: 0.0, to: 0.12 },
  { key: 'stock', from: 0.12, to: 0.24 },
  { key: 'approve', from: 0.24, to: 0.34 },
  { key: 'shipdate', from: 0.34, to: 0.47 },
  { key: 'wave', from: 0.47, to: 0.56 },
  { key: 'pick', from: 0.56, to: 0.7 },
  { key: 'pack', from: 0.7, to: 0.83 },
  { key: 'dispatch', from: 0.83, to: 1.0 },
] as const;

export const CHANNELS = ['Shopify', 'BigCommerce', 'Amazon', 'Walmart', 'WooCommerce', 'Adobe Commerce', 'eBay', 'Etsy',
  'TikTok Shop', 'Wix', 'Squarespace', 'Salesforce Commerce', 'Shopify POS', 'B2B portal', 'EDI partners', 'Any API'];

