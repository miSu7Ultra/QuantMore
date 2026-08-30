package com.quantmore.modules.knowledgebase.service;

import com.quantmore.common.exception.BusinessException;
import com.quantmore.common.exception.ErrorCode;
import com.quantmore.infrastructure.mapper.KnowledgeBaseMapper;
import com.quantmore.infrastructure.mapper.RagChatMapper;
import com.quantmore.modules.knowledgebase.model.KbVisibility;
import com.quantmore.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.quantmore.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import com.quantmore.modules.knowledgebase.model.RagChatDTO.CreateSessionRequest;
import com.quantmore.modules.knowledgebase.model.RagChatDTO.SessionDTO;
import com.quantmore.modules.knowledgebase.model.RagChatDTO.SessionDetailDTO;
import com.quantmore.modules.knowledgebase.model.RagChatDTO.SessionListItemDTO;
import com.quantmore.modules.knowledgebase.model.RagChatMessageEntity;
import com.quantmore.modules.knowledgebase.model.RagChatSessionEntity;
import com.quantmore.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.quantmore.modules.knowledgebase.repository.RagChatMessageRepository;
import com.quantmore.modules.knowledgebase.repository.RagChatSessionRepository;
import com.quantmore.modules.user.model.UserPrincipal;
import com.quantmore.modules.user.model.UserRole;
import com.quantmore.modules.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.List;

/**
 * RAG 聊天会话服务
 * 提供RAG聊天会话的创建、获取、更新、删除等操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatSessionService {

    private final RagChatSessionRepository sessionRepository;
    private final RagChatMessageRepository messageRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseQueryService queryService;
    private final RagChatMapper ragChatMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseQueryProperties queryProperties;
    private final CurrentUserService currentUserService;

    /**
     * 创建新会话（校验知识库可见性，会话归属当前用户）
     */
    @Transactional
    public SessionDTO createSession(CreateSessionRequest request) {
        UserPrincipal user = currentUserService.get();
        // 验证知识库存在且对当前用户可见
        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository
            .findAllById(request.knowledgeBaseIds());

        if (knowledgeBases.size() != request.knowledgeBaseIds().size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部分知识库不存在");
        }
        if (user.role() != UserRole.ADMIN) {
            for (KnowledgeBaseEntity kb : knowledgeBases) {
                if (kb.getVisibility() == KbVisibility.PRIVATE && !user.id().equals(kb.getOwnerId())) {
                    throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN,
                        "知识库不可见或不存在: " + kb.getId());
                }
            }
        }

        // 创建会话
        RagChatSessionEntity session = new RagChatSessionEntity();
        session.setUserId(user.id());
        session.setTitle(request.title() != null && !request.title().isBlank()
            ? request.title()
            : generateTitle(knowledgeBases));
        session.setKnowledgeBases(new HashSet<>(knowledgeBases));

        session = sessionRepository.save(session);

        log.info("创建 RAG 聊天会话: id={}, title={}, userId={}", session.getId(), session.getTitle(), user.id());

        return ragChatMapper.toSessionDTO(session);
    }

    /**
     * 获取会话列表（仅当前用户）
     */
    public List<SessionListItemDTO> listSessions() {
        return sessionRepository.findAllByUserIdOrderByPinnedAndUpdatedAtDesc(
                currentUserService.get().id())
            .stream()
            .map(ragChatMapper::toSessionListItemDTO)
            .toList();
    }

    /**
     * 获取会话详情（包含消息）
     * 分两次查询避免笛卡尔积问题
     */
    public SessionDetailDTO getSessionDetail(Long sessionId) {
        // 先加载会话和知识库
        RagChatSessionEntity session = sessionRepository
            .findByIdWithKnowledgeBases(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
        assertOwnership(session);

        // 再单独加载消息（避免笛卡尔积）
        List<RagChatMessageEntity> messages = messageRepository
            .findBySessionIdOrderByMessageOrderAsc(sessionId);

        // 转换知识库列表
        List<KnowledgeBaseListItemDTO> kbDTOs = knowledgeBaseMapper.toListItemDTOList(
            new java.util.ArrayList<>(session.getKnowledgeBases())
        );

        return ragChatMapper.toSessionDetailDTO(session, messages, kbDTOs);
    }

    /**
     * 准备流式消息（保存用户消息，创建 AI 消息占位）
     *
     * @return AI 消息的 ID
     */
    @Transactional
    public Long prepareStreamMessage(Long sessionId, String question) {
        RagChatSessionEntity session = sessionRepository.findByIdWithKnowledgeBases(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
        assertOwnership(session);

        // 获取当前消息数量作为起始顺序
        int nextOrder = session.getMessageCount();

        // 保存用户消息
        RagChatMessageEntity userMessage = new RagChatMessageEntity();
        userMessage.setSession(session);
        userMessage.setType(RagChatMessageEntity.MessageType.USER);
        userMessage.setContent(question);
        userMessage.setMessageOrder(nextOrder);
        userMessage.setCompleted(true);
        messageRepository.save(userMessage);

        // 创建 AI 消息占位（未完成）
        RagChatMessageEntity assistantMessage = new RagChatMessageEntity();
        assistantMessage.setSession(session);
        assistantMessage.setType(RagChatMessageEntity.MessageType.ASSISTANT);
        assistantMessage.setContent("");
        assistantMessage.setMessageOrder(nextOrder + 1);
        assistantMessage.setCompleted(false);
        assistantMessage = messageRepository.save(assistantMessage);

        // 更新会话消息数量
        session.setMessageCount(nextOrder + 2);
        sessionRepository.save(session);

        log.info("准备流式消息: sessionId={}, messageId={}", sessionId, assistantMessage.getId());

        return assistantMessage.getId();
    }

    /**
     * 流式响应完成后更新消息
     */
    @Transactional
    public void completeStreamMessage(Long messageId, String content) {
        RagChatMessageEntity message = messageRepository.findById(messageId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "消息不存在"));
        assertOwnership(message.getSession());

        message.setContent(content);
        message.setCompleted(true);
        messageRepository.save(message);

        log.info("完成流式消息: messageId={}, contentLength={}", messageId, content.length());
    }

    /**
     * 获取流式回答（带多轮上下文）
     */
    public Flux<String> getStreamAnswer(Long sessionId, String question) {
        RagChatSessionEntity session = sessionRepository.findByIdWithKnowledgeBases(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
        assertOwnership(session);

        List<Long> kbIds = session.getKnowledgeBaseIds();
        List<Message> history = queryProperties.getHistory().isEnabled()
            ? loadHistoryMessages(sessionId) : List.of();

        log.info("加载历史上下文: sessionId={}, historySize={}", sessionId, history.size());
        return queryService.answerQuestionStream(kbIds, question, history);
    }

    /**
     * 更新会话标题
     */
    @Transactional
    public void updateSessionTitle(Long sessionId, String title) {
        RagChatSessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
        assertOwnership(session);

        session.setTitle(title);
        sessionRepository.save(session);

        log.info("更新会话标题: sessionId={}, title={}", sessionId, title);
    }

    /**
     * 切换会话置顶状态
     */
    @Transactional
    public void togglePin(Long sessionId) {
        RagChatSessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
        assertOwnership(session);

        // 处理 null 值（兼容旧数据）
        Boolean currentPinned = session.getIsPinned() != null ? session.getIsPinned() : false;
        session.setIsPinned(!currentPinned);
        sessionRepository.save(session);

        log.info("切换会话置顶状态: sessionId={}, isPinned={}", sessionId, session.getIsPinned());
    }

    /**
     * 更新会话的知识库关联
     */
    @Transactional
    public void updateSessionKnowledgeBases(Long sessionId, List<Long> knowledgeBaseIds) {
        RagChatSessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
        assertOwnership(session);

        UserPrincipal user = currentUserService.get();
        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository
            .findAllById(knowledgeBaseIds);
        if (knowledgeBases.size() != knowledgeBaseIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部分知识库不存在");
        }
        if (user.role() != UserRole.ADMIN) {
            for (KnowledgeBaseEntity kb : knowledgeBases) {
                if (kb.getVisibility() == KbVisibility.PRIVATE && !user.id().equals(kb.getOwnerId())) {
                    throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN,
                        "知识库不可见或不存在: " + kb.getId());
                }
            }
        }

        session.setKnowledgeBases(new HashSet<>(knowledgeBases));
        sessionRepository.save(session);

        log.info("更新会话知识库: sessionId={}, kbIds={}", sessionId, knowledgeBaseIds);
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        RagChatSessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
        assertOwnership(session);
        sessionRepository.deleteById(sessionId);

        log.info("删除会话: sessionId={}", sessionId);
    }

    // ========== 私有方法 ==========

    /**
     * 会话归属校验：仅 owner 或管理员
     */
    private void assertOwnership(RagChatSessionEntity session) {
        UserPrincipal user = currentUserService.get();
        if (user.role() == UserRole.ADMIN) {
            return;
        }
        if (!user.id().equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该会话");
        }
    }

    /**
     * 加载会话中最近的历史消息作为多轮上下文。
     * 排除当前轮的 user 消息（prepareStreamMessage 中 completed=true 但尚未回答）。
     */
    private List<Message> loadHistoryMessages(Long sessionId) {
        int limit = queryProperties.getHistory().getMaxMessages() + 1;
        List<RagChatMessageEntity> recent = messageRepository
            .findRecentCompletedBySessionId(sessionId, PageRequest.of(0, limit));

        if (recent.isEmpty()) {
            return List.of();
        }

        // 查询结果按 messageOrder DESC 排列，最后一条（DESC 首条）是当前轮的 user 消息，排除
        List<RagChatMessageEntity> historyMessages = recent.size() <= 1
            ? List.of()
            : recent.subList(1, recent.size());

        // 反转为正序（时间从早到晚）
        return historyMessages.reversed().stream()
            .map(m -> m.getType() == RagChatMessageEntity.MessageType.USER
                ? (Message) new UserMessage(m.getContent())
                : (Message) new AssistantMessage(m.getContent()))
            .toList();
    }

    private String generateTitle(List<KnowledgeBaseEntity> knowledgeBases) {
        if (knowledgeBases.isEmpty()) {
            return "新对话";
        }
        if (knowledgeBases.size() == 1) {
            return knowledgeBases.getFirst().getName();
        }
        return knowledgeBases.size() + " 个知识库对话";
    }
}
