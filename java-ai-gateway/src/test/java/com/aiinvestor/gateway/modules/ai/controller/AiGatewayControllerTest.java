package com.aiinvestor.gateway.modules.ai.controller;

import com.aiinvestor.gateway.modules.ai.service.ChatHistoryService;
import com.aiinvestor.gateway.modules.ai.service.HumanHandoffService;
import com.aiinvestor.gateway.modules.ai.service.PythonAiClientService;
import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import com.aiinvestor.gateway.modules.shared.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGatewayControllerTest {

    @Mock
    private PythonAiClientService pythonAiClientService;

    @Mock
    private ChatHistoryService chatHistoryService;

    @Mock
    private HumanHandoffService humanHandoffService;

    @InjectMocks
    private AiGatewayController aiGatewayController;

    @BeforeEach
    void setUp() {
        UserDO user = new UserDO();
        user.setId(1L);
        user.setRole("vip");
        UserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void shouldReuseSameTraceIdAcrossPersistenceStreamingAndAnswerUpdate() {
        when(pythonAiClientService.buildThreadId(1L, "sess_trace"))
                .thenReturn("1:sess_trace");
        when(pythonAiClientService.streamChatSse(anyString(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(Flux.just(
                        ServerSentEvent.<String>builder()
                                .data("{\"stage\":\"final_answer\",\"data\":{\"answer\":\"统一 trace\"}}")
                                .build(),
                        ServerSentEvent.<String>builder()
                                .data("{\"stage\":\"done\",\"data\":{\"status\":\"success\"}}")
                                .build()
                ));

        aiGatewayController.stream("帮我分析茅台", "sess_trace").blockLast();

        ArgumentCaptor<String> savedTraceId = ArgumentCaptor.forClass(String.class);
        verify(chatHistoryService).saveTurn(
                anyLong(),
                anyString(),
                anyString(),
                savedTraceId.capture(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                anyString(),
                org.mockito.ArgumentMatchers.anyInt()
        );

        ArgumentCaptor<String> streamedTraceId = ArgumentCaptor.forClass(String.class);
        verify(pythonAiClientService).streamChatSse(
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                streamedTraceId.capture()
        );

        verify(chatHistoryService).updateTurnAnswerByTraceId(savedTraceId.getValue(), "统一 trace");
        assertEquals(savedTraceId.getValue(), streamedTraceId.getValue());
    }
}
