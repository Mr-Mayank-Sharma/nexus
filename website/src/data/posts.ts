/**
 * Insights. Original writing; every outside figure names its source, and figures we could only read in an abstract
 * or a press story say so. Add a post by adding an entry; the list, the post page and the sitemap pick it up.
 */
export type Block = { h: string } | { p: string } | { q: string; by: string } | { list: string[] };
export interface Post {
  slug: string; title: string; summary: string; tag: string; minutes: number; date: string;
  body: Block[]; sources: { label: string; url: string }[];
}

export const POSTS: Post[] = [
  {
    slug: 'store-pick-failures-are-predictable',
    title: 'Store pick failures are predictable. Route like it.',
    summary: 'A store that "has" an item often can\'t find it. Research on real retailer orders shows that ranking stores by how often they fail saves money.',
    tag: 'Research', minutes: 5, date: '2026-10-19',
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
    tag: 'Operations', minutes: 4, date: '2026-10-18',
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
    tag: 'Product', minutes: 4, date: '2026-10-17',
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
    tag: 'Industry', minutes: 3, date: '2026-10-15',
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
    tag: 'Product', minutes: 3, date: '2026-10-14',
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
