import { ref, onMounted, onUnmounted, watch, isRef } from 'vue';

// 全局静态 Cache Map (闭包形态) 保存各请求的响应快照以达成 SWR (Stale-while-revalidate) 体验
// 结构: Map<string, { data: any, timestamp: number, promise: Promise }>
const globalCache = new Map();

/**
 * 零依赖的 SWR 生产级组合式函数 
 * @param {string | Ref<string> | function} keyFn 唯一标识键或返回键的函数，支持传如 '/api/getUsage' 等字符串
 * @param {function} fetcher 执行拉取动作的 async 函数，签名为 `(key) => Promise`
 * @param {object} options 控制配置参数如 { refreshInterval: number, dedupingInterval: number }
 */
export function useSWR(keyFn, fetcher, options = {}) {
    const {
        refreshInterval = 0, // 每多少毫秒轮询一次 (0为不轮询)
        dedupingInterval = 2000, // 去重/防抖阈值 (多长时间内的相同请求被合并)
    } = options;

    const data = ref(null);
    const error = ref(null);
    const isLoading = ref(true);

    let pollingTimer = null;
    let isDisposed = false;

    const resolveKey = () => {
        if (typeof keyFn === 'function') return keyFn();
        if (isRef(keyFn)) return keyFn.value;
        return keyFn;
    };

    const revalidate = async (optionsOverride = {}) => {
        if (isDisposed) return;
        const currentKey = resolveKey();
        if (!currentKey) {
            isLoading.value = false;
            return;
        }

        const now = Date.now();
        const cacheEntry = globalCache.get(currentKey);

        // 1. Stale-while-revalidate 初步吐出: 如果有旧数据，先无痛映射给组件让其闪速渲染 UI
        if (cacheEntry && cacheEntry.data !== undefined) {
            data.value = cacheEntry.data;
            // 如果处于去重时间窗口内，阻止真实发包
            if (now - cacheEntry.timestamp < dedupingInterval && !optionsOverride.force) {
                isLoading.value = false;
                return;
            }
        }

        // 2. 防并发锁 (Deduping concurrent requests)
        if (cacheEntry && cacheEntry.promise) {
            isLoading.value = true;
            try {
                const res = await cacheEntry.promise;
                if (!isDisposed && resolveKey() === currentKey) {
                    data.value = res;
                    error.value = null;
                }
            } catch (err) {
                if (!isDisposed && resolveKey() === currentKey) error.value = err;
            } finally {
                if (!isDisposed && resolveKey() === currentKey) isLoading.value = false;
            }
            return;
        }

        // 3. 真实发包拉取 (Revalidate)
        isLoading.value = true;
        const requestPromise = Promise.resolve(fetcher(currentKey));

        // 把该 Promise 注册到全局池，防止其他组件多重发起
        globalCache.set(currentKey, { ...globalCache.get(currentKey), promise: requestPromise });

        try {
            const result = await requestPromise;
            if (!isDisposed && resolveKey() === currentKey) {
                data.value = result;
                error.value = null;
            }
            // 将真实新数据沉淀至缓存快照
            globalCache.set(currentKey, { data: result, timestamp: Date.now(), promise: null });
        } catch (err) {
            if (!isDisposed && resolveKey() === currentKey) {
                error.value = err;
            }
            globalCache.set(currentKey, { ...globalCache.get(currentKey), promise: null });
        } finally {
            if (!isDisposed && resolveKey() === currentKey) {
                isLoading.value = false;
            }
        }
    };

    const startPolling = () => {
        if (refreshInterval > 0 && !pollingTimer) {
            pollingTimer = setInterval(() => {
                // 轮询时采用强制刷新穿透旧数据防抖锁定
                revalidate({ force: true });
            }, refreshInterval);
        }
    };

    const stopPolling = () => {
        if (pollingTimer) {
            clearInterval(pollingTimer);
            pollingTimer = null;
        }
    };

    const mutate = (newData, shouldRevalidate = true) => {
        const currentKey = resolveKey();
        if (currentKey) {
            globalCache.set(currentKey, { data: newData, timestamp: Date.now(), promise: null });
            if (resolveKey() === currentKey) data.value = newData;
            if (shouldRevalidate) revalidate({ force: true });
        }
    };

    // 根据依赖 Key 的变更触发拉取
    watch(() => resolveKey(), (newKey, oldKey) => {
        if (newKey !== oldKey) {
            data.value = null;
            error.value = null;
            revalidate();
        }
    }, { immediate: true });

    onMounted(() => {
        // 激活焦点时恢复轮询
        startPolling();
    });

    onUnmounted(() => {
        isDisposed = true;
        stopPolling();
    });

    return {
        data,
        error,
        isLoading,
        mutate,
        revalidate,
        startPolling,
        stopPolling
    };
}
