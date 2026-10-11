package com.atriplus.chat.service.ai;

import com.atriplus.chat.client.dto.ChatMessageItem;
import com.atriplus.chat.config.MessageBufferProperties;
import lombok.RequiredArgsConstructor;

/**
 * 按 Agent 实际用户消息边界累计字符和原图，避免用消息条数限制正常聚合。
 */
@RequiredArgsConstructor
final class AgentBatchBudget {
    private static final String HEADER = "以下是QQ群中刚刚连续出现的消息：\n\n";
    private static final String IMAGE_CAPTION = "以下是这条消息附带的动画表情原图,仅供本轮理解：";
    private static final String TRUNCATED = "\n[消息过长，正文已截断]";

    private final MessageBufferProperties properties;
    private int chars = HEADER.length();
    private int images;
    private int count;


    /** 空值表示当前消息及后续消息应继续排队，不能跳过它而打乱顺序。 */
    ChatMessageItem accept(ChatMessageItem item) {
        if (chars + length(item) <= properties.maxTextChars()
                && images + item.images().size() <= properties.maxImages()) {
            record(item);
            return item;
        }
        if (count > 0) return null;

        // 单条超限，保留事件和发送者，仅截正文及过多的原图。
        var retainedImages = item.images().subList(0, Math.min(item.images().size(), properties.maxImages()));
        String text = String.valueOf(item.text());
        if (retainedImages.size() < item.images().size()) {
            text += "\n[附带原图过多，本轮仅携带前" + properties.maxImages() + "张]";
        }
        var empty = new ChatMessageItem(item.eventId(), item.senderName(), "", retainedImages);
        int available = properties.maxTextChars() - chars - length(empty);
        if (available < TRUNCATED.length()) {
            throw new IllegalArgumentException("事件 ID 或发送者超过单轮文本预算");
        }
        if (text.length() > available) {
            int end = available - TRUNCATED.length();
            // UTF-16 字符预算不能从代理对中间切开 emoji 等字符。
            if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            text = text.substring(0, end) + TRUNCATED;
        }
        var result = new ChatMessageItem(item.eventId(), item.senderName(), text, retainedImages);
        record(result);
        return result;
    }

    /** 与 agent-service 的包装保持一致，包含 message_description 和原图文字提示。 */
    static int length(ChatMessageItem item) {
        return ("[eventId=" + item.eventId() + "] 发送者=" + item.senderName()
                + "\n<message_description>\n" + item.text() + "\n</message_description>\n").length()
                + item.images().size() * IMAGE_CAPTION.length();
    }

    private void record(ChatMessageItem item) {
        chars += length(item);
        images += item.images().size();
        count++;
    }
}
