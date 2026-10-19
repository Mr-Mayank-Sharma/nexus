/**
 * The site's facts in one place. Every capability carries an honest status:
 *  - live:   run against a real outside system (a Shopify or BigCommerce development store) and the result checked
 *  - tested: covered by automated tests, and for some, run end to end on a real database; not yet run against the
 *            real outside system it talks to
 *  - next:   not built yet
 * If a status changes, change it here; every page reads from this file.
 */
export type Status = 'live' | 'tested' | 'next';
export const STATUS_LABEL: Record<Status, string> = { live: 'Verified live', tested: 'Built and tested', next: 'Not built yet' };
export const STATUS_CHIP: Record<Status, string> = { live: 'chip chip--go', tested: 'chip chip--warn', next: 'chip chip--dim' };

export interface Capability { title: string; body: string; status: Status }
export interface ProductPage {
  slug: string; eyebrow: string; title: string; lead: string; problem: string;
  capabilities: Capability[]; outcome: string; related: string[];
}

export const PRODUCT: ProductPage[] = [
  {
    slug: 'order-routing', eyebrow: 'Order routing',
    title: 'Keep every order together',
    lead: 'Rules decide which warehouse or store ships each order. Nexus prefers one location that holds the whole order, then as few as possible, and steers away from stores that keep failing picks.',
    problem: 'Line-by-line "nearest location" routing splits orders into extra parcels. Each split is another pick, another box, another label and another tracking email, and when a store cannot find the item the order stalls.',
    capabilities: [
      { title: 'Rules and strategies', body: 'Ordered rules with conditions, plus rule-based, scoring and hybrid strategies. A stock-aware allocator never promises more than a location holds.', status: 'tested' },
      { title: 'Fewest shipments first', body: 'One location for the whole order when any can; otherwise the smallest set of locations that covers it.', status: 'tested' },
      { title: 'Failure-aware choice', body: 'Each store\'s pick-failure rate, from your own rejections over 90 days and smoothed so a quiet store is not judged on three orders, counts against it when routing.', status: 'tested' },
      { title: 'Backorders, not silent shortfalls', body: 'Demand no location can cover becomes an explicit backorder and an alert, never a quiet over-promise.', status: 'tested' },
    ],
    outcome: 'On the replay sample (invented data, 220 orders), keeping orders together cut shipments from 265 to 220 and split orders from 45 to none.',
    related: ['what-if', 'store-fulfilment', 'fewer-split-shipments'],
  },
  {
    slug: 'inventory', eyebrow: 'Stock you can trust',
    title: 'One honest number per location',
    lead: 'Every change to stock is a ledger line: receipts, picks, transfers, counts, returns, write-offs. What you can sell is computed from that, per location, and published to Shopify location by location.',
    problem: 'Shopify\'s own oversell check looks at total stock, not each location\'s, and stock fed in once a day goes stale between updates. Selling what a location does not have is how most cancellations start.',
    capabilities: [
      { title: 'A ledger behind every number', body: 'Stock rows change only through recorded movements; a check reconciles every row against its ledger.', status: 'tested' },
      { title: 'Bring in your current stock', body: 'Reads Shopify\'s physical on-hand for each mapped location and creates opening balances. Dry run first; never pushes numbers back out.', status: 'live' },
      { title: 'Publish per location', body: 'Sellable stock per location, after buffers and caps, sent to the matching Shopify location. Dry run by default; unknown stock is skipped, never published as zero.', status: 'live' },
      { title: 'Held units', body: 'Units a store says it cannot find are held out of what can be sold at once, without changing on-hand until a manager approves.', status: 'tested' },
    ],
    outcome: 'On a Shopify development store, Nexus brought in 6,923 stock rows from on-hand with the ledger balanced, and a stock write was read back from Shopify exactly.',
    related: ['stop-overselling', 'scheduled-jobs', 'transfers-and-counts'],
  },
  {
    slug: 'store-fulfilment', eyebrow: 'Store fulfilment',
    title: 'When a store can\'t find it, the order still ships whole',
    lead: 'Stores pick, pack and ship online orders. When a store rejects a line, Nexus sends the whole order to another location that holds all of it, cancels the first store\'s open pick work, and tells it what to put back.',
    problem: 'Research on ship-from-store programs found that ordering store attempts by their chance of failing, not just by cost, cut fulfilment cost by as much as 22% on real retailer data. Most systems simply re-split the order.',
    capabilities: [
      { title: 'Reject a pick, keep the order together', body: 'A rejection re-routes the whole order to a location that can ship all of it, or raises a visible shortfall if none can.', status: 'tested' },
      { title: 'Stores that rejected an order never get it back', body: 'Every earlier rejection of the order is excluded from the next choice.', status: 'tested' },
      { title: 'Pick work cancelled', body: 'The first store\'s pending pick tasks for the order are cancelled; lines already picked are counted so the store knows to put them back.', status: 'tested' },
      { title: 'BOPIS, ship-from-store, endless aisle', body: 'Store pickup lifecycle, ship-from-store routing and store-assisted orders.', status: 'tested' },
    ],
    outcome: 'Run end to end on a real database: a 2-unit order rejected at one location moved whole to another, with the first location\'s reservation released and the rejected unit held out of availability.',
    related: ['store-pick-failures', 'order-routing', 'ship-from-store'],
  },
  {
    slug: 'scheduled-jobs', eyebrow: 'Scheduled jobs',
    title: 'Every run, every failed row, and why',
    lead: 'Your team schedules its own imports, exports and syncs. Every run keeps its counts, a log, the file it produced, and each row that failed with the reason, as a CSV you can open.',
    problem: 'When a nightly sync half-works, the usual answer is "some rows failed" and a ticket to engineering. People need to see which rows and why, and fix them themselves.',
    capabilities: [
      { title: 'Schedules people can read', body: 'Every few minutes, hourly, daily or weekly in your time zone; never more often than every five minutes.', status: 'tested' },
      { title: 'Failed rows with reasons', body: 'Each run keeps the rows that failed or need attention, with the reason, downloadable as CSV.', status: 'tested' },
      { title: 'Safe by default', body: 'New jobs start switched off; one run at a time per job; a run left hanging by a restart is closed out.', status: 'tested' },
      { title: 'Job types', body: 'Shopify order import, product sync and stock import; NetSuite stock import; stock and pick-failure exports.', status: 'tested' },
    ],
    outcome: 'On a local install the scheduler started a run on its own at the five-minute mark, and a 6,938-row stock export downloaded as CSV.',
    related: ['inventory', 'integrations'],
  },
];

export interface UseCase { slug: string; title: string; eyebrow: string; lead: string; how: string[]; product: string }
export const USE_CASES: UseCase[] = [
  { slug: 'stop-overselling', eyebrow: 'Use case', title: 'Stop overselling at your busiest location', product: 'inventory',
    lead: 'Sell what each location really has, not what the total says.',
    how: ['Bring in each location\'s physical on-hand from Shopify, then keep it as a ledger.', 'Publish what can be sold per location, after buffers and caps, and only for stock Nexus knows about.', 'Hold units a store cannot find out of availability at once.'] },
  { slug: 'fewer-split-shipments', eyebrow: 'Use case', title: 'Fewer split shipments', product: 'order-routing',
    lead: 'One parcel when one location can ship it all.',
    how: ['Prefer one location for the whole order; cover the rest with as few as possible.', 'Replay last quarter\'s orders under the new rule before you switch it on.', 'See exactly which orders would ship differently, and why.'] },
  { slug: 'store-pick-failures', eyebrow: 'Use case', title: 'Handle store pick failures without breaking the order', product: 'store-fulfilment',
    lead: 'A store says "not found". The customer never notices.',
    how: ['Re-route the whole order to a location that holds all of it.', 'Never send it back to a store that already rejected it.', 'Learn each store\'s failure rate and route around unreliable stock.'] },
  { slug: 'ship-from-store', eyebrow: 'Use case', title: 'Ship from store', product: 'store-fulfilment',
    lead: 'Turn store stock into online sales without turning stores into a warehouse problem.',
    how: ['Route online orders to stores when they are the best choice.', 'Give stores pick, pack and ship with reasons for every rejection.', 'Keep store stock honest so online promises hold.'] },
  { slug: 'bopis', eyebrow: 'Use case', title: 'Buy online, pick up in store', product: 'store-fulfilment',
    lead: 'Promise pickup only where the item really is.',
    how: ['Pickup orders follow their own lifecycle, from ready to collected.', 'Availability comes from the same per-location ledger.', 'Stores see what is waiting and for whom.'] },
  { slug: 'transfers-and-counts', eyebrow: 'Use case', title: 'Transfers and cycle counts', product: 'inventory',
    lead: 'Move and count stock, and keep the books right.',
    how: ['Transfers out and in move stock through the ledger, with what was shipped and what arrived.', 'Cycle counts reconcile shelves with records.', 'Receiving transfers inside Shopify POS is not built yet: Shopify now does this natively, so Nexus will sync with it rather than rebuild it.'] },
];

export interface Integration { name: string; status: Status; note: string }
export const INTEGRATIONS: Integration[] = [
  { name: 'Shopify', status: 'live', note: 'Orders, products and stock import verified on a development store; per-location stock publishing; fulfilment and refunds built.' },
  { name: 'BigCommerce', status: 'live', note: 'Product import verified on a development store; orders, stock and shipments built.' },
  { name: 'NetSuite', status: 'tested', note: 'Stock import over SuiteQL with token-based authentication. Not yet run against a real account.' },
  { name: 'Shopify POS', status: 'tested', note: 'Works through Shopify\'s locations, which POS reads. POS transfer receiving is not built.' },
  { name: 'Any POS (stock counts)', status: 'tested', note: 'A secured endpoint any POS or middleware can push counts to; Nexus compares and corrects only on request.' },
  { name: 'EDI (850, 856, 810, 940)', status: 'tested', note: 'Parse and generate common documents, with acknowledgements.' },
  { name: 'SAP', status: 'next', note: 'Not built. Designed with the first customer who needs it.' },
  { name: 'Square, Lightspeed and other POS', status: 'next', note: 'No vendor adapter yet.' },
];

export interface Proof { what: string; result: string; status: Status; when: string }
export const PROOF: Proof[] = [
  { what: 'Shopify order import', result: '5,052 orders imported; order total 3,679,289.59 and 9,445 line items equal Shopify\'s to the cent', status: 'live', when: '15 Oct 2026' },
  { what: 'Shopify product sync', result: '4,935 products and 9,996 variants, equal to the shop\'s count', status: 'live', when: '15 Oct 2026' },
  { what: 'Shopify stock import', result: '6,923 stock rows from physical on-hand; every row matches its ledger', status: 'live', when: '18 Oct 2026' },
  { what: 'Shopify stock publish', result: 'One SKU written to a location and read back from Shopify; a repeat run wrote nothing', status: 'live', when: '15 Oct 2026' },
  { what: 'BigCommerce product import', result: '13 products and 68 variants, equal to the store\'s catalogue', status: 'live', when: '15 Oct 2026' },
  { what: 'Store rejection re-route', result: 'Whole order moved to another location; reservation released; rejected unit held', status: 'tested', when: 'real database' },
  { what: 'Scheduled jobs', result: 'Scheduler started a run on its own; 6,938-row export downloaded', status: 'tested', when: 'local install' },
  { what: 'NetSuite stock import', result: 'Signing checked against an independent implementation; never run on a real account', status: 'tested', when: 'not yet live' },
  { what: 'Webhooks, carrier labels, fulfilment push', result: 'Built with tests; not yet run against the real services', status: 'tested', when: 'not yet live' },
  { what: 'SAP connector, POS vendor adapters', result: 'Not built', status: 'next', when: '' },
];

export const FAQ: { q: string; a: string }[] = [
  { q: 'What is Nexus?', a: 'Order orchestration for retailers who sell on Shopify and fulfil from stores and warehouses: honest stock per location, routing that keeps orders together and avoids stores that fail picks, and a way to test rule changes on your own orders first.' },
  { q: 'Is it live with customers?', a: 'Not yet. Nexus is in a private pilot. Its Shopify and BigCommerce paths have been run against development stores (the results are on the "What we have verified" page), and we are taking on our first founding customers now.' },
  { q: 'Does it replace Shopify?', a: 'No. Shopify stays your storefront and point of sale. Nexus sits beside it, decides where orders ship from, and keeps each location\'s stock right in Shopify.' },
  { q: 'Can I see the effect before I change anything?', a: 'Yes. The routing what-if replays your past orders under different rules and shows shipments, splits, unfilled units and expected failed picks. Stock imports and publishing run as dry runs first.' },
  { q: 'How does it connect to my store?', a: 'Through a Shopify custom app that you install, with read scopes first; write scopes only when you decide to publish stock or push fulfilments. Credentials are encrypted at rest and never shown again.' },
  { q: 'What about NetSuite, SAP or my POS?', a: 'NetSuite stock import is built and waiting for a sandbox to verify. SAP and POS vendor adapters are not built yet; Shopify POS works through Shopify\'s own locations.' },
  { q: 'How is my data kept separate?', a: 'Every table is isolated per customer at the database level (row-level security), integration secrets are sealed with a key you hold, and access is role-based with multi-factor sign-in.' },
  { q: 'What does it cost?', a: 'See Pricing. Founding customers get a free development instance and two months free to test it on their own orders.' },
];

export const AUDIENCE: { who: string; gets: string }[] = [
  { who: 'Head of e-commerce', gets: 'Promises customers can trust, and fewer cancellations at peak.' },
  { who: 'Head of operations', gets: 'Fewer split parcels and store failures you can see, measure and fix.' },
  { who: 'Store operations', gets: 'Clear pick, pack and reject reasons, and orders that move on without a phone call.' },
  { who: 'IT and engineering', gets: 'Read-only first, dry runs everywhere, isolated data and jobs people can run themselves.' },
  { who: 'Finance', gets: 'Stock that reconciles to a ledger, and a cost model you can test before you change a rule.' },
];
