/**
 * The site's facts in one place. Every capability carries an honest status:
 *  - live:   run against a real outside system (a Shopify or BigCommerce development store) and the result checked
 *  - tested: covered by automated tests, and for some, run end to end on a real database; not yet run against the
 *            real outside system it talks to
 *  - early:  a connector exists in the code but is thin and has never met the real system; built out with the first customer
 *  - next:   not built yet
 * If a status changes, change it here; every page reads from this file.
 */
export type Status = 'live' | 'tested' | 'early' | 'next';
export const STATUS_LABEL: Record<Status, string> = { live: 'Verified live', tested: 'Built and tested', early: 'Early connector', next: 'Not built yet' };
export const STATUS_CHIP: Record<Status, string> = { live: 'chip chip--go', tested: 'chip chip--warn', early: 'chip chip--dim', next: 'chip chip--dim' };

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
      { title: 'Routing groups you build on screen', body: 'Which orders, which locations, how to sort them (distance, group order, sales velocity, weeks of supply, broken size runs, store reliability) and what to do when nothing fits: try the next rule, park the order, or cancel it after some days.', status: 'tested' },
      { title: 'Simulate before you switch', body: 'Run a rule set on today\'s real orders and stock and see what it would do, order by order with the reason, without changing anything.', status: 'tested' },
      { title: 'Daily limits and substitutes', body: 'A store takes only as many orders a day as it can pick; a limit of zero stops new orders without hiding its stock online. When a product is out everywhere, an approved substitute can ship instead.', status: 'tested' },
      { title: 'Move orders by hand or in bulk', body: 'Move one order to another location, or every open order off a store that closes for a day; each move is kept with who did it and why.', status: 'tested' },
    ],
    outcome: 'On the replay sample (invented data, 220 orders), keeping orders together cut shipments from 265 to 220 and split orders from 45 to none.',
    related: ['what-if', 'store-fulfilment', 'fewer-split-shipments'],
  },
  {
    slug: 'inventory', eyebrow: 'Stock you can trust',
    title: 'One honest number per location',
    lead: 'Every change to stock is a ledger line: receipts, picks, transfers, counts, returns, write-offs. What you can sell is computed from that, per location, and published to your channels location by location (Shopify today, with more to follow).',
    problem: 'Most storefronts and marketplaces check oversell against total stock, not each location\'s, and stock fed in once a day goes stale between updates. Selling what a location does not have is how most cancellations start.',
    capabilities: [
      { title: 'A ledger behind every number', body: 'Stock rows change only through recorded movements; a check reconciles every row against its ledger.', status: 'tested' },
      { title: 'Bring in your current stock', body: 'Reads physical on-hand per location from Shopify or NetSuite and creates opening balances. Dry run first; never pushes numbers back out.', status: 'live' },
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
  {
    slug: 'store-network', eyebrow: 'Store network',
    title: 'Stores that help each other, without phone calls',
    lead: 'Stores never talk to each other directly: every lookup, hold and transfer goes through Nexus, which keeps one stock record per store. Stock on its way between stores always has an owner, and a short delivery is settled, not lost.',
    problem: 'Transfers asked for by phone, stock sold twice while it waits to leave, boxes that arrive short and take days to sort out, and store managers who ignore requests that earn their store nothing.',
    capabilities: [
      { title: 'Who has it, and how sure we are', body: 'Stock at every store with a confidence level and the reason: how many are left, when it was last counted, whether the store recently failed to find it.', status: 'tested' },
      { title: 'Transfers that hold the stock at once', body: 'Approving a transfer holds the stock at the sending store; shipping moves it into transit, where it is counted but never sold; receiving puts what arrived on sale.', status: 'tested' },
      { title: 'Short deliveries settled', body: 'A small shortfall settles itself as a transit loss; a bigger one waits for a manager to say what was found.', status: 'tested' },
      { title: 'Hold at another store', body: 'Hold an item at the store the customer will visit, so they travel only when it is waiting.', status: 'tested' },
      { title: 'Credit for the store that sold it', body: 'When one store sells and another ships, the selling store keeps its share of the sale.', status: 'tested' },
      { title: 'Rebalance before markdown', body: 'Suggestions to move slow stock to stores in the same cluster where it sells, only where the margin beats the cost of moving it.', status: 'tested' },
    ],
    outcome: 'On a development database, a transfer ran end to end in the browser: approved (the sending store could no longer sell the 3 units), shipped (3 in transit), received 2, and the missing one written off, with stock right at every step.',
    related: ['transfers-and-counts', 'store-fulfilment', 'inventory'],
  },
  {
    slug: 'shipping-and-messages', eyebrow: 'Shipping and messages',
    title: 'Labels and customer emails through one gateway',
    lead: 'The Nexus Gateway is a separate service every Nexus installation calls for rates, labels, voids, return labels, address checks, tracking and customer emails. Carrier and email accounts live in the gateway, per store, never in your order system.',
    problem: 'Per-label fees from shipping aggregators, carrier passwords copied into every system, and labels that fail at the packing bench with no record of why.',
    capabilities: [
      { title: 'One API for every carrier', body: 'Rates from every carrier you have an account with, cheapest first; labels kept so they can be printed again; voids, return labels, address checks and tracking.', status: 'tested' },
      { title: 'Your own carrier accounts, per store', body: 'Each store can use its own FedEx or UPS account. Keys are encrypted in the gateway and never shown again.', status: 'tested' },
      { title: 'Customer emails per store', body: 'Pickup ready, cancelled, shipped: templates per store, sent through your email provider.', status: 'tested' },
      { title: 'Every call recorded', body: 'Each request with its result and how long it took, for health and for audit.', status: 'tested' },
    ],
    outcome: 'From a development install, an order was labelled through the gateway (tracking stored on the box and the order), shipped and completed, and a test email was rendered into the gateway test outbox. FedEx and UPS are checked against a server that answers like them; their sandboxes come next.',
    related: ['store-fulfilment', 'integrations'],
  },
  {
    slug: 'ai', eyebrow: 'AI that proposes, people decide',
    title: 'AI that works on your live orders, and asks first',
    lead: 'Nexus uses Claude for language and judgement and plain statistics for numbers. Every AI answer is built from live data; every change an AI wants becomes a proposal that a person or your policy approves, and every one can be undone.',
    problem: 'AI that guesses numbers, changes things nobody can trace, and sends customer details to a model it doesn\'t need to.',
    capabilities: [
      { title: 'Ask about your operation', body: 'Where is this order, which orders are stuck, who can sell this, which stores keep failing picks: answered from live data, read-only, with an audit of every question.', status: 'tested' },
      { title: 'Exceptions found and fixed', body: 'An agent finds stuck orders, works out why, and proposes a move, a hold or a cancel; you approve, or set a policy to apply small ones automatically.', status: 'tested' },
      { title: 'Routing changes in plain words', body: 'Describe the change; the agent drafts the rules, simulates them next to today\'s rules on real orders, and proposes the switch. Undo puts the old rules back.', status: 'tested' },
      { title: 'Delivery dates you can keep', body: 'Ship date from each store\'s cut-off and capacity; delivery time from its own past deliveries, promising the slower end so you are rarely late.', status: 'tested' },
      { title: 'Phantom stock and store reliability', body: 'Stock most likely not on the shelf, ranked so a count goes where it pays, and each store\'s pick reliability feeding routing. Scores show their reasons.', status: 'tested' },
      { title: 'Orders from AI shopping assistants', body: 'Checkout for AI shopping agents, with real stock and dates; their changes after purchase stay inside your policy.', status: 'tested' },
    ],
    outcome: 'Built and tested with a scripted model on every build: the right data found, customer names and emails masked before anything leaves, every answer grounded and audited. The run against the live model comes with the first customer\'s key.',
    related: ['order-routing', 'store-network'],
  },
];

export interface UseCase { slug: string; title: string; eyebrow: string; lead: string; how: string[]; product: string }
export const USE_CASES: UseCase[] = [
  { slug: 'stop-overselling', eyebrow: 'Use case', title: 'Stop overselling at your busiest location', product: 'inventory',
    lead: 'Sell what each location really has, not what the total says.',
    how: ['Bring in each location\'s physical on-hand from your store platform or ERP, then keep it as a ledger.', 'Publish what can be sold per location, after buffers and caps, and only for stock Nexus knows about.', 'Hold units a store cannot find out of availability at once.'] },
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

export type IntegrationKind = 'Storefront' | 'Marketplace' | 'ERP' | 'Point of sale' | 'B2B and EDI' | 'Carrier' | 'Open';
export interface Integration { name: string; kind: IntegrationKind; status: Status; note: string }
export const INTEGRATIONS: Integration[] = [
  { name: 'Shopify', kind: 'Storefront', status: 'live', note: 'Orders, products and stock import verified on a development store; per-location stock publishing; fulfilment and refunds built.' },
  { name: 'BigCommerce', kind: 'Storefront', status: 'live', note: 'Product import verified on a development store; orders, stock and shipments built.' },
  { name: 'Adobe Commerce (Magento)', kind: 'Storefront', status: 'early', note: 'Connector started in the code; not yet run against a real store.' },
  { name: 'Amazon', kind: 'Marketplace', status: 'early', note: 'Order channel modelled and connector started; not yet run against Seller Central.' },
  { name: 'Walmart Marketplace', kind: 'Marketplace', status: 'early', note: 'Channel modelled; connector is a generic shell today.' },
  { name: 'eBay', kind: 'Marketplace', status: 'early', note: 'Channel modelled; connector is a generic shell today.' },
  { name: 'NetSuite', kind: 'ERP', status: 'tested', note: 'Stock import over SuiteQL with token-based authentication. Not yet run against a real account.' },
  { name: 'SAP', kind: 'ERP', status: 'early', note: 'Connector started in the code; designed with the first customer who needs it.' },
  { name: 'Shopify POS', kind: 'Point of sale', status: 'tested', note: 'Works through Shopify\'s locations, which POS reads. POS transfer receiving is not built.' },
  { name: 'Any POS (stock counts)', kind: 'Point of sale', status: 'tested', note: 'A secured endpoint any POS or middleware can push counts to; Nexus compares and corrects only on request.' },
  { name: 'Square, Lightspeed and other POS', kind: 'Point of sale', status: 'next', note: 'No vendor adapter yet.' },
  { name: 'EDI (850, 856, 810, 940)', kind: 'B2B and EDI', status: 'tested', note: 'Parse and generate common documents, with acknowledgements.' },
  { name: 'FedEx', kind: 'Carrier', status: 'tested', note: 'Rates, labels, return labels, voids, address checks and tracking through the Nexus Gateway; checked against a server that answers like FedEx, not yet against FedEx\'s sandbox.' },
  { name: 'UPS', kind: 'Carrier', status: 'tested', note: 'The same through the Nexus Gateway; not yet run against the UPS test environment.' },
  { name: 'Email (SMTP: SES, SendGrid, Postmark)', kind: 'Carrier', status: 'tested', note: 'Customer emails per store through the Nexus Gateway; rendered into a test outbox, not yet sent through a real provider.' },
  { name: 'AI shopping agents', kind: 'Open', status: 'tested', note: 'Checkout for AI shopping assistants in the shape of the Agentic Commerce Protocol, with real stock and delivery dates; not yet run with an agent platform.' },
  { name: 'Any JSON API (generic connections)', kind: 'Open', status: 'tested', note: 'Import orders or stock, and export orders, shipments or stock, to any system with a JSON API: configured, not coded. Templates for WooCommerce, Magento 2 and Squarespace (not yet run against real stores).' },
  { name: 'REST API and webhooks', kind: 'Open', status: 'tested', note: 'Every screen in Nexus runs on the same documented API, so anything can integrate.' },
  { name: 'CSV and scheduled files', kind: 'Open', status: 'tested', note: 'Scheduled imports and exports with every failed row kept, for systems without an API.' },
];

export interface Proof { what: string; result: string; status: Status; when: string }
export const PROOF: Proof[] = [
  { what: 'Shopify order import', result: '5,052 orders imported; order total 3,679,289.59 and 9,445 line items equal Shopify\'s to the cent', status: 'live', when: '15 Oct 2026' },
  { what: 'Shopify product sync', result: '4,935 products and 9,996 variants, equal to the shop\'s count', status: 'live', when: '15 Oct 2026' },
  { what: 'Shopify stock import', result: '6,923 stock rows from physical on-hand; every row matches its ledger', status: 'live', when: '18 Oct 2026' },
  { what: 'Shopify stock publish', result: 'One SKU written to a location and read back from Shopify; a repeat run wrote nothing', status: 'live', when: '15 Oct 2026' },
  { what: 'BigCommerce product import', result: '13 products and 68 variants, equal to the store\'s catalogue', status: 'live', when: '15 Oct 2026' },
  { what: 'New order model, copied from the old', result: '5,224 orders copied; grand totals equal the old model\'s to the cent (3,702,542.02)', status: 'tested', when: 'development database' },
  { what: 'Transfer between stores', result: 'Approved, shipped, received 2 of 3 and the missing one written off, in the browser, with stock right at every step', status: 'tested', when: 'development database' },
  { what: 'Label through the Nexus Gateway', result: 'Order labelled, shipped and completed; tracking on the box and the order (test carrier)', status: 'tested', when: 'local install' },
  { what: 'Store rejection re-route', result: 'Whole order moved to another location; reservation released; rejected unit held', status: 'tested', when: 'real database' },
  { what: 'Scheduled jobs', result: 'Scheduler started a run on its own; 6,938-row export downloaded', status: 'tested', when: 'local install' },
  { what: 'NetSuite stock import', result: 'Signing checked against an independent implementation; never run on a real account', status: 'tested', when: 'not yet live' },
  { what: 'Webhooks, carrier labels, fulfilment push', result: 'Built with tests; not yet run against the real services', status: 'tested', when: 'not yet live' },
  { what: 'Amazon, Walmart, eBay, Magento, SAP', result: 'Early connectors in the code; never run against the real systems', status: 'early', when: '' },
  { what: 'POS vendor adapters (Square, Lightspeed)', result: 'Not built', status: 'next', when: '' },
];

export const FAQ: { q: string; a: string }[] = [
  { q: 'What is Nexus?', a: 'Order orchestration for omnichannel retailers: orders from every storefront, marketplace, store and B2B partner in one place, honest stock per location, routing that keeps orders together and avoids stores that fail picks, and a way to test rule changes on your own orders first.' },
  { q: 'Is it only for Shopify?', a: 'No. Shopify and BigCommerce are the connections we have verified so far, NetSuite and EDI are built and tested, and Amazon, Walmart, eBay, Magento and SAP connectors are early. Anything else can use the API or scheduled files. We build out connectors with the first customer who needs them.' },
  { q: 'Is it live with customers?', a: 'Not yet. Nexus is in a private pilot. Its Shopify and BigCommerce paths have been run against development stores (the results are on the "What we have verified" page), and we are taking on our first founding customers now.' },
  { q: 'Does it replace my storefront or ERP?', a: 'No. Your storefronts, marketplaces, POS and ERP stay where they are. Nexus sits between them, decides where each order ships from, and keeps each location\'s stock right in every channel.' },
  { q: 'Can I see the effect before I change anything?', a: 'Yes. The routing what-if replays your past orders under different rules and shows shipments, splits, unfilled units and expected failed picks. Stock imports and publishing run as dry runs first.' },
  { q: 'How does it connect?', a: 'With each platform\'s own API and credentials you create (for Shopify, a custom app), read-only first; write access only when you decide to publish stock or push fulfilments. Credentials are encrypted at rest and never shown again.' },
  { q: 'What about NetSuite, SAP or my POS?', a: 'NetSuite stock import is built and waiting for a sandbox to verify. The SAP connector is early, and POS vendor adapters are not built yet; Shopify POS works through Shopify\'s own locations, and any POS can push stock counts to Nexus.' },
  { q: 'How is my data kept separate?', a: 'Each customer runs on its own database, so no other company\'s data is ever in it. Integration secrets are sealed with a key you hold, carrier and email accounts live in the Nexus Gateway rather than in the order system, and access is role-based with multi-factor sign-in.' },
  { q: 'What does it cost?', a: 'See Pricing. Founding customers get a free development instance and two months free to test it on their own orders.' },
];

export const AUDIENCE: { who: string; gets: string }[] = [
  { who: 'Head of e-commerce', gets: 'Promises customers can trust, and fewer cancellations at peak.' },
  { who: 'Head of operations', gets: 'Fewer split parcels and store failures you can see, measure and fix.' },
  { who: 'Store operations', gets: 'Clear pick, pack and reject reasons, and orders that move on without a phone call.' },
  { who: 'IT and engineering', gets: 'Read-only first, dry runs everywhere, isolated data and jobs people can run themselves.' },
  { who: 'Finance', gets: 'Stock that reconciles to a ledger, and a cost model you can test before you change a rule.' },
];
