# Marketing website

Static site (Astro 5) for selling Nexus. Separate from the product: nothing here talks to the Nexus backend.

```bash
cd website
npm ci
npm run dev        # http://localhost:4321
npm test           # replay-engine parity tests (must match the Java engine's numbers)
npm run build      # type-check + static build into dist/
```

## What to change before launch

All in `src/site.config.ts` unless noted:

| What | Where |
|---|---|
| Product name (working name "Nexus") | `site.name`, `site.legalName`, then `node scripts/make-og.mjs` to redraw `public/og.png` |
| Contact email | `site.contactEmail` (currently a placeholder) |
| Domain | `SITE_URL` environment variable at build time (canonical links, sitemap, social image) |
| Contact form backend | `PUBLIC_FORM_ENDPOINT` environment variable (any endpoint that accepts a JSON POST, e.g. Formspree or a Cloudflare Pages Function). Without it the form opens the visitor's email app. |
| Founding offer wording | `site.offer` |
| Feature statuses, verified numbers, integrations | `src/data/content.ts` (every page reads from it) |
| Articles | `src/data/posts.ts` |
| Privacy and terms | `src/pages/privacy.astro`, `src/pages/terms.astro`: drafts, have them reviewed by a lawyer |


## Deploy (Cloudflare Pages, free tier)

1. Push the repo to GitHub (already done for this repo).
2. Cloudflare dashboard → Workers & Pages → Create → Pages → Connect to Git → pick the repo.
3. Build settings: root directory `website`, build command `npm run build`, output directory `dist`.
4. Environment variables: `SITE_URL=https://your-domain`, optionally `PUBLIC_FORM_ENDPOINT`, and `NODE_VERSION=20`.
5. Custom domains → add your domain; Cloudflare issues the certificate.

Netlify and Vercel work the same way (static output in `dist/`). For AWS: `aws s3 sync dist/ s3://bucket` behind CloudFront.

## How it is built

- `src/lib/network.ts`: the Three.js globe in the hero (loaded after the page so it never blocks reading).
- `src/lib/motion.ts`: smooth scroll (Lenis), word reveals, scrubbed statements, counters, scene switching (dark/light), magnetic buttons, cursor.
- `src/components/Journey.astro`: the pinned "journey of one order" story (sticky on phones).
- `src/lib/replay.ts`: a TypeScript port of the product's routing replay engine, used by the in-page what-if. `replay.test.ts` checks it gives the same numbers as the Java engine on the same sample.
- Respects `prefers-reduced-motion`, and the content reads without JavaScript.
