declare module '*.vue' {
  import type { DefineComponent } from 'vue';

  const component: DefineComponent<object, object, unknown>;
  export default component;
}

interface ImportMetaEnv {}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

