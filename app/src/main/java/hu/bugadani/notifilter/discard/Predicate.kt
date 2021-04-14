package hu.bugadani.notifilter.discard

import android.service.notification.StatusBarNotification

interface Predicate<IN> {
    fun matches(input: IN) : Boolean
}

class And<IN>(private val a: Predicate<IN>, private val b: Predicate<IN>) : Predicate<IN> {
    override fun matches(input: IN): Boolean {
        return a.matches(input) and b.matches(input)
    }
}

class Or<IN>(private val a: Predicate<IN>, private val b: Predicate<IN>) : Predicate<IN> {
    override fun matches(input: IN): Boolean {
        return a.matches(input) or b.matches(input)
    }
}

class Not<IN>(private val inner: Predicate<IN>) : Predicate<IN> {
    override fun matches(input: IN): Boolean {
        return !inner.matches(input)
    }
}

class Equals<IN>(private val expectation: IN): Predicate<IN> {
    override fun matches(input: IN): Boolean {
        return input == expectation
    }
}

class TickerText(private val inner: Predicate<CharSequence?>): Predicate<StatusBarNotification> {
    override fun matches(input: StatusBarNotification): Boolean {
        return inner.matches(input.notification.tickerText)
    }
}

class Category(private val inner: Predicate<CharSequence?>): Predicate<StatusBarNotification> {
    override fun matches(input: StatusBarNotification): Boolean {
        return inner.matches(input.notification.category)
    }
}