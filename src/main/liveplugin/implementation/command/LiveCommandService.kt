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
import spp.command.LiveCommand
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function

interface LiveCommandService {
    companion object {
        val LIVE_STATUS_MANAGER_FUNCTIONS = Key.create<Function<Array<Any?>, Any?>>("SPP_LIVE_STATUS_MANAGER_FUNCTIONS")
        val PLUGIN_UI_FUNCTIONS = Key.create<Function<Array<Any?>, String>>("PLUGIN_UI_FUNCTIONS")
        val REGISTER = Key.create<BiConsumer<String, BiConsumer<String, Consumer<Array<Any?>>>>>("SPP_COMMAND_REGISTER")
        val UNREGISTER = Key.create<Consumer<String>>("SPP_COMMAND_UNREGISTER")
    }

    val project: Project
    fun init()
    fun registerLiveCommand(command: LiveCommand)
    fun unregisterLiveCommand(commandName: String)
    fun getRegisteredLiveCommands(): List<LiveCommand>
}
