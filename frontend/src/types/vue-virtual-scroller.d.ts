declare module 'vue-virtual-scroller' {
  import type { DefineComponent, Plugin } from 'vue'

  const plugin: Plugin
  export default plugin

  type ScrollerComponent = DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>

  export const RecycleScroller: ScrollerComponent
  export const DynamicScroller: ScrollerComponent
  export const DynamicScrollerItem: ScrollerComponent
}
