package com.atriplus.chat.controller;

import com.atriplus.chat.event.MessageDetectedEvent;
import com.atriplus.chat.service.MessageIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/*
 * 消息接收控制器
 */
@RestController
@RequestMapping("/internal/messages")
@Profile("rabbitmq")
public class MessageIngestController {

    // 消息接收服务
    @Autowired
    private  MessageIngestService messageIngestService;

    private static final Logger log =
            LoggerFactory.getLogger(MessageIngestController.class);

    // 接收消息
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody MessageDetectedEvent event) {

        messageIngestService.ingest(event);

        return ResponseEntity.accepted().build();
    }

}
