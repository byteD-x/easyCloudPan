# Database 目录说明

`database/` 目录已不再存放可执行 SQL 初始化脚本。

当前项目统一使用 Flyway 管理数据库结构变更，迁移脚本位置：

```text
backend/src/main/resources/db/migration/
```

## 当前迁移脚本

- `V1__Initial_Schema.sql`：初始化基础表结构
- `V2__Add_Performance_Indexes_For_File_Cleanup.sql`：补充文件清理相关索引
- `V3__Add_Share_Table_And_Performance_Indexes.sql`：补充分享表与性能索引

## 规范

- 新增数据库变更必须新增 Flyway 脚本，禁止直接改线上/本地库结构后不留脚本。
- 命名格式：`V[版本]__[描述].sql`
- 已执行过的历史脚本不要修改，只追加新版本。

## 常用命令

在仓库根目录执行。

1. 启动数据库容器：
```bat
docker compose -f ops/docker/docker-compose.yml up -d postgres
```

2. 启动后端并自动迁移：
```bat
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

3. 查看迁移状态：
```bat
cd backend
mvn flyway:info
```

4. 手动执行迁移：
```bat
cd backend
mvn flyway:migrate
```

## 参考

- Flyway 官方文档：<https://flywaydb.org/documentation/>
- 项目协作规范：`AGENTS.md`
