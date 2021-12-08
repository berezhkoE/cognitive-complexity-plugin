package com.github.berezhkoe.cognitivecomplexityplugin.settings

import com.github.berezhkoe.cognitivecomplexityplugin.settings.CognitiveComplexitySettingsState.ThresholdState
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.psi.PsiManager
import com.intellij.ui.*
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.layout.panel
import com.intellij.ui.table.JBTable
import com.intellij.ui.tabs.*
import com.intellij.util.ui.*
import net.miginfocom.swing.MigLayout
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import java.awt.*
import java.util.*
import javax.swing.*
import javax.swing.table.*

class CognitiveComplexitySettingsConfigurable :
    BoundSearchableConfigurable("Cognitive Complexity", "Cognitive Complexity", _id = ID) {
    companion object {
        const val ID = "Settings.CognitiveComplexity"
    }

    private var thresholdsTableModel: ThresholdsTableModel = ThresholdsTableModel(settings)

    private val settings
        get() = CognitiveComplexitySettings.getInstance()

    private val checkBox: JBCheckBox =
        JBCheckBox("Show property accessor complexity in Kotlin", settings.showPropertyAccessorsComplexity)

    override fun createPanel(): DialogPanel {
        val panel = DialogPanel(GridBagLayout())
        val gb = GridBag().apply {
            defaultAnchor = GridBagConstraints.WEST
            defaultFill = GridBagConstraints.HORIZONTAL
            nextLine().next()
        }
        panel.add(
            JBLabel(""),
            gb.insets(JBUI.insetsRight(UIUtil.DEFAULT_HGAP))
        )
        panel.add(
            checkBox,
            gb.next()
        )
        gb.nextLine().next()
        panel.add(
            thresholdsTableModel.createComponent(),
            gb.next().fillCell().weightx(1.0).weighty(1.0).insets(10, 0, 1, 0)
        )
        panel.reset()
        return panel
    }

    private fun createTable(): JPanel {
        val table = JBTable(thresholdsTableModel)

        val sorter: TableRowSorter<TableModel> = TableRowSorter(table.model)
        sorter.sortKeys += RowSorter.SortKey(0, SortOrder.ASCENDING)
        sorter.sortsOnUpdates = true
        table.rowSorter = sorter

        table.tableHeader.isEnabled = false

        table.setShowGrid(false)
        TableHoverListener.DEFAULT.removeFrom(table)
        table.emptyText.text = "No Thresholds Specified"

        table.emptyText.appendSecondaryText(
            "Add threshold",
            SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
        ) {
            val popup = JBPopupFactory.getInstance().createListPopup(ColorListPopupStep(thresholdsTableModel))
            popup.showInCenterOf(table)
        }
        val shortcut =
            KeymapUtil.getShortcutsText(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD).shortcuts)
        if (shortcut.isNotEmpty()) table.emptyText.appendText(" ($shortcut)")

        // configure color renderer and its editor
        val editor = ComboBox(thresholdsTableModel.getColors().toTypedArray())
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
                val popup = JBPopupFactory.getInstance().createListPopup(ColorListPopupStep(thresholdsTableModel))
                it.preferredPopupPoint.let { point -> popup.show(point) }
            }
            .setAddIcon(AllIcons.General.Add)
            .disableUpDownActions()
            .createPanel()
    }

    override fun isModified(): Boolean {
        return super.isModified()
                || thresholdsTableModel.isModified
                || settings.showPropertyAccessorsComplexity != checkBox.isSelected
    }

    override fun apply() {
        super.apply()
        thresholdsTableModel.apply()
        settings.showPropertyAccessorsComplexity = checkBox.isSelected
        invokeLater {
            ProjectManager.getInstance().openProjects.forEach { project ->
                FileEditorManager.getInstance(project).openFiles.map { PsiManager.getInstance(project).findFile(it) }
                    .forEach {
                        it?.let {
                            DaemonCodeAnalyzerImpl.getInstance(project).restart(it)
                        }
                    }
            }
        }
    }

    override fun reset() {
        super.reset()
        thresholdsTableModel.reset()
        checkBox.isSelected = settings.showPropertyAccessorsComplexity
    }
}

// table support

private data class Column(val name: String, val type: Class<*>, val editable: Boolean)

//data class HintColor(val colorId: String?)

private val columns = arrayOf(
    Column("Threshold", Int::class.javaObjectType, true),
    Column("Color", ThresholdState::class.java, true),
    Column("Text", String::class.java, true),
)

private class ThresholdsTableModel(val settings: CognitiveComplexitySettings) :
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
        if (null != settings.getColor(name)) return name
        val parent = table ?: return null
        return ColorChooser.chooseColor(parent, "Choose Hint Color", null)
            ?.let { ColorUtil.toHex(it) }
    }

    private fun resolveDuplicateThreshold(row: Int, threshold: Int): Boolean {
        val parent = table ?: return false

        val index = hintConfigurations.indexOfFirst { it.threshold == threshold }
        if (index != -1) {
            Messages.showErrorDialog(
                parent,
                " "
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
        val parent = table!!

        val index = hintConfigurations.indexOfFirst { it.color == colorName }
        if (index != -1) {
            Messages.showErrorDialog(
                parent,
                " "
            )
        } else {
            hintConfigurations.add(0, ThresholdState.fromMapping(0, colorName, "Oh what a %complexity%!"))
            fireTableRowsInserted(0, 0)
            selectRow(0)
        }
    }

    fun getColors(): List<String> {
        val list = mutableListOf<String>()
        for (key in settings.ourDefaultColors.keys) {
            list += IdeBundle.message("color.name." + key.lowercase(Locale.ENGLISH))
        }
        list.sort()
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
        table.emptyText.text = "No Thresholds Specified"

        table.emptyText.appendSecondaryText(
            "Add threshold",
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

//private class ColorPainter(val color: Color) : RegionPainter<Component?> {
//    fun asIcon(): Icon = RegionPaintIcon(36, 12, this).withIconPreScaled(false)
//
//    override fun paint(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, c: Component?) {
//        g.color = color
//        g.fillRect(x, y, width, height)
//    }
//}

private fun updateColorRenderer(renderer: JLabel, selected: Boolean, background: Color?): JLabel {
    if (!selected) renderer.background = background
    renderer.horizontalTextPosition = SwingConstants.LEFT
//    renderer.icon = background?.let { if (selected) ColorPainter(it).asIcon() else null }
    return renderer
}

private class ComboBoxColorRenderer(val settings: CognitiveComplexitySettings) : DefaultListCellRenderer() {
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

private class TableColorRenderer(val settings: CognitiveComplexitySettings) : DefaultTableCellRenderer() {
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