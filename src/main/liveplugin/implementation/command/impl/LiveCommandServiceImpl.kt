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

import com.intellij.openapi.project.Project
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import liveplugin.implementation.command.LiveCommandService
import spp.command.LiveCommand
import spp.command.LiveCommandContext
import java.util.function.BiConsumer
import java.util.function.Consumer

class LiveCommandServiceImpl(override val project: Project) : LiveCommandService {

    private val commands = mutableSetOf<LiveCommand>()

    override fun init() {
        project.putUserData(
            LiveCommandService.UNREGISTER,
            Consumer<String> { commandName ->
                unregisterLiveCommand(commandName)
            })
        project.putUserData(
            LiveCommandService.REGISTER,
            BiConsumer<String, BiConsumer<String, Consumer<Array<Any?>>>> { data, trigger ->
                val command = JsonObject(data)
                registerLiveCommand(DevLiveCommand(command, trigger))
            })
    }

    override fun registerLiveCommand(command: LiveCommand) {
        commands.add(command)
    }

    override fun unregisterLiveCommand(commandName: String) {
        commands.removeIf { it.name == commandName }
    }

    override fun getRegisteredLiveCommands(): List<LiveCommand> {
        return commands.toList()
    }

    private inner class DevLiveCommand(
        val command: JsonObject, val trigger: BiConsumer<String, Consumer<Array<Any?>>>
    ) : LiveCommand() {
        override val name: String
            get() = command.getString("name")
        override val description: String
            get() = command.getString("description")
        override val params: List<String>
            get() = command.getJsonArray("params").map { it.toString() }
        override val aliases: Set<String>
            get() = command.getJsonArray("aliases")?.map { it.toString() }?.toSet() ?: emptySet()
        override val selectedIcon: String?
            get() = command.getString("selectedIcon")
        override val unselectedIcon: String?
            get() = command.getString("unselectedIcon")

        override fun trigger(context: LiveCommandContext) {
            val contextJson = JsonObject()
            contextJson.put("args", context.args)
            contextJson.put("sourceFile", context.sourceFile.absolutePath)
            contextJson.put("lineNumber", context.lineNumber)
            contextJson.put("artifactQualifiedName", context.artifactQualifiedName)
            context.guideArtifactQualifiedName?.let { contextJson.put("guideArtifactQualifiedName", it) }

            val userData = JsonObject()
            context.getUserData().forEach {
                try {
                    Json.encode(it.value) // check if it is json serializable
                    userData.put(it.key.toString(), it.value)
                } catch (e: Exception) {
                    println("Failed to encode user data: " + it.key + ": " + it.value)
                }
            }
            contextJson.put("userData", userData)

            trigger.accept(contextJson.toString(), context.eventConsumer)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as DevLiveCommand
            if (name != other.name) return false
            return true
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }
    }
}
