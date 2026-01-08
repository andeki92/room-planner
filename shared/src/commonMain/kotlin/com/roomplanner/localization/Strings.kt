package com.roomplanner.localization

/**
 * Complete contract for all app strings.
 * Every language implementation MUST implement all properties.
 * Missing translations = compilation error (compile-time safety).
 */
interface Strings {
    // ==================== Project Browser Screen ====================
    val appTitle: String
    val settingsButton: String
    val createProjectButton: String
    val noProjectsYet: String
    val tapPlusToCreate: String

    // ==================== Settings Screen ====================
    val settingsTitle: String
    val backButton: String
    val languageLabel: String
    val measurementUnitsLabel: String
    val imperialLabel: String
    val metricLabel: String
    val moreSettingsComingSoon: String

    // ==================== Floor Plan Screen ====================
    val loadingProject: String
    val floorPlanMode: String
    val drawingCanvasComingSoon: String

    // ==================== Create Project Dialog ====================
    val newProjectTitle: String
    val enterProjectNamePrompt: String
    val projectNameLabel: String
    val createButton: String
    val cancelButton: String

    // ==================== Project Card ====================
    val deleteButton: String
    val deleteProjectTitle: String

    /**
     * Formatted string with project name placeholder.
     * Example: "This will permanently delete 'Kitchen'. This cannot be undone."
     */
    fun deleteProjectMessage(projectName: String): String

    // ==================== Common ====================
    val loading: String
    val back: String
    val settings: String
}
