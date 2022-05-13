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
package liveplugin.implementation.command.impl

import liveplugin.implementation.command.LiveCommandService
import spp.command.LiveCommand

class LiveCommandServiceImpl : LiveCommandService {

    private val commands = mutableSetOf<LiveCommand>()

    override fun registerLiveCommand(command: LiveCommand) {
        commands.add(command)
    }

    override fun unregisterLiveCommand(commandName: String) {
        commands.removeIf { it.name == commandName }
    }

    override fun getRegisteredLiveCommands(): List<LiveCommand> {
        return commands.toList()
    }
}
