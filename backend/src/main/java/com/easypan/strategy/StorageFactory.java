package com.easypan.strategy;

import com.easypan.entity.enums.StorageTypeEnum;
import com.easypan.exception.BusinessException;
import com.easypan.strategy.impl.LocalStorageStrategy;
import com.easypan.strategy.impl.OssStorageStrategy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储策略工厂，根据配置获取对应的存储策略实例.
 */
@Component
public class StorageFactory implements ApplicationContextAware {

    @Value("${app.storage.type:local}")
    private String storageType;

    private static final Map<String, StorageStrategy> STRATEGY_MAP = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Map<String, StorageStrategy> beans = applicationContext.getBeansOfType(StorageStrategy.class);
        beans.values().forEach(strategy -> {
            if (strategy instanceof LocalStorageStrategy) {
                STRATEGY_MAP.put(StorageTypeEnum.LOCAL.getCode(), strategy);
            } else if (strategy instanceof OssStorageStrategy) {
                STRATEGY_MAP.put(StorageTypeEnum.OSS.getCode(), strategy);
                STRATEGY_MAP.put(StorageTypeEnum.MINIO.getCode(), strategy);
            }
        });
    }

    /**
     * 获取默认存储策略.
     *
     * @return 存储策略实例
     */
    public StorageStrategy getStorageStrategy() {
        StorageStrategy strategy = STRATEGY_MAP.get(storageType);
        if (strategy == null) {
            strategy = STRATEGY_MAP.get(StorageTypeEnum.LOCAL.getCode());
        }
        if (strategy == null) {
            throw new BusinessException("存储配置错误，未找到有效的存储策略，请联系管理员");
        }
        return strategy;
    }

    public StorageStrategy getStorageStrategy(String type) {
        return STRATEGY_MAP.get(type);
    }
}
