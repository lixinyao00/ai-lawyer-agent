package com.ruoyi.lawyer.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * OpenAiStreamParser V2 parseChunk 单元测试
 *
 * @author ruoyi
 */
class OpenAiStreamParserV2Test
{
    private final OpenAiStreamParser parser = new OpenAiStreamParser();

    @Test
    void parsesContentAndReasoningDelta()
    {
        String line = "data: {\"choices\":[{\"delta\":{\"content\":\"劳动合同\","
                + "\"reasoning_content\":\"先分析\"},\"finish_reason\":null}]}";

        OpenAiStreamParser.StreamChunk chunk = parser.parseChunk(line);

        assertNotNull(chunk);
        assertEquals("劳动合同", chunk.getContentDelta());
        assertEquals("先分析", chunk.getReasoningDelta());
        assertNull(chunk.getFinishReason());
        assertTrue(chunk.getToolCallDeltas().isEmpty());
    }

    @Test
    void parsesToolCallDeltasWithIndexAndFragments()
    {
        String first = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                + "\"id\":\"call_00_1\",\"function\":{\"name\":\"calc_legal_amount\","
                + "\"arguments\":\"{\\\"calcType\\\":\"}}]},\"finish_reason\":null}]}";

        OpenAiStreamParser.StreamChunk chunk = parser.parseChunk(first);

        assertNotNull(chunk);
        assertEquals(1, chunk.getToolCallDeltas().size());
        OpenAiStreamParser.ToolCallDelta delta = chunk.getToolCallDeltas().get(0);
        assertEquals(0, delta.getIndex());
        assertEquals("call_00_1", delta.getId());
        assertEquals("calc_legal_amount", delta.getName());
        assertEquals("{\"calcType\":", delta.getArgumentsFragment());

        String second = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                + "\"function\":{\"arguments\":\"\\\"loan_interest\\\"}\"}}]},\"finish_reason\":null}]}";
        OpenAiStreamParser.StreamChunk fragment = parser.parseChunk(second);
        assertNotNull(fragment);
        assertEquals("\"loan_interest\"}", fragment.getToolCallDeltas().get(0).getArgumentsFragment());
        assertNull(fragment.getToolCallDeltas().get(0).getId());
    }

    @Test
    void parsesFinishReason()
    {
        OpenAiStreamParser.StreamChunk chunk = parser
                .parseChunk("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}");

        assertNotNull(chunk);
        assertEquals("tool_calls", chunk.getFinishReason());
    }

    @Test
    void parsesTrailingUsagePacketWithEmptyChoices()
    {
        String line = "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":1280,"
                + "\"completion_tokens\":420,\"total_tokens\":1700}}";

        OpenAiStreamParser.StreamChunk chunk = parser.parseChunk(line);

        assertNotNull(chunk);
        assertNull(chunk.getContentDelta());
        assertNotNull(chunk.getUsage());
        assertEquals(1280, chunk.getUsage().getPromptTokens());
        assertEquals(420, chunk.getUsage().getCompletionTokens());
        assertEquals(1700, chunk.getUsage().getTotalTokens());
        assertFalse(chunk.getUsage().isEstimated());
    }

    @Test
    void ignoresNonDataDoneHeartbeatAndInvalidJson()
    {
        assertNull(parser.parseChunk(": heartbeat"));
        assertNull(parser.parseChunk("event: message"));
        assertNull(parser.parseChunk("data: [DONE]"));
        assertNull(parser.parseChunk("data: {not-json"));
        assertNull(parser.parseChunk(""));
        assertNull(parser.parseChunk(null));
        assertNull(parser.parseChunk("data: {\"choices\":[]}"));
    }

    @Test
    void legacyParseLineBehaviorIsPreserved()
    {
        String line = "data: {\"choices\":[{\"delta\":{\"content\":\"劳动合同\"}}]}";

        assertEquals("劳动合同", parser.parseLine(line));
        assertTrue(parser.isDataLine(line));
        assertFalse(parser.isDone(line));
        assertNull(parser.parseLine("data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}"));
    }

    @Test
    void tokenEstimatorNeverReturnsZeroForEmptyOutput()
    {
        AiChatClient.ChatMessage message = new AiChatClient.ChatMessage("user", "劳动合同法第四十七条");

        AiChatClient.TokenUsage empty = TokenEstimator.estimate(java.util.List.of(message), "");

        assertTrue(empty.isEstimated());
        assertTrue(empty.getPromptTokens() > 0);
        assertTrue(empty.getCompletionTokens() > 0);
        assertTrue(empty.getTotalTokens() > 0);
        AiChatClient.TokenUsage text = TokenEstimator.estimate(java.util.List.of(message), "abcd");
        assertTrue(text.getCompletionTokens() >= 1);
    }
}
