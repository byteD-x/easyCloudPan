/* eslint-disable no-console */
/**
 * 前端日志工具（仅在开发环境输出）.
 *
 * 示例：
 * `logger.info('请求', { url: '/api/login' })`
 * `logger.network('POST', '/api/login', 200, 45)`
 */

const isDev = import.meta.env.DEV

type LogLevel = 'info' | 'success' | 'warn' | 'error'
type LogValue = unknown

interface LevelMeta {
  bg: string
  text: string
  icon: string
}

const LEVEL_META: Record<LogLevel, LevelMeta> = {
  info: { bg: '#2196F3', text: '#fff', icon: '[I]' },
  success: { bg: '#4CAF50', text: '#fff', icon: '[S]' },
  warn: { bg: '#FF9800', text: '#fff', icon: '[W]' },
  error: { bg: '#F44336', text: '#fff', icon: '[E]' },
}

class Logger {
  private formatTime(): string {
    const now = new Date()
    const hh = now.getHours().toString().padStart(2, '0')
    const mm = now.getMinutes().toString().padStart(2, '0')
    const ss = now.getSeconds().toString().padStart(2, '0')
    const ms = now.getMilliseconds().toString().padStart(3, '0')
    return `${hh}:${mm}:${ss}.${ms}`
  }

  private print(level: LogLevel, title: string, content: LogValue, ...args: LogValue[]) {
    if (!isDev && level !== 'error') {
      return
    }

    const meta = LEVEL_META[level]
    const badgeStyle = `background:${meta.bg};color:${meta.text};border-radius:3px;padding:2px 6px;font-weight:bold;`
    const timeStyle = 'color:#9e9e9e;font-size:11px;'

    console.log(
      `%c${this.formatTime()}%c %c${meta.icon} ${title}%c`,
      timeStyle,
      '',
      badgeStyle,
      '',
      content,
      ...args
    )
  }

  info(title: string, content: LogValue, ...args: LogValue[]) {
    this.print('info', title, content, ...args)
  }

  success(title: string, content: LogValue, ...args: LogValue[]) {
    this.print('success', title, content, ...args)
  }

  warn(title: string, content: LogValue, ...args: LogValue[]) {
    this.print('warn', title, content, ...args)
  }

  error(title: string, content: LogValue, ...args: LogValue[]) {
    this.print('error', title, content, ...args)
  }

  /**
   * 网络请求日志：方法、路径、状态码、耗时.
   */
  network(method: string, url: string, status: number, durationMs: number) {
    if (!isDev) {
      return
    }

    const isOk = status >= 200 && status < 400
    const isSlow = durationMs > 500
    const level: LogLevel = isSlow ? 'warn' : (isOk ? 'success' : 'error')
    const meta = LEVEL_META[level]

    const badgeStyle = `background:${meta.bg};color:${meta.text};border-radius:3px;padding:2px 6px;font-weight:bold;`
    const timeStyle = 'color:#9e9e9e;font-size:11px;'
    const durationColor = isSlow ? '#FF9800' : '#4CAF50'
    const durationStyle = `color:${durationColor};font-weight:bold;`

    console.log(
      `%c${this.formatTime()}%c %c${method.toUpperCase()} ${url} [${status}]%c %c${durationMs}ms%c`,
      timeStyle,
      '',
      badgeStyle,
      '',
      durationStyle,
      ''
    )
  }

  /**
   * 路由导航日志.
   */
  route(from: string, to: string, name?: string) {
    if (!isDev) {
      return
    }

    const timeStyle = 'color:#9e9e9e;font-size:11px;'
    const routeStyle = 'background:#1f4f68;color:#fff;border-radius:3px;padding:2px 6px;font-weight:bold;'
    const label = name ? ` (${name})` : ''

    console.log(`%c${this.formatTime()}%c %cROUTE%c ${from} -> ${to}${label}`, timeStyle, '', routeStyle, '')
  }

  /**
   * 创建可折叠日志分组.
   */
  group(title: string) {
    if (!isDev) {
      return
    }
    console.groupCollapsed(`%cGROUP ${title}`, 'color:#1f4f68;font-weight:bold;')
  }

  /**
   * 结束日志分组.
   */
  groupEnd() {
    if (!isDev) {
      return
    }
    console.groupEnd()
  }
}

export const logger = new Logger()
