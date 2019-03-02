package org.descendant.bootanims

class InvalidAnimationException(errorCode: AnimErrors, description: String? = null) :
    Exception(errorCode.toString() + " caused by " + description.orEmpty())