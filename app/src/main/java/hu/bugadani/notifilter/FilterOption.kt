package hu.bugadani.notifilter

/**
 *  Variants are stored as strings. If you rename any of these, you'll need to create a compatibility
 *  enum with the old variants and update [SettingsHelper.load] to convert from old to new.
 */
enum class FilterOption {
    Ignore,
    ManualReset,
    AutoReset1Minute,
    AutoReset5Minutes,
}
