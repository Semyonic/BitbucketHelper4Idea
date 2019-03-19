package ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor


class EditorAreaIllustration : AnAction() {
    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val editor = anActionEvent.getRequiredData<Editor>(CommonDataKeys.EDITOR)
        val caretModel = editor.getCaretModel()
    }

    override fun update(e: AnActionEvent) {
        //
    }
}