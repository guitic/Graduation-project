import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import prettier from 'eslint-config-prettier'
import globals from 'globals'

export default [
  {
    ignores: ['dist/**', 'node_modules/**', '*.min.js']
  },

  js.configs.recommended,

  // vue3-recommended：含 no-mutating-props / no-v-html / require-v-for-key
  ...pluginVue.configs['flat/recommended'],

  // 必须放最后：关掉所有与 Prettier 冲突的格式规则（格式交给 Prettier，ESLint 只判缺陷）
  prettier,

  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser }
    },
    rules: {
      // ---- 真实缺陷类：error ----
      eqeqeq: ['error', 'always', { null: 'ignore' }],
      // caughtErrors: 'none' —— 本项目 catch (e) 普遍不使用 e，
      // 因为错误提示已由 request.js 拦截器统一 ElMessage 弹出，
      // 组件层只需"停止后续流程"。这是刻意设计，不是遗漏。
      // 若某处真的需要读 e，请在 catch 内使用它，否则会被这里放过。
      'no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' }
      ],
      'no-undef': 'error',
      'no-debugger': 'error',
      'vue/no-mutating-props': 'error',
      'vue/no-dupe-keys': 'error',
      'vue/require-v-for-key': 'error',

      // ---- 安全类 ----
      // 本项目模板中未使用 v-html，此规则防止未来引入 XSS 面
      'vue/no-v-html': 'warn',

      // ---- 可维护性 ----
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'vue/component-name-in-template-casing': ['warn', 'PascalCase'],
      'vue/require-default-prop': 'warn',

      // ---- 本项目约定 ----
      // Login / Dashboard / People 等单词组件名是既有约定
      'vue/multi-word-component-names': 'off',
      // 缩进与换行交给 Prettier，避免与格式化结果互相打架
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-self-closing': 'off'
    }
  }
]
