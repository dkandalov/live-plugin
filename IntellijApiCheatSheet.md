### IntelliJ API mini cheat sheet

Getting source code (see also [Build Community Edition page](http://www.jetbrains.org/pages/viewpage.action?pageId=983225)):
```
git clone https://github.com/JetBrains/intellij-community.git
```
"Manager" postfix in class names in general means the class is "Facade" for some subsystem. 
"Manager" classes can also have "ManagerEx" implementation with extended functionality 
(e.g. ``ApplicationManager``, ``ApplicationManagerEx``).


#### "Core" classes

- [Application](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/application/Application.java) - 
provides access to core application-wide functionality and methods for working with the IDEA thread model
- [MessageBus](https://github.com/JetBrains/intellij-community/blob/master/platform/util/src/com/intellij/util/messages/MessageBus.java) - 
core of subscribe/publish messaging infrastructure
- [Project](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/project/Project.java) - 
represents project in IntelliJ, used in many APIs for project-specific functionality
- [ProjectManager](https://github.com/JetBrains/intellij-community/blob/master/platform/projectModel-api/src/com/intellij/openapi/project/ProjectManager.java) - 
open/close/get list of projects
- [ProjectManagerListener](https://github.com/JetBrains/intellij-community/blob/master/platform/projectModel-api/src/com/intellij/openapi/project/ProjectManagerListener.java) - 
callback on project open/close


#### Actions

- [AnAction](https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/actionSystem/AnAction.java) - 
all user interactions are performed through actions (see also EditorAction)
- [AnActionEvent](https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/actionSystem/AnActionEvent.java) - 
container for the information necessary to execute or update AnAction
- [ActionManager](https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/actionSystem/ActionManager.java) - 
register/unregister/find actions
- [IntentionAction](https://github.com/JetBrains/intellij-community/blob/master/platform/analysis-api/src/com/intellij/codeInsight/intention/IntentionAction.java) - 
interface for intention actions (Alt-Enter actions in editor)
- [IntentionManager](https://github.com/JetBrains/intellij-community/blob/master/platform/analysis-api/src/com/intellij/codeInsight/intention/IntentionManager.java) - 
register/unregister intentions


#### Editing files

- [Editor](https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/editor/Editor.java) - 
represents an instance of the IDEA text editor
- [CaretModel](https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/editor/CaretModel.java) - 
moves the caret and retrieves information about caret position
- [SelectionModel](https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/editor/SelectionModel.java) - 
selects text in editor and retries information about the selection
- [MarkupModel](https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/editor/markup/MarkupModel.java) - 
highlights ranges of text in a document, paints markers on the gutter and so on
- [FileEditorManager](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/fileEditor/FileEditorManager.java) - 
open/close/get current editor
- [Document](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/editor/Document.java) - 
represents the contents of a text file loaded into memory and possibly opened in a text editor
(see also [IntelliJ Platform SDK page](http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/documents.html))
- [FileDocumentManager](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/fileEditor/FileDocumentManager.java) - 
gets document for VirtualFile, etc.
- [VirtualFile](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/vfs/VirtualFile.java) - 
represent a file on disk, in archive, HTTP server, etc. 
(see also [IntelliJ Platform SDK page](http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/virtual_file.html))
- [VirtualFileSystem](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/vfs/VirtualFileSystem.java) - 
abstraction on top of file systems; delete/move/rename files (see also [IntelliJ Platform SDK page](http://www.jetbrains.org/intellij/sdk/docs/basics/virtual_file_system.html))
- [VirtualFileListener](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/vfs/VirtualFileListener.java) - 
receives notifications about changes in the virtual file system
- [VirtualFileManager](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/vfs/VirtualFileManager.java) - 
add VirtualFile listener, refresh VirtualFileSystem
- [PsiElement](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/psi/PsiElement.java) - 
the common base interface for all elements of the PSI tree (see also [IntelliJ Platform SDK page](http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/psi_files.html))


#### Syntax tree
- [PsiFile](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/psi/PsiFile.java) - 
PSI element representing a file
- [PsiManager](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/psi/PsiManager.java) - 
gets PsiFile for VirtualFile
- [PsiUtil](https://github.com/JetBrains/intellij-community/blob/master/plugins/devkit/src/util/PsiUtil.java) - 
misc methods for PSI manipulation


#### Project structure

- [ProjectRootManager](https://github.com/JetBrains/intellij-community/blob/master/platform/projectModel-api/src/com/intellij/openapi/roots/ProjectRootManager.java) - 
allows to query and modify the list of root files and directories belonging to a project
- [ModuleManager](https://github.com/JetBrains/intellij-community/blob/master/platform/projectModel-api/src/com/intellij/openapi/module/ModuleManager.java) - 
get/modify project modules
- [ModuleRootManager](https://github.com/JetBrains/intellij-community/blob/master/platform/projectModel-api/src/com/intellij/openapi/roots/ModuleRootManager.java) - 
contains information about the contents and dependencies of a module


#### UI elements

- [Messages](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/ui/Messages.java) - 
various dialogs with yes/no/ok/cancel buttons
- [DialogBuilder](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/ui/DialogBuilder.java) - 
builder for custom dialogs
- [ToolWindowManager](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/wm/ToolWindowManager.java) - 
register/unregister tool windows

See also [User Interface Components IntelliJ Platform SDK page](http://www.jetbrains.org/intellij/sdk/docs/user_interface_components/user_interface_components.html).


#### Threading rules

- EDT read: just do it
- EDT write: runWriteAction{...}
- Other threads read: runReadAction{...}
- Other threads write: N/A (i.e. invokeOnEDT{...})

See also 
[General Threading Rules](http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html)
in IntelliJ Platform SDK documentation.
