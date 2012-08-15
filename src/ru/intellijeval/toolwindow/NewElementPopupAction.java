package ru.intellijeval.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import ru.intellijeval.toolwindow.fileChooser.actions.NewFolderAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;

/**
 * User: dima
 * Date: 13/08/2012
 */
class NewElementPopupAction extends AnAction implements DumbAware, PopupAction {
	public void actionPerformed(final AnActionEvent event) {
		showPopup(event.getDataContext());
	}

	protected void showPopup(DataContext context) {
		createPopup(context).showInBestPositionFor(context);
	}

	protected ListPopup createPopup(DataContext dataContext) {
		return JBPopupFactory.getInstance().createActionGroupPopup(
				IdeBundle.message("title.popup.new.element"),
				getGroup(),
				dataContext,
				false,
				false,
				false,
				null,
				-1,
				LangDataKeys.PRESELECT_NEW_ACTION_CONDITION.getData(dataContext)
		);
	}

	private ActionGroup getGroup() {
		return new ActionGroup() {
			@NotNull @Override
			public AnAction[] getChildren(AnActionEvent e) {
				return new AnAction[]{
						new NewFolderAction("Directory", "Directory", AllIcons.Nodes.Folder),
						new NewFileAction("File", AllIcons.FileTypes.Text)
				};
			}
		};
	}

	public void update(AnActionEvent e) {
		Presentation presentation = e.getPresentation();
		presentation.setEnabled(true);
	}
}
