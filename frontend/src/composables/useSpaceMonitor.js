import { ref, computed, getCurrentInstance, watch } from 'vue';
import * as accountService from "@/services/accountService";
import { useSWR } from "./useSWR";

export function useSpaceMonitor() {
    const { proxy } = getCurrentInstance();
    const spaceChipUpdating = ref(false);

    // 基于 useSWR 机制改写的缓存轮询
    const spaceSwrOptions = ref({ refreshInterval: 0, dedupingInterval: 3000 });
    const {
        data: useSpaceInfoCache,
        isLoading: refreshingUseSpace,
        revalidate,
        startPolling,
        stopPolling
    } = useSWR('/api/getUseSpace', accountService.getUseSpace, spaceSwrOptions.value);

    // 回退原始初态
    const useSpaceInfo = computed(() => useSpaceInfoCache.value || { useSpace: 0, totalSpace: 1 });

    let spaceChipTimer = null;

    const useSpacePercent = computed(() => {
        const total = Number(useSpaceInfo.value.totalSpace || 1);
        const used = Number(useSpaceInfo.value.useSpace || 0);
        return Math.min(100, Math.max(0, Math.round((used / total) * 10000) / 100));
    });

    const remainSpaceLabel = computed(() => {
        const total = Number(useSpaceInfo.value.totalSpace || 0);
        const used = Number(useSpaceInfo.value.useSpace || 0);
        return proxy.Utils.size2Str(Math.max(0, total - used));
    });

    const remainSpacePercent = computed(() => {
        const total = Number(useSpaceInfo.value.totalSpace || 0);
        const used = Number(useSpaceInfo.value.useSpace || 0);
        if (total <= 0) return 0;
        const remain = Math.max(0, total - used);
        return Math.max(0, Math.min(100, Math.round((remain / total) * 10000) / 100));
    });

    const useSpaceState = computed(() => {
        const percent = useSpacePercent.value;
        if (percent >= 90) return "danger";
        if (percent >= 75) return "warning";
        return "safe";
    });

    const markSpaceUpdated = () => {
        spaceChipUpdating.value = true;
        if (spaceChipTimer) clearTimeout(spaceChipTimer);
        spaceChipTimer = setTimeout(() => {
            spaceChipUpdating.value = false;
        }, 420);
    };

    // 观测 SWR 回调的数据以驱动胶囊高亮
    watch(() => useSpaceInfoCache.value, (newVal, oldVal) => {
        if (newVal && oldVal) {
            if (newVal.useSpace !== oldVal.useSpace || newVal.totalSpace !== oldVal.totalSpace) {
                markSpaceUpdated();
            }
        }
    });

    const getUseSpace = () => {
        revalidate();
    };

    const ensureSpaceAutoRefresh = (activeTaskCount) => {
        if (activeTaskCount > 0) {
            spaceSwrOptions.value.refreshInterval = 2000;
            startPolling();
            getUseSpace();
        } else {
            spaceSwrOptions.value.refreshInterval = 0;
            stopPolling();
            getUseSpace();
        }
    };

    const disposeSpaceMonitor = () => {
        stopPolling();
        if (spaceChipTimer) clearTimeout(spaceChipTimer);
    };

    return {
        useSpaceInfo,
        spaceChipUpdating,
        refreshingUseSpace,
        useSpacePercent,
        remainSpaceLabel,
        remainSpacePercent,
        useSpaceState,
        getUseSpace,
        ensureSpaceAutoRefresh,
        disposeSpaceMonitor
    };
}
