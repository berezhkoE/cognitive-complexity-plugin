package cc.settings

import cc.CCBundle.message
import cc.settings.CCSettingsState.ThresholdState
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.*
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.*
import java.awt.*
import java.util.*
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

@Suppress("UnstableApiUsage")
class CognitiveComplexitySettingsConfigurable :
    BoundSearchableConfigurable("Cognitive Complexity", "Cognitive Complexity", _id = ID) {
    companion object {
        const val ID = "Settings.CognitiveComplexity"
    }

    private var thresholdsTableModel: ThresholdsTableModel = ThresholdsTableModel(settings)

    private val settings
        get() = CCSettings.getInstance()

    private var panel: DialogPanel = DialogPanel(BorderLayout())

    private val defaultText = JBTextField(message("default.hint.text"))

    private val showHintBeforeAnnotations =
        JBCheckBox(message("settings.show.hint.before.annotations"), settings.showBeforeAnnotations)

    override fun createPanel(): DialogPanel {
        val gb = GridBag().apply {
            defaultAnchor = GridBagConstraints.WEST
            defaultFill = GridBagConstraints.HORIZONTAL
            nextLine()
        }
        val north = JPanel(GridBagLayout())
        north.add(
            JBLabel(message("settings.default.text")), gb.next().weightx(0.5)
        )
        north.add(
            defaultText, gb.next().weightx(0.5)
        )
        gb.nextLine()
        north.add(
            showHintBeforeAnnotations, gb.next().weightx(0.0).apply { gb.gridwidth = 2 }
        )
        north.border = JBUI.Borders.emptyBottom(5)

        val south = JPanel(VerticalLayout(5))
        south.border = JBUI.Borders.emptyTop(5)
        south.add(
            VerticalLayout.TOP,
            ComponentPanelBuilder.createCommentComponent(message("settings.table.description"), true)
        )
        south.add(
            VerticalLayout.TOP,
            ComponentPanelBuilder.createCommentComponent(message("settings.table.description.additional.small"), true)
        )
        south.add(
            VerticalLayout.TOP,
            ComponentPanelBuilder.createCommentComponent(message("settings.table.description.additional.big"), true)
        )

        panel.add(BorderLayout.NORTH, north)
        panel.add(BorderLayout.CENTER, thresholdsTableModel.createComponent())
        panel.add(BorderLayout.SOUTH, south)

        panel.reset()
        return panel
    }

    override fun isModified(): Boolean {
        return super.isModified()
                || thresholdsTableModel.isModified
                || settings.defaultText != defaultText.text
                || settings.showBeforeAnnotations != showHintBeforeAnnotations.isSelected
    }

    override fun apply() {
        super.apply()
        thresholdsTableModel.apply()
        settings.defaultText = defaultText.text
        settings.showBeforeAnnotations = showHintBeforeAnnotations.isSelected

        invokeLater {
            InlayHintsPassFactory.forceHintsUpdateOnNextPass()
            ProjectManager.getInstance().openProjects.forEach { project ->
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        }
    }

    override fun reset() {
        super.reset()
        thresholdsTableModel.reset()
        defaultText.text = settings.defaultText
        showHintBeforeAnnotations.isSelected = settings.showBeforeAnnotations
    }
}

// table support

private class Column(private val key: String, val type: Class<*>, val editable: Boolean) {
    val name: String
        get() = message(key)
}

private val columns = arrayOf(
    Column("settings.column.threshold", Int::class.javaObjectType, true),
    Column("settings.column.color", ThresholdState::class.java, true),
    Column("settings.column.text", String::class.java, true),
)

@Suppress("UnstableApiUsage")
private class ThresholdsTableModel(val settings: CCSettings) :
    AbstractTableModel(), EditableModel, UnnamedConfigurable {
    val hintConfigurations = mutableListOf<ThresholdState>()
    private var table: JTable? = null

    private fun copy(list: List<ThresholdState>) = list.map { copy(it) }.toMutableList()
    private fun copy(configuration: ThresholdState) =
        ThresholdState.fromMapping(configuration.threshold, configuration.color!!, configuration.text!!)

    private fun selectRow(row: Int) {
        val table = table ?: return
        table.setRowSelectionInterval(row, row)
        table.scrollRectToVisible(table.getCellRect(row, 0, true))
    }

    private fun getConfiguration(row: Int): ThresholdState {
        return hintConfigurations[row]
    }

    private fun resolveCustomColor(value: Any?): String? {
        val name = value as? String ?: return null
        if (null != settings.getColor(name) || message("settings.colors.dialog.no.color") == name) return name
        val parent = table ?: return null
        return ColorChooser.chooseColor(parent, message("settings.colors.dialog.choose.color"), null)
            ?.let { ColorUtil.toHex(it) }
    }

    private fun resolveDuplicateThreshold(row: Int, threshold: Int): Boolean {
        val parent = table ?: return false

        val index = hintConfigurations.indexOfFirst { it.threshold == threshold }
        if (index != -1) {
            Messages.showErrorDialog(
                parent,
                message("settings.same.threshold.value.error")
            )
            return false
        }
        getConfiguration(row).threshold = threshold
        fireTableRowsUpdated(row, row)
        selectRow(row)

        return true
    }

    fun addHintColor(color: String?) {
        val colorName = resolveCustomColor(color) ?: return

        val threshold = hintConfigurations.maxByOrNull { it.threshold }?.threshold?.plus(1) ?: 0
        hintConfigurations.add(
            0,
            ThresholdState.fromMapping(
                threshold,
                colorName,
                settings.defaultText!!
            )
        )
        fireTableRowsInserted(0, 0)
        selectRow(hintConfigurations.size - 1)
    }

    fun getColors(): List<String> {
        val list = mutableListOf<String>()
        list += message("settings.colors.dialog.no.color")
        for (key in settings.ourDefaultColors.keys) {
            list += IdeBundle.message("color.name." + key.lowercase(Locale.ENGLISH))
        }
        list += IdeBundle.message("settings.file.color.custom.name")
        return list
    }

    // TableModel

    override fun getColumnCount() = columns.size

    override fun getColumnName(column: Int) = columns[column].name

    override fun getColumnClass(column: Int) = columns[column].type

    override fun isCellEditable(row: Int, column: Int) = columns[column].editable

    override fun getRowCount() = hintConfigurations.size

    override fun getValueAt(row: Int, column: Int): Any? {
        return when (column) {
            0 -> getConfiguration(row).threshold
            1 -> getConfiguration(row)
            2 -> getConfiguration(row).text
            else -> null
        }
    }

    override fun setValueAt(value: Any?, row: Int, column: Int) {
        val configuration = getConfiguration(row)
        when (column) {
            0 -> {
                resolveDuplicateThreshold(row, value as? Int ?: return)
            }
            1 -> {
                getConfiguration(row).color = resolveCustomColor(value)
                fireTableRowsUpdated(row, row)
                selectRow(row)
            }
            2 -> {
                configuration.text = value as? String ?: return
            }
        }
        fireTableCellUpdated(row, column)
    }

    // EditableModel

    override fun addRow() = throw UnsupportedOperationException()

    override fun removeRow(row: Int) {
        hintConfigurations.removeAt(row)
        fireTableRowsDeleted(row, row)
    }

    override fun exchangeRows(oldRow: Int, newRow: Int) {
    }


    override fun canExchangeRows(oldRow: Int, newRow: Int): Boolean {
        return false
    }

    // UnnamedConfigurable

    override fun createComponent(): JComponent {
        val table = JBTable(this)

        val sorter: TableRowSorter<TableModel> = TableRowSorter(table.model)
        sorter.sortKeys += RowSorter.SortKey(0, SortOrder.ASCENDING)
        sorter.sortsOnUpdates = true
        table.rowSorter = sorter

        table.tableHeader.isEnabled = false

        table.setShowGrid(false)
        TableHoverListener.DEFAULT.removeFrom(table)
        table.emptyText.text = message("settings.no.thresholds.specified")

        table.emptyText.appendSecondaryText(
            message("settings.add.threshold"),
            SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
        ) {
            val popup = JBPopupFactory.getInstance().createListPopup(ColorListPopupStep(this))
            popup.showInCenterOf(table)
        }
        val shortcut =
            KeymapUtil.getShortcutsText(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD).shortcuts)
        if (shortcut.isNotEmpty()) table.emptyText.appendText(" ($shortcut)")

        this.table = table

        // configure color renderer and its editor
        val editor = ComboBox(getColors().toTypedArray())
        editor.renderer = ComboBoxColorRenderer(settings)
        table.setDefaultEditor(ThresholdState::class.java, DefaultCellEditor(editor))
        table.setDefaultRenderer(ThresholdState::class.java, TableColorRenderer(settings))
        // align boolean renderer to left
        val booleanRenderer = table.getDefaultRenderer(Boolean::class.javaObjectType)
        val rendererCheckBox = booleanRenderer as? JCheckBox
        rendererCheckBox?.horizontalAlignment = SwingConstants.LEFT
        // align boolean editor to left
        val booleanEditor = table.getDefaultEditor(Boolean::class.javaObjectType)
        val editorWrapper = booleanEditor as? DefaultCellEditor
        val editorCheckBox = editorWrapper?.component as? JCheckBox
        editorCheckBox?.horizontalAlignment = SwingConstants.LEFT
        // create and configure table decorator
        return ToolbarDecorator.createDecorator(table)
            .setAddAction {
                val popup = JBPopupFactory.getInstance().createListPopup(ColorListPopupStep(this))
                it.preferredPopupPoint.let { point -> popup.show(point) }
            }
            .setAddIcon(AllIcons.General.Add)
            .disableUpDownActions()
            .createPanel()
    }

    override fun isModified(): Boolean {
        return settings.thresholdsList != hintConfigurations
    }

    override fun apply() {
        settings.thresholdsList.clear()
        settings.thresholdsList.addAll(copy(hintConfigurations))
    }

    override fun reset() {
        hintConfigurations.clear()
        hintConfigurations.addAll(copy(settings.thresholdsList))
        fireTableDataChanged()
    }
}

// renderers

private class ColorPainter(val color: Color) : RegionPainter<Component?> {
    fun asIcon(): Icon = RegionPaintIcon(36, 12, this).withIconPreScaled(false)

    override fun paint(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, c: Component?) {
        g.color = color
        g.fillRect(x, y, width, height)
    }
}

private fun updateColorRenderer(renderer: JLabel, selected: Boolean, background: Color?): JLabel {
    if (!selected) renderer.background = background
    renderer.horizontalTextPosition = SwingConstants.LEFT
    renderer.icon = background?.let { if (selected) ColorPainter(it).asIcon() else null }
    return renderer
}

private class ComboBoxColorRenderer(val settings: CCSettings) : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        selected: Boolean,
        focused: Boolean
    ): Component {
        super.getListCellRendererComponent(list, value, index, selected, focused)
        return updateColorRenderer(this, selected, value?.toString()?.let { settings.getColor(it) })
    }
}

private class TableColorRenderer(val settings: CCSettings) : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable?, value: Any?,
        selected: Boolean, focused: Boolean, row: Int, column: Int
    ): Component {
        val configuration = value as? ThresholdState
        super.getTableCellRendererComponent(
            table,
            configuration?.color?.let { settings.getColorName(it) },
            selected,
            focused,
            row,
            column
        )
        return updateColorRenderer(this, selected, configuration?.color?.let { settings.getColor(it) })
    }

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        val bounds = Rectangle(width, height)
        JBInsets.removeFrom(bounds, insets)
        val icon = AllIcons.General.ArrowDown
        icon.paintIcon(
            this, g,
            bounds.x + bounds.width - icon.iconWidth,
            bounds.y + (bounds.height - icon.iconHeight) / 2
        )
    }
}

private class ColorListPopupStep(val model: ThresholdsTableModel) : BaseListPopupStep<String>(null, model.getColors()) {
    override fun getBackgroundFor(value: String?) = value?.let { model.settings.getColor(it) }
    override fun onChosen(value: String?, finalChoice: Boolean): PopupStep<*>? {
        // invoke later to close popup before showing dialog
        invokeLater { model.addHintColor(value) }
        return null
    }
}