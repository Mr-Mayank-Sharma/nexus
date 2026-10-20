import type { APIRoute } from 'astro';
import { POSTS } from '../data/posts';
import { site as cfg } from '../site.config';

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** The blog as an RSS feed, newest first. */
export const GET: APIRoute = ({ site }) => {
  const base = site ?? new URL('https://example.com');
  const items = [...POSTS].sort((a, b) => b.date.localeCompare(a.date)).map(p => {
    const link = new URL(`/blog/${p.slug}`, base).toString();
    return `<item><title>${esc(p.title)}</title><link>${link}</link><guid>${link}</guid><pubDate>${new Date(p.date + 'T09:00:00Z').toUTCString()}</pubDate><category>${esc(p.tag)}</category><description>${esc(p.summary)}</description></item>`;
  }).join('');
  const xml = `<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>${esc(cfg.name)} blog</title><link>${new URL('/blog', base)}</link><description>${esc('Practical writing on order management.')}</description><language>en</language>${items}</channel></rss>`;
  return new Response(xml, { headers: { 'Content-Type': 'application/rss+xml; charset=utf-8' } });
};
