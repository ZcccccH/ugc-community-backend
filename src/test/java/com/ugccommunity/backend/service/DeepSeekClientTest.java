package com.ugccommunity.backend.service;

import com.ugccommunity.backend.entity.SandboxChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekClientTest {
    @Test
    void generateReplySendsCompatibleRequestAndParsesContent() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        DeepSeekClient client = new DeepSeekClient(restTemplate, "test-key", "https://api.deepseek.com/", "deepseek-v4-flash");

        server.expect(once(), requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("{\"model\":\"deepseek-v4-flash\",\"stream\":false,\"messages\":[{\"role\":\"system\"},{\"role\":\"user\",\"content\":\"你好\"}]}", false))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"你好，我已进入箱庭。\"}}]}", MediaType.APPLICATION_JSON));

        String reply = client.generateReply("世界观=测试世界", Collections.singletonList(message("user", "你好")));

        assertEquals("你好，我已进入箱庭。", reply);
        server.verify();
    }

    @Test
    void generateReplyWrapsUpstreamFailure() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        DeepSeekClient client = new DeepSeekClient(restTemplate, "test-key", "https://api.deepseek.com", "deepseek-v4-flash");

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withServerError());

        assertThrows(DeepSeekClient.DeepSeekException.class,
                () -> client.generateReply("暂无挂载内容", Collections.singletonList(message("user", "你好"))));
        server.verify();
    }

    @Test
    void generateReplyRejectsMissingApiKeyWithoutCallingNetwork() {
        DeepSeekClient client = new DeepSeekClient(new RestTemplate(), "", "https://api.deepseek.com", "deepseek-v4-flash");

        assertThrows(DeepSeekClient.DeepSeekException.class,
                () -> client.generateReply("暂无挂载内容", Collections.singletonList(message("user", "你好"))));
    }

    private SandboxChatMessage message(String role, String content) {
        SandboxChatMessage message = new SandboxChatMessage();
        message.setRole(role);
        message.setContent(content);
        message.setMockReply(0);
        return message;
    }
}
