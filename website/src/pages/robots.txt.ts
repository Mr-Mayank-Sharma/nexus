import type { APIRoute } from 'astro';

// Generated so the sitemap line always carries the real domain (SITE_URL at build time).
export const GET: APIRoute = ({ site }) =>
  new Response(`User-agent: *\nAllow: /\nSitemap: ${new URL('/sitemap-index.xml', site)}\n`, { headers: { 'Content-Type': 'text/plain' } });
