import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';

// The public address of the site. Set SITE_URL when you deploy so the sitemap, canonical links and social cards are right.
const site = process.env.SITE_URL || 'https://example.com';

export default defineConfig({
  // the blog used to live at /resources
  redirects: { '/resources': '/blog', '/resources/[slug]': '/blog/[slug]' },
  site,
  integrations: [sitemap()],
  build: { inlineStylesheets: 'auto' },
  vite: { build: { chunkSizeWarningLimit: 900 } },
});
