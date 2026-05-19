# frontend/CLAUDE.md

React 18 + TypeScript + Vite. Node 20 (use nvm).

## Install & Dev Commands

```bash
npm install --legacy-peer-deps   # required — react-idle-timer@4.3.6 requires React 16; --legacy-peer-deps matches the Dockerfile
npm run dev                      # Vite dev server
npm run build                    # tsc + vite build
npm run lint                     # ESLint 9 flat config (eslint.config.js)
```

## Redux Architecture

Two patterns coexist — don't mix them:

- **RTK slices** (`src/store/Login/login-slice.ts`, `src/store/Home/home-slice.ts`): use `createSlice` from `@reduxjs/toolkit`.
- **Old-style action + reducer pairs** (`Cart/`, `ManageProducts/`, `Orders/`): separate `actions.ts` and `reducers.ts` files using plain action creator functions.

Always import `RootState` and `AppDispatch` from `src/store` (the store module), not from individual slices:

```typescript
import { AppDispatch, RootState } from "../../store";
```

In components: `useDispatch<AppDispatch>()` and `useSelector((state: RootState) => ...)`.

In store action files (actions.ts / slices), never import `AppDispatch` from `src/store` — it creates a circular dependency. Use the local alias instead:

```typescript
import type { ThunkDispatch } from "@reduxjs/toolkit";
import { AnyAction } from "@reduxjs/toolkit";
type LocalDispatch = ThunkDispatch<unknown, undefined, AnyAction>;
```

## ESLint Config

ESLint 9 flat config (`eslint.config.js`). Unused variables/args prefixed with `_` are allowed. Run `npm run lint` to check — CI will catch issues.
