package com.quantmore.infrastructure.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentParseService 集成测试
 * 使用真实的文件和服务进行端到端测试
 */
@Tag("integration")
@DisplayName("文档解析服务集成测试")
class DocumentParseIntegrationTest {

    private DocumentParseService documentParseService;
    private TextCleaningService textCleaningService;

    @BeforeEach
    void setUp() {
        // 使用真实的服务实例
        textCleaningService = new TextCleaningService();
        documentParseService = new DocumentParseService(textCleaningService);
    }

    @Test
    @DisplayName("集成测试 - 解析 TXT 格式简历")
    void testParseTxtResume() throws IOException {
        // Given: 从测试资源加载文件
        InputStream inputStream = getClass().getResourceAsStream("/test-files/sample-resume.txt");
        assertNotNull(inputStream, "测试文件不存在");

        byte[] content = inputStream.readAllBytes();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample-resume.txt",
            "text/plain",
            content
        );

        // When: 解析文件
        String result = documentParseService.parseContent(file);

        // Then: 验证解析结果
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // 验证基本信息
        assertTrue(result.contains("张三"), "应包含姓名");
        assertTrue(result.contains("zhangsan@example.com"), "应包含邮箱");
        
        // 验证教育背景
        assertTrue(result.contains("清华大学"), "应包含教育背景");
        assertTrue(result.contains("计算机科学与技术"), "应包含专业");
        
        // 验证工作经验
        assertTrue(result.contains("字节跳动"), "应包含工作单位");
        assertTrue(result.contains("腾讯"), "应包含工作单位");
        assertTrue(result.contains("高级后端工程师"), "应包含职位");
        
        // 验证技术栈
        assertTrue(result.contains("Java"), "应包含技术栈");
        assertTrue(result.contains("Spring Boot"), "应包含技术栈");
        assertTrue(result.contains("Redis"), "应包含技术栈");
        
        // 验证项目经验
        assertTrue(result.contains("推荐系统重构项目"), "应包含项目名称");
        assertTrue(result.contains("支付系统开发"), "应包含项目名称");
        
        // 验证分隔线已被清理
        assertFalse(result.contains("===================="), "分隔线应被清理");
        assertFalse(result.contains("--------"), "分隔线应被清理");
    }

    @Test
    @DisplayName("集成测试 - 解析 Markdown 格式简历")
    void testParseMarkdownResume() throws IOException {
        // Given: 从测试资源加载 Markdown 文件
        InputStream inputStream = getClass().getResourceAsStream("/test-files/sample-resume.md");
        assertNotNull(inputStream, "测试文件不存在");

        byte[] content = inputStream.readAllBytes();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample-resume.md",
            "text/markdown",
            content
        );

        // When: 解析文件
        String result = documentParseService.parseContent(file);

        // Then: 验证解析结果
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // 验证基本信息
        assertTrue(result.contains("李四"), "应包含姓名");
        assertTrue(result.contains("全栈工程师"), "应包含职位");
        assertTrue(result.contains("lisi@example.com"), "应包含邮箱");
        
        // 验证教育背景
        assertTrue(result.contains("北京大学"), "应包含教育背景");
        assertTrue(result.contains("软件工程"), "应包含专业");
        assertTrue(result.contains("硕士"), "应包含学历");
        
        // 验证工作经历
        assertTrue(result.contains("阿里巴巴"), "应包含工作单位");
        assertTrue(result.contains("美团"), "应包含工作单位");
        
        // 验证项目经验
        assertTrue(result.contains("智能客服系统"), "应包含项目名称");
        assertTrue(result.contains("电商数据分析平台"), "应包含项目名称");
        assertTrue(result.contains("微服务框架脚手架"), "应包含项目名称");
        
        // 验证技术栈
        assertTrue(result.contains("React"), "应包含前端技术");
        assertTrue(result.contains("TypeScript"), "应包含语言");
        assertTrue(result.contains("Spring Boot"), "应包含后端框架");
        assertTrue(result.contains("Kubernetes"), "应包含 DevOps 工具");
        
        // 验证荣誉证书
        assertTrue(result.contains("AWS Certified"), "应包含证书");
        assertTrue(result.contains("ICPC"), "应包含竞赛荣誉");
        
        // 验证 Markdown 特定内容
        // Emoji 可能被保留或转换，取决于 Tika 的处理
        assertTrue(result.contains("联系方式") || result.contains("邮箱"),
            "应包含联系方式部分");
    }

    @Test
    @DisplayName("集成测试 - 解析包含特殊字符的文本")
    void testParseTextWithSpecialCharacters() {
        // Given: 包含各种特殊字符的文本
        String content = """
            姓名：王五 🧑‍💻
            
            联系方式：
            📧 wangwu@example.com
            📱 139-0000-0000
            🏠 北京市海淀区
            
            技能清单：
            ✓ Java / Spring Boot
            ✓ Python / Django
            ✓ JavaScript / React
            
            工作经历：
            2020 → 2023  某科技公司
            - 负责后端开发
            - 性能优化 (50ms → 20ms)
            - 代码覆盖率 30% → 85%
            
            GitHub: https://github.com/wangwu
            个人网站: https://wangwu.dev
            
            备注：C++、C# 等语言也有涉猎
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "special-chars.txt",
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
        assertTrue(result.contains("github.com"));
    }

    @Test
    @DisplayName("集成测试 - 验证文本清理效果")
    void testTextCleaningIntegration() {
        // Given: 包含需要清理的内容
        String dirtyContent = """
            个人简历
            ============
            
            
            
            姓名：赵六
            
            
            工作经验
            --------
            
            2020-2023  某公司  工程师    
            
            
            技能清单
            ========
            
            - Java
            - Python    
            
            
            
            image123.png
            file:///tmp/tika-temp.html?query=123
            https://example.com/image.png
            
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "dirty.txt",
            "text/plain",
            dirtyContent.getBytes(StandardCharsets.UTF_8)
        );

        // When
        String result = documentParseService.parseContent(file);

        // Then
        assertNotNull(result);
        
        // 验证分隔线被清理
        assertFalse(result.contains("============"));
        assertFalse(result.contains("--------"));
        assertFalse(result.contains("========"));
        
        // 验证图片文件名被清理
        assertFalse(result.contains("image123.png"));
        
        // 验证临时文件路径被清理
        assertFalse(result.contains("file:///"));
        
        // 验证图片链接被清理
        assertFalse(result.contains("https://example.com/image.png"));
        
        // 验证有效内容保留
        assertTrue(result.contains("赵六"));
        assertTrue(result.contains("Java"));
        assertTrue(result.contains("某公司"));
        
        // 验证连续空行被压缩
        assertFalse(result.contains("\n\n\n"), "不应有超过2个连续换行符");
    }

    @Test
    @DisplayName("集成测试 - 解析多语言混合文本")
    void testParseMultilingualText() {
        // Given: 中英文混合
        String content = """
            Resume of John Zhang (张强)
            
            Personal Information
            Name: John Zhang / 张强
            Email: john.zhang@example.com
            Location: Beijing, China / 中国北京
            
            Education
            Master of Computer Science, Peking University
            北京大学 计算机科学 硕士
            
            Work Experience
            2020-2023  Google Beijing  Software Engineer
            2020-2023  谷歌北京  软件工程师
            
            Skills
            - Programming Languages: Java, Python, Go
            - 编程语言：Java、Python、Go
            
            - Frameworks: Spring Boot, Django, Gin
            - 框架：Spring Boot、Django、Gin
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "multilingual.txt",
            "text/plain",
            content.getBytes(StandardCharsets.UTF_8)
        );

        // When
        String result = documentParseService.parseContent(file);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("John Zhang"));
        assertTrue(result.contains("张强"));
        assertTrue(result.contains("Peking University"));
        assertTrue(result.contains("北京大学"));
        assertTrue(result.contains("Google Beijing"));
        assertTrue(result.contains("谷歌北京"));
        assertTrue(result.contains("Spring Boot"));
    }

    @Test
    @DisplayName("性能测试 - 大文件解析")
    void testLargeFilePerformance() {
        // Given: 生成大文本（约 50KB）
        StringBuilder largeContent = new StringBuilder();
        largeContent.append("个人简历\n\n");

        for (int i = 0; i < 100; i++) {
            largeContent.append("工作经历 ").append(i).append("\n");
            largeContent.append("2020-2023  公司名称  职位\n");
            largeContent.append("- 负责系统架构设计和开发\n");
            largeContent.append("- 使用 Java、Spring Boot、MySQL、Redis 等技术\n");
            largeContent.append("- 优化系统性能，提升响应速度\n");
            largeContent.append("- 参与代码审查和技术分享\n\n");
        }

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "large-resume.txt",
            "text/plain",
            largeContent.toString().getBytes(StandardCharsets.UTF_8)
        );

        // When: 解析文件
        String result = documentParseService.parseContent(file);

        // Then: 验证解析成功
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("个人简历"));
        assertTrue(result.contains("工作经历"));
    }

    @Test
    @DisplayName("边界测试 - 空内容处理")
    void testEmptyContentHandling() {
        // Given: 各种空内容情况
        String[] emptyContents = {
            "",
            " ",
            "\n",
            "\n\n\n",
            "   \n   \n   ",
            "\t\t\t"
        };

        for (String content : emptyContents) {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8)
            );

            // When
            String result = documentParseService.parseContent(file);

            // Then: 应返回空字符串或仅包含少量空白字符
            assertNotNull(result);
            assertTrue(result.isEmpty() || result.isBlank(), 
                "空内容应返回空或空白字符串，实际: '" + result + "'");
        }
    }

    @Test
    @DisplayName("边界测试 - 只有噪音的文档")
    void testNoiseOnlyDocument() {
        // Given: 只包含需要清理的内容
        String noiseContent = """
            ============
            --------
            ========
            
            image001.png
            image002.jpg
            image003.jpeg
            
            file:///tmp/temp.html
            https://example.com/pic1.png
            https://example.com/pic2.jpg
            
            ============
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "noise-only.txt",
            "text/plain",
            noiseContent.getBytes(StandardCharsets.UTF_8)
        );

        // When
        String result = documentParseService.parseContent(file);

        // Then: 所有噪音被清理后，应该是空的或只有少量空白
        assertNotNull(result);
        assertTrue(result.isEmpty() || result.isBlank(),
            "纯噪音文档清理后应为空，实际: '" + result + "'");
    }
}
