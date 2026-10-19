import { PRODUCT, USE_CASES } from '../data/content';

/** Turns a slug used in content.ts `related` lists into a link. */
export function relatedLink(slug: string): { href: string; label: string } {
  const p = PRODUCT.find(x => x.slug === slug);
  if (p) return { href: `/product/${p.slug}`, label: p.eyebrow };
  const u = USE_CASES.find(x => x.slug === slug);
  if (u) return { href: `/use-cases/${u.slug}`, label: u.title };
  if (slug === 'what-if') return { href: '/what-if', label: 'Routing what-if' };
  if (slug === 'integrations') return { href: '/integrations', label: 'Integrations' };
  throw new Error(`Unknown related slug: ${slug}`);
}
