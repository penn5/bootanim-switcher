package org.descendant.bootanims

class InvalidAnimationException(val errorCode: AnimErrors, val description: String? = null) :
    Exception(errorCode.toString() + " caused by " + description.orEmpty())