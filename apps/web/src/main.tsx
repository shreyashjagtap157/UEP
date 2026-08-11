import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App } from './app/App'
import { initializeAuthentication } from './auth/keycloak'
import './styles/global.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
})

const root = document.getElementById('root')
if (!root) throw new Error('Application root element not found')

async function bootstrap() {
  try {
    await initializeAuthentication()
    createRoot(root!).render(
      <StrictMode>
        <QueryClientProvider client={queryClient}>
          <App />
        </QueryClientProvider>
      </StrictMode>,
    )
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Authentication initialization failed'
    root!.innerHTML = `<main class="centered-status"><section class="hero-card"><h1>Unable to start</h1><p>${escapeHtml(message)}</p></section></main>`
  }
}

function escapeHtml(value: string) {
  return value.replace(/[&<>'"]/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character] ?? character)
}

void bootstrap()
