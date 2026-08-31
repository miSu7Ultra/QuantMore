package com.quantmore.modules.generator.eval;

import com.quantmore.common.exception.BusinessException;
import com.quantmore.common.exception.ErrorCode;
import com.quantmore.common.transaction.TransactionalExecutor;
import com.quantmore.modules.generator.dto.GenerateStrategyRequest;
import com.quantmore.modules.generator.dto.GenerateStrategyResponse;
import com.quantmore.modules.generator.repository.StrategyGenerationRepository;
import com.quantmore.modules.generator.service.StrategyGeneratorService;
import com.quantmore.modules.knowledgebase.model.VectorStatus;
import com.quantmore.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.quantmore.modules.user.model.UserEntity;
import com.quantmore.modules.user.model.UserRole;
import com.quantmore.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StrategyEvalService 测试")
class StrategyEvalServiceTest {

  @Mock private StrategyGeneratorService generatorService;
  @Mock private StrategyGenerationRepository generationRepository;
  @Mock private UserRepository userRepository;
  @Mock private KnowledgeBaseRepository knowledgeBaseRepository;
  @Mock private PythonSyntaxCheckService syntaxService;
  @Mock private EvalJudgeService judgeService;
  @Mock private TransactionalExecutor transactionalExecutor;

  private EvalProperties properties;
  private StrategyEvalService service;

  @BeforeEach
  void setUp() {
    properties = new EvalProperties();
    properties.setVectorWaitTimeout(Duration.ofSeconds(1));
    service = new StrategyEvalService(
        generatorService, generationRepository, userRepository, knowledgeBaseRepository,
        syntaxService, judgeService, properties, transactionalExecutor);
  }

  private EvalCase caseMeta(String id) {
    return new EvalCase(id, "策略" + id, "STOCK", "DAILY", "金叉买入", "死叉卖出", "", "SIMPLE");
  }

  private UserEntity admin() {
    UserEntity admin = new UserEntity();
    admin.setId(1L);
    admin.setUsername("admin");
    admin.setRole(UserRole.ADMIN);
    return admin;
  }

  private void stubReadyKb() {
    when(userRepository.findFirstByRoleOrderByIdAsc(UserRole.ADMIN))
        .thenReturn(Optional.of(admin()));
    when(knowledgeBaseRepository.countByVectorStatus(VectorStatus.PENDING)).thenReturn(0L);
    when(knowledgeBaseRepository.countByVectorStatus(VectorStatus.PROCESSING)).thenReturn(0L);
    when(knowledgeBaseRepository.countByVectorStatus(VectorStatus.COMPLETED)).thenReturn(1L);
    when(knowledgeBaseRepository.countByVectorStatus(VectorStatus.FAILED)).thenReturn(0L);
  }

  private GenerateStrategyResponse response() {
    return new GenerateStrategyResponse(
        1L, "策略", "策略.py", "STOCK", "DAILY", "def initialize(context):\n    pass\n",
        "说明", null, LocalDateTime.now());
  }

  @Test
  @DisplayName("每个用例跑 RAG 与 no-RAG 两分支,skipRetrieval 分别为 null/true,共清理 2 条记录")
  void runsBothBranchesPerCase() {
    stubReadyKb();
    when(syntaxService.pythonInfo())
        .thenReturn(new PythonSyntaxCheckService.PythonInfo(true, "Python 3.9.6"));
    when(syntaxService.check(anyString()))
        .thenReturn(new PythonSyntaxCheckService.SyntaxCheckResult("PASS", "", "3.9", List.of()));
    when(judgeService.judge(any(), anyString()))
        .thenReturn(new EvalJudgeService.JudgeResult(85, true, List.of()));
    when(generatorService.generateForUser(any(), any())).thenReturn(response());

    EvalReport.FullReport report = service.run(List.of(caseMeta("s01")));

    assertThat(report.results()).hasSize(1);
    assertThat(report.kbReady()).isTrue();
    assertThat(report.kbCompleted()).isEqualTo(1);
    assertThat(report.summary().ragPassed()).isEqualTo(1);
    assertThat(report.summary().noRagPassed()).isEqualTo(1);

    ArgumentCaptor<GenerateStrategyRequest> reqCaptor =
        ArgumentCaptor.forClass(GenerateStrategyRequest.class);
    verify(generatorService, times(2)).generateForUser(reqCaptor.capture(), any());
    assertThat(reqCaptor.getAllValues().get(0).skipRetrieval()).isNull();
    assertThat(reqCaptor.getAllValues().get(1).skipRetrieval()).isTrue();

    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(transactionalExecutor).run(runnableCaptor.capture());
    runnableCaptor.getValue().run();
    verify(generationRepository).deleteAllByIdInBatch(List.of(1L, 1L));
  }

  @Test
  @DisplayName("分支生成异常记为 generationFailed,不中断后续用例,该分支不做语法与评委")
  void generationFailureDoesNotInterrupt() {
    stubReadyKb();
    when(syntaxService.pythonInfo())
        .thenReturn(new PythonSyntaxCheckService.PythonInfo(true, "Python 3.9.6"));
    when(syntaxService.check(anyString()))
        .thenReturn(new PythonSyntaxCheckService.SyntaxCheckResult("PASS", "", "3.9", List.of()));
    when(judgeService.judge(any(), anyString()))
        .thenReturn(new EvalJudgeService.JudgeResult(80, true, List.of()));
    when(generatorService.generateForUser(any(), any()))
        .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR, "boom"))
        .thenReturn(response());

    EvalReport.FullReport report = service.run(List.of(caseMeta("s01")));

    assertThat(report.summary().generationFailures()).isEqualTo(1);
    assertThat(report.summary().judgeFailures()).isEqualTo(0);
    verify(syntaxService, times(1)).check(anyString());
    verify(judgeService, times(1)).judge(any(), anyString());
  }

  @Test
  @DisplayName("评委失败记为 judgeFailed,不影响统计完成")
  void judgeFailureIsRecorded() {
    stubReadyKb();
    when(syntaxService.pythonInfo())
        .thenReturn(new PythonSyntaxCheckService.PythonInfo(true, "Python 3.9.6"));
    when(syntaxService.check(anyString()))
        .thenReturn(new PythonSyntaxCheckService.SyntaxCheckResult("PASS", "", "3.9", List.of()));
    when(judgeService.judge(any(), anyString()))
        .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR, "评委失败"));
    when(generatorService.generateForUser(any(), any())).thenReturn(response());

    EvalReport.FullReport report = service.run(List.of(caseMeta("s01")));

    assertThat(report.summary().judgeFailures()).isEqualTo(2);
    assertThat(report.summary().ragPassed()).isEqualTo(0);
  }

  @Test
  @DisplayName("无 ADMIN 用户抛 IllegalStateException")
  void noAdminThrows() {
    when(userRepository.findFirstByRoleOrderByIdAsc(UserRole.ADMIN)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.run(List.of(caseMeta("s01"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN");
  }

  @Test
  @DisplayName("无 COMPLETED 知识库抛 IllegalStateException")
  void noCompletedKbThrows() {
    when(userRepository.findFirstByRoleOrderByIdAsc(UserRole.ADMIN))
        .thenReturn(Optional.of(admin()));
    when(knowledgeBaseRepository.countByVectorStatus(VectorStatus.PENDING)).thenReturn(0L);
    when(knowledgeBaseRepository.countByVectorStatus(VectorStatus.PROCESSING)).thenReturn(0L);
    when(knowledgeBaseRepository.countByVectorStatus(VectorStatus.COMPLETED)).thenReturn(0L);

    assertThatThrownBy(() -> service.run(List.of(caseMeta("s01"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("COMPLETED");
  }
}
