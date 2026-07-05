export const createSSE = (
  url: string,
  headers: Record<string, string>,
  onMessage: (data: string, close: () => void) => void,
  onError: (close: () => void) => void,
) => {
  const controller = new AbortController()
  let closed = false

  const close = () => {
    closed = true
    controller.abort()
  }

  fetch(url, { headers, signal: controller.signal })
    .then(async (response) => {
      if (!response.ok || !response.body) {
        onError(close)
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (!closed) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (trimmed.startsWith('data:')) {
            onMessage(trimmed.slice(5).trimStart(), close)
          }
        }
      }
    })
    .catch(() => {
      if (!closed) onError(close)
    })

  return close
}
