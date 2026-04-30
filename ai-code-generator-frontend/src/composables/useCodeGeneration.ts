import { ref, onBeforeUnmount } from 'vue'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** SSE business-error payload sent by the backend */
export interface BusinessErrorPayload {
  error: boolean
  code: number
  message: string
}

/** Callbacks the composable delegates to the host component */
export interface CodeGenHandlers {
  /** A decoded text chunk arrived from the SSE stream */
  onChunk: (chunk: string) => void
  /** The stream finished normally (done event received) */
  onDone: (showSuccess: boolean) => Promise<void>
  /** The EventSource onerror fired when readyState was not CONNECTING;
   *  return true if recovery succeeded so the composable can call onDone(false) */
  onStreamError: () => Promise<boolean>
  /** Backend sent a business-error event (rate-limit, validation, etc.) */
  onBusinessError: (code: number, message: string) => void
  /** Connection-level fatal error that could not be recovered */
  onFatalError: (content: string) => void
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

export function useCodeGeneration(apiBaseUrl: string) {
  // ---- state ---------------------------------------------------------------

  const isGenerating = ref(false)

  let eventSource: EventSource | null = null
  let streamCompleted = false

  // ---- helpers -------------------------------------------------------------

  /**
   * Decodes an SSE data payload into the plain-text chunk.
   * Copied from the original AppChatPage implementation.
   */
  function decodeSsePayload(payload: string): string {
    const normalized = payload.trim()
    if (!normalized) return ''

    // When multiple JSON objects are concatenated on one line, split them
    if (normalized.includes('}{')) {
      return normalized
        .replace(/}\s*{/g, '}\n{')
        .split('\n')
        .map((item) => decodeSsePayload(item))
        .join('')
    }

    try {
      const parsed = JSON.parse(normalized)
      if (typeof parsed?.d === 'string') return parsed.d
      if (typeof parsed?.data === 'string') return parsed.data
      if (typeof parsed === 'string') return parsed
    } catch {
      // Fallback: extract "d" fields with a regex
      const matches = Array.from(normalized.matchAll(/"d"\s*:\s*"((?:\\.|[^"\\])*)"/g))
      if (matches.length > 0) {
        return matches.map(([, chunk]) => JSON.parse(`"${chunk}"`) as string).join('')
      }
      return normalized
    }

    return ''
  }

  function closeEventSource() {
    eventSource?.close()
    eventSource = null
  }

  // ---- public API ----------------------------------------------------------

  /**
   * Start a new SSE generation. Automatically aborts any in-flight stream.
   *
   * @param appId   - The application id
   * @param message - The user prompt to send
   * @param handlers - Callbacks for stream events
   */
  function start(appId: string, message: string, handlers: CodeGenHandlers) {
    // Always clean up any existing stream first
    abort()

    streamCompleted = false
    isGenerating.value = true

    const url = new URL(`${apiBaseUrl}/app/chat/gen/code`, window.location.origin)
    url.searchParams.set('appId', appId)
    url.searchParams.set('message', message)

    eventSource = new EventSource(url.toString(), { withCredentials: true })

    // -- plain message chunks ------------------------------------------------

    eventSource.onmessage = (event) => {
      if (streamCompleted) return
      const chunk = decodeSsePayload(event.data)
      if (chunk) handlers.onChunk(chunk)
    }

    // -- explicit completion -------------------------------------------------

    eventSource.addEventListener('done', async () => {
      if (streamCompleted) return
      streamCompleted = true
      isGenerating.value = false
      closeEventSource()
      await handlers.onDone(true)
    })

    // -- backend business error (rate-limit, validation, etc.) ----------------

    eventSource.addEventListener('business-error', (event: MessageEvent) => {
      if (streamCompleted) return
      streamCompleted = true
      isGenerating.value = false
      closeEventSource()

      try {
        const errorData: BusinessErrorPayload = JSON.parse(event.data)
        handlers.onBusinessError(
          errorData.code ?? -1,
          errorData.message ?? '生成过程中出现错误',
        )
      } catch {
        handlers.onFatalError('服务器返回错误')
      }
    })

    // -- connection-level error or stream closure ----------------------------

    eventSource.onerror = async () => {
      if (streamCompleted) return

      // readyState CONNECTING after a stream is done is normal —
      // treat it as implicit completion
      if (eventSource?.readyState === EventSource.CONNECTING) {
        streamCompleted = true
        isGenerating.value = false
        closeEventSource()
        await handlers.onDone(false)
        return
      }

      // Try to recover (fetch detail + history); if there's content we're ok
      const recovered = await handlers.onStreamError()
      if (recovered) {
        streamCompleted = true
        isGenerating.value = false
        closeEventSource()
        await handlers.onDone(false)
      } else {
        streamCompleted = true
        isGenerating.value = false
        closeEventSource()
        handlers.onFatalError('生成中断，请稍后重试。')
      }
    }
  }

  /** Abort the current SSE stream immediately (no callbacks are fired). */
  function abort() {
    streamCompleted = true
    closeEventSource()
    isGenerating.value = false
  }

  // Ensure the EventSource is always cleaned up when the host component unmounts
  onBeforeUnmount(() => {
    abort()
  })

  return { isGenerating, start, abort }
}
