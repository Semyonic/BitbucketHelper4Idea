import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import java.awt.Dimension
import javax.swing.JTextField


class SampleDialogWrapper : DialogWrapper(true) {
    init {
        init()
    }

    override fun createCenterPanel(): JComponent? {
        val dialogPanel = JPanel(BorderLayout())

        val reviewCommentLbl = JLabel("Comment")
        reviewCommentLbl.preferredSize = Dimension(100, 100)
        dialogPanel.add(reviewCommentLbl, BorderLayout.CENTER)

        val reviewComment = JTextField()
        reviewComment.preferredSize = Dimension(50,50)
        dialogPanel.add(reviewComment,BorderLayout.BEFORE_LINE_BEGINS)

        return dialogPanel
    }
}