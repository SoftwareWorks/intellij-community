// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToProvider

/**
 * @author Vladislav.Soroka
 * @since 11/8/2016
 */
class GradleDelegatesToProvider : GrDelegatesToProvider {

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    val file = closure.containingFile
    if (file == null || !FileUtilRt.extensionEquals(file.name, GradleConstants.EXTENSION)) return null

    for (contributor in GradleMethodContextContributor.EP_NAME.extensions) {
      val delegatesToInfo = contributor.getDelegatesToInfo(closure)
      if (delegatesToInfo != null) return delegatesToInfo
    }
    return null
  }
}