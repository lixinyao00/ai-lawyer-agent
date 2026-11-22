import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true
})

const defaultLinkOpenRender =
  md.renderer.rules.link_open ||
  function (tokens, idx, options, env, self) {
    return self.renderToken(tokens, idx, options)
  }

md.renderer.rules.link_open = function (tokens, idx, options, env, self) {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpenRender(tokens, idx, options, env, self)
}

/**
 * 将 Markdown 文本安全渲染为 HTML（链接统一新标签页打开，XSS 清洗）
 * @param {string} text
 * @returns {string}
 */
export function renderMarkdown(text) {
  if (!text || typeof text !== 'string') {
    return ''
  }
  const rawHtml = md.render(text)
  return DOMPurify.sanitize(rawHtml, {
    ADD_ATTR: ['target', 'rel']
  })
}
