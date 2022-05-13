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

import spp.jetbrains.marker.source.mark.api.event.IEventCode
import spp.jetbrains.marker.source.mark.guide.GuideMark
import spp.protocol.artifact.ArtifactQualifiedName
import java.io.File
import java.util.function.Consumer

class LiveCommandContext(
    val args: List<String>,
    val sourceFile: File,
    val lineNumber: Int,
    val artifactQualifiedName: ArtifactQualifiedName,
    val guideArtifactQualifiedName: ArtifactQualifiedName? = null,
    internal val eventConsumer: Consumer<Array<Any?>>
) {
    private val userData: MutableMap<Any, Any> = mutableMapOf()

    fun getUserData(): Map<Any, Any> = userData

    fun getUserData(key: String): Any? {
        return userData[key]
    }

    fun putUserData(key: String, value: Any?) {
        if (value != null) {
            userData.put(key, value)
        } else {
            userData.remove(key)
        }
    }

    /**
     * Trigger event on [GuideMark].
     */
    fun triggerEvent(eventCode: IEventCode, params: List<Any?>, listen: (() -> Unit)? = null) {
        eventConsumer.accept(arrayOf(eventCode, params, listen))
    }
}
