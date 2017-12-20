/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package liveplugin.toolwindow.addplugin.git

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.remote.GitRememberedInputs
import liveplugin.toolwindow.addplugin.git.com.intellij.dvcs.ui.CloneDvcsDialog
import liveplugin.toolwindow.addplugin.git.com.intellij.dvcs.ui.CloneDvcsDialog.TestResult.SUCCESS
import java.io.File

class GitCloneDialog(project: Project, defaultUrl: String?): CloneDvcsDialog(project, GitVcs.NAME, GitUtil.DOT_GIT, defaultUrl) {

    private val myGit = ServiceManager.getService(Git::class.java)

    constructor(project: Project): this(project, null)

    override fun test(url: String): CloneDvcsDialog.TestResult {
        val result = myGit.lsRemote(myProject, File("."), url)
        return if (result.success()) SUCCESS else TestResult(result.errorOutputAsJoinedString)
    }

    override fun getRememberedInputs() = GitRememberedInputs.getInstance()

    override fun getDimensionServiceKey() = "GitCloneDialog"

    override fun getHelpId() = "reference.VersionControl.Git.CloneRepository"
}