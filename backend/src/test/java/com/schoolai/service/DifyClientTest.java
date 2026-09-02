package com.schoolai.service;

import com.schoolai.config.DifyConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DifyClientTest {

    @Test
    void removeThinking_shouldStripReasoningSplitAcrossStreamChunks() {
        DifyClient client = new DifyClient(new DifyConfig());
        String streamedAnswer = "<think>内部推理第一段" + "内部推理第二段</think>\n最终回答";

        assertEquals("\n最终回答", client.removeThinking(streamedAnswer));
    }

    @Test
    void removeThinking_shouldKeepVisibleTextWhenStreamEndsInsideReasoningBlock() {
        DifyClient client = new DifyClient(new DifyConfig());

        assertEquals("全国大学生电子设计竞赛\n", client.removeThinking(
                "全国大学生电子设计竞赛\n<think>未完成的内部推理"));
    }

    @Test
    void selectFinalAnswer_shouldUseWorkflowOutputAfterEmptyMessageEnd() {
        DifyClient client = new DifyClient(new DifyConfig());

        String answer = client.selectFinalAnswer("", "<think>内部推理", "外研社·国才杯英语挑战赛", "");

        assertEquals("外研社·国才杯英语挑战赛", answer);
    }
}
