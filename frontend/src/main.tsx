/**
 * Browser entry point: installs the form format hints and mounts `App` inside the TanStack Query provider.
 */
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from './app/App.tsx';
import { explainFormatErrors } from './components/shared/formatHints.ts';
import './styles/base.css';

const queries = new QueryClient();
explainFormatErrors(document);

createRoot(document.getElementById('root')!).render(<QueryClientProvider client={queries}><App /></QueryClientProvider>);
