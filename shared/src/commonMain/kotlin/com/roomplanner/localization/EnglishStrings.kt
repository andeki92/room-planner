package com.roomplanner.localization

/**
 * English language strings.
 * Default language for the app.
 */
object EnglishStrings : Strings {
    // Project Browser Screen
    override val appTitle = "Room Planner"
    override val settingsButton = "Settings"
    override val createProjectButton = "Create Project"
    override val noProjectsYet = "No projects yet"
    override val tapPlusToCreate = "Tap + to create your first project"

    // Settings Screen
    override val settingsTitle = "Settings"
    override val backButton = "Back"
    override val languageLabel = "Language"
    override val measurementUnitsLabel = "Measurement Units"
    override val imperialLabel = "Imperial (feet, inches)"
    override val metricLabel = "Metric (meters, centimeters)"
    override val moreSettingsComingSoon = "More settings coming in Phase 1+"

    // Floor Plan Screen
    override val loadingProject = "Loading..."
    override val floorPlanMode = "Floor Plan Mode"
    override val drawingCanvasComingSoon = "Drawing canvas implementation coming in Phase 1"
    override val drawingInstructions = "Tap to place points • Lines connect automatically"

    override fun vertexCount(count: Int) = "$count point${if (count != 1) "s" else ""}"

    // Create Project Dialog
    override val newProjectTitle = "New Project"
    override val enterProjectNamePrompt = "Enter a name for your project:"
    override val projectNameLabel = "Project Name"
    override val createButton = "Create"
    override val cancelButton = "Cancel"

    // Project Card
    override val deleteButton = "Delete"
    override val deleteProjectTitle = "Delete Project?"

    override fun deleteProjectMessage(projectName: String) =
        "This will permanently delete \"$projectName\". This cannot be undone."

    // Common
    override val loading = "Loading..."
    override val back = "Back"
    override val settings = "Settings"

    // Tool Mode (Phase 1.4b)
    override val drawToolButton = "Draw"
    override val selectToolButton = "Select"
}
