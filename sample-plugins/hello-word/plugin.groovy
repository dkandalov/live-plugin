import com.intellij.openapi.project.Project

import static util.Util.*
import org.mockito.Mockito

//-- classpath: /Users/dima/.m2/repository/org/mockito/mockito-all/1.8.4/mockito-all-1.8.4.jar

showPopup("hello world!", event)
showPopup2("hello world 2")
Mockito.mock(Project.class)

