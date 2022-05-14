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
package spp.command

import com.intellij.openapi.application.ApplicationManager
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.runBlocking

@Suppress("unused")
abstract class LiveCommand {
    abstract val name: String
    abstract val description: String
    open val params: List<String> = emptyList()
    open val aliases: Set<String> = emptySet()
    open val selectedIcon: String? = null
    open val unselectedIcon: String? = null

    open fun trigger(context: LiveCommandContext) {
        ApplicationManager.getApplication().runReadAction {
            runBlocking {
                triggerSuspend(context)
            }
        }
    }

    open suspend fun triggerSuspend(context: LiveCommandContext) = Unit

    fun toJson(): String {
        return JsonObject().apply {
            put("name", name)
            put("description", description)
            put("params", params)
            put("aliases", aliases)
            put("selectedIcon", selectedIcon)
            put("unselectedIcon", unselectedIcon)
        }.toString()
    }
}
