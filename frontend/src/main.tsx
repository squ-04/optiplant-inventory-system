import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from './app/App.tsx';
import './styles/base.css';

const queries = new QueryClient();

createRoot(document.getElementById('root')!).render(<QueryClientProvider client={queries}><App /></QueryClientProvider>);
