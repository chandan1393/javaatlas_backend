package com.javaatlas.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AdminFeedbackControllerTest {

    @Test
    void csvCellsAreQuotedAndFormulasNeutralised() {
        assertEquals("\"hello\"", AdminFeedbackController.cell("hello"));
        assertEquals("\"say \"\"hi\"\"\"", AdminFeedbackController.cell("say \"hi\""));
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", AdminFeedbackController.cell("=HYPERLINK(\"x\")"));
        assertEquals("\"'+1\"", AdminFeedbackController.cell("+1"));
    }
}
