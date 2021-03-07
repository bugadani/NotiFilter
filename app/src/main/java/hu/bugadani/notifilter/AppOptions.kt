package hu.bugadani.notifilter

data class AppOptions(
    val filterOption: FilterOption,
    val overrideGroups: Boolean
) {
    fun isEmpty(): Boolean {
        return filterOption == FilterOption.Ignore && !overrideGroups
    }
}
