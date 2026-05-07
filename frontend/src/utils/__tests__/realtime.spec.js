import { buildChatWsUrl } from '@/utils/realtime'

describe('buildChatWsUrl', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('falls back to the current local dev origin when no websocket base url is configured', () => {
    vi.stubEnv('VITE_WS_BASE_URL', '')

    const expectedBase = window.location.origin.replace(/^http/, 'ws')

    expect(buildChatWsUrl('token 123')).toBe(`${expectedBase}/ws/chat?token=token%20123`)
  })

  it('uses the configured websocket base url when provided', () => {
    vi.stubEnv('VITE_WS_BASE_URL', 'https://chat.example.com')

    expect(buildChatWsUrl('abc')).toBe('wss://chat.example.com/ws/chat?token=abc')
  })
})
