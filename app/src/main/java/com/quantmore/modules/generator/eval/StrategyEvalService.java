package com.quantmore.modules.generator.eval;

import com.quantmore.common.transaction.TransactionalExecutor;
import com.quantmore.modules.generator.dto.GenerateStrategyRequest;
import com.quantmore.modules.generator.dto.GenerateStrategyResponse;
import com.quantmore.modules.generator.repository.StrategyGenerationRepository;
import com.quantmore.modules.generator.service.StrategyGeneratorService;
import com.quantmore.modules.knowledgebase.model.VectorStatus;
import com.quantmore.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.quantmore.modules.user.model.UserEntity;
import com.quantmore.modules.user.model.UserPrincipal;
import com.quantmore.modules.user.model.UserRole;
import com.quantmore.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 策略生成评测编排：每个用例跑 RAG / no-RAG 两分支，逐分支做语法检查与 LLM 评委评分，
 * 最后聚合报告并清理本次评测产生的生成记录。不走 Controller，不受限流影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyEvalService {

  private static final long KB_POLL_INTERVAL_MS = 2000;

  private final StrategyGeneratorService generatorService;
  private final StrategyGenerationRepository generationRepository;
  private final UserRepository userRepository;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final PythonSyntaxCheckService syntaxService;
  private final EvalJudgeService judgeService;
  private final EvalProperties properties;
  private final TransactionalExecutor transactionalExecutor;

  public EvalReport.FullReport run(List<EvalCase> cases) {
    UserEntity admin = userRepository.findFirstByRoleOrderByIdAsc(UserRole.ADMIN)
        .orElseThrow(() -> new IllegalStateException("评测需要至少一个 ADMIN 用户"));
    UserPrincipal user = new UserPrincipal(admin.getId(), admin.getUsername(), admin.getRole());
    KbReadiness kb = waitForVectorReady();

    List<EvalReport.CaseResult> results = new ArrayList<>();
    List<Long> createdIds = new ArrayList<>();
    for (EvalCase caseMeta : cases) {
      EvalReport.BranchResult rag = runBranch(caseMeta, user, true);
      EvalReport.BranchResult noRag = runBranch(caseMeta, user, false);
      if (rag.generationId() != null) {
        createdIds.add(rag.generationId());
      }
      if (noRag.generationId() != null) {
        createdIds.add(noRag.generationId());
      }
      results.add(new EvalReport.CaseResult(caseMeta, rag, noRag));
      log.info("评测用例完成: id={}, rag={}, noRag={}",
          caseMeta.id(), branchBrief(rag), branchBrief(noRag));
    }

    cleanup(createdIds);

    String pythonVersion = syntaxService.pythonInfo().version();
    return new EvalReport.FullReport(
        LocalDateTime.now().toString(),
        admin.getUsername(),
        resolveGenerateProvider(),
        properties.getJudgeProvider(),
        pythonVersion,
        kb.ready(),
        (int) kb.completed(),
        (int) kb.failed(),
        results,
        EvalReport.EvalSummary.of(results, properties.getJudgePassScore())
    );
  }

  private EvalReport.BranchResult runBranch(EvalCase caseMeta, UserPrincipal user, boolean ragEnabled) {
    long start = System.currentTimeMillis();
    try {
      GenerateStrategyResponse response =
          generatorService.generateForUser(toRequest(caseMeta, ragEnabled), user);
      long elapsed = System.currentTimeMillis() - start;

      PythonSyntaxCheckService.SyntaxCheckResult syntax = syntaxService.check(response.code());
      EvalReport.BranchResult branch;
      try {
        EvalJudgeService.JudgeResult judge = judgeService.judge(caseMeta, response.code());
        branch = new EvalReport.BranchResult(
            ragEnabled, true, null, response.id(), elapsed, syntax, true, judge, null);
      } catch (Exception e) {
        branch = new EvalReport.BranchResult(
            ragEnabled, true, null, response.id(), elapsed, syntax, false, null, e.getMessage());
      }
      return branch;
    } catch (Exception e) {
      log.warn("评测分支失败: case={}, rag={}, error={}",
          caseMeta.id(), ragEnabled, e.getMessage(), e);
      long elapsed = System.currentTimeMillis() - start;
      return new EvalReport.BranchResult(
          ragEnabled, false, e.getMessage(), null, elapsed, null, false, null, null);
    }
  }

  private GenerateStrategyRequest toRequest(EvalCase caseMeta, boolean ragEnabled) {
    return new GenerateStrategyRequest(
        caseMeta.name(),
        caseMeta.market(),
        caseMeta.frequency(),
        caseMeta.buyConditions(),
        caseMeta.sellConditions(),
        caseMeta.riskControls(),
        ragEnabled ? null : List.of(),
        resolveGenerateProvider(),
        ragEnabled ? null : true
    );
  }

  private String resolveGenerateProvider() {
    String provider = properties.getGenerateProvider();
    return (provider == null || provider.isBlank()) ? null : provider;
  }

  /**
   * 等待所有知识库向量化进入终态（PENDING/PROCESSING 清零或超时）；
   * 无任何 COMPLETED 知识库视为致命错误。
   */
  private KbReadiness waitForVectorReady() {
    long deadline = System.currentTimeMillis() + properties.getVectorWaitTimeout().toMillis();
    boolean ready = false;
    while (System.currentTimeMillis() < deadline) {
      long pending = knowledgeBaseRepository.countByVectorStatus(VectorStatus.PENDING);
      long processing = knowledgeBaseRepository.countByVectorStatus(VectorStatus.PROCESSING);
      if (pending + processing == 0) {
        ready = true;
        break;
      }
      sleep(KB_POLL_INTERVAL_MS);
    }
    long completed = knowledgeBaseRepository.countByVectorStatus(VectorStatus.COMPLETED);
    long failed = knowledgeBaseRepository.countByVectorStatus(VectorStatus.FAILED);
    if (completed == 0) {
      throw new IllegalStateException(
          "无任何 COMPLETED 知识库，请先设置 APP_SEED_KB_DIR=docs 启动导入种子知识库");
    }
    if (!ready) {
      log.warn("等待向量化超时(仍有 PENDING/PROCESSING)，评测继续，RAG 分支可能检索不到内容");
    }
    if (failed > 0) {
      knowledgeBaseRepository.findByVectorStatusOrderByUploadedAtDesc(VectorStatus.FAILED)
          .forEach(kb -> log.warn("向量化 FAILED 知识库: id={}, name={}", kb.getId(), kb.getName()));
    }
    return new KbReadiness(ready, completed, failed);
  }

  private void cleanup(List<Long> ids) {
    if (ids.isEmpty() || !properties.isCleanupRecords()) {
      return;
    }
    try {
      transactionalExecutor.run(() -> generationRepository.deleteAllByIdInBatch(ids));
      log.info("评测生成记录已清理: count={}", ids.size());
    } catch (Exception e) {
      log.warn("评测生成记录清理失败: count={}, error={}", ids.size(), e.getMessage(), e);
    }
  }

  private String branchBrief(EvalReport.BranchResult branch) {
    if (!branch.generationOk()) {
      return "生成失败";
    }
    String syntax = branch.syntax() == null ? "-" : branch.syntax().status();
    if (!branch.judgeOk()) {
      return "语法" + syntax + "/评委失败";
    }
    return "语法" + syntax + "/评分" + branch.judge().score();
  }

  private void sleep(long millis) {
    try {
      TimeUnit.MILLISECONDS.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private record KbReadiness(boolean ready, long completed, long failed) {
  }
}
