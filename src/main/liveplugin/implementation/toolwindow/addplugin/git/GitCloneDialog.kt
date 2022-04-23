package liveplugin.implementation.toolwindow.addplugin.git

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.remote.GitRememberedInputs
import liveplugin.implementation.toolwindow.addplugin.git.com.intellij.dvcs.ui.CloneDvcsDialog
import liveplugin.implementation.toolwindow.addplugin.git.com.intellij.dvcs.ui.CloneDvcsDialog.TestResult.SUCCESS
import java.io.File

class GitCloneDialog(project: Project): CloneDvcsDialog(project, GitVcs.NAME, GitUtil.DOT_GIT, null) {

    private val myGit = service<Git>()

    override fun test(url: String): TestResult {
        val result = myGit.lsRemote(myProject, File("."), url)
        return if (result.success()) SUCCESS else TestResult(result.errorOutputAsJoinedString)
    }

    override fun getRememberedInputs() = GitRememberedInputs.getInstance()

    override fun getDimensionServiceKey() = "GitCloneDialog"

    override fun getHelpId() = "reference.VersionControl.Git.CloneRepository"
}