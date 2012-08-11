package ru.intellijeval.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import ru.intellijeval.EvalData;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

/**
 * @author DKandalov
 */
public class SettingsForm implements Configurable {
	private JPanel root;
	private JTable table;
	private JButton addButton;
	private JButton removeButton;

	private EvalData model;
	private EvalData originalModel;

	public SettingsForm() {
		model = EvalData.getInstance();
		originalModel = new EvalData();
		originalModel.loadState(model);

		updateUI();

		addButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String path = JOptionPane.showInputDialog("Path to new plugin:");
				if (path == null || path.isEmpty()) return;
				((DefaultTableModel) table.getModel()).addRow(new String[]{path, path});
			}
		});
		removeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (table.getSelectedRow() == -1) return;
				((DefaultTableModel) table.getModel()).removeRow(table.getSelectedRow());
			}
		});
	}

	private void updateUI() {
		DefaultTableModel model = new DefaultTableModel();
		model.addColumn("Plugin name");
		model.addColumn("Plugin path");
		for (Map.Entry<String, String> entry : this.model.getPluginPaths().entrySet()) {
			model.addRow(new String[]{entry.getValue(), entry.getValue()});
		}
		table.setModel(model);
	}

	private void updateModel() {
		EvalData newModel = toEvalData(table.getModel());
		model.loadState(newModel);
	}

	private static EvalData toEvalData(TableModel model) {
		EvalData result = new EvalData();
		for (int i = 0; i < model.getRowCount(); i++) {
			result.getPluginPaths().put((String) model.getValueAt(i, 0), (String) model.getValueAt(i, 1));
		}
		return result;
	}

	@Override
	public void apply() throws ConfigurationException {
		updateModel();
	}

	@Override
	public boolean isModified() {
		return model.equals(originalModel);
	}

	@Override
	public void reset() {
		model.loadState(originalModel);
		updateUI();
	}

	@Override
	public JComponent createComponent() {
		throw new UnsupportedOperationException();
	}

	@Nls
	@Override
	public String getDisplayName() {
		return "Intellij eval";
	}

	@Override
	public String getHelpTopic() {
		return null;
	}

	@Override
	public void disposeUIResources() {
	}

	public JPanel getRoot() {
		return root;
	}
}
