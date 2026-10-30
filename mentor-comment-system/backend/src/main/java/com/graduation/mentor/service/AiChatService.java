package com.graduation.mentor.service;

import com.graduation.mentor.common.AuthContext;
import com.graduation.mentor.dto.AiChatDtos;
import com.graduation.mentor.service.ai.AiChatClientHolder;
import com.graduation.mentor.service.ai.AiErrorMessages;
import com.graduation.mentor.service.ai.AiRateLimiter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

/**
 * AI 小助手（多轮对话）。
 *
 * <p><b>定位</b>：帮用户"把事做完"——学生看懂点评并落地成计划，导师把点评写具体， 教务理解统计口径。它不是通用聊天机器人，闲聊会被引导回学业。
 *
 * <p><b>三条硬约束</b>：
 *
 * <ol>
 *   <li><b>场景由服务端定</b>：按登录角色选系统提示词，前端传什么都不生效。 否则学生传 {@code scene=admin} 就能拿到教务口径提示词——提示词本身就是权限边界。
 *   <li><b>Key 复用教务配置</b>：走 {@link AiChatClientHolder}，与摘要共用同一份配置和同一个 ChatClient。 教务改 Key
 *       后，对话和摘要同时生效。
 *   <li><b>无 Key 不报错</b>：返回固定说明文案（{@code source=DISABLED}）， 前端把它当普通回复渲染。助手不可用不应该阻断任何业务流程。
 * </ol>
 *
 * <p><b>为什么不落库</b>：对话内容可能包含学生个人信息，落库就要配套脱敏、保留期、 删除机制。当前定位是"用完即走"的辅助工具，历史只保留在前端内存里，刷新即清空。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    /** 上下文最多保留的消息条数（10 轮），再长对回答质量无益，只增加成本 */
    private static final int MAX_HISTORY = 20;

    /** 单条回复的长度上限，超出截断，防止模型跑飞把前端撑爆 */
    private static final int MAX_REPLY_CHARS = 2000;

    private final AiChatClientHolder holder;
    private final AuthContext authContext;
    private final AiRateLimiter rateLimiter;

    public AiChatDtos.ChatResp chat(AiChatDtos.ChatReq req) {
        Long userId = authContext.userId();
        String role = authContext.role();

        // 先扣配额：模型调用失败也计数，否则失败重试可以无限打
        int remaining = rateLimiter.acquire(userId);
        long start = System.currentTimeMillis();

        AiChatDtos.ChatResp resp = new AiChatDtos.ChatResp();
        resp.setRemainingToday(remaining);

        ChatClient client = holder.get();
        if (client == null) {
            resp.setSource("DISABLED");
            resp.setReply(
                    "AI 助手当前不可用："
                            + holder.unavailableReason()
                            + "。\n你仍然可以正常使用点评查看、提交与统计功能；如需开启助手，请联系教务管理员。");
            resp.setLatencyMs(System.currentTimeMillis() - start);
            return resp;
        }

        try {
            String reply =
                    client.prompt()
                            .messages(buildMessages(role, req.getMessages()))
                            .call()
                            .content();
            resp.setSource("LLM");
            resp.setModel(holder.config().model());
            resp.setReply(clean(reply));
        } catch (RuntimeException e) {
            // 模型抖动不该变成红色报错：给一句人话，用户还能继续用系统
            log.warn("AI 对话失败：{}", e.toString());
            resp.setSource("DISABLED");
            resp.setReply("AI 助手暂时不可用：" + AiErrorMessages.describe(e) + "\n请稍后重试，或直接联系导师/教务。");
        }
        resp.setLatencyMs(System.currentTimeMillis() - start);
        return resp;
    }

    // ------------------------------------------------------------- 内部

    private List<Message> buildMessages(String role, List<AiChatDtos.Msg> history) {
        List<Message> list = new ArrayList<>();
        list.add(new SystemMessage(systemPrompt(role)));

        int from = Math.max(0, history.size() - MAX_HISTORY);
        for (AiChatDtos.Msg m : history.subList(from, history.size())) {
            String text = m.getContent();
            if (text == null || text.isBlank()) {
                continue;
            }
            if ("assistant".equalsIgnoreCase(m.getRole())) {
                list.add(new AssistantMessage(text));
            } else {
                list.add(new UserMessage(text));
            }
        }
        return list;
    }

    private String clean(String reply) {
        if (reply == null) {
            return "抱歉，这次没有生成内容，请换个说法再问一次。";
        }
        String r = reply.trim();
        if (r.isEmpty()) {
            return "抱歉，这次没有生成内容，请换个说法再问一次。";
        }
        return r.length() > MAX_REPLY_CHARS ? r.substring(0, MAX_REPLY_CHARS) + "…" : r;
    }

    /**
     * 按角色选系统提示词。
     *
     * <p>三个角色共用同一份 Key，但能力边界完全不同：学生不能被承诺成绩， 导师不能被代写判断，教务不能被编造统计。写在同一处便于对照维护。
     */
    private String systemPrompt(String role) {
        return switch (role) {
            case "STUDENT" -> STUDENT_PROMPT;
            case "TEACHER" -> TEACHER_PROMPT;
            default -> ADMIN_PROMPT;
        };
    }

    private static final String STUDENT_PROMPT =
            """
            你是「学业导师点评系统」里的学习助手，服务对象是在校学生。

            你能做：
            - 解释导师点评里出现的术语、要求、评分标准；
            - 根据点评内容给出可执行的学习建议（做什么、什么时候做、怎么检查）；
            - 帮学生把模糊目标拆成周计划；
            - 说明本系统怎么用（在哪里看点评、点评什么时候可见、怎么联系导师）。

            你不能做（必须拒绝并说明原因）：
            - 编造导师没写过的评价、承诺或分数；
            - 代替导师做学业评价，或承诺成绩、保研、评优、奖学金结果；
            - 透露其他学生的任何信息；
            - 回答与学业无关的内容（闲聊、娱乐、时政等），遇到时礼貌说明并引导回学业。

            回答要求：
            - 中文，简洁，分点，一般不超过 300 字；
            - 涉及成绩、处分、政策等具体结论时，明确写"以学院和导师的正式通知为准"；
            - 信息不足时先问一个关键问题，不要凭空假设。
            """;

    private static final String TEACHER_PROMPT =
            """
            你是「学业导师点评系统」的写作助手，服务对象是学业导师。

            你能做：
            - 帮导师把点评写得更具体（补事实、补行动项、补检查时间点）；
            - 检查措辞是否可能引起误解或过于笼统；
            - 根据已有内容给出结构建议（现状—问题—下一步）。

            你不能做：
            - 编造学生的成绩、表现或任何原文没有的事实；
            - 代替导师做判断，或生成对学生的人身评价；
            - 输出其他学生的信息。

            回答要求：中文，简洁，分点，不超过 300 字。
            """;

    private static final String ADMIN_PROMPT =
            """
            你是「学业导师点评系统」的教务助手，服务对象是教务管理员。

            你能做：
            - 解释统计口径：只统计已发布点评、隐私门槛 k=5、趋势按桶级样本量判定；
            - 帮写审核意见草稿（要求具体、可执行、不评价学生人格）；
            - 解释状态机：草稿 / 待审核 / 已通过 / 待发布 / 已发布 / 退回修改 / 已驳回 / 已撤回。

            你不能做：
            - 编造统计数据；没有数据就直说"当前没有可用的统计数据"；
            - 输出学生个人隐私信息。

            回答要求：中文，简洁，分点，不超过 300 字。
            """;
}
