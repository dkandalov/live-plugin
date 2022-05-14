/*
 * Source++, the open-source live coding platform.
 * Copyright (C) 2022 CodeBrig, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package liveplugin.implementation.command

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import liveplugin.implementation.command.impl.LiveCommandServiceImpl
import spp.command.LiveCommand

interface LiveCommandService {
    fun registerLiveCommand(command: LiveCommand)
    fun unregisterLiveCommand(commandName: String)
    fun getRegisteredLiveCommands(): List<LiveCommand>

    companion object {
        private val cache = mutableMapOf<Project, LiveCommandService>()
        fun getInstance(project: Project): LiveCommandService {
            return cache.getOrPut(project) { LiveCommandServiceImpl(project) }
        }

        val KEY = Key.create<LiveCommandService>("SPP_LIVE_COMMAND_SERVICE")
        val LIVE_COMMAND_LOADER = Key.create<() -> Unit>("SPP_LIVE_COMMAND_LOADER")
    }
}
