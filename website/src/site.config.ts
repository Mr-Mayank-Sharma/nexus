/**
 * Everything that changes when the name, domain or offer changes lives here. Nothing else in the site hard-codes it.
 * TODO before launch: set `contactEmail` to a mailbox that exists, and set SITE_URL when deploying.
 */
export const site = {
  name: 'Nexus',                       // working name; change here when the final name is chosen
  legalName: 'Nexus (working name)',   // replace with the registered business name
  tagline: 'Order orchestration for omnichannel retailers',
  description:
    'Every channel, every location, one brain. Nexus takes orders from your storefronts, marketplaces, stores and B2B partners, keeps each location\'s stock honest, and routes every order to the place that can really ship it.',
  contactEmail: 'hello@example.com',   // TODO: a real mailbox
  formEndpoint: import.meta.env.PUBLIC_FORM_ENDPOINT as string | undefined, // optional form backend (Formspree, Netlify Forms, a Cloudflare Pages Function)
  address: '',                         // required in commercial email footers in some countries; fill in when you have it
  social: { linkedin: '', x: '', github: '' } as Record<string, string>,
  offer: {
    title: 'Founding customer program',
    summary: 'The first ten retailers get a hands-on pilot at a founding price.',
    items: [
      'A free development instance connected to a test copy of your store, set up with you',
      'Two months free on the development instance to test it on your own orders',
      'Production and user-acceptance environments set up for a one-time fee that depends on your size (about $1,000 for a typical retailer)',
      'Then a simple monthly subscription, quoted up front',
    ],
    fine: 'Indicative terms for the founding program; final terms are agreed in writing.',
  },
  // Dates for numbers shown on the site. Update them whenever the numbers are re-measured.
  verified: { date: '18 Oct 2026', place: 'Shopify and BigCommerce development stores' },
} as const;

export type NavLink = { label: string; href: string; blurb?: string };
export type NavGroup = { label: string; links: NavLink[] };

export const nav: NavGroup[] = [
  {
    label: 'Product',
    links: [
      { label: 'Order routing', href: '/product/order-routing', blurb: 'Rules that keep an order together' },
      { label: 'Stock you can trust', href: '/product/inventory', blurb: 'A ledger, per location' },
      { label: 'Store fulfilment', href: '/product/store-fulfilment', blurb: 'Picks, rejections, re-routes' },
      { label: 'Release by ship date', href: '/release-by-ship-date', blurb: 'B2B orders wait for their date' },
      { label: 'Routing what-if', href: '/what-if', blurb: 'Replay your orders under new rules' },
      { label: 'Scheduled jobs', href: '/product/scheduled-jobs', blurb: 'Imports, exports, every failed row' },
      { label: 'Store network', href: '/product/store-network', blurb: 'Lookups, holds, transfers, credit' },
      { label: 'Shipping and messages', href: '/product/shipping-and-messages', blurb: 'Labels and emails through one gateway' },
      { label: 'AI', href: '/product/ai', blurb: 'Proposes; people decide' },
      { label: 'Integrations', href: '/integrations', blurb: 'Storefronts, marketplaces, ERP, POS, EDI' },
      { label: 'Nexus WMS (2027)', href: '/wms', blurb: 'Why OMS and WMS are separate' },
    ],
  },
  {
    label: 'Use cases',
    links: [
      { label: 'Stop overselling', href: '/use-cases/stop-overselling' },
      { label: 'Fewer split shipments', href: '/use-cases/fewer-split-shipments' },
      { label: 'Store pick failures', href: '/use-cases/store-pick-failures' },
      { label: 'Ship from store', href: '/use-cases/ship-from-store' },
      { label: 'Buy online, pick up in store', href: '/use-cases/bopis' },
      { label: 'Transfers and counts', href: '/use-cases/transfers-and-counts' },
    ],
  },
  {
    label: 'Resources',
    links: [
      { label: 'Blog', href: '/blog', blurb: 'Practical writing on order management' },
      { label: 'What is an OMS?', href: '/blog/what-is-an-order-management-system', blurb: 'A plain-language guide' },
      { label: 'Routing what-if', href: '/what-if' },
      { label: 'What we have verified', href: '/proof' },
      { label: 'Security', href: '/security' },
    ],
  },
  { label: 'Pricing', links: [{ label: 'Pricing and the founding program', href: '/pricing' }] },
  { label: 'Company', links: [{ label: 'About', href: '/about' }, { label: 'Contact', href: '/contact' }] },
];

export const footerGroups: NavGroup[] = [
  { label: 'Product', links: nav[0].links },
  { label: 'Use cases', links: nav[1].links },
  { label: 'Company', links: [
    { label: 'About', href: '/about' }, { label: 'What we have verified', href: '/proof' },
    { label: 'Blog', href: '/blog' }, { label: 'Pricing', href: '/pricing' }, { label: 'Security', href: '/security' },
    { label: 'Contact', href: '/contact' } ] },
  { label: 'Legal', links: [{ label: 'Privacy', href: '/privacy' }, { label: 'Terms', href: '/terms' }] },
];
