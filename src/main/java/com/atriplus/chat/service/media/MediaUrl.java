package com.atriplus.chat.service.media;

import java.net.URI;

/** 只校验保存的媒体地址格式；预览由浏览器直接读取，不发起服务器下载。 */
public final class MediaUrl {
    private MediaUrl() {}

    public static URI validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0) throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("请输入有效的 HTTP/HTTPS 图片地址");
        }
    }
}
