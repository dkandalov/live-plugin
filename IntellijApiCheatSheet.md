### IntelliJ API mini cheat sheet

Getting source code:
```
git clone https://github.com/JetBrains/intellij-community.git
```
"Manager" means "Facade". Managers can also have "ManagerEx" implementation with extended functionality.

#### "Core" classes

Application - provides access to core application-wide functionality and methods for working with the IDEA thread model
MessageBus - core of subscribe/publish messaging infrastructure

Project - represents project in IntelliJ, used in many APIs for project-specific functionality
ProjectManager - open/close/get list of projects
ProjectManagerListener - callback on project open/close


#### Actions

AnAction - all user interactions are performed through actions (see also EditorAction)
AnActionEvent - container for the information necessary to execute or update AnAction
ActionManager - register/unregister/find actions
IntentionAction - interface for intention actions (Alt-Enter actions in editor)
IntentionManager - register/unregister intentions


#### Editing files

Editor - represents an instance of the IDEA text editor
CaretModel - moves the caret and retrieves information about caret position
SelectionModel - selects text in editor and retries information about the selection
MarkupModel - highlights ranges of text in a document, paints markers on the gutter and so on
FileEditorManager - open/close/get current editor

Document - represents the contents of a text file loaded into memory and possibly opened in a text editor
(http://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+Architectural+Overview#IntelliJIDEAArchitecturalOverview-Documents)
FileDocumentManager - gets document for VirtualFile, etc.

VirtualFile - represent a file on disk, in archive, HTTP server, etc. (http://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+Architectural+Overview#IntelliJIDEAArchitecturalOverview-VirtualFiles)
VirtualFileSystem - abstraction on top of file systems; delete/move/rename files
(http://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+Virtual+File+System)
VirtualFileListener - receives notifications about changes in the virtual file system
VirtualFileManager - add VirtualFile listener, refresh VirtualFileSystem

PsiElement - the common base interface for all elements of the PSI tree
(http://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+Architectural+Overview#IntelliJIDEAArchitecturalOverview-PsiFiles)
PsiFile - PSI element representing a file
PsiManager - gets PsiFile for VirtualFile
PsiUtil - misc methods for PSI manipulation


#### Project structure

ProjectRootManager - allows to query and modify the list of root files and directories belonging to a project
ModuleManager - get/modify project modules
ModuleRootManager - contains information about the contents and dependencies of a module

#### UI elements

Messages - various dialogs with yes/no/ok/cancel buttons
DialogBuilder - builder for custom dialogs
ToolWindowManager - register/unregister tool windows


#### Threading rules
EDT read: just do it
EDT write: runWriteAction{...}
Other threads read: runReadAction{...}
Other threads write: N/A (i.e. invokeOnEDT{...})
See also 
[General Threading Rules](http://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+Architectural+Overview#IntelliJIDEAArchitecturalOverview-Threading)
on confluence.
