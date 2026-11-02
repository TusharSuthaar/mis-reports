import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { AuthProvider } from './app/auth';
import './styles.css';

// The mouse wheel over a number box being typed in (a %, an amount) would change its value: the box lets go instead and the page scrolls.
document.addEventListener(
  'wheel',
  (e) => {
    const box = document.activeElement;
    if (box instanceof HTMLInputElement && box.type === 'number' && e.target === box) box.blur();
  },
  { passive: true },
);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
