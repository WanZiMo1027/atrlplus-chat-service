package com.atriplus.chat.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/**
 * 消息聚合、调用间隔及 Redis 处理锁配置；间隔和锁租期相互独立。
 */
@Validated
@ConfigurationProperties(prefix = "chat.buffer")
public record MessageBufferProperties(
        @DefaultValue("5000") @Min(1) long debounceMillis,
        @DefaultValue("8000") @Min(1) long minIntervalMillis,
        @DefaultValue("32768") @Min(128) int maxTextChars,
        @DefaultValue("8") @Min(1) int maxImages,
        @DefaultValue("120000") @Min(1) long lockLeaseMillis,
        @DefaultValue("10000") @Min(1) long lockRenewMillis
) {
    /** 明确使用完整构造器绑定配置，避免测试用无参构造器使 YAML 覆盖失效。 */
    @ConstructorBinding
    public MessageBufferProperties {
    }

    /** 单元测试与默认配置使用相同参数，避免测试遗漏生产默认行为。 */
    public MessageBufferProperties() {
        this(5000, 8000, 32768, 8, 120000, 10000);
    }

    /** 续租必须早于租期结束，不能把八秒调用间隔当成锁的生命周期。 */
    @AssertTrue(message = "lock-renew-millis 必须小于 lock-lease-millis")
    public boolean isRenewBeforeExpiry() {
        return lockRenewMillis < lockLeaseMillis;
    }
}
