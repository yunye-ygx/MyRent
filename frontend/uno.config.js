import { defineConfig, presetAttributify, presetIcons, presetUno } from 'unocss'

export default defineConfig({
  presets: [presetUno(), presetAttributify(), presetIcons()],
  shortcuts: {
    'app-shell': 'min-h-screen bg-[var(--color-bg)] text-[var(--color-text)]',
    'app-container': 'mx-auto w-full max-w-[var(--container-max)] px-6 lg:px-10',
    'app-surface': 'rounded-[var(--radius-xl)] bg-[var(--color-surface)] shadow-[var(--shadow-soft)]',
    'app-muted': 'text-[var(--color-text-muted)]'
  }
})
