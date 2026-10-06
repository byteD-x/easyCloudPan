package com.easypan;

import com.easypan.component.RedisComponent;
import com.easypan.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 应用初始化运行器，在应用启动后执行初始化操作.
 */
@Component("initRun")
public class InitRun implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(InitRun.class);

    @Resource
    private DataSource dataSource;

    @Resource
    private RedisComponent redisComponent;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            logger.info(connection.toString());
            redisComponent.getSysSettingsDto();
            logger.info(redisComponent.getSysSettingsDto().toString());
            logger.info("服务启动成功，可以开始愉快的开发了");
        } catch (Exception e) {
            logger.error("数据库或者redis设置失败，请检查配置");
            throw new BusinessException("服务启动失败");
        }
    }
}
