package com.roomplanner.localization

/**
 * Norwegian (Bokmål) language strings.
 */
object NorwegianStrings : Strings {
    // Project Browser Screen
    override val appTitle = "Romplanlegger"
    override val settingsButton = "Innstillinger"
    override val createProjectButton = "Opprett prosjekt"
    override val noProjectsYet = "Ingen prosjekter ennå"
    override val tapPlusToCreate = "Trykk + for å opprette ditt første prosjekt"

    // Settings Screen
    override val settingsTitle = "Innstillinger"
    override val backButton = "Tilbake"
    override val languageLabel = "Språk"
    override val measurementUnitsLabel = "Måleenheter"
    override val imperialLabel = "Imperial (fot, tommer)"
    override val metricLabel = "Metrisk (meter, centimeter)"
    override val moreSettingsComingSoon = "Flere innstillinger kommer i fase 1+"

    // Floor Plan Screen
    override val loadingProject = "Laster..."
    override val floorPlanMode = "Plantegning-modus"
    override val drawingCanvasComingSoon = "Implementering av tegneflate kommer i fase 1"
    override val drawingInstructions = "Trykk for å plassere punkter • Linjer kobles automatisk"

    override fun vertexCount(count: Int) = "$count punkt${if (count != 1) "er" else ""}"

    // Create Project Dialog
    override val newProjectTitle = "Nytt prosjekt"
    override val enterProjectNamePrompt = "Skriv inn et navn for prosjektet ditt:"
    override val projectNameLabel = "Prosjektnavn"
    override val createButton = "Opprett"
    override val cancelButton = "Avbryt"

    // Project Card
    override val deleteButton = "Slett"
    override val deleteProjectTitle = "Slett prosjekt?"

    override fun deleteProjectMessage(projectName: String) =
        "Dette vil permanent slette \"$projectName\". Dette kan ikke angres."

    // Common
    override val loading = "Laster..."
    override val back = "Tilbake"
    override val settings = "Innstillinger"

    // Tool Mode (Phase 1.4b)
    override val drawToolButton = "Tegn"
    override val selectToolButton = "Velg"
}
