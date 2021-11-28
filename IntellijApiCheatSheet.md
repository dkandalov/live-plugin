### IntelliJ API mini cheat sheet
You can explore code on GitHub or [Upsourse]:
```
git clone https://github.com/JetBrains/intellij-community.git
```
"Manager" postfix in class names in general means the class is "Facade" for some subsystem. 
"Manager" classes can also have "ManagerEx" implementation with extended functionality 
(e.g. ``ApplicationManager``, ``ApplicationManagerEx``).


#### "Core" classes
- [Application] - provides access to core application-wide functionality and methods for working with the IDEA thread model
- [MessageBus] - core of subscribe/publish messaging infrastructure
- [Project] - represents project in IntelliJ, used in many APIs for project-specific functionality
- [ProjectManager] - open/close/get list of projects
- [ProjectManagerListener] - callback on project open/close
- [DumbAware] - marker interface for actions and toolwindows that can work while indices are being updated


#### Actions
- [AnAction] - all user interactions are performed through actions (see also EditorAction)
- [AnActionEvent] - container for the information necessary to execute or update AnAction
- [ActionManager] - register/unregister/find actions
- [IntentionAction] - interface for intention actions (Alt-Enter actions in editor)
- [IntentionManager] - register/unregister intentions


#### Editing files
- [Editor] - represents an instance of the IDEA text editor
- [CaretModel] - moves the caret and retrieves information about caret position
- [SelectionModel] - selects text in editor and retries information about the selection
- [MarkupModel] - highlights ranges of text in a document, paints markers on the gutter and so on
- [FileEditorManager] - open/close/get current editor
- [Document] - represents the contents of a text file loaded into memory and possibly opened in a text editor
(see also [IntelliJ Platform SDK docs](https://plugins.jetbrains.com/docs/intellij/documents.html))
- [FileDocumentManager] - gets document for VirtualFile, etc.
- [VirtualFile] - represent a file on disk, in archive, HTTP server, etc. 
(see also [IntelliJ Platform SDK docs](http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/virtual_file.html))
- [VirtualFileSystem] - abstraction on top of file systems; delete/move/rename files (see also [IntelliJ Platform SDK docs](https://plugins.jetbrains.com/docs/intellij/virtual-file-system.html))
- [VirtualFileListener] - receives notifications about changes in the virtual file system
- [VirtualFileManager] - add VirtualFile listener, refresh VirtualFileSystem


#### Syntax tree
- [PsiElement] - the common base interface for all elements of the PSI tree (see also [IntelliJ Platform SDK docs](https://plugins.jetbrains.com/docs/intellij/psi-files.html))
- [PsiFile] - PSI element representing a file
- [PsiManager] - gets PsiFile for VirtualFile
- "PsiUtil" classes - misc methods for PSI manipulation


#### Project structure
- [ProjectRootManager] - query and modify the list of root files and directories belonging to a project
- [ModuleManager] - get/modify project modules
- [ModuleRootManager] - contains information about the contents and dependencies of a module


#### UI elements
- [Messages] - various dialogs with yes/no/ok/cancel buttons
- [DialogBuilder] - builder for custom dialogs
- [ToolWindowManager] - get registered tool windows, balloon notification for tool windows
- [ToolWindowFactory] - creates tool windows
- See also [User Interface Components IntelliJ Platform SDK page](https://plugins.jetbrains.com/docs/intellij/user-interface-components.html)


#### Threading rules
- EDT read: just do it
- EDT write: runWriteAction{...}
- Other threads read: runReadAction{...}
- Other threads write: N/A (i.e. invokeOnEDT{...})
- See also [General Threading Rules].


### Other useful APIs
- com.intellij.codeInsight.completion.CompletionContributor
- com.intellij.lang.LanguageExtension
- com.intellij.patterns.PsiElementPattern (and com.intellij.patterns.PlatformPatterns, com.intellij.patterns.StandardPatterns)
- com.intellij.psi.util.PsiUtilCore


[Upsourse]: https://upsource.jetbrains.com/idea-ce/structure/HEAD
[Application]: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/application/Application.java
[MessageBus]: https://github.com/JetBrains/intellij-community/blob/master/platform/extensions/src/com/intellij/util/messages/MessageBus.java
[Project]: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/project/Project.java
[ProjectManager]: https://github.com/JetBrains/intellij-community/blob/master/platform/projectModel-api/src/com/intellij/openapi/project/ProjectManager.java
[ProjectManagerListener]: https://github.com/JetBrains/intellij-community/blob/master/platform/projectModel-api/src/com/intellij/openapi/project/ProjectManagerListener.java
[DumbAware]: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/project/DumbAware.java

[AnAction]: https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/actionSystem/AnAction.java
[AnActionEvent]: https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/actionSystem/AnActionEvent.java
[ActionManager]: https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/actionSystem/ActionManager.java
[IntentionAction]: https://github.com/JetBrains/intellij-community/blob/master/platform/analysis-api/src/com/intellij/codeInsight/intention/IntentionAction.java
[IntentionManager]: https://github.com/JetBrains/intellij-community/blob/master/platform/analysis-api/src/com/intellij/codeInsight/intention/IntentionManager.java

[Editor]: https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/editor/Editor.java
[CaretModel]: https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/editor/CaretModel.java
[SelectionModel]: https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/editor/SelectionModel.java
[MarkupModel]: https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/editor/markup/MarkupModel.java
[FileEditorManager]: https://github.com/JetBrains/intellij-community/blob/master/platform/analysis-api/src/com/intellij/openapi/fileEditor/FileEditorManager.java
[Document]: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/editor/Document.java
[FileDocumentManager]: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/fileEditor/FileDocumentManager.java
[VirtualFile]: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/vfs/VirtualFile.java
[VirtualFileSystem]: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/vfs/VirtualFileSystem.java
[VirtualFileListener]: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/vfs/VirtualFileListener.java
[VirtualFileManager]: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/vfs/VirtualFileManager.java

[PsiElement]: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/psi/PsiElement.java
[PsiFile]: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/psi/PsiFile.java
[PsiManager]: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/psi/PsiManager.java

[ProjectRootManager]: https://github.com/JetBrains/intellij-community/blob/master/platform/projectModel-api/src/com/intellij/openapi/roots/ProjectRootManager.java
[ModuleManager]: https://github.com/JetBrains/intellij-community/blob/master/platform/projectModel-api/src/com/intellij/openapi/module/ModuleManager.java
[ModuleRootManager]: https://github.com/JetBrains/intellij-community/blob/master/platform/projectModel-api/src/com/intellij/openapi/roots/ModuleRootManager.java

[Messages]: https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/ui/Messages.java
[DialogBuilder]: https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/ui/DialogBuilder.java
[ToolWindowManager]: https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/wm/ToolWindowManager.kt
[ToolWindowFactory]: https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/wm/ToolWindowFactory.java

[General Threading Rules]: http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html
