import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth-guard';

/** Every feature is lazily loaded, so the initial bundle stays small. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Sign in · TradeWise',
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    title: 'Create account · TradeWise',
    loadComponent: () => import('./features/auth/register').then((m) => m.Register),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    title: 'Dashboard · TradeWise',
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'trade',
    canActivate: [authGuard],
    title: 'Trade · TradeWise',
    loadComponent: () => import('./features/trading/trading').then((m) => m.Trading),
  },
  {
    path: 'stocks/:symbol',
    canActivate: [authGuard],
    title: 'Stock · TradeWise',
    loadComponent: () => import('./features/stock/stock-detail').then((m) => m.StockDetail),
  },
  {
    path: 'portfolio',
    canActivate: [authGuard],
    title: 'Portfolio · TradeWise',
    loadComponent: () => import('./features/portfolio/portfolio').then((m) => m.Portfolio),
  },
  {
    path: 'orders',
    canActivate: [authGuard],
    title: 'Orders · TradeWise',
    loadComponent: () => import('./features/orders/orders').then((m) => m.Orders),
  },
  {
    path: 'account',
    canActivate: [authGuard],
    title: 'Account · TradeWise',
    loadComponent: () => import('./features/account/account').then((m) => m.Account),
  },
  { path: '**', redirectTo: 'dashboard' },
];
