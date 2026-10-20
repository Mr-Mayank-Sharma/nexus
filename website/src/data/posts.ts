/**
 * Insights. Original writing; every outside figure names its source, and figures we could only read in an abstract
 * or a press story say so. Add a post by adding an entry; the list, the post page and the sitemap pick it up.
 */
export type Block = { h: string } | { p: string } | { q: string; by: string } | { list: string[] };
export interface Post {
  slug: string; title: string; summary: string; tag: string; minutes: number; date: string;
  body: Block[]; sources: { label: string; url: string }[];
  /** The product page this article leads to; shown as a box inside the article. */
  product?: { href: string; label: string; line: string };
}

export const TOPICS = ['Guide', 'Research', 'Operations', 'B2B', 'Integrations', 'Product', 'Industry'] as const;
export const AUTHOR = { name: 'The Nexus team', role: 'Order management, built with retailers' };

export const POSTS: Post[] = [
  {
    slug: 'what-is-an-order-management-system',
    title: 'What is an order management system, and when do you need one?',
    summary: 'A plain-language guide: what an OMS does that your storefront does not, the signs you have outgrown your current setup, and what to look for.',
    tag: 'Guide', minutes: 7, date: '2026-10-20',
    product: { href: '/product/order-routing', label: 'Nexus OMS', line: 'See how Nexus routes every order to the location that can really ship it.' },
    body: [
      { p: 'An order management system (OMS) sits between the places you sell and the places you ship from. Your storefront takes the order; the OMS decides what happens next: which warehouse or store ships it, whether the stock is really there, when work should start, and how everyone hears about it.' },
      { h: 'What an OMS does that a storefront does not' },
      { list: [
        'Takes orders from every channel (website, marketplaces, stores, business customers) into one queue.',
        'Keeps one honest stock number per location, not one total, so no channel sells what a location does not have.',
        'Chooses where each order ships from, by rules you can see and test.',
        'Holds orders that should not start yet, such as a wholesale order shipping in two months.',
        'Sends work to the warehouse or store, and status and tracking back to the channel.',
      ] },
      { h: 'Signs you have outgrown your current setup' },
      { list: [
        'You sell on more than one channel and stock numbers disagree between them.',
        'Stores fulfil online orders and some picks come back "not found".',
        'Orders split into several parcels when one location had everything.',
        'People copy orders between systems by hand, or export and import spreadsheets every day.',
        'B2B orders placed early lock up stock that this week\'s orders need.',
      ] },
      { h: 'What to look for' },
      { p: 'Ask to see the rules on your own data before you commit: a good OMS lets you replay past orders under a new rule and shows what would change. Ask how stock gets in (from your ERP, your POS, your storefront) and whether anything writes back to your channels before you switch it on. And ask what is verified against real systems today, not what is on the roadmap.' },
      { h: 'OMS and WMS are different jobs' },
      { p: 'A warehouse management system (WMS) runs the inside of one warehouse: receiving, putaway, slotting, picking paths, labour. An OMS decides which location gets each order in the first place. Many retailers run both; the OMS hands orders to whichever WMS each warehouse uses.' },
    ],
    sources: [],
  },
  {
    slug: 'b2b-orders-should-wait-for-their-ship-date',
    title: 'Why a B2B order placed today should not reserve stock today',
    summary: 'Wholesale orders often arrive weeks or months before they ship. Reserving their stock early quietly starves the orders that ship sooner.',
    tag: 'B2B', minutes: 4, date: '2026-10-20',
    product: { href: '/release-by-ship-date', label: 'Release by ship date', line: 'Nexus holds far-off orders and releases them a few days before they ship.' },
    body: [
      { p: 'In B2C, an order should ship today, so reserving its stock the moment it arrives is right. B2B works differently: a retailer places a wholesale order in November for delivery in January. If the system reserves those units in November, they sit locked for two months while direct orders for the same product are refused or delayed.' },
      { h: 'The collision' },
      { p: 'Several advance orders can claim the same units. The first ones to be reserved win, regardless of which ships first. The result is backorders on orders due this week, while the stock waits for an order due in January.' },
      { h: 'The fix: release by ship date' },
      { list: [
        'Record the requested ship date on every order that has one.',
        'Hold fulfilment until ship date minus a window (for example three days), so the order reserves nothing until then.',
        'At the release time, send the order to allocation like any other, and let it take the stock it needs.',
        'Let a person release early or change the date when the customer asks.',
      ] },
      { p: 'This is how Nexus handles it: a hold per order, a window per order type that each business sets, and a schedule that releases orders automatically when their time comes.' },
    ],
    sources: [],
  },
  {
    slug: 'connect-any-storefront-without-a-connector',
    title: 'Connecting a storefront that has no ready-made connector',
    summary: 'Most platforms expose orders over a JSON API. With a field mapping and a preview, connecting one is configuration, not a project.',
    tag: 'Integrations', minutes: 5, date: '2026-10-19',
    product: { href: '/integrations', label: 'Integrations', line: 'Generic connections import and export from any system with a JSON API.' },
    body: [
      { p: 'Ready-made connectors cover the biggest platforms. Everything else, a regional marketplace, a custom B2B portal, an ERP, usually has an API that returns orders as JSON. What differs is the shape: where the list of orders sits, what the fields are called, how pages work and how you sign in.' },
      { h: 'Four things describe almost any API' },
      { list: [
        'Where to call and how to sign in: an address, and a token, key or password.',
        'How pages work: a page number, an offset, a cursor in the response, or a link header.',
        'Where the records are in the response, and which field means what.',
        'How to ask only for what changed since last time.',
      ] },
      { h: 'Preview before anything is saved' },
      { p: 'The step that saves the most time is seeing the result before trusting it: the first page of real records, each mapped field next to the original, and the records that would fail with the reason. Fix the mapping, preview again, then schedule it.' },
      { h: 'Safe by default' },
      { p: 'Imports should never create the same order twice, a dry run should come before the first real run, and secrets should be stored encrypted and never shown again.' },
    ],
    sources: [],
  },
  {
    slug: 'safety-stock-per-location',
    title: 'Safety stock belongs to a location, not a product',
    summary: 'A buffer that protects a busy store is wasted at a quiet warehouse. Setting it per location is how you sell more without overselling.',
    tag: 'Operations', minutes: 4, date: '2026-10-18',
    product: { href: '/product/inventory', label: 'Stock you can trust', line: 'Nexus publishes sellable stock per location, after buffers.' },
    body: [
      { p: 'Safety stock is the number of units you hold back from sale to absorb errors: miscounts, items damaged on the shelf, or a sale at the till that has not reached the system yet. Most setups set one buffer per product. That is too much in some places and too little in others.' },
      { h: 'Why per location' },
      { list: [
        'A busy store with walk-in customers changes stock between system updates; it needs a bigger buffer.',
        'A warehouse with scanned picks and frequent counts can run a small one.',
        'A store that often reports "not found" on picks is telling you its records drift.',
      ] },
      { h: 'What to publish' },
      { p: 'Publish sellable stock per location: on hand, minus what is already allocated or reserved, minus held units, minus the buffer. Never publish a number you do not know: a product with no stock record at a location should be left alone, not set to zero.' },
    ],
    sources: [],
  },
  {
    slug: 'ship-from-store-checklist',
    title: 'Ship-from-store: a checklist before you switch it on',
    summary: 'Stores can turn into fast, close-to-the-customer fulfilment centres. These are the things to get right first.',
    tag: 'Guide', minutes: 5, date: '2026-10-17',
    product: { href: '/product/store-fulfilment', label: 'Store fulfilment', line: 'Picks, rejections and whole-order re-routes, built for stores.' },
    body: [
      { p: 'Ship-from-store sends online orders to stores that hold the stock, often closer to the customer than any warehouse. Done well, it sells stock that would otherwise sit and gets parcels out faster. Done badly, it produces cancelled orders and frustrated store teams.' },
      { h: 'Before the first order' },
      { list: [
        'Stock per store you trust, refreshed often, with a buffer for walk-in sales.',
        'A daily cut-off and a capacity per store, so a busy Saturday does not swamp the team.',
        'A pick list with clear item details and a way to say "not found" with a reason.',
        'Packing supplies and a label printer at each store.',
      ] },
      { h: 'When a pick fails' },
      { p: 'Decide in advance what happens: the whole order moves to another location that holds all of it, the store\'s pick work is cancelled, and the missing unit is held out of what can be sold. Track each store\'s miss rate and let routing use it.' },
      { h: 'Start small' },
      { p: 'Begin with a few stores and a few categories, replay last month\'s orders to see what would have shipped from where, and widen from there.' },
    ],
    sources: [],
  },
  {
    slug: 'store-pick-failures-are-predictable',
    title: 'Store pick failures are predictable. Route like it.',
    summary: 'A store that "has" an item often can\'t find it. Research on real retailer orders shows that ranking stores by how often they fail saves money.',
    tag: 'Research', minutes: 5, date: '2026-10-19',
    product: { href: '/product/store-fulfilment', label: 'Store fulfilment', line: 'Nexus learns each store\'s miss rate and routes around it.' },
    body: [
      { p: 'Ship-from-store looks simple on a map: the nearest store with stock ships the order. In practice the system\'s stock number and the shelf disagree often enough that a share of store picks come back as "not found". Every one of those costs a pick attempt, a delay and often a split order.' },
      { h: 'What the research found' },
      { p: 'A study by Das, Ravi and Sridhar (Manufacturing & Service Operations Management, 2023) modelled the probability that a store fails to pick an order and used it to choose the order in which stores are tried. Tested on order data from several large US retailers, the approach cut fulfilment cost by as much as 22 percent, according to Carnegie Mellon\'s summary of the work. Their recommendation was direct: put pick-failure probabilities into the order management system.' },
      { p: 'An older and larger finding explains why. DeHoratius and Raman studied about 370,000 inventory records at a 37-store retailer and found that 65 percent were inaccurate. The errors varied much more by product category than by store, which means some items are reliably harder to trust than others.' },
      { h: 'What that means for routing' },
      { list: [
        'Measure each store\'s failure rate from its own rejections, over a window long enough to mean something.',
        'Smooth the rate so a store with three orders is not judged on luck.',
        'Count the expected cost of a failed pick against a store when choosing where an order ships.',
        'When a pick does fail, move the whole order to a location that holds all of it, rather than splitting it.',
      ] },
      { p: 'This is how routing works in Nexus today: a 90-day rejection rate per store, smoothed towards a 5 percent prior, applied as a penalty when choosing a location. You can see its effect on invented orders in the routing what-if.' },
    ],
    sources: [
      { label: 'Carnegie Mellon Tepper: research on store pickup and ship-from-store failures (2023)', url: 'https://www.cmu.edu/tepper/news/stories/2023/january/e-commerce-store-pickup-research.html' },
      { label: 'Das, Ravi, Sridhar. M&SOM, DOI 10.1287/msom.2022.1164', url: 'https://doi.org/10.1287/msom.2022.1164' },
      { label: 'DeHoratius, Raman. Inventory record inaccuracy. Management Science 54(4), 2008', url: 'https://ideas.repec.org/a/inm/ormnsc/v54y2008i4p627-641.html' },
    ],
  },
  {
    slug: 'per-location-stock-stops-overselling',
    title: 'Why total stock oversells, and per-location stock doesn\'t',
    summary: 'A busy store can sell out while the total still says "in stock". The fix is a number per location, kept as a ledger.',
    tag: 'Operations', minutes: 4, date: '2026-10-16',
    product: { href: '/use-cases/stop-overselling', label: 'Stop overselling', line: 'Sell what each location really has.' },
    body: [
      { p: 'Retailers on Shopify with several locations keep running into the same complaint in community threads: orders accepted for items the assigned location no longer had. The total across locations was positive, so the sale went through.' },
      { h: 'Three causes, usually together' },
      { list: [
        'Availability judged on the total, not on the location that will ship.',
        'Stock fed in on a schedule, so it is stale between updates.',
        'Units a store cannot find still counted as sellable.',
      ] },
      { h: 'What fixes it' },
      { p: 'Keep every location\'s stock as a ledger of movements, so the number has a history you can check. Publish what can be sold per location, after any safety buffer. Never publish a number you do not know: a SKU with no stock record should be left alone, not set to zero. And when a store says it cannot find a unit, take that unit out of availability straight away.' },
      { p: 'Nexus does each of these. On a Shopify development store it brought in 6,923 stock rows from physical on-hand with every row matching its ledger, and its first stock write was read back from Shopify exactly.' },
    ],
    sources: [
      { label: 'Shopify Community: products overselling with multi-location orders', url: 'https://community.shopify.com/t/products-overselling-multi-location-orders/227730' },
    ],
  },
  {
    slug: 'test-routing-rules-before-they-go-live',
    title: 'Test a routing rule on last quarter\'s orders before you switch it on',
    summary: 'A rule change is a bet on thousands of orders. Replaying your history under the new rule tells you the shipments, splits and failed picks first.',
    tag: 'Product', minutes: 4, date: '2026-10-15',
    product: { href: '/what-if', label: 'Routing what-if', line: 'Replay your own orders under a new rule.' },
    body: [
      { p: 'Most order systems let you edit routing rules and then watch what happens in production. That is a slow and expensive way to learn that a new rule doubles split shipments in one region.' },
      { h: 'Replay instead' },
      { p: 'Take a set of past orders and the stock each location held. Run them through two or three rules in turn, each with its own copy of the stock, and compare the results: how many shipments, how many orders split, how many units could not be filled, and how many picks you would expect to fail given each store\'s record.' },
      { p: 'On the invented sample in Nexus (220 orders, one distribution centre, three stores), keeping orders together cut shipments from 265 to 220 and split orders from 45 to none. Adding each store\'s failure rate lowered expected failed picks from 20.3 to 13.2 without adding a single shipment.' },
      { h: 'What a replay does not tell you' },
      { p: 'It is a comparison of rules on the same orders, not a forecast. It does not model replenishment during the period, cancellations or carrier delays. Those limits are why we show the numbers next to each other rather than as a promise.' },
    ],
    sources: [],
  },
  {
    slug: 'inventory-trust-before-ai-agents',
    title: 'Agents need stock they can trust first',
    summary: 'The big retail conferences of 2026 talked about AI agents. The quieter message was that most retailers\' stock data is not ready for them.',
    tag: 'Industry', minutes: 3, date: '2026-10-14',
    body: [
      { p: 'Agentic commerce dominated the stages at NRF and Shoptalk in 2026. Under the demos, recaps from the floor kept returning to one dependency: an agent that promises delivery is only as good as the stock and order data behind it.' },
      { p: 'Gartner has forecast that more than 40 percent of agentic AI projects will be cancelled by the end of 2027. Whatever the exact figure, the pattern is familiar: automation built on numbers nobody trusts.' },
      { h: 'Where to start' },
      { list: [
        'One honest stock number per location, with a ledger behind it.',
        'Routing whose decisions you can explain and replay.',
        'Jobs that show every failed row and why, so people fix data instead of filing tickets.',
      ] },
      { p: 'Those are not glamorous. They are what make anything clever on top of them safe.' },
    ],
    sources: [
      { label: 'Omni Talk: lessons from NRF 2026', url: 'https://omnitalk.blog/2026/01/23/the-death-of-optimization-first-retail-four-lessons-from-nrf-2026/' },
      { label: 'Sitoo: takeaways from NRF 2026', url: 'https://sitoo.com/resources/top-trends-and-takeaways-from-nrf-2026' },
      { label: 'Gartner press release, 7 April 2026', url: 'https://www.gartner.com/en/newsroom/press-releases/2026-04-07-gartner-forecasts-supply-chain-management-software-with-agentic-ai-will-grow-to-53-billion-in-spend-by-2030' },
    ],
  },
  {
    slug: 'why-every-failed-row-matters',
    title: '"Some rows failed" is not an answer',
    summary: 'When a nightly import half-works, operations teams need the rows and the reasons, not a ticket to engineering.',
    tag: 'Product', minutes: 3, date: '2026-10-13',
    product: { href: '/product/scheduled-jobs', label: 'Scheduled jobs', line: 'Every run keeps its failed rows and the reason.' },
    body: [
      { p: 'Imports, exports and syncs are where data quietly goes wrong: a SKU with no match, a location that was never mapped, a price in the wrong format. The usual report is a count and a line in a log only an engineer can read.' },
      { h: 'What a run should leave behind' },
      { list: [
        'Counts: rows read, succeeded, failed, needing attention.',
        'Each failed row with its reason, as a file you can open in a spreadsheet.',
        'The output the run produced, and a readable log.',
        'A schedule in plain words and your own time zone.',
      ] },
      { p: 'Scheduled jobs in Nexus keep all of that for every run. New jobs start switched off, a job never runs twice at once, and a run left hanging by a restart is closed out rather than blocking the next one.' },
    ],
    sources: [],
  },
];
