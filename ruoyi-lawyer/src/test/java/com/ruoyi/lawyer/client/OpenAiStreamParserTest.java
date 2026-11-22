package com.ruoyi.lawyer.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * OpenAiStreamParser 单元测试
 *
 * @author ruoyi
 */
class OpenAiStreamParserTest
{
    private final OpenAiStreamParser parser = new OpenAiStreamParser();

    @Test
    void parsesNormalDelta()
    {
        String line = "data: {\"choices\":[{\"delta\":{\"content\":\"劳动合同\"}}]}";

        assertEquals("劳动合同", parser.parseLine(line));
        assertTrue(parser.isDataLine(line));
        assertFalse(parser.isDone(line));
    }

    @Test
    void emptyDeltaReturnsNull()
    {
        assertNull(parser.parseLine("data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}"));
        assertNull(parser.parseLine("data: {\"choices\":[{\"delta\":{}}]}"));
        assertNull(parser.parseLine("data: {\"choices\":[]}"));
    }

    @Test
    void doneMarkerIsRecognized()
    {
        String line = "data: [DONE]";

        assertTrue(parser.isDone(line));
        assertNull(parser.parseLine(line));
    }

    @Test
    void nonDataLinesAreIgnored()
    {
        assertNull(parser.parseLine(": ping"));
        assertNull(parser.parseLine("event: delta"));
        assertNull(parser.parseLine(""));
        assertNull(parser.parseLine(null));
        assertFalse(parser.isDone("event: [DONE]"));
    }

    @Test
    void invalidJsonIsSkippedInsteadOfThrowing()
    {
        assertNull(parser.parseLine("data: {not-json"));
        assertNull(parser.parseLine("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}"));
    }
}
