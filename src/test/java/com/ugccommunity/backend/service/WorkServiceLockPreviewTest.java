package com.ugccommunity.backend.service;

import com.ugccommunity.backend.entity.Work;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkServiceLockPreviewTest {
    @Test
    void shortPaidContentIsNotSentInFull() {
        String content = "这是一段只有二十来个字的付费短文，不应该原样下发。";
        String preview = WorkService.lockPreviewText(content);
        assertTrue(preview.length() < content.length(), preview);
        assertTrue(content.startsWith(preview.replace("...", "")));
    }

    @Test
    void previewIsCappedAtEightyCharacters() {
        String content = "字".repeat(600);
        assertEquals(83, WorkService.lockPreviewText(content).length());
    }

    @Test
    void tinyContentGivesNoPreview() {
        assertEquals("", WorkService.lockPreviewText("短"));
        assertEquals("", WorkService.lockPreviewText(null));
    }

    @Test
    void lockedImageWorkOnlyExposesFirstImage() {
        Work work = new Work();
        work.setType("image");
        work.setMediaUrl("/a.jpg,/b.jpg,/c.jpg");
        assertEquals("/a.jpg", WorkService.lockedMediaUrl(work, false));
        assertEquals("/a.jpg,/b.jpg,/c.jpg", WorkService.lockedMediaUrl(work, true));
    }

    @Test
    void previewStripsFormattingMarkupBeforeTruncating() {
        String content = "<font size=\"20\">**<i>~~<u>" + "格".repeat(300) + "</u>~~</i>**</font>";
        String preview = WorkService.lockPreviewText(content);
        org.junit.jupiter.api.Assertions.assertEquals("格".repeat(80) + "...", preview);
    }

    @Test
    void plainTextUnescapesUserCharactersAndDropsHeadings() {
        org.junit.jupiter.api.Assertions.assertEquals("小标题\n2**3 a~b <u>", WorkService.plainTextFromMarkup("## 小标题\n2\\*\\*3 a\\~b \\<u>"));
        org.junit.jupiter.api.Assertions.assertEquals("", WorkService.plainTextFromMarkup(null));
    }
}
