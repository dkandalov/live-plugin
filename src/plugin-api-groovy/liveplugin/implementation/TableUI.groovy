package liveplugin.implementation

import com.intellij.ide.ClipboardSynchronizer
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel

import javax.swing.*
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent

class TableUI {
	static ListTableModel<List<String>> newTableModel(List<String> header, List<List<String>> items) {
		def columnNames = header.collect { new StringListColumnInfo(it, header.indexOf(it)) }.toArray(new ColumnInfo[0])
		new ListTableModel<List<String>>(columnNames, items)
	}

	static createTable(List<String> header, List<List<String>> rows) {
		createTable(newTableModel(header, rows))
	}

	static JBScrollPane createTable(ListTableModel tableModel) {
		def table = new TableView(tableModel).with {
			striped = true
			showGrid = false
			it
		}
		registerCopyToClipboardShortCut(table.component, tableModel)
		new JBScrollPane(table)
	}

	private static registerCopyToClipboardShortCut(JTable table, ListTableModel tableModel) {
		def copyKeyStroke = KeymapUtil.getKeyStroke(ActionManager.instance.getAction(IdeActions.ACTION_COPY).shortcutSet)
		table.registerKeyboardAction(new AbstractAction() {
			@Override void actionPerformed(ActionEvent event) {
				def selectedCells = table.selectedRows.collect { row ->
					(0..<tableModel.columnCount).collect { column ->
						tableModel.getValueAt(row, column).toString()
					}
				}
				def content = new StringSelection(selectedCells.collect { it.join(",") }.join("\n"))
				ClipboardSynchronizer.instance.setContent(content, content)
			}
		}, "Copy", copyKeyStroke, JComponent.WHEN_FOCUSED)
	}

	private static class StringListColumnInfo extends ColumnInfo<List<String>, String> {
		private final int index

		StringListColumnInfo(String name, int index) {
			super(name)
			this.index = index
		}

		@Override String valueOf(List<String> list) {
			list[index]
		}

		@Override Comparator<List<String>> getComparator() {
			new Comparator<List<String>>() {
				@Override int compare(List<String> row1, List<String> row2) {
					row1[index].compareTo(row2[index])
				}
			}
		}
	}
}
