<script setup lang="ts">
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import javascript from 'highlight.js/lib/languages/javascript'
import 'highlight.js/styles/github.css'

const props = defineProps<{
  content?: string
}>()

hljs.registerLanguage('html', xml)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)

const markdown = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: true,
  highlight(code: string, language: string) {
    const normalizedLanguage = language?.toLowerCase?.() || ''
    if (normalizedLanguage && hljs.getLanguage(normalizedLanguage)) {
      return `<pre class="md-code-block"><code class="hljs language-${normalizedLanguage}">${hljs.highlight(code, {
        language: normalizedLanguage,
        ignoreIllegals: true,
      }).value}</code></pre>`
    }

    return `<pre class="md-code-block"><code class="hljs">${hljs.highlightAuto(code, ['xml', 'css', 'javascript']).value}</code></pre>`
  },
})
</script>

<template>
  <div class="markdown-renderer" v-html="markdown.render(content || '')" />
</template>

<style scoped>
.markdown-renderer {
  color: inherit;
  font-size: 14px;
  line-height: 1.8;
  word-break: break-word;
}

.markdown-renderer :deep(*:first-child) {
  margin-top: 0;
}

.markdown-renderer :deep(*:last-child) {
  margin-bottom: 0;
}

.markdown-renderer :deep(h1),
.markdown-renderer :deep(h2),
.markdown-renderer :deep(h3),
.markdown-renderer :deep(h4) {
  margin: 1.1em 0 0.5em;
  color: #0f172a;
  line-height: 1.35;
}

.markdown-renderer :deep(h1) {
  font-size: 1.5rem;
}

.markdown-renderer :deep(h2) {
  font-size: 1.28rem;
}

.markdown-renderer :deep(h3) {
  font-size: 1.1rem;
}

.markdown-renderer :deep(p),
.markdown-renderer :deep(ul),
.markdown-renderer :deep(ol),
.markdown-renderer :deep(blockquote) {
  margin: 0.7em 0;
}

.markdown-renderer :deep(ul),
.markdown-renderer :deep(ol) {
  padding-left: 1.3em;
}

.markdown-renderer :deep(li + li) {
  margin-top: 0.25em;
}

.markdown-renderer :deep(a) {
  color: #2563eb;
  text-decoration: none;
}

.markdown-renderer :deep(a:hover) {
  text-decoration: underline;
}

.markdown-renderer :deep(blockquote) {
  padding: 0.9em 1em;
  color: #475569;
  background: rgba(241, 245, 249, 0.9);
  border-left: 4px solid rgba(59, 130, 246, 0.45);
  border-radius: 12px;
}

.markdown-renderer :deep(code:not(pre code)) {
  padding: 0.18em 0.45em;
  color: #be123c;
  font-size: 0.92em;
  background: rgba(248, 250, 252, 0.95);
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 8px;
}

.markdown-renderer :deep(.md-code-block) {
  margin: 0.9em 0;
  overflow-x: auto;
  background: #f8fafc;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 16px;
}

.markdown-renderer :deep(.md-code-block code) {
  display: block;
  padding: 16px 18px;
  font-size: 13px;
  line-height: 1.7;
  background: transparent;
}

.markdown-renderer :deep(table) {
  width: 100%;
  margin: 0.9em 0;
  overflow: hidden;
  border-collapse: collapse;
  border-radius: 14px;
  box-shadow: inset 0 0 0 1px rgba(226, 232, 240, 0.95);
}

.markdown-renderer :deep(th),
.markdown-renderer :deep(td) {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid rgba(226, 232, 240, 0.95);
}

.markdown-renderer :deep(th) {
  background: rgba(248, 250, 252, 0.95);
}
</style>
