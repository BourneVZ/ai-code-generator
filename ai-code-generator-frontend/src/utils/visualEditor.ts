export interface ElementAttribute {
  name: string
  value: string
}

export interface SelectedElementInfo {
  uid: string
  tagName: string
  id: string
  className: string
  textContent: string
  attributes: ElementAttribute[]
  selectorPath: string
}

export type VisualEditorAction = 'select' | 'deselect'

export interface VisualEditorMessage {
  source: 'visual-editor'
  action: VisualEditorAction
  element: SelectedElementInfo
}

const STYLE_ID = 've-style'
const SCRIPT_ID = 've-script'
const HOVER_CLASS = 've-hover'
const SELECTED_CLASS = 've-selected'

const INJECTED_CSS = `
.${HOVER_CLASS} {
  outline: 2px dashed #1890ff !important;
  outline-offset: 1px !important;
}
.${SELECTED_CLASS} {
  outline: 3px solid #1890ff !important;
  outline-offset: 1px !important;
}
`

function generateUid(): string {
  return `el-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
}

function buildSelectorPath(el: Element): string {
  const path: string[] = []
  let current: Element | null = el

  while (current && current !== document.body && current !== document.documentElement) {
    let selector = current.tagName.toLowerCase()
    if (current.id) {
      selector = `#${CSS.escape(current.id)}`
      path.unshift(selector)
      break
    }
    const classes = current.className && typeof current.className === 'string'
      ? current.className.trim().split(/\s+/).filter(Boolean).slice(0, 2).join('.')
      : ''
    if (classes) {
      selector += `.${classes.replace(/:/g, '\\:')}`
    }
    path.unshift(selector)
    current = current.parentElement
  }
  return path.join(' > ')
}

function isExcludedElement(el: Element): boolean {
  return (
    el === document.body ||
    el === document.documentElement ||
    el.id === STYLE_ID ||
    el.id === SCRIPT_ID
  )
}

function extractElementInfo(el: Element): SelectedElementInfo {
  const importantAttrs = ['href', 'src', 'alt', 'type', 'placeholder', 'name', 'value', 'role', 'aria-label']
  const attributes: ElementAttribute[] = []
  importantAttrs.forEach((name) => {
    const val = el.getAttribute(name)
    if (val !== null) {
      attributes.push({ name, value: val })
    }
  })

  return {
    uid: generateUid(),
    tagName: el.tagName.toLowerCase(),
    id: el.id || '',
    className: typeof el.className === 'string' ? el.className.trim() : '',
    textContent: (el.textContent || '').trim().slice(0, 100),
    attributes,
    selectorPath: buildSelectorPath(el),
  }
}

function buildScriptContent(): string {
  return `
(function() {
  if (window.__ve_loaded__) return;
  window.__ve_loaded__ = true;

  var HOVER_CLASS = '${HOVER_CLASS}';
  var SELECTED_CLASS = '${SELECTED_CLASS}';
  var STYLE_ID = '${STYLE_ID}';
  var SCRIPT_ID = '${SCRIPT_ID}';
  var selectedElements = [];
  var currentHovered = null;

  function isExcluded(el) {
    return el === document.body ||
      el === document.documentElement ||
      el.id === STYLE_ID ||
      el.id === SCRIPT_ID;
  }

  function buildSelectorPath(el) {
    var path = [];
    var current = el;
    while (current && current !== document.body && current !== document.documentElement) {
      var selector = current.tagName.toLowerCase();
      if (current.id) {
        try { selector = '#' + CSS.escape(current.id); } catch(e) { selector = '#' + current.id; }
        path.unshift(selector);
        break;
      }
      var cls = (typeof current.className === 'string' ? current.className.trim() : '').split(/\\s+/).filter(Boolean).slice(0, 2).join('.');
      if (cls) { selector += '.' + cls.replace(/:/g, '\\\\:'); }
      path.unshift(selector);
      current = current.parentElement;
    }
    return path.join(' > ');
  }

  function buildElementInfo(el) {
    var importantAttrs = ['href', 'src', 'alt', 'type', 'placeholder', 'name', 'value', 'role', 'aria-label'];
    var attrs = [];
    importantAttrs.forEach(function(name) {
      var val = el.getAttribute(name);
      if (val !== null) attrs.push({ name: name, value: val });
    });
    return {
      uid: 'el-' + Date.now() + '-' + Math.random().toString(16).slice(2, 8),
      tagName: el.tagName.toLowerCase(),
      id: el.id || '',
      className: typeof el.className === 'string' ? el.className.trim() : '',
      textContent: (el.textContent || '').trim().slice(0, 100),
      attributes: attrs,
      selectorPath: buildSelectorPath(el)
    };
  }

  function postMessage(action, el) {
    var info = buildElementInfo(el);
    window.parent.postMessage({
      source: 'visual-editor',
      action: action,
      element: info
    }, '*');
  }

  document.addEventListener('mouseover', function(e) {
    var target = e.target;
    if (!target || !target.tagName || isExcluded(target)) return;
    if (currentHovered === target) return;
    if (currentHovered && !selectedElements.includes(currentHovered)) {
      currentHovered.classList.remove(HOVER_CLASS);
    }
    if (!selectedElements.includes(target)) {
      target.classList.add(HOVER_CLASS);
    }
    currentHovered = target;
  }, true);

  document.addEventListener('mouseout', function(e) {
    var target = e.target;
    if (!target || !target.tagName) return;
    if (!selectedElements.includes(target)) {
      target.classList.remove(HOVER_CLASS);
    }
    if (currentHovered === target) {
      currentHovered = null;
    }
  }, true);

  document.addEventListener('click', function(e) {
    var target = e.target;
    if (!target || !target.tagName || isExcluded(target)) return;
    e.preventDefault();
    e.stopPropagation();
    e.stopImmediatePropagation();

    var idx = selectedElements.indexOf(target);
    if (idx >= 0) {
      target.classList.remove(SELECTED_CLASS);
      selectedElements.splice(idx, 1);
      postMessage('deselect', target);
    } else {
      target.classList.add(SELECTED_CLASS);
      target.classList.remove(HOVER_CLASS);
      selectedElements.push(target);
      currentHovered = null;
      postMessage('select', target);
    }
  }, true);

  window.addEventListener('message', function(e) {
    var data = e.data;
    if (!data || data.source !== 'visual-editor-parent') return;
    if (data.action === 'deselect') {
      var target = document.querySelector(data.selectorPath);
      if (target) {
        target.classList.remove(SELECTED_CLASS);
        selectedElements = selectedElements.filter(function(el) { return el !== target; });
      }
    }
  });
})();
`
}

export function injectVisualEditor(iframe: HTMLIFrameElement): boolean {
  try {
    const doc = iframe.contentDocument
    if (!doc) return false

    if (doc.getElementById(SCRIPT_ID)) return true

    const style = doc.createElement('style')
    style.id = STYLE_ID
    style.textContent = INJECTED_CSS
    doc.head.appendChild(style)

    const script = doc.createElement('script')
    script.id = SCRIPT_ID
    script.textContent = buildScriptContent()
    doc.body.appendChild(script)

    return true
  } catch {
    return false
  }
}

export function removeVisualEditor(iframe: HTMLIFrameElement): void {
  try {
    const doc = iframe.contentDocument
    if (!doc) return

    const style = doc.getElementById(STYLE_ID)
    if (style) style.remove()

    const script = doc.getElementById(SCRIPT_ID)
    if (script) script.remove()

    doc.querySelectorAll(`.${HOVER_CLASS}, .${SELECTED_CLASS}`).forEach((el) => {
      el.classList.remove(HOVER_CLASS, SELECTED_CLASS)
    })

    const win = iframe.contentWindow as Window & { __ve_loaded__?: boolean }
    if (win) {
      win.__ve_loaded__ = false
    }
  } catch {
    // cross-origin, silently ignore
  }
}

export function deselectElementInIframe(iframe: HTMLIFrameElement, selectorPath: string): void {
  try {
    iframe.contentWindow?.postMessage(
      {
        source: 'visual-editor-parent',
        action: 'deselect',
        selectorPath,
      },
      '*',
    )
  } catch {
    // cross-origin, silently ignore
  }
}

export function createVisualEditorMessageHandler(
  onElement: (element: SelectedElementInfo, action: VisualEditorAction) => void,
): (event: MessageEvent) => void {
  return (event: MessageEvent) => {
    const data = event.data
    if (!data || data.source !== 'visual-editor') return
    onElement(data.element, data.action)
  }
}

export function formatElementsForPrompt(elements: SelectedElementInfo[]): string {
  if (!elements.length) return ''

  const lines = elements.map((el, i) => {
    let desc = `<${el.tagName}`
    if (el.id) desc += ` id="${el.id}"`
    if (el.className) desc += ` class="${el.className}"`
    desc += '>'
    if (el.textContent) {
      desc += ` 内容: "${el.textContent}"`
    }
    return `${i + 1}. ${desc}`
  })

  return `\n\n[用户选中的网页元素]\n${lines.join('\n')}\n\n请针对以上选中的元素进行修改。`
}
