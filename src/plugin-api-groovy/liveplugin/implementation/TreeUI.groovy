package liveplugin.implementation

import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.Disposable
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.ui.treeStructure.SimpleTreeStructure
import com.intellij.util.ui.tree.TreeUtil

import javax.swing.*

class TreeUI {
	interface TreeNode<T> {
		Collection<TreeNode<T>> children()
		PresentationData presentation()
	}

	static JComponent createTree(TreeNode root, Disposable disposable) {
		def rootNode = new DelegatingNode(root)
		def treeStructure = new SimpleTreeStructure() {
			@Override Object getRootElement() {
				rootNode
			}
		}
		def treeModel = new StructureTreeModel(treeStructure, disposable)
		def asyncTreeModel = new AsyncTreeModel(treeModel, disposable)
		def tree = new SimpleTree(asyncTreeModel)

		TreeUtil.installActions(tree)
		TreeUIHelper.getInstance().installTreeSpeedSearch(tree)
		ScrollPaneFactory.createScrollPane(tree)
	}

	private static class DelegatingNode extends SimpleNode {
		private final TreeNode delegate

		DelegatingNode(TreeNode delegate, DelegatingNode parent = null) {
			super(parent)
			this.delegate = delegate
			templatePresentation.applyFrom(delegate.presentation())
		}

		@Override SimpleNode[] getChildren() {
			delegate.children().collect { new DelegatingNode(it, this) }
		}
	}
}
