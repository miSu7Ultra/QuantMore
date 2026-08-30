package com.quantmore.infrastructure.file;

import com.quantmore.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DocumentParseService 测试类
 * 
 * 测试覆盖：
 * 1. 文本文件解析（TXT、MD）
 * 2. 字节数组解析
 * 3. 异常处理
 * 4. 文本清理集成
 * 5. 边界条件
 */
@DisplayName("文档解析服务测试")
class DocumentParseServiceTest {

    private DocumentParseService documentParseService;

    @Mock
    private TextCleaningService textCleaningService;

    @Mock
    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        documentParseService = new DocumentParseService(textCleaningService);

        // 默认行为：TextCleaningService 直接返回输入（单元测试关注 DocumentParseService 逻辑）
        when(textCleaningService.cleanText(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("解析简单文本文件 - TXT")
    void testParseTxtFile() throws Exception {
        // Given: 准备一个简单的 TXT 文件
        String content = "这是一份文档\n姓名：张三\n技能：Java、Python";
        MultipartFile file = new MockMultipartFile(
            "file",
            "resume.txt",
            "text/plain",
            content.getBytes(StandardCharsets.UTF_8)
        );

        // When: 解析文件
        String result = documentParseService.parseContent(file);

        // Then: 验证结果
        assertNotNull(result);
        assertTrue(result.contains("张三"));
        assertTrue(result.contains("Java"));
        verify(textCleaningService, times(1)).cleanText(anyString());
    }

    @Test
    @DisplayName("解析 Markdown 文件")
    void testParseMarkdownFile() throws Exception {
        // Given: Markdown 内容
        String content = """
            # 技术文档
            
            ## 基本信息
            - 姓名：李四
            - 邮箱：lisi@example.com
            
            ## 工作经验
            2020-2023 某公司 - 高级工程师
            """;
        
        MultipartFile file = new MockMultipartFile(
            "file",
            "resume.md",
            "text/markdown",
            content.getBytes(StandardCharsets.UTF_8)
        );

        // When
        String result = documentParseService.parseContent(file);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("技术文档"));
        assertTrue(result.contains("李四"));
        assertTrue(result.contains("高级工程师"));
    }

    @Test
    @DisplayName("解析字节数组 - 带文件名")
    void testParseFromByteArray() throws Exception {
        // Given
        String content = "测试内容\n第二行";
        byte[] fileBytes = content.getBytes(StandardCharsets.UTF_8);
        String fileName = "test.txt";

        // When
        String result = documentParseService.parseContent(fileBytes, fileName);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("测试内容"));
        verify(textCleaningService, times(1)).cleanText(anyString());
    }

    @Test
    @DisplayName("解析空文件 - 应返回空字符串")
    void testParseEmptyFile() throws Exception {
        // Given: 空文件
        MultipartFile file = new MockMultipartFile(
            "file",
            "empty.txt",
            "text/plain",
            new byte[0]
        );

        // When
        when(textCleaningService.cleanText(anyString())).thenReturn("");
        String result = documentParseService.parseContent(file);

        // Then
        assertEquals("", result);
    }

    @Test
    @DisplayName("解析包含特殊字符的文件")
    void testParseFileWithSpecialCharacters() throws Exception {
        // Given: 包含各种特殊字符
        String content = """
            姓名：张三 👨‍💻
            技能：Java、Python、Go
            邮箱：zhangsan@example.com
            GitHub：https://github.com/zhangsan
            
            ==================
            
            个人简介：
            - 精通 Spring Boot
            - 熟悉 Docker & Kubernetes
            """;
        
        MultipartFile file = new MockMultipartFile(
            "file",
            "resume_special.txt",
            "text/plain",
            content.getBytes(StandardCharsets.UTF_8)
        );

        // When
        String result = documentParseService.parseContent(file);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("张三"));
        assertTrue(result.contains("Spring Boot"));
    }

    @Test
    @DisplayName("解析失败 - IO 异常")
    void testParseFailureWithIOException() throws Exception {
        // Given: 创建一个会抛出异常的 MultipartFile
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("error.txt");
        when(file.isEmpty()).thenReturn(false);  // 文件不为空
        when(file.getSize()).thenReturn(1024L);  // 文件有内容
        when(file.getInputStream()).thenThrow(new IOException("IO Error"));

        // When & Then
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> documentParseService.parseContent(file)
        );
        
        assertTrue(exception.getMessage().contains("文件解析失败"));
    }

    @Test
    @DisplayName("解析中文文档内容")
    void testParseChineseResume() throws Exception {
        // Given: 中文文档
        String content = """
            技术文档
            
            姓名：王五
            性别：男
            年龄：28
            学历：本科
            专业：计算机科学与技术
            
            工作经验：
            2018-2021 ABC公司 软件工程师
            - 负责后端开发
            - 使用 Spring Boot、MySQL、Redis
            
            2021-2024 XYZ公司 高级工程师
            - 架构设计和技术选型
            - 团队管理
            
            项目经验：
            1. 电商平台（2019-2020）
               技术栈：Spring Cloud、MySQL、RabbitMQ
               
            2. 支付系统（2021-2022）
               技术栈：Spring Boot、PostgreSQL、Kafka
            """;
        
        MultipartFile file = new MockMultipartFile(
            "file",
            "文档示例.txt",
            "text/plain",
            content.getBytes(StandardCharsets.UTF_8)
        );

        // When
        String result = documentParseService.parseContent(file);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("王五"));
        assertTrue(result.contains("Spring Boot"));
        assertTrue(result.contains("电商平台"));
        assertTrue(result.contains("支付系统"));
    }

    @Test
    @DisplayName("下载并解析文件 - 成功")
    void testDownloadAndParseContent() throws Exception {
        // Given
        String storageKey = "resumes/test-resume.txt";
        String originalFilename = "test-resume.txt";
        String content = "文档内容";
        byte[] fileBytes = content.getBytes(StandardCharsets.UTF_8);
        
        when(fileStorageService.downloadFile(storageKey)).thenReturn(fileBytes);

        // When
        String result = documentParseService.downloadAndParseContent(
            fileStorageService,
            storageKey,
            originalFilename
        );

        // Then
        assertNotNull(result);
        verify(fileStorageService, times(1)).downloadFile(storageKey);
        verify(textCleaningService, times(1)).cleanText(anyString());
    }

    @Test
    @DisplayName("下载并解析文件 - 下载失败")
    void testDownloadAndParseContentFailure() {
        // Given
        String storageKey = "resumes/missing.txt";
        String originalFilename = "missing.txt";
        
        when(fileStorageService.downloadFile(storageKey)).thenReturn(null);

        // When & Then
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> documentParseService.downloadAndParseContent(
                fileStorageService,
                storageKey,
                originalFilename
            )
        );
        
        assertTrue(exception.getMessage().contains("下载文件失败"));
    }

    @Test
    @DisplayName("下载并解析文件 - 空字节数组")
    void testDownloadAndParseEmptyContent() {
        // Given
        String storageKey = "resumes/empty.txt";
        String originalFilename = "empty.txt";
        
        when(fileStorageService.downloadFile(storageKey)).thenReturn(new byte[0]);

        // When & Then
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> documentParseService.downloadAndParseContent(
                fileStorageService,
                storageKey,
                originalFilename
            )
        );
        
        assertTrue(exception.getMessage().contains("下载文件失败"));
    }

    @Test
    @DisplayName("验证文本清理服务被调用")
    void testTextCleaningServiceIsCalled() throws Exception {
        // Given
        String rawContent = "原始内容\n\n\n\n多个空行";
        MultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            rawContent.getBytes(StandardCharsets.UTF_8)
        );

        String expectedCleanedContent = "原始内容\n\n多个空行";
        when(textCleaningService.cleanText(anyString())).thenReturn(expectedCleanedContent);

        // When
        String result = documentParseService.parseContent(file);

        // Then
        assertEquals(expectedCleanedContent, result);
        verify(textCleaningService, times(1)).cleanText(anyString());
    }

    @Test
    @DisplayName("解析包含 URL 的文档")
    void testParseDocumentWithUrls() throws Exception {
        // Given
        String content = """
            个人博客：https://blog.example.com
            GitHub：https://github.com/user
            LinkedIn：https://linkedin.com/in/user
            
            项目地址：https://github.com/user/project
            """;
        
        MultipartFile file = new MockMultipartFile(
            "file",
            "profile.txt",
            "text/plain",
            content.getBytes(StandardCharsets.UTF_8)
        );

        // When
        String result = documentParseService.parseContent(file);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("blog.example.com") || 
                   result.contains("github.com"));
    }

    /**
     * 集成测试：创建真实文件并测试
     */
    @Test
    @DisplayName("集成测试 - 真实文件解析")
    void testIntegrationWithRealFile(@TempDir Path tempDir) throws Exception {
        // Given: 创建临时文件
        Path testFile = tempDir.resolve("test-resume.txt");
        String content = """
            张三的文档
            ============
            
            教育背景
            --------
            2015-2019  清华大学  计算机科学与技术  本科
            
            工作经验
            --------
            2019-2022  腾讯  后端工程师
            2022-至今  字节跳动  高级后端工程师
            
            技能清单
            --------
            - 编程语言：Java、Python、Go
            - 框架：Spring Boot、Django、Gin
            - 数据库：MySQL、PostgreSQL、Redis
            - 中间件：Kafka、RabbitMQ、RocketMQ
            """;
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        // 创建 MultipartFile
        byte[] fileBytes = Files.readAllBytes(testFile);
        MultipartFile file = new MockMultipartFile(
            "file",
            "test-resume.txt",
            "text/plain",
            fileBytes
        );

        // 使用真实的 TextCleaningService
        TextCleaningService realCleaningService = new TextCleaningService();
        DocumentParseService realService = new DocumentParseService(realCleaningService);

        // When
        String result = realService.parseContent(file);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("张三"));
        assertTrue(result.contains("清华大学"));
        assertTrue(result.contains("Spring Boot"));
        
        // 验证分隔线被清理
        assertFalse(result.contains("============"));
        assertFalse(result.contains("--------"));
    }

}
