package liveplugin

import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.ui.treeStructure.SimpleTreeBuilder
import com.intellij.ui.treeStructure.SimpleTreeStructure

import javax.swing.*
import javax.swing.tree.DefaultTreeModel

class TreeUIUtil {
	interface TreeNode<T> {
		Collection<TreeNode<T>> children()
		PresentationData presentation()
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

	static JComponent createTree(TreeNode root) {
		def rootNode = new DelegatingNode(root)
		def tree = new SimpleTree()
		def treeStructure = new SimpleTreeStructure() {
			@Override Object getRootElement() {
				rootNode
			}
		}
		def treeBuilder = new SimpleTreeBuilder(tree, tree.getModel() as DefaultTreeModel, treeStructure, null)
		treeBuilder.initRoot()
		treeBuilder.expand(rootNode, null)

		ScrollPaneFactory.createScrollPane(tree)
	}
}
