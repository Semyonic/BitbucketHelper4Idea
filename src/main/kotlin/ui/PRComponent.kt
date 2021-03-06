package ui

import bitbucket.data.PR
import bitbucket.data.PRParticipant
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URL
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import java.util.concurrent.Executor
import java.util.function.Consumer
import javax.swing.*

open class PRComponent(
        val pr: PR,
        imagesSource: MediaSource<Icon>,
        awtExecutor: Executor) : JPanel() {

    val greenColor = Color(0, 168, 9)
    val redColor = Color(192, 1, 0)

    private val checkoutBtn = JButton("▼ Checkout")
    val approveBtn = JButton("Approve")
    val declineBtn = JButton("Decline")
    val mergeBtn = JButton("Merge")
    private val prLink: JLabel
    private val targetBranchLabel: JLabel
    private val authorLabel: JBLabel
    private val reviewersPanel: ReviewersPanel

    companion object {
        const val LEFT_RIGHT_INSET = 7
        const val TOP_BOTTOM_INSET = 10
    }

    init {
        this.prLink = this.createPrLinkLabel(this.pr)
        this.targetBranchLabel = JBLabel("To: ${this.pr.toBranch}")
        val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val updatedAt = this.pr.updatedAt.toLocalDateTime().format(dateTimeFormatter)
        val commentsCount = "${this.pr.commentCount} " + if (this.pr.commentCount == 1) {
            "comment"
        } else {
            "comments"
        }
        this.authorLabel = JBLabel("By: ${this.pr.author.user.displayName} - #${this.pr.id} ($commentsCount) "
                                 + "last updated $updatedAt")
        this.reviewersPanel = ReviewersPanel(ArrayList(this.pr.reviewers), imagesSource, awtExecutor)
        this.mergeBtn.isVisible = false
        this.declineBtn.isVisible = false
        this.approveBtn.isVisible = false

        this.createComponentSpecificButton()
        this.checkoutBtn.addActionListener { Model.checkout(this.pr) }

        this.border = UIUtil.getTextFieldBorder()
        this.background = UIUtil.getListBackground(false)

        this.layout = GridBagLayout()
        val gbc = GridBagConstraints()
        gbc.insets.left = LEFT_RIGHT_INSET
        gbc.insets.right = LEFT_RIGHT_INSET
        gbc.insets.top = TOP_BOTTOM_INSET
        gbc.insets.bottom = TOP_BOTTOM_INSET / 2

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.gridwidth = 4
        gbc.weightx = 1.0
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL

        this.add(this.prLink, gbc)
        gbc.gridy++
        gbc.insets.top = 0
        gbc.insets.bottom = 0

        this.add(this.targetBranchLabel, gbc)
        gbc.gridy++
        this.add(this.authorLabel, gbc)

        gbc.weightx = 0.0
        gbc.gridwidth = 1
        gbc.gridy++
        gbc.fill = GridBagConstraints.NONE
        gbc.insets.top = TOP_BOTTOM_INSET
        gbc.insets.bottom = TOP_BOTTOM_INSET
        this.add(this.checkoutBtn, gbc)
        gbc.gridx++
        this.add(this.approveBtn, gbc)
        gbc.gridx + 2
        this.add(this.declineBtn, gbc)
        gbc.gridx++
        this.add(this.declineBtn, gbc)
        gbc.gridx++
        gbc.anchor = GridBagConstraints.WEST
        this.add(this.mergeBtn, gbc)

        gbc.gridx++
        gbc.weightx = 1.0
        gbc.anchor = GridBagConstraints.EAST
        this.add(this.reviewersPanel, gbc)
    }

    private fun createPrLinkLabel(pr: PR): LinkLabel<*> {
        val prLinkLabel = LinkLabel.create(pr.title) { BrowserUtil.browse(pr.links.getSelfHref()) }
        prLinkLabel.font = prLinkLabel.font.deriveFont(prLinkLabel.font.size * 1.4f)
        prLinkLabel.toolTipText = "<html>${pr.title}<br>link: ${pr.links.getSelfHref()}<br><br>Open in browser</html>"
        return prLinkLabel
    }

    open fun createComponentSpecificButton() {
        this.approveBtn.addActionListener {
            Model.approve(this.pr, Consumer { approved ->
                if (approved) {
                    this.approveBtn.text = "Approved"
                    this.approveBtn.isEnabled = false
                }
            })
        }
        this.approveBtn.foreground = this.greenColor
        this.approveBtn.font = UIUtil.getButtonFont()
        this.approveBtn.isVisible = true
        this.declineBtn.isVisible = true
        this.mergeBtn.isVisible = true
    }

    open fun currentBranchChanged(branch: String) {
        val isActive = this.pr.fromBranch == branch
        this.border = if (isActive) BorderFactory.createLineBorder(UIUtil.getListSelectionBackground(), 3)
        else UIUtil.getTextFieldBorder()
        this.approveBtn.isVisible = true
        this.checkoutBtn.isVisible = !isActive

        this.declineBtn.addActionListener {
            Model.decline(this.pr, Consumer { declined ->
                if (declined) {
                    this.declineBtn.text = "Declined"
                    this.declineBtn.isEnabled = false
                }
            })
        }
        this.declineBtn.foreground = this.redColor
        this.declineBtn.font = UIUtil.getButtonFont()
        this.declineBtn.isVisible = true
    }
}

/** A pull-request where an author is yourself */
class OwnPRComponent(ownPR: PR,
                     imagesSource: MediaSource<Icon>,
                     awtExecutor: Executor)
    : PRComponent(ownPR, imagesSource, awtExecutor) {

    override fun createComponentSpecificButton() {
        this.mergeBtn.isVisible = true
        this.approveBtn.isVisible = true
        this.declineBtn.isVisible = true
        this.mergeBtn.isEnabled = pr.mergeStatus.canMerge
        if (!pr.mergeStatus.canMerge) {
            this.mergeBtn.toolTipText = pr.mergeStatus.vetoesSummaries()
        } else {
            this.mergeBtn.addActionListener {
                Model.merge(this.pr, Consumer { approved ->
                    if (approved) {
                        this.mergeBtn.text = "Merged"
                        this.mergeBtn.isEnabled = false
                    }
                })
            }
        }
        this.mergeBtn.foreground = this.greenColor
        this.mergeBtn.font = UIUtil.getButtonFont()
    }

    override fun currentBranchChanged(branch: String) {
        super.currentBranchChanged(branch)
        this.approveBtn.isVisible = true
        this.declineBtn.isVisible = true
    }
}

class ReviewersPanel(reviewers: MutableList<PRParticipant>,
                     imagesSource: MediaSource<Icon>,
                     awtExecutor: Executor) : JPanel(HorizontalLayout(5)) {
    companion object {
        const val ALWAYS_DISPLAY_REVIEWERS_COUNT = 5
    }

    init {
        this.isOpaque = false
        reviewers.sortWith(Comparator { o1, o2 -> o1.status.compareTo(o2.status) })
        val labels: Map<PRParticipant, ReviewerItem> = reviewers.associateWith { prParticipant -> ReviewerItem(prParticipant) }

        val alwaysVisibleReviewerCount = if (reviewers.size == ALWAYS_DISPLAY_REVIEWERS_COUNT + 1)
            reviewers.size
        else
            Math.min(ALWAYS_DISPLAY_REVIEWERS_COUNT, reviewers.size)

        reviewers.take(alwaysVisibleReviewerCount).forEach { this.add(labels[it]) }

        val reviewersInCombo = reviewers.size - alwaysVisibleReviewerCount
        if (reviewersInCombo > 0) {
            val otherReviewersButton = JLayeredPane()
            val realButton = JButton("+$reviewersInCombo")
            val avatarSize = ReviewerComponentFactory.avatarSize
            realButton.setBounds(0, ReviewerComponentFactory.statusIconSize / 3, avatarSize, avatarSize)
            otherReviewersButton.add(realButton)
            this.add(otherReviewersButton)
            val height = this.preferredSize.height
            otherReviewersButton.preferredSize = Dimension(height, height)
            val menu = JBPopupMenu()
            reviewers.takeLast(reviewersInCombo).forEach { prParticipant: PRParticipant ->
                val itemPanel = JPanel(FlowLayout(FlowLayout.LEFT))
                itemPanel.add(labels[prParticipant])
                itemPanel.add(JLabel(prParticipant.user.displayName))
                menu.add(itemPanel)
            }
            realButton.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    menu.show(otherReviewersButton, e.x, e.y)
                }
            })
        }

        labels.forEach { prParticipant: PRParticipant, label: ReviewerItem ->
            imagesSource.retrieve(URL(prParticipant.user.links.getIconHref()))
                    .thenAcceptAsync(Consumer { icon -> label.setAvatar(icon) }, awtExecutor)
        }
    }
}

class ReviewerItem(reviewer: PRParticipant) : JLayeredPane() {
    private val avatarLabel: JLabel = JLabel(ReviewerComponentFactory.defaultAvatarIcon)

    companion object {
        val AVATAR_Z_INDEX = Integer(0)
        val STATUS_ICON_Z_INDEX = Integer(1)
    }

    init {
        val avatarSize = ReviewerComponentFactory.avatarSize
        val statusIconSize = ReviewerComponentFactory.statusIconSize
        val size = avatarSize + statusIconSize / 3
        this.preferredSize = Dimension(size, size)
        this.avatarLabel.setBounds(0, statusIconSize / 3, avatarSize, avatarSize)
        this.add(this.avatarLabel, AVATAR_Z_INDEX)
        val statusIcon = ReviewerComponentFactory.getStatusIcon(reviewer)
        if (statusIcon != null) {
            val statusLabel = JLabel(statusIcon)
            statusLabel.setBounds(size - statusIconSize, 0, statusIconSize, statusIconSize)
            this.add(statusLabel, STATUS_ICON_Z_INDEX)
        }
    }

    fun setAvatar(icon: Icon) {
        this.avatarLabel.icon = icon
    }
}