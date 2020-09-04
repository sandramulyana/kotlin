/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.overrides

import org.jetbrains.kotlin.ir.declarations.IrOverridableMember
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.resolve.OverridingUtil
import org.jetbrains.kotlin.resolve.OverridingUtil.OverrideCompatibilityInfo.incompatible
import org.jetbrains.kotlin.types.AbstractTypeChecker
import org.jetbrains.kotlin.types.AbstractTypeCheckerContext
import org.jetbrains.kotlin.ir.types.IrTypeCheckerContextWithAdditionalAxioms

fun isOverridableBy(
    irBuiltIns: IrBuiltIns,
    superMember: IrOverridableMember,
    subMember: IrOverridableMember
    // subClass: IrClass?, Would only be needed for external overridability conditions.
): Boolean {
    return isOverridableBy(irBuiltIns, superMember, subMember, false).result == OverridingUtil.OverrideCompatibilityInfo.Result.OVERRIDABLE
}

fun isOverridableBy(
    irBuiltIns: IrBuiltIns,
    superMember: IrOverridableMember,
    subMember: IrOverridableMember,
    // subClass: IrClass?, Would only be needed for external overridability conditions.
    checkReturnType: Boolean
): OverridingUtil.OverrideCompatibilityInfo {
    val basicResult = isOverridableByWithoutExternalConditions(irBuiltIns, superMember, subMember, checkReturnType)
    return if (basicResult.result == OverridingUtil.OverrideCompatibilityInfo.Result.OVERRIDABLE)
        OverridingUtil.OverrideCompatibilityInfo.success()
    else
        basicResult
    // The frontend goes into external overridability condition details here, but don't deal with them in IR (yet?).
}

private val IrOverridableMember.compiledValueParameters
    get() = when (this) {
        is IrSimpleFunction -> extensionReceiverParameter?.let { listOf(it) + valueParameters } ?: valueParameters
        is IrProperty -> getter!!.extensionReceiverParameter?.let { listOf(it) } ?: emptyList()
        else -> error("Unexpected declaration for compiledValueParameters: $this")
    }

private val IrOverridableMember.returnType
    get() = when (this) {
        is IrSimpleFunction -> this.returnType
        is IrProperty -> this.getter!!.returnType
        else -> error("Unexpected declaration for returnType: $this")
    }

private val IrOverridableMember.typeParameters
    get() = when (this) {
        is IrSimpleFunction -> this.typeParameters
        is IrProperty -> this.getter!!.typeParameters
        else -> error("Unexpected declaration for typeParameters: $this")
    }

private fun isOverridableByWithoutExternalConditions(
    irBuiltIns: IrBuiltIns,
    superMember: IrOverridableMember,
    subMember: IrOverridableMember,
    checkReturnType: Boolean
): OverridingUtil.OverrideCompatibilityInfo {
    val basicOverridability = getBasicOverridabilityProblem(superMember, subMember)
    if (basicOverridability != null) return basicOverridability

    val superValueParameters = superMember.compiledValueParameters
    val subValueParameters = subMember.compiledValueParameters
    val superTypeParameters = superMember.typeParameters
    val subTypeParameters = subMember.typeParameters

    if (superTypeParameters.size != subTypeParameters.size) {
        /* TODO: do we need this in IR?
        superValueParameters.forEachIndexed { index, superParameter ->
            if (!AbstractTypeChecker.equalTypes(
                    defaultTypeCheckerContext as AbstractTypeCheckerContext,
                    superParameter.type,
                    subValueParameters[index].type
                )
            ) {
                return OverrideCompatibilityInfo.incompatible("Type parameter number mismatch")
            }
        }
        return OverrideCompatibilityInfo.conflict("Type parameter number mismatch")
        */

        return incompatible("Type parameter number mismatch")
    }

    val typeCheckerContext =
        IrTypeCheckerContextWithAdditionalAxioms(irBuiltIns, superTypeParameters, subTypeParameters)

    /* TODO: check the bounds. See OverridingUtil.areTypeParametersEquivalent()
    superTypeParameters.forEachIndexed { index, parameter ->
        if (!AbstractTypeChecker.areTypeParametersEquivalent(
                typeCheckerContext as AbstractTypeCheckerContext,
                subTypeParameters[index].type,
                parameter.type
            )
        ) return OverrideCompatibilityInfo.incompatible("Type parameter bounds mismatch")
    }
    */

    assert(superValueParameters.size == subValueParameters.size)

    superValueParameters.forEachIndexed { index, parameter ->
        if (!AbstractTypeChecker.equalTypes(
                typeCheckerContext as AbstractTypeCheckerContext,
                subValueParameters[index].type,
                parameter.type
            )
        ) return incompatible("Value parameter type mismatch")
    }

    if (superMember is IrSimpleFunction && subMember is IrSimpleFunction && superMember.isSuspend != subMember.isSuspend) {
        return OverridingUtil.OverrideCompatibilityInfo.conflict("Incompatible suspendability")
    }

    if (checkReturnType) {
        if (!AbstractTypeChecker.isSubtypeOf(
                typeCheckerContext as AbstractTypeCheckerContext,
                subMember.returnType,
                superMember.returnType
            )
        ) return OverridingUtil.OverrideCompatibilityInfo.conflict("Return type mismatch")
    }
    return OverridingUtil.OverrideCompatibilityInfo.success()
}

private fun getBasicOverridabilityProblem(
    superMember: IrOverridableMember,
    subMember: IrOverridableMember
): OverridingUtil.OverrideCompatibilityInfo? {
    if (superMember is IrSimpleFunction && subMember !is IrSimpleFunction ||
        superMember is IrProperty && subMember !is IrProperty
    ) {
        return incompatible("Member kind mismatch")
    }
    require((superMember is IrSimpleFunction || superMember is IrProperty)) {
        "This type of IrDeclaration cannot be checked for overridability: $superMember"
    }

    return if (superMember.name != subMember.name) {
        incompatible("Name mismatch")
    } else
        checkReceiverAndParameterCount(superMember, subMember)
}

private fun checkReceiverAndParameterCount(
    superMember: IrOverridableMember,
    subMember: IrOverridableMember
): OverridingUtil.OverrideCompatibilityInfo? {
    return when (superMember) {
        is IrSimpleFunction -> {
            require(subMember is IrSimpleFunction)
            when {
                superMember.extensionReceiverParameter == null != (subMember.extensionReceiverParameter == null) -> {
                    incompatible("Receiver presence mismatch")
                }
                superMember.valueParameters.size != subMember.valueParameters.size -> {
                    incompatible("Value parameter number mismatch")
                }
                else -> null
            }
        }
        is IrProperty -> {
            require(subMember is IrProperty)
            if (superMember.getter?.extensionReceiverParameter == null != (subMember.getter?.extensionReceiverParameter == null)) {
                incompatible("Receiver presence mismatch")
            } else null
        }
        else -> error("Unxpected declaration for value parameter check: $superMember")
    }
}
