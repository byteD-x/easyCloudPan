import { onCLS, onINP, onLCP, onTTFB, onFCP } from 'web-vitals'
import { logger } from './logger'

interface WebVitalMetric {
  name: string
  value: number
  rating: 'good' | 'needs-improvement' | 'poor'
  delta: number
  id: string
  navigationType: string
}

const VITALS_THRESHOLD = {
  LCP: { good: 2500, poor: 4000 },
  INP: { good: 200, poor: 500 },
  CLS: { good: 0.1, poor: 0.25 },
  FCP: { good: 1800, poor: 3000 },
  TTFB: { good: 800, poor: 1800 },
}

function getRating(name: string, value: number): 'good' | 'needs-improvement' | 'poor' {
  const threshold = VITALS_THRESHOLD[name as keyof typeof VITALS_THRESHOLD]
  if (!threshold) return 'good'
  if (value <= threshold.good) return 'good'
  if (value <= threshold.poor) return 'needs-improvement'
  return 'poor'
}

function sendToAnalytics(metric: WebVitalMetric) {
  const body = JSON.stringify({
    name: metric.name,
    value: metric.value,
    rating: metric.rating,
    delta: metric.delta,
    id: metric.id,
    navigationType: metric.navigationType,
    page: window.location.pathname,
    timestamp: Date.now(),
    userAgent: navigator.userAgent,
    deviceType: getDeviceType(),
    connectionType: getConnectionType(),
  })

  if (navigator.sendBeacon) {
    const blob = new Blob([body], { type: 'application/json' })
    navigator.sendBeacon('/api/analytics/web-vitals', blob)
  } else {
    fetch('/api/analytics/web-vitals', {
      body,
      method: 'POST',
      keepalive: true,
      headers: { 'Content-Type': 'application/json' },
    }).catch(() => {
      // 静默失败，避免影响主流程
    })
  }

  if (import.meta.env.DEV) {
    logger.info('WebVitals', `${metric.name}: ${metric.value.toFixed(2)}ms (${metric.rating})`)
  }
}

function getDeviceType(): string {
  const ua = navigator.userAgent.toLowerCase()
  if (/mobile|android|iphone|ipad|ipod|blackberry|iemobile|opera mini/i.test(ua)) {
    if (/tablet|ipad/i.test(ua)) return 'tablet'
    return 'mobile'
  }
  return 'desktop'
}

function getConnectionType(): string {
  const connection = (navigator as Navigator & { connection?: { effectiveType?: string } }).connection
  return connection?.effectiveType || 'unknown'
}

function handleMetric(name: string, metric: { value: number; delta: number; id: string; navigationType: string }) {
  const webVitalMetric: WebVitalMetric = {
    name,
    value: metric.value,
    rating: getRating(name, metric.value),
    delta: metric.delta,
    id: metric.id,
    navigationType: metric.navigationType,
  }
  sendToAnalytics(webVitalMetric)
}

export function initWebVitals() {
  onLCP((metric) => handleMetric('LCP', metric))
  onINP((metric) => handleMetric('INP', metric))
  onCLS((metric) => handleMetric('CLS', metric))
  onTTFB((metric) => handleMetric('TTFB', metric))
  onFCP((metric) => handleMetric('FCP', metric))
}

export { VITALS_THRESHOLD }
