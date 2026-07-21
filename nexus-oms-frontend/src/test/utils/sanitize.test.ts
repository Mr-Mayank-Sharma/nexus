import { describe, it, expect } from 'vitest'
import { sanitizeHTML, sanitizeText, sanitizeURL, preventXSS } from '../../utils/sanitize'

describe('sanitizeHTML', () => {
  it('allows safe HTML tags', () => {
    const result = sanitizeHTML('<p>Hello <b>world</b></p>')
    expect(result).toContain('<p>')
    expect(result).toContain('<b>')
  })

  it('strips script tags', () => {
    const result = sanitizeHTML('<p>Hello</p><script>alert("xss")</script>')
    expect(result).not.toContain('<script>')
    expect(result).toContain('Hello')
  })

  it('strips onerror attributes', () => {
    const result = sanitizeHTML('<img src="x" onerror="alert(1)">')
    expect(result).not.toContain('onerror')
  })

  it('allows anchor tags with safe attributes', () => {
    const result = sanitizeHTML('<a href="https://example.com" target="_blank">link</a>')
    expect(result).toContain('href=')
    expect(result).toContain('target=')
  })
})

describe('sanitizeText', () => {
  it('escapes HTML entities', () => {
    const result = sanitizeText('<script>alert("xss")</script>')
    expect(result).toContain('&lt;script&gt;')
    expect(result).not.toContain('<script>')
  })

  it('preserves plain text', () => {
    expect(sanitizeText('Hello world')).toBe('Hello world')
  })
})

describe('sanitizeURL', () => {
  it('allows http URLs', () => {
    expect(sanitizeURL('http://example.com')).toBe('http://example.com/')
  })

  it('allows https URLs', () => {
    expect(sanitizeURL('https://example.com/path')).toBe('https://example.com/path')
  })

  it('allows mailto URLs', () => {
    expect(sanitizeURL('mailto:user@example.com')).toBe('mailto:user@example.com')
  })

  it('rejects javascript URLs', () => {
    expect(sanitizeURL('javascript:alert(1)')).toBeNull()
  })

  it('rejects data URLs', () => {
    expect(sanitizeURL('data:text/html,<script>alert(1)</script>')).toBeNull()
  })
})

describe('preventXSS', () => {
  it('escapes HTML special characters', () => {
    expect(preventXSS('<script>')).toBe('&lt;script&gt;')
    expect(preventXSS('"onclick="')).toBe('&quot;onclick=&quot;')
    expect(preventXSS("it's")).toBe("it&#x27;s")
  })

  it('escapes ampersand', () => {
    expect(preventXSS('a & b')).toBe('a &amp; b')
  })

  it('escapes forward slash', () => {
    expect(preventXSS('</script>')).toBe('&lt;&#x2F;script&gt;')
  })
})
