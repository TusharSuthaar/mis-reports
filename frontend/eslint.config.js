// Lint: TypeScript's recommended rules and the React hooks rules (effects and memos list what they use).
// A deliberate exception is a disable comment that says why: `// eslint-disable-next-line <rule> -- reason`.
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist/'] },
  {
    files: ['src/**/*.{ts,tsx}'],
    extends: [tseslint.configs.recommended],
    plugins: { 'react-hooks': reactHooks },
    rules: reactHooks.configs.recommended.rules,
    linterOptions: { reportUnusedDisableDirectives: 'error' },
  },
);
