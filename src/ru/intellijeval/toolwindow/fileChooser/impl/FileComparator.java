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

/**
 * @author Yura Cangea
 */
package ru.intellijeval.toolwindow.fileChooser.impl;

import com.intellij.ide.util.treeView.NodeDescriptor;
import ru.intellijeval.toolwindow.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Comparator;

public final class FileComparator implements Comparator<NodeDescriptor> {
  private static final ru.intellijeval.toolwindow.fileChooser.impl.FileComparator INSTANCE = new ru.intellijeval.toolwindow.fileChooser.impl.FileComparator();

  private FileComparator() {
    // empty
  }

  public static ru.intellijeval.toolwindow.fileChooser.impl.FileComparator getInstance() {
    return INSTANCE;
  }

  public int compare(NodeDescriptor nodeDescriptor1, NodeDescriptor nodeDescriptor2) {
    int weight1 = getWeight(nodeDescriptor1);
    int weight2 = getWeight(nodeDescriptor2);

    if (weight1 != weight2) {
      return weight1 - weight2;
    }

    return nodeDescriptor1.toString().compareToIgnoreCase(nodeDescriptor2.toString());
  }

   private static int getWeight(NodeDescriptor descriptor) {
     VirtualFile file = ((FileNodeDescriptor)descriptor).getElement().getFile();
     return file == null || file.isDirectory() ? 0 : 1;
   }
}
