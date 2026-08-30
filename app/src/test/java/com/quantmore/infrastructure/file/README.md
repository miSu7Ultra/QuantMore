# 文件基础设施测试说明

本目录包含文档解析、文本清洗与文件存储的单元/集成测试:

- `DocumentParseServiceTest` / `DocumentParseIntegrationTest` - Tika 文档解析
- `TextCleaningServiceTest` - 文本清洗
- `FileStorageServiceTest` - S3(MinIO)文件存储

样例文件位于 `app/src/test/resources/test-files/`。
