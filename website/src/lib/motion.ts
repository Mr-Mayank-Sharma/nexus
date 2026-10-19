import Lenis from 'lenis';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

gsap.registerPlugin(ScrollTrigger);

export const reduced = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
export const fine = () => window.matchMedia('(hover: hover) and (pointer: fine)').matches;

let lenis: Lenis | null = null;
export const getLenis = () => lenis;

/** Smooth scrolling, wired into GSAP's ticker so scroll-linked animation and the scroll itself share one clock. */
function smoothScroll() {
  if (reduced()) return;
  lenis = new Lenis({ duration: 1.1, easing: t => Math.min(1, 1.001 - Math.pow(2, -10 * t)), smoothWheel: true });
  lenis.on('scroll', ScrollTrigger.update);
  gsap.ticker.add(time => lenis!.raf(time * 1000));
  gsap.ticker.lagSmoothing(0);
}

/** Splits the text of [data-split] into words that rise into view. */
function splitHeadlines() {
  document.querySelectorAll<HTMLElement>('[data-split]').forEach(el => {
    const text = el.textContent ?? '';
    el.setAttribute('aria-label', text.trim());
    el.innerHTML = text.trim().split(/\s+/).map(w => `<span class="word" aria-hidden="true"><span>${w}</span></span>`).join(' ');
    const inner = el.querySelectorAll<HTMLElement>('.word > span');
    if (reduced()) return;
    gsap.set(inner, { yPercent: 115 });
    ScrollTrigger.create({
      trigger: el, start: 'top 88%', once: true,
      onEnter: () => gsap.to(inner, { yPercent: 0, duration: 1.1, ease: 'power4.out', stagger: 0.07 }),
    });
  });
}

/** [data-statement]: words darken one by one as you scroll through the block. */
function statements() {
  document.querySelectorAll<HTMLElement>('[data-statement]').forEach(el => {
    const text = el.textContent ?? '';
    el.setAttribute('aria-label', text.trim());
    el.innerHTML = text.trim().split(/\s+/).map(w => `<span class="w" aria-hidden="true">${w}</span>`).join(' ');
    if (reduced()) { el.querySelectorAll<HTMLElement>('.w').forEach(w => (w.style.opacity = '1')); return; }
    const words = el.querySelectorAll<HTMLElement>('.w');
    gsap.fromTo(words, { opacity: 0.18 }, {
      opacity: 1, ease: 'none', stagger: 0.12,
      scrollTrigger: { trigger: el, start: 'top 78%', end: 'bottom 38%', scrub: 0.6 },
    });
  });
}

function reveals() {
  const items = gsap.utils.toArray<HTMLElement>('[data-reveal]');
  if (reduced()) { items.forEach(i => i.classList.add('is-in')); return; }
  ScrollTrigger.batch(items, {
    start: 'top 90%', once: true,
    onEnter: batch => batch.forEach((el, i) => {
      const delay = parseFloat((el as HTMLElement).dataset.delay ?? '0') + i * 0.08;
      gsap.delayedCall(delay, () => (el as HTMLElement).classList.add('is-in'));
    }),
  });
}

function counters() {
  document.querySelectorAll<HTMLElement>('[data-count]').forEach(el => {
    const target = parseFloat(el.dataset.count ?? '0');
    const decimals = parseInt(el.dataset.decimals ?? '0', 10);
    const prefix = el.dataset.prefix ?? '';
    const suffix = el.dataset.suffix ?? '';
    const fmt = (n: number) => prefix + n.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals }) + suffix;
    if (reduced()) { el.textContent = fmt(target); return; }
    el.textContent = fmt(0);
    const state = { v: 0 };
    ScrollTrigger.create({
      trigger: el, start: 'top 90%', once: true,
      onEnter: () => gsap.to(state, { v: target, duration: 1.8, ease: 'power3.out', onUpdate: () => (el.textContent = fmt(state.v)) }),
    });
  });
}

/** Buttons lean towards the pointer. */
function magnetic() {
  if (reduced() || !fine()) return;
  document.querySelectorAll<HTMLElement>('[data-magnetic]').forEach(el => {
    el.addEventListener('pointermove', e => {
      const r = el.getBoundingClientRect();
      gsap.to(el, { x: (e.clientX - r.left - r.width / 2) * 0.22, y: (e.clientY - r.top - r.height / 2) * 0.3, duration: 0.5, ease: 'power3.out' });
    });
    el.addEventListener('pointerleave', () => gsap.to(el, { x: 0, y: 0, duration: 0.8, ease: 'elastic.out(1, 0.45)' }));
  });
}

function cursor() {
  const c = document.querySelector<HTMLElement>('.cursor');
  if (!c || reduced() || !fine()) return;
  const pos = { x: -100, y: -100 }, cur = { x: -100, y: -100 };
  window.addEventListener('pointermove', e => { pos.x = e.clientX; pos.y = e.clientY; c.style.opacity = '1'; });
  document.addEventListener('pointerleave', () => (c.style.opacity = '0'));
  gsap.ticker.add(() => {
    cur.x += (pos.x - cur.x) * 0.2; cur.y += (pos.y - cur.y) * 0.2;
    c.style.transform = `translate3d(${cur.x}px, ${cur.y}px, 0)`;
  });
  document.addEventListener('pointerover', e => {
    c.dataset.hover = (e.target as HTMLElement).closest('a, button, summary, input, select, textarea, [data-cursor]') ? 'true' : 'false';
  });
}

/** [data-scene-trigger="light"|"dark"]: the page background and text change as that block enters. */
function scenes() {
  const root = document.documentElement;
  document.querySelectorAll<HTMLElement>('[data-scene-trigger]').forEach(el => {
    const scene = el.dataset.sceneTrigger!;
    ScrollTrigger.create({
      trigger: el, start: 'top 55%', end: 'bottom 45%',
      onEnter: () => (root.dataset.scene = scene), onEnterBack: () => (root.dataset.scene = scene),
    });
  });
}

function menu() {
  const m = document.getElementById('menu');
  const open = document.querySelector<HTMLElement>('.burger');
  const close = document.querySelector<HTMLElement>('.menu__close');
  if (!m || !open || !close) return;
  const set = (on: boolean) => {
    m.dataset.open = String(on);
    open.setAttribute('aria-expanded', String(on));
    if (on) { lenis?.stop(); document.body.style.overflow = 'hidden'; close.focus(); }
    else { lenis?.start(); document.body.style.overflow = ''; open.focus(); }
  };
  open.addEventListener('click', () => set(true));
  close.addEventListener('click', () => set(false));
  m.querySelectorAll('a').forEach(a => a.addEventListener('click', () => set(false)));
  window.addEventListener('keydown', e => { if (e.key === 'Escape' && m.dataset.open === 'true') set(false); });
}

/** [data-parallax="0.15"]: drifts by that share of its height while it crosses the screen. */
function parallax() {
  document.querySelectorAll<HTMLElement>('[data-parallax]').forEach(el => {
    const k = parseFloat(el.dataset.parallax || '0.15');
    gsap.fromTo(el, { yPercent: -k * 50 }, { yPercent: k * 50, ease: 'none', scrollTrigger: { trigger: el.parentElement ?? el, start: 'top bottom', end: 'bottom top', scrub: true } });
  });
}

/** [data-clip]: the element is uncovered from the bottom edge as it scrolls in, and its image settles from a zoom. */
function clipReveals() {
  document.querySelectorAll<HTMLElement>('[data-clip]').forEach(el => {
    const img = el.querySelector('img, video');
    const tl = gsap.timeline({ scrollTrigger: { trigger: el, start: 'top 92%', end: 'top 35%', scrub: 0.8 } });
    tl.fromTo(el, { clipPath: 'inset(18% 8% 0% 8% round 28px)' }, { clipPath: 'inset(0% 0% 0% 0% round 18px)', ease: 'none' }, 0);
    if (img) tl.fromTo(img, { scale: 1.25 }, { scale: 1, ease: 'none' }, 0);
  });
}

/** [data-drift="-40"]: giant text that slides sideways (in % of its width) as the page scrolls past it. */
function drift() {
  document.querySelectorAll<HTMLElement>('[data-drift]').forEach(el => {
    const to = parseFloat(el.dataset.drift || '-30');
    gsap.fromTo(el, { xPercent: 0 }, { xPercent: to, ease: 'none', scrollTrigger: { trigger: el.parentElement ?? el, start: 'top bottom', end: 'bottom top', scrub: true } });
  });
}

/** [data-expand]: a framed block grows from an inset card to full bleed while it scrolls in. */
function expanders() {
  document.querySelectorAll<HTMLElement>('[data-expand]').forEach(el => {
    gsap.fromTo(el, { '--inset': '9vw', '--radius': '36px' } as gsap.TweenVars, {
      '--inset': '0vw', '--radius': '0px', ease: 'none',
      scrollTrigger: { trigger: el, start: 'top 85%', end: 'top 10%', scrub: true },
    } as gsap.TweenVars);
  });
}

/** [data-tone]: two-tone headings whose second line fills in with ink as you scroll. */
function toneFill() {
  document.querySelectorAll<HTMLElement>('[data-tone]').forEach(el => {
    gsap.fromTo(el, { backgroundSize: '0% 100%' }, { backgroundSize: '100% 100%', ease: 'none', scrollTrigger: { trigger: el, start: 'top 85%', end: 'top 45%', scrub: true } });
  });
}

export function initMotion() {
  smoothScroll();
  menu();
  statements();
  magnetic();
  cursor();
  scenes();
  if (!reduced()) { parallax(); clipReveals(); drift(); expanders(); toneFill(); }
  const html = document.documentElement;
  const afterIntro = () => { splitHeadlines(); reveals(); counters(); ScrollTrigger.refresh(); };
  if (html.classList.contains('preload')) {
    lenis?.stop();
    window.addEventListener('nx:intro-done', () => { lenis?.start(); afterIntro(); }, { once: true });
  } else afterIntro();
  window.addEventListener('load', () => ScrollTrigger.refresh());
}

export { gsap, ScrollTrigger };
