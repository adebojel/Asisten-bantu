import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { KobraProvider } from './context/KobraContext';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <KobraProvider>
      <App />
    </KobraProvider>
  </React.StrictMode>
);
