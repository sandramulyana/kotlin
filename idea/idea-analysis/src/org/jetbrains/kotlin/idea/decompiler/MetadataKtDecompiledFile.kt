/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.decompiler

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.decompiler.textBuilder.DecompiledText

class MetadataKtDecompiledFile(provider: KotlinDecompiledFileViewProvider, buildDecompiledText: (VirtualFile) -> DecompiledText) : KtDecompiledFile(provider, buildDecompiledText)
