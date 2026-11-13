package eu.wanderclan.wordquest.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.*

/** Check every Text, including text merged into a button's semantics. */
fun ComposeTestRule.assertReadableText(name: String) {
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes().forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            layouts.forEach { layout ->
                val text = layout.layoutInput.text.text
                assertFalse("Ellipsised text in $name: $text", layout.multiParagraph.didExceedMaxLines)
                for (line in 0 until layout.lineCount) {
                    assertTrue("Clipped line in $name: $text",
                        layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1 &&
                            layout.getLineBottom(line) <= layout.size.height + 1)
                    val end = layout.getLineEnd(line)
                    assertFalse("Word broken in $name: $text at $end",
                        end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit())
                }
            }
        }
}
